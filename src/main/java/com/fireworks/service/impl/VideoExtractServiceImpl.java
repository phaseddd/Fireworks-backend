package com.fireworks.service.impl;

import com.fireworks.service.VideoExtractService;
import com.fireworks.dto.VideoExtractResult;
import com.fireworks.enums.VideoExtractStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import lombok.extern.slf4j.Slf4j;
import org.htmlunit.BrowserVersion;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.util.WebConnectionWrapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频提取服务实现
 * <p>
 * 核心功能：从二维码图片中提取视频播放地址
 * <p>
 * 设计原则：
 * <ul>
 *   <li>通用优先：使用正则表达式匹配，一套代码覆盖大多数场景</li>
 *   <li>多二维码支持：一张图片可能包含多个二维码（公众号码+视频码），遍历尝试</li>
 *   <li>分层提取：直链 → 已知平台API → HtmlUnit渲染提取</li>
 *   <li>静默失败：任何异常都不抛出，返回失败状态供调用方处理</li>
 * </ul>
 * <p>
 * 提取策略（按优先级）：
 * <ol>
 *   <li>直链检测：二维码内容本身就是 .mp4/.m3u8 URL</li>
 *   <li>已知平台API：针对 fwmall、虎城等 SPA 站点直接调用数据接口</li>
 *   <li>HtmlUnit渲染：执行 JS 后从 DOM 或网络响应中提取视频URL</li>
 *   <li>兜底标记：渲染后仍失败则标记 NEED_DYNAMIC_RENDER，记录目标网址</li>
 * </ol>
 *
 * @see ProductVideoExtractAsyncService 异步调用入口
 */
@Slf4j
@Service
public class VideoExtractServiceImpl implements VideoExtractService {

    // ==================== 超时配置 ====================
    /** 图片下载超时时间（毫秒） */
    private static final int IMAGE_DOWNLOAD_TIMEOUT_MS = 10000;
    /** HTTP 请求超时时间（毫秒） */
    private static final int HTTP_TIMEOUT_MS = 10000;
    /** 视频URL验证超时时间（毫秒） */
    private static final int VIDEO_URL_VALIDATE_TIMEOUT_MS = 5000;
    /** HTTP 请求 User-Agent */
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    /** 二维码图片放大阈值（像素），小于此值会放大以提高识别率 */
    private static final int QR_SCALE_THRESHOLD_PX = 640;
    /** HtmlUnit JS 执行等待时间（毫秒） */
    private static final int HTMLUNIT_JS_WAIT_MS = 8000;
    /** HtmlUnit 后台 JS 等待时间（毫秒） */
    private static final int HTMLUNIT_BACKGROUND_JS_WAIT_MS = 3000;
    /** HtmlUnit 最大跟随页面数（处理 JS 跳转） */
    private static final int HTMLUNIT_MAX_FOLLOW_UP_PAGES = 2;

    /** JSON 解析器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ==================== 已知平台 API 模板 ====================
    /** 虎城烟花视频 API 地址模板 */
    private static final String HUCHENG_VIDEO_API_TEMPLATE =
            "https://htglhy.huchengfireworks.com/addons/shopro/goods.goods/video_list?id=%s";

    /** fwmall 视频 API 地址模板（H5 为 SPA，需直接调用数据接口） */
    private static final String FWMALL_VIDEO_API_TEMPLATE =
            "https://v2.fwmall.com.cn/api/wxmall/goods/goodsDetail?productId=%s";

    // ==================== 正则模式 ====================
    /** URL 参数提取模板：支持 query 及 hash 路由中的 query（如 #/pages/xxx?store_id=1&id=2） */
    private static final Pattern QUERY_PARAM_PATTERN_TEMPLATE =
            Pattern.compile("(^|[?&])%s=([^&#]+)", Pattern.CASE_INSENSITIVE);

    /** 视频直链 URL 模式：匹配 .mp4 或 .m3u8 结尾的 URL */
    private static final Pattern DIRECT_VIDEO_URL_PATTERN =
            Pattern.compile("^https?://[^\\s]+\\.(?:mp4|m3u8)(?:\\?[^\\s]*)?$", Pattern.CASE_INSENSITIVE);

    /** JS 跳转模式：匹配 window.location = "url" 或 window.location.href = "url" */
    private static final Pattern JS_LOCATION_ASSIGN_PATTERN =
            Pattern.compile("(?i)window\\.location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]");

    /**
     * 视频 URL 提取正则模式列表（按优先级排序）
     * <p>
     * 优先级：
     * <ol>
     *   <li>DATA.video 变量赋值</li>
     *   <li>JSON/对象字面量中的 video 字段</li>
     *   <li>&lt;source src="..."&gt; 标签</li>
     *   <li>&lt;video src="..."&gt; 标签</li>
     *   <li>通用 .mp4/.m3u8 URL（兜底）</li>
     * </ol>
     */
    private static final List<Pattern> VIDEO_URL_PATTERNS = List.of(
            // 模式1: 变量直取（如 DATA.video = "url"）
            Pattern.compile("\\bDATA\\b\\s*\\.\\s*video\\s*[:=]\\s*[\"']([^\"']+\\.(?:mp4|m3u8)(?:\\?[^\"']*)?)[\"']",
                    Pattern.CASE_INSENSITIVE),

            // 模式2: JSON/对象字面量字段（如 \"video\":\"https:\\/\\/...mp4\"）
            Pattern.compile("(?:var\\s+)?\\bDATA\\b\\s*=\\s*\\{[\\s\\S]*?[\"']video[\"']\\s*[:=]\\s*[\"']([^\"']+\\.(?:mp4|m3u8)(?:\\?[^\"']*)?)[\"']",
                    Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"']video[\"']\\s*[:=]\\s*[\"']([^\"']+\\.(?:mp4|m3u8)(?:\\?[^\"']*)?)[\"']",
                    Pattern.CASE_INSENSITIVE),

            // 模式2: <source src="...">
            Pattern.compile("<source[^>]*src=[\"']([^\"']+\\.(?:mp4|m3u8)(?:\\?[^\"']*)?)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE),

            // 模式3: <video src="...">
            Pattern.compile("<video[^>]*src=[\"']([^\"']+\\.(?:mp4|m3u8)(?:\\?[^\"']*)?)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE),

            // 模式4: 通用URL（兜底）
            Pattern.compile("(https?://[^\\s\"'<>]+\\.(?:mp4|m3u8)(?:\\?[^\\s\"'<>]*)?)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(//[^\\s\"'<>]+\\.(?:mp4|m3u8)(?:\\?[^\\s\"'<>]*)?)", Pattern.CASE_INSENSITIVE)
    );

    // ==================== 核心方法 ====================

    /**
     * 从图片 URL 中解析所有二维码内容
     * <p>
     * 处理流程：
     * <ol>
     *   <li>下载图片</li>
     *   <li>构建解码候选图（原图 + 放大图）</li>
     *   <li>使用 ZXing 的多二维码识别器解码</li>
     *   <li>尝试多种二值化策略（HybridBinarizer / GlobalHistogramBinarizer）</li>
     * </ol>
     *
     * @param imageUrl 图片 URL
     * @return 解析出的二维码内容列表（可能为空）
     */
    private List<String> parseAllQrCodes(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return List.of();
        }

        try {
            byte[] imageBytes = httpGetBytes(imageUrl, IMAGE_DOWNLOAD_TIMEOUT_MS);

            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("下载二维码图片为空: {}", imageUrl);
                return List.of();
            }

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                log.warn("无法读取二维码图片: {}", imageUrl);
                return List.of();
            }

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            hints.put(DecodeHintType.ALSO_INVERTED, Boolean.TRUE);
            hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));

            List<BufferedImage> candidates = buildDecodeCandidates(image);
            for (BufferedImage candidateImage : candidates) {
                List<String> decoded = tryDecodeAllQrCodes(candidateImage, hints);
                if (!decoded.isEmpty()) {
                    return decoded;
                }
            }

            throw NotFoundException.getNotFoundInstance();

        } catch (NotFoundException e) {
            log.warn("图片中未找到二维码: {}", imageUrl);
            return List.of();
        } catch (Exception e) {
            log.error("解析二维码失败: {}", imageUrl, e);
            return List.of();
        }
    }

    /**
     * 【对外接口】从二维码图片中提取视频 URL
     * <p>
     * 这是服务的主入口方法，完整流程：
     * <ol>
     *   <li>解析图片中的所有二维码</li>
     *   <li>过滤出 HTTP/HTTPS URL</li>
     *   <li>优先尝试非微信域名的 URL（避免公众号关注码干扰）</li>
     *   <li>遍历每个 URL 尝试提取视频，成功即返回</li>
     *   <li>所有尝试失败则返回最佳失败结果（便于后续分析）</li>
     * </ol>
     *
     * @param qrCodeImageUrl 二维码图片 URL
     * @return 提取结果（包含状态、视频URL、目标网址、描述信息）
     */
    @Override
    public VideoExtractResult extractVideoFromQrCodeImage(String qrCodeImageUrl) {
        if (!StringUtils.hasText(qrCodeImageUrl)) {
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.SKIPPED)
                    .message("缺少二维码图片")
                    .build();
        }

        List<String> qrContents = parseAllQrCodes(qrCodeImageUrl);
        if (qrContents.isEmpty()) {
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.FAILED)
                    .message("未识别到二维码")
                    .build();
        }

        List<String> urls = new ArrayList<>();
        for (String content : qrContents) {
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String trimmed = content.trim();
            if (isHttpUrl(trimmed)) {
                urls.add(trimmed);
            }
        }

        if (urls.isEmpty()) {
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.UNSUPPORTED)
                    .message("二维码内容不是可访问的URL")
                    .build();
        }

        urls = urls.stream().distinct().toList();

        // 避免公众号关注码等干扰：优先尝试非 weixin 域名
        List<String> ordered = new ArrayList<>(urls);
        ordered.sort(Comparator.comparing(VideoExtractServiceImpl::isWeixinUrl));

        VideoExtractResult best = null;
        for (String url : ordered) {
            VideoExtractResult attempt = tryExtractVideoFromPageUrl(url);
            if (attempt == null) {
                continue;
            }
            if (attempt.getStatus() == VideoExtractStatus.SUCCESS) {
                return attempt;
            }
            best = pickBetter(best, attempt);
        }

        return best != null ? best : VideoExtractResult.builder()
                .status(VideoExtractStatus.FAILED)
                .message("所有二维码均未提取到视频")
                .targetUrl(ordered.get(0))
                .build();
    }

    /**
     * 从目标 URL 提取视频（单个 URL 的完整提取流程）
     * <p>
     * 按优先级尝试：
     * <ol>
     *   <li>直链检测：URL 本身就是 .mp4/.m3u8</li>
     *   <li>已知平台 API：fwmall、虎城等 SPA 站点</li>
     *   <li>HtmlUnit 渲染：执行 JS 后从页面/网络响应中提取</li>
     * </ol>
     *
     * @param pageUrl 目标页面 URL
     * @return 提取结果
     */
    private VideoExtractResult tryExtractVideoFromPageUrl(String pageUrl) {
        if (!StringUtils.hasText(pageUrl)) {
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.FAILED)
                    .message("目标网址为空")
                    .build();
        }

        String url = pageUrl.trim();

        // 1) 二维码直链
        if (DIRECT_VIDEO_URL_PATTERN.matcher(url).matches()) {
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.SUCCESS)
                    .videoUrl(url)
                    .targetUrl(url)
                    .message("二维码为视频直链")
                    .build();
        }

        // 2) 已知平台API（SPA/动态加载专用）
        VideoExtractResult known = tryExtractFromKnownPlatforms(url);
        if (known != null) {
            return known;
        }

        // 3) 静态抓取 + 正则提取
        return tryExtractFromHtml(url);
    }

    /**
     * 尝试从已知平台的数据接口提取视频
     * <p>
     * 针对 SPA 站点，直接调用其数据 API 比渲染 JS 更高效可靠。
     * 目前支持：
     * <ul>
     *   <li>fwmall：烟花商城，API 返回商品详情含 video_url</li>
     *   <li>虎城烟花：后台管理系统，API 返回视频列表</li>
     * </ul>
     *
     * @param pageUrl 目标页面 URL
     * @return 提取结果；若非已知平台则返回 null，交由后续策略处理
     */
    private VideoExtractResult tryExtractFromKnownPlatforms(String pageUrl) {
        // fwmall: https://v2.fwmall.com.cn/wxmall/default3/#/pages/goodsdetail?store_id=560&id=73886
        if (pageUrl.contains("fwmall.com.cn") && pageUrl.contains("goodsdetail")) {
            String id = extractQueryParam(pageUrl, "id");
            if (StringUtils.hasText(id)) {
                String apiUrl = String.format(FWMALL_VIDEO_API_TEMPLATE, id);
                VideoExtractResult result = tryExtractVideoFromFwmallApi(apiUrl, pageUrl);
                if (result != null) {
                    return result;
                }
            }
        }

        // hucheng: 已有稳定API
        if (pageUrl.contains("huchengfireworks.com")) {
            String id = extractQueryParam(pageUrl, "id");
            if (StringUtils.hasText(id)) {
                String apiUrl = String.format(HUCHENG_VIDEO_API_TEMPLATE, id);
                VideoExtractResult result = tryExtractVideoFromHuchengApi(apiUrl, pageUrl);
                if (result != null) {
                    return result;
                }
            }
        }

        return null;
    }

    /**
     * 从 fwmall 烟花商城 API 提取视频 URL
     * <p>
     * API 响应格式：
     * <pre>{@code
     * {
     *   "status": 1,
     *   "data": {
     *     "info": {
     *       "video_url_com": "https://...",  // 压缩版视频（优先）
     *       "video_url": "https://..."       // 原始视频
     *     }
     *   }
     * }
     * }</pre>
     *
     * @param apiUrl    fwmall 商品详情 API 地址
     * @param targetUrl 原始页面 URL（用于结果记录）
     * @return 提取结果
     */
    private VideoExtractResult tryExtractVideoFromFwmallApi(String apiUrl, String targetUrl) {
        try {
            HttpResponse<String> resp = httpGetText(apiUrl, HTTP_TIMEOUT_MS);
            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                        .targetUrl(targetUrl)
                        .message("fwmall API请求失败: HTTP " + status)
                        .build();
            }

            JsonNode json = OBJECT_MAPPER.readTree(resp.body());
            if (json.path("status").asInt(0) != 1) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                        .targetUrl(targetUrl)
                        .message("fwmall API返回异常")
                        .build();
            }

            JsonNode info = json.path("data").path("info");
            String videoUrl = textOrNull(info.get("video_url_com"));
            if (!StringUtils.hasText(videoUrl)) {
                videoUrl = textOrNull(info.get("video_url"));
            }

            if (!StringUtils.hasText(videoUrl)) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                        .targetUrl(targetUrl)
                        .message("fwmall API未返回视频字段")
                        .build();
            }

            String normalized = normalizeUrl(videoUrl, targetUrl);
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.SUCCESS)
                    .videoUrl(normalized)
                    .targetUrl(targetUrl)
                    .message("fwmall API提取成功")
                    .build();

        } catch (Exception e) {
            log.warn("fwmall API提取失败: apiUrl={}, targetUrl={}", apiUrl, targetUrl, e);
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                    .targetUrl(targetUrl)
                    .message("fwmall API提取失败")
                    .build();
        }
    }

    /**
     * 从虎城烟花 API 提取视频 URL
     * <p>
     * API 响应格式：
     * <pre>{@code
     * {
     *   "code": 1,
     *   "data": {
     *     "list": [
     *       { "video_url": "https://...", "url": "https://..." }
     *     ]
     *   }
     * }
     * }</pre>
     *
     * @param apiUrl    虎城视频列表 API 地址
     * @param targetUrl 原始页面 URL（用于结果记录）
     * @return 提取结果
     */
    private VideoExtractResult tryExtractVideoFromHuchengApi(String apiUrl, String targetUrl) {
        try {
            HttpResponse<String> resp = httpGetText(apiUrl, HTTP_TIMEOUT_MS);
            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(targetUrl)
                        .message("虎城API请求失败: HTTP " + status)
                        .build();
            }

            JsonNode json = OBJECT_MAPPER.readTree(resp.body());
            if (json.path("code").asInt(-1) != 1) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(targetUrl)
                        .message("虎城API返回错误: " + json.path("msg").asText(""))
                        .build();
            }

            JsonNode list = json.path("data").path("list");
            if (!list.isArray() || list.isEmpty()) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(targetUrl)
                        .message("虎城视频列表为空")
                        .build();
            }

            JsonNode firstVideo = list.get(0);
            String videoUrl = textOrNull(firstVideo.get("video_url"));
            if (!StringUtils.hasText(videoUrl)) {
                videoUrl = textOrNull(firstVideo.get("url"));
            }

            if (!StringUtils.hasText(videoUrl)) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(targetUrl)
                        .message("虎城API未返回视频字段")
                        .build();
            }

            String normalized = normalizeUrl(videoUrl, targetUrl);
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.SUCCESS)
                    .videoUrl(normalized)
                    .targetUrl(targetUrl)
                    .message("虎城API提取成功")
                    .build();

        } catch (Exception e) {
            log.warn("虎城API提取失败: apiUrl={}, targetUrl={}", apiUrl, targetUrl, e);
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.FAILED)
                    .targetUrl(targetUrl)
                    .message("虎城API提取失败")
                    .build();
        }
    }

    /**
     * 从 HTML 页面提取视频（入口方法）
     * <p>
     * 当前实现直接委托给 HtmlUnit 渲染，未来可扩展为：
     * <ol>
     *   <li>先尝试静态 HTTP 抓取 + 正则</li>
     *   <li>检测 SPA 壳页面后再调用 HtmlUnit</li>
     * </ol>
     *
     * @param pageUrl 目标页面 URL
     * @return 提取结果
     */
    private VideoExtractResult tryExtractFromHtml(String pageUrl) {
        return tryExtractFromHtmlUnit(pageUrl);
    }

    /**
     * 从 HTML 文本中提取视频 URL（委托给文本提取）
     *
     * @param html    HTML 文本内容
     * @param pageUrl 页面 URL（用于相对路径解析）
     * @return 视频 URL 或 null
     */
    private String extractVideoFromHtml(String html, String pageUrl) {
        return extractVideoFromText(html, pageUrl);
    }

    /**
     * 从任意文本中提取视频 URL（核心正则匹配逻辑）
     * <p>
     * 按优先级依次尝试 {@link #VIDEO_URL_PATTERNS} 中的正则模式，
     * 找到的第一个匹配即返回。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>遍历正则模式列表</li>
     *   <li>匹配成功后进行 URL 反转义（JSON 转义、HTML 实体）</li>
     *   <li>规范化 URL（补全协议、解析相对路径）</li>
     *   <li>可选：软校验视频 URL 可访问性</li>
     * </ol>
     *
     * @param text    待匹配的文本（HTML、JSON、JS 等）
     * @param baseUrl 基础 URL（用于相对路径解析）
     * @return 视频 URL 或 null
     */
    private String extractVideoFromText(String text, String baseUrl) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        for (Pattern pattern : VIDEO_URL_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String candidate = unescapeUrlCandidate(matcher.group(1));
                String normalized = normalizeUrl(candidate, baseUrl);
                if (StringUtils.hasText(normalized) && softValidateVideoUrl(normalized)) {
                    return normalized;
                }
                if (StringUtils.hasText(normalized)) {
                    // 软校验失败也不强拦截，避免误判
                    return normalized;
                }
            }
        }
        return null;
    }

    /**
     * 使用 HtmlUnit 无头浏览器渲染页面并提取视频
     * <p>
     * 这是最强大也是最耗资源的提取策略，用于处理 SPA/JS 动态加载的页面。
     * <p>
     * 工作流程：
     * <ol>
     *   <li>构建 HtmlUnit WebClient，配置 JS 执行、重定向、超时</li>
     *   <li>注册网络嗅探器，监听所有网络请求中的视频 URL</li>
     *   <li>加载页面并等待 JS 执行完成</li>
     *   <li>优先使用嗅探器捕获的视频 URL</li>
     *   <li>若嗅探器未捕获，则从渲染后的 DOM 中正则匹配</li>
     *   <li>检测 JS 跳转（window.location），跟随跳转页面继续提取</li>
     * </ol>
     *
     * @param pageUrl 目标页面 URL
     * @return 提取结果
     */
    private VideoExtractResult tryExtractFromHtmlUnit(String pageUrl) {
        HtmlUnitVideoSniffer sniffer = new HtmlUnitVideoSniffer();

        try (WebClient webClient = buildHtmlUnitClient(sniffer)) {
            HtmlPage page = webClient.getPage(pageUrl);
            waitForJs(webClient);

            int status = page.getWebResponse().getStatusCode();
            if (status < 200 || status >= 300) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(pageUrl)
                        .message("页面访问失败: HTTP " + status)
                        .build();
            }

            String finalUrl = page.getUrl() != null ? page.getUrl().toString() : pageUrl;

            String videoUrl = StringUtils.hasText(sniffer.bestVideoUrl)
                    ? sniffer.bestVideoUrl
                    : extractVideoFromText(page.asXml(), finalUrl);
            if (StringUtils.hasText(videoUrl)) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.SUCCESS)
                        .videoUrl(videoUrl)
                        .targetUrl(finalUrl)
                        .message("HtmlUnit渲染提取成功")
                        .build();
            }

            List<String> followUps = extractFollowUpPageUrls(page.asXml(), finalUrl);
            int tries = 0;
            for (String next : followUps) {
                if (tries >= HTMLUNIT_MAX_FOLLOW_UP_PAGES) {
                    break;
                }
                tries++;

                try {
                    Page nextPage = webClient.getPage(next);
                    waitForJs(webClient);
                    if (nextPage instanceof HtmlPage nextHtml) {
                        String nextUrl = nextHtml.getUrl() != null ? nextHtml.getUrl().toString() : next;
                        String nextVideo = StringUtils.hasText(sniffer.bestVideoUrl)
                                ? sniffer.bestVideoUrl
                                : extractVideoFromText(nextHtml.asXml(), nextUrl);
                        if (StringUtils.hasText(nextVideo)) {
                            return VideoExtractResult.builder()
                                    .status(VideoExtractStatus.SUCCESS)
                                    .videoUrl(nextVideo)
                                    .targetUrl(nextUrl)
                                    .message("HtmlUnit跟随页面提取成功")
                                    .build();
                        }
                    }
                } catch (Exception e) {
                    log.debug("HtmlUnit跟随页面失败: {}", next, e);
                }
            }

            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                    .targetUrl(finalUrl)
                    .message("HtmlUnit渲染后仍未找到视频URL")
                    .build();

        } catch (FailingHttpStatusCodeException e) {
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.FAILED)
                    .targetUrl(pageUrl)
                    .message("页面访问失败: HTTP " + e.getStatusCode())
                    .build();
        } catch (Exception e) {
            log.warn("HtmlUnit渲染访问异常: {}", pageUrl, e);
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                    .targetUrl(pageUrl)
                    .message("HtmlUnit渲染访问异常")
                    .build();
        }
    }

    /**
     * 构建配置好的 HtmlUnit WebClient
     * <p>
     * 配置项：
     * <ul>
     *   <li>启用 JavaScript 执行</li>
     *   <li>禁用 CSS（提升性能）</li>
     *   <li>禁用脚本错误异常（容错）</li>
     *   <li>启用重定向跟随</li>
     *   <li>注册网络请求拦截器（视频 URL 嗅探）</li>
     * </ul>
     *
     * @param sniffer 视频 URL 嗅探器，用于拦截网络请求
     * @return 配置好的 WebClient 实例
     */
    private static WebClient buildHtmlUnitClient(HtmlUnitVideoSniffer sniffer) {
        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getOptions().setJavaScriptEnabled(true);
        webClient.getOptions().setCssEnabled(false);
        webClient.getOptions().setThrowExceptionOnScriptError(false);
        webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
        webClient.getOptions().setRedirectEnabled(true);
        webClient.getOptions().setTimeout(HTTP_TIMEOUT_MS);

        webClient.setWebConnection(new WebConnectionWrapper(webClient) {
            @Override
            public WebResponse getResponse(WebRequest request) throws java.io.IOException {
                URL requestUrl = request.getUrl();
                WebResponse response = super.getResponse(request);
                sniffer.tryAccept(requestUrl, response);
                return response;
            }
        });

        return webClient;
    }

    /**
     * 等待 HtmlUnit 中的 JavaScript 执行完成
     * <p>
     * 分两阶段等待：
     * <ol>
     *   <li>等待启动前的 JS（如 DOMContentLoaded 回调）</li>
     *   <li>等待后台异步 JS（如 AJAX、setTimeout）</li>
     * </ol>
     *
     * @param webClient HtmlUnit 客户端
     */
    private static void waitForJs(WebClient webClient) {
        webClient.waitForBackgroundJavaScriptStartingBefore(HTMLUNIT_JS_WAIT_MS);
        webClient.waitForBackgroundJavaScript(HTMLUNIT_BACKGROUND_JS_WAIT_MS);
    }

    /**
     * 从 HTML 中提取 JS 跳转目标 URL
     * <p>
     * 匹配 {@code window.location = "url"} 或 {@code window.location.href = "url"} 模式，
     * 用于处理 JS 中的页面跳转逻辑。
     *
     * @param html    HTML/JS 文本
     * @param baseUrl 基础 URL（用于相对路径解析）
     * @return 跳转目标 URL 列表（去重）
     */
    private static List<String> extractFollowUpPageUrls(String html, String baseUrl) {
        if (!StringUtils.hasText(html)) {
            return List.of();
        }

        LinkedHashSet<String> urls = new LinkedHashSet<>();
        Matcher m = JS_LOCATION_ASSIGN_PATTERN.matcher(html);
        while (m.find()) {
            String candidate = m.group(1);
            String normalized = normalizeUrl(candidate, baseUrl);
            if (StringUtils.hasText(normalized)) {
                urls.add(normalized);
            }
        }
        return new ArrayList<>(urls);
    }

    /**
     * HtmlUnit 网络请求视频嗅探器
     * <p>
     * 通过拦截 WebClient 的所有网络请求，从请求 URL 和响应体中提取视频地址。
     * 这种方式可以捕获 AJAX 动态加载的视频，比 DOM 解析更可靠。
     * <p>
     * 线程安全：使用 volatile 保证可见性。
     */
    private static final class HtmlUnitVideoSniffer {
        /** 捕获到的最佳视频 URL */
        private volatile String bestVideoUrl;

        /**
         * 尝试从网络请求/响应中提取视频 URL
         * <p>
         * 检测策略：
         * <ol>
         *   <li>请求 URL 本身是视频直链（.mp4/.m3u8）</li>
         *   <li>响应体为文本类型时，正则匹配视频 URL</li>
         * </ol>
         *
         * @param requestUrl 请求 URL
         * @param response   响应对象
         */
        void tryAccept(URL requestUrl, WebResponse response) {
            if (requestUrl == null || response == null) {
                return;
            }

            String url = requestUrl.toString();
            if (DIRECT_VIDEO_URL_PATTERN.matcher(url).matches()) {
                bestVideoUrl = url;
                return;
            }

            String contentType;
            try {
                contentType = response.getContentType();
            } catch (Exception ignored) {
                contentType = null;
            }

            boolean maybeText = true;
            if (contentType != null) {
                String ct = contentType.toLowerCase(Locale.ROOT);
                maybeText = ct.contains("json") || ct.contains("text") || ct.contains("javascript") || ct.contains("xml") || ct.contains("html");
            }
            if (!maybeText) {
                return;
            }

            try {
                String body = response.getContentAsString();
                String found = null;
                for (Pattern p : VIDEO_URL_PATTERNS) {
                    Matcher matcher = p.matcher(body);
                    if (matcher.find()) {
                        found = matcher.group(1);
                        break;
                    }
                }
                if (StringUtils.hasText(found)) {
                    String candidate = unescapeUrlCandidate(found);
                    String normalized = normalizeUrl(candidate, requestUrl.toString());
                    bestVideoUrl = StringUtils.hasText(normalized) ? normalized : candidate;
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 对候选 URL 进行反转义处理
     * <p>
     * 处理常见的转义形式：
     * <ul>
     *   <li>JSON 转义：{@code \/} → {@code /}，{@code \u002f} → {@code /}</li>
     *   <li>HTML 实体：{@code &amp;} → {@code &}</li>
     * </ul>
     *
     * @param candidate 原始候选 URL
     * @return 反转义后的 URL
     */
    private static String unescapeUrlCandidate(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return candidate;
        }

        String url = candidate.trim();

        // 常见JSON转义
        url = url.replace("\\/", "/");
        url = url.replace("\\u002f", "/")
                .replace("\\u002F", "/");
        url = url.replace("\\u0026", "&")
                .replace("\\u003d", "=")
                .replace("\\u003f", "?")
                .replace("\\u0025", "%");

        // 常见HTML实体
        url = url.replace("&amp;", "&");
        return url;
    }

    /**
     * 构建二维码解码候选图列表
     * <p>
     * 包含原图和可选的放大图，以提高小尺寸二维码的识别率。
     *
     * @param original 原始图片
     * @return 候选图列表（原图 + 可选放大图）
     */
    private static List<BufferedImage> buildDecodeCandidates(BufferedImage original) {
        List<BufferedImage> candidates = new ArrayList<>();
        candidates.add(original);

        int factor = suggestScaleFactor(original);
        if (factor > 1) {
            candidates.add(scaleUpImage(original, factor));
        }

        return candidates;
    }

    /**
     * 根据图片尺寸建议放大倍数
     * <p>
     * 规则：
     * <ul>
     *   <li>最大边 ≥ 640px：不放大</li>
     *   <li>最大边 < 320px：放大 3 倍</li>
     *   <li>其他：放大 2 倍</li>
     * </ul>
     *
     * @param image 图片
     * @return 建议的放大倍数
     */
    private static int suggestScaleFactor(BufferedImage image) {
        int maxDim = Math.max(image.getWidth(), image.getHeight());
        if (maxDim >= QR_SCALE_THRESHOLD_PX) {
            return 1;
        }
        if (maxDim < 320) {
            return 3;
        }
        return 2;
    }

    /**
     * 放大图片以提高二维码识别率
     * <p>
     * 使用最近邻插值算法，保留二维码的锐利边缘。
     *
     * @param src    原始图片
     * @param factor 放大倍数
     * @return 放大后的图片
     */
    private static BufferedImage scaleUpImage(BufferedImage src, int factor) {
        if (factor <= 1) {
            return src;
        }

        int width = Math.max(1, src.getWidth() * factor);
        int height = Math.max(1, src.getHeight() * factor);

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(src, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }

    /**
     * 尝试解码图片中的所有二维码
     * <p>
     * 使用两种二值化策略（HybridBinarizer / GlobalHistogramBinarizer）提高识别率。
     * 先尝试多码识别，失败则回退到单码识别。
     *
     * @param image 图片
     * @param hints ZXing 解码提示参数
     * @return 解码出的二维码内容列表（可能为空）
     */
    private static List<String> tryDecodeAllQrCodes(BufferedImage image, Map<DecodeHintType, Object> hints) {
        List<BinaryBitmap> bitmaps = List.of(
                toBitmap(image, true),
                toBitmap(image, false)
        );

        MultiFormatReader reader = new MultiFormatReader();
        GenericMultipleBarcodeReader multipleReader = new GenericMultipleBarcodeReader(reader);

        for (BinaryBitmap bitmap : bitmaps) {
            try {
                Result[] results;
                try {
                    results = multipleReader.decodeMultiple(bitmap, hints);
                } catch (NotFoundException e) {
                    Result single = reader.decode(bitmap, hints);
                    results = new Result[]{single};
                }

                LinkedHashSet<String> contents = new LinkedHashSet<>();
                for (Result result : results) {
                    if (result == null) {
                        continue;
                    }
                    String text = result.getText();
                    if (StringUtils.hasText(text)) {
                        contents.add(text.trim());
                    }
                }

                if (!contents.isEmpty()) {
                    return new ArrayList<>(contents);
                }
            } catch (NotFoundException ignored) {
                // try next strategy
            } finally {
                reader.reset();
            }
        }

        return List.of();
    }

    /**
     * 将图片转换为 ZXing 二值化位图
     *
     * @param image             图片
     * @param useHybridBinarizer 是否使用 HybridBinarizer（更精准但慢）
     * @return 二值化位图
     */
    private static BinaryBitmap toBitmap(BufferedImage image, boolean useHybridBinarizer) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        Binarizer binarizer = useHybridBinarizer ? new HybridBinarizer(source) : new GlobalHistogramBinarizer(source);
        return new BinaryBitmap(binarizer);
    }

    /**
     * 软校验视频 URL 的可访问性
     * <p>
     * 发送 HEAD 请求检查 URL 是否可访问。
     * 校验失败不作为强失败条件（部分源站不支持 HEAD 请求）。
     *
     * @param videoUrl 视频 URL
     * @return true 如果可访问或无法判断；false 仅当明确失败
     */
    private boolean softValidateVideoUrl(String videoUrl) {
        if (!StringUtils.hasText(videoUrl)) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(videoUrl))
                    .timeout(Duration.ofMillis(VIDEO_URL_VALIDATE_TIMEOUT_MS))
                    .header("User-Agent", USER_AGENT)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = httpClient().send(request, HttpResponse.BodyHandlers.discarding());
            int status = resp.statusCode();
            return status >= 200 && status < 400;
        } catch (Exception e) {
            // 部分源站不支持 HEAD 或会拦截；不作为强失败
            return true;
        }
    }

    /** 共享的 HTTP 客户端实例（线程安全、支持重定向） */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
            .build();

    /**
     * 获取共享的 HTTP 客户端
     *
     * @return HTTP 客户端实例
     */
    private static HttpClient httpClient() {
        return HTTP_CLIENT;
    }

    /**
     * HTTP GET 请求，返回字节数组
     *
     * @param url       请求 URL
     * @param timeoutMs 超时时间（毫秒）
     * @return 响应体字节数组
     * @throws Exception 网络异常
     */
    private static byte[] httpGetBytes(String url, int timeoutMs) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> resp = httpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() == HttpURLConnection.HTTP_OK) {
            return resp.body();
        }
        return resp.body();
    }

    /**
     * HTTP GET 请求，返回文本响应
     *
     * @param url       请求 URL
     * @param timeoutMs 超时时间（毫秒）
     * @return HTTP 响应对象
     * @throws Exception 网络异常
     */
    private static HttpResponse<String> httpGetText(String url, int timeoutMs) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return httpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 安全地从 JSON 节点获取文本值
     *
     * @param node JSON 节点
     * @return 文本值，若为 null 或空则返回 null
     */
    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return StringUtils.hasText(text) ? text : null;
    }

    /**
     * 判断内容是否为 HTTP/HTTPS URL
     *
     * @param content 待检查的内容
     * @return true 如果是 HTTP/HTTPS URL
     */
    private static boolean isHttpUrl(String content) {
        return content != null && (content.startsWith("http://") || content.startsWith("https://"));
    }

    /**
     * 判断 URL 是否为微信域名
     * <p>
     * 用于在多二维码场景下优先处理非微信 URL，避免公众号关注码干扰。
     *
     * @param url 待检查的 URL
     * @return true 如果是微信域名
     */
    private static boolean isWeixinUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("weixin.qq.com") || lower.contains("mp.weixin.qq.com");
    }

    /**
     * 从两个提取结果中选择更优的一个
     * <p>
     * 优先级规则：
     * <ol>
     *   <li>NEED_DYNAMIC_RENDER 优于其他失败状态（便于后续补规则）</li>
     *   <li>FAILED 优于 UNSUPPORTED（有更多诊断信息）</li>
     * </ol>
     *
     * @param current   当前最佳结果
     * @param candidate 候选结果
     * @return 更优的结果
     */
    private static VideoExtractResult pickBetter(VideoExtractResult current, VideoExtractResult candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }

        // 优先返回 NEED_DYNAMIC_RENDER，便于后续补规则/云渲染兜底
        if (current.getStatus() != VideoExtractStatus.NEED_DYNAMIC_RENDER
                && candidate.getStatus() == VideoExtractStatus.NEED_DYNAMIC_RENDER) {
            return candidate;
        }

        // 其次返回 FAILED（带targetUrl）
        if (current.getStatus() == VideoExtractStatus.UNSUPPORTED
                && candidate.getStatus() == VideoExtractStatus.FAILED) {
            return candidate;
        }

        return current;
    }

    /**
     * 规范化 URL
     * <p>
     * 处理以下情况：
     * <ul>
     *   <li>协议相对 URL（{@code //example.com}）：补全协议</li>
     *   <li>相对路径：基于 baseUrl 解析为绝对路径</li>
     *   <li>绝对 URL：直接返回</li>
     * </ul>
     *
     * @param candidate 候选 URL
     * @param baseUrl   基础 URL（用于解析相对路径）
     * @return 规范化后的绝对 URL
     */
    private static String normalizeUrl(String candidate, String baseUrl) {
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String url = candidate.trim();

        if (url.startsWith("//")) {
            String scheme = "https:";
            if (StringUtils.hasText(baseUrl)) {
                try {
                    URI base = URI.create(baseUrl);
                    if (StringUtils.hasText(base.getScheme())) {
                        scheme = base.getScheme() + ":";
                    }
                } catch (Exception ignored) {
                }
            }
            return scheme + url;
        }

        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }

        if (!StringUtils.hasText(baseUrl)) {
            return url;
        }

        try {
            URI base = URI.create(baseUrl);
            return base.resolve(url).toString();
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 判断 HTML 是否为 SPA 壳页面
     * <p>
     * 特征检测：
     * <ul>
     *   <li>存在 {@code id="app"} 或 {@code id="root"} 的容器元素</li>
     *   <li>包含多个 {@code <script>} 标签</li>
     *   <li>不包含直接的视频标签或视频 URL</li>
     * </ul>
     * <p>
     * 注意：此方法当前未被调用，保留用于未来优化静态抓取策略。
     *
     * @param html HTML 文本
     * @return true 如果是 SPA 壳页面
     */
    private static boolean looksLikeSpaShell(String html) {
        if (!StringUtils.hasText(html)) {
            return false;
        }
        String lower = html.toLowerCase(Locale.ROOT);

        boolean hasAppRoot = lower.contains("id=app") || lower.contains("id=\"app\"")
                || lower.contains("id=root") || lower.contains("id=\"root\"");
        if (!hasAppRoot) {
            return false;
        }

        int scriptCount = countOccurrences(lower, "<script");
        boolean hasVideoHints = lower.contains(".mp4") || lower.contains(".m3u8")
                || lower.contains("<video") || lower.contains("<source");

        return scriptCount >= 2 && !hasVideoHints;
    }

    /**
     * 计算子字符串出现次数
     *
     * @param text   文本
     * @param needle 要查找的子字符串
     * @return 出现次数
     */
    private static int countOccurrences(String text, String needle) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(needle)) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * 从 URL 中提取查询参数值
     * <p>
     * 支持标准查询字符串和 hash 路由中的参数（如 Vue Router 的 {@code #/pages/xxx?id=123}）。
     *
     * @param url URL 字符串
     * @param key 参数名
     * @return 参数值，未找到返回 null
     */
    private static String extractQueryParam(String url, String key) {
        if (!StringUtils.hasText(url) || !StringUtils.hasText(key)) {
            return null;
        }
        Pattern pattern = Pattern.compile(String.format(QUERY_PARAM_PATTERN_TEMPLATE.pattern(), Pattern.quote(key)),
                QUERY_PARAM_PATTERN_TEMPLATE.flags());
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return null;
    }
}
