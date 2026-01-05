package com.fireworks.service.impl;

import com.fireworks.service.VideoExtractService;
import com.fireworks.videoextract.VideoExtractResult;
import com.fireworks.videoextract.VideoExtractStatus;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频提取服务实现
 * 说明：
 * 1) 支持一张图片多个二维码
 * 2) 优先尝试：直链 → 已知平台API → 静态HTML正则
 * 3) 对 SPA/动态加载：返回 NEED_DYNAMIC_RENDER，并记录目标网址
 */
@Slf4j
@Service
public class VideoExtractServiceImpl implements VideoExtractService {

    private static final int IMAGE_DOWNLOAD_TIMEOUT_MS = 10000;
    private static final int HTTP_TIMEOUT_MS = 10000;
    private static final int VIDEO_URL_VALIDATE_TIMEOUT_MS = 5000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int QR_SCALE_THRESHOLD_PX = 640;

    // 虎城烟花视频API地址
    private static final String HUCHENG_VIDEO_API_TEMPLATE =
            "https://htglhy.huchengfireworks.com/addons/shopro/goods.goods/video_list?id=%s";

    // fwmall 视频API地址（H5为SPA，静态HTML无法提取，需直打API）
    private static final String FWMALL_VIDEO_API_TEMPLATE =
            "https://v2.fwmall.com.cn/api/wxmall/goods/goodsDetail?productId=%s";

    // URL参数提取：支持 query 及 hash 路由中的 query（如 #/pages/xxx?store_id=1&id=2）
    private static final Pattern QUERY_PARAM_PATTERN_TEMPLATE =
            Pattern.compile("(^|[?&])%s=([^&#]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern DIRECT_VIDEO_URL_PATTERN =
            Pattern.compile("^https?://[^\\s]+\\.(?:mp4|m3u8)(?:\\?[^\\s]*)?$", Pattern.CASE_INSENSITIVE);

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

    private List<String> parseAllQrCodes(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return List.of();
        }

        try {
            byte[] imageBytes = HttpRequest.get(imageUrl)
                    .timeout(IMAGE_DOWNLOAD_TIMEOUT_MS)
                    .header("User-Agent", USER_AGENT)
                    .execute()
                    .bodyBytes();

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
     * 从目标URL提取视频
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

    private VideoExtractResult tryExtractVideoFromFwmallApi(String apiUrl, String targetUrl) {
        try (HttpResponse resp = HttpRequest.get(apiUrl)
                .timeout(HTTP_TIMEOUT_MS)
                .header("User-Agent", USER_AGENT)
                .execute()) {

            if (resp.getStatus() < 200 || resp.getStatus() >= 300) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                        .targetUrl(targetUrl)
                        .message("fwmall API请求失败: HTTP " + resp.getStatus())
                        .build();
            }

            JSONObject json = JSONUtil.parseObj(resp.body());
            if (json.getInt("status", 0) != 1) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                        .targetUrl(targetUrl)
                        .message("fwmall API返回异常")
                        .build();
            }

            JSONObject data = json.getJSONObject("data");
            JSONObject info = data != null ? data.getJSONObject("info") : null;
            String videoUrl = info != null ? info.getStr("video_url_com") : null;
            if (!StringUtils.hasText(videoUrl)) {
                videoUrl = info != null ? info.getStr("video_url") : null;
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

    private VideoExtractResult tryExtractVideoFromHuchengApi(String apiUrl, String targetUrl) {
        try (HttpResponse resp = HttpRequest.get(apiUrl)
                .timeout(HTTP_TIMEOUT_MS)
                .header("User-Agent", USER_AGENT)
                .execute()) {

            if (resp.getStatus() < 200 || resp.getStatus() >= 300) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(targetUrl)
                        .message("虎城API请求失败: HTTP " + resp.getStatus())
                        .build();
            }

            JSONObject json = JSONUtil.parseObj(resp.body());
            if (json.getInt("code", -1) != 1) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(targetUrl)
                        .message("虎城API返回错误: " + json.getStr("msg"))
                        .build();
            }

            JSONObject data = json.getJSONObject("data");
            JSONArray list = data != null ? data.getJSONArray("list") : null;
            if (list == null || list.isEmpty()) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(targetUrl)
                        .message("虎城视频列表为空")
                        .build();
            }

            JSONObject firstVideo = list.getJSONObject(0);
            String videoUrl = firstVideo != null ? firstVideo.getStr("video_url") : null;
            if (!StringUtils.hasText(videoUrl) && firstVideo != null) {
                videoUrl = firstVideo.getStr("url");
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

    private VideoExtractResult tryExtractFromHtml(String pageUrl) {
        try (HttpResponse resp = HttpRequest.get(pageUrl)
                .timeout(HTTP_TIMEOUT_MS)
                .setFollowRedirects(true)
                .header("User-Agent", USER_AGENT)
                .execute()) {

            int status = resp.getStatus();
            if (status < 200 || status >= 300) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.FAILED)
                        .targetUrl(pageUrl)
                        .message("页面访问失败: HTTP " + status)
                        .build();
            }

            String html = resp.body();
            String videoUrl = extractVideoFromHtml(html, pageUrl);
            if (StringUtils.hasText(videoUrl)) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.SUCCESS)
                        .videoUrl(videoUrl)
                        .targetUrl(pageUrl)
                        .message("静态HTML提取成功")
                        .build();
            }

            if (looksLikeSpaShell(html)) {
                return VideoExtractResult.builder()
                        .status(VideoExtractStatus.NEED_DYNAMIC_RENDER)
                        .targetUrl(pageUrl)
                        .message("疑似SPA/动态加载，静态HTML未包含视频URL")
                        .build();
            }

            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.FAILED)
                    .targetUrl(pageUrl)
                    .message("静态HTML未找到视频URL")
                    .build();

        } catch (Exception e) {
            log.warn("页面访问异常: {}", pageUrl, e);
            return VideoExtractResult.builder()
                    .status(VideoExtractStatus.FAILED)
                    .targetUrl(pageUrl)
                    .message("页面访问异常")
                    .build();
        }
    }

    private String extractVideoFromHtml(String html, String pageUrl) {
        if (!StringUtils.hasText(html)) {
            return null;
        }

        for (Pattern pattern : VIDEO_URL_PATTERNS) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                String candidate = unescapeUrlCandidate(matcher.group(1));
                String normalized = normalizeUrl(candidate, pageUrl);
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

    private static List<BufferedImage> buildDecodeCandidates(BufferedImage original) {
        List<BufferedImage> candidates = new ArrayList<>();
        candidates.add(original);

        int factor = suggestScaleFactor(original);
        if (factor > 1) {
            candidates.add(scaleUpImage(original, factor));
        }

        return candidates;
    }

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

    private static BinaryBitmap toBitmap(BufferedImage image, boolean useHybridBinarizer) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        Binarizer binarizer = useHybridBinarizer ? new HybridBinarizer(source) : new GlobalHistogramBinarizer(source);
        return new BinaryBitmap(binarizer);
    }

    private boolean softValidateVideoUrl(String videoUrl) {
        if (!StringUtils.hasText(videoUrl)) {
            return false;
        }
        try (HttpResponse resp = HttpRequest.head(videoUrl)
                .timeout(VIDEO_URL_VALIDATE_TIMEOUT_MS)
                .setFollowRedirects(true)
                .header("User-Agent", USER_AGENT)
                .execute()) {
            int status = resp.getStatus();
            return status >= 200 && status < 400;
        } catch (Exception e) {
            // 部分源站不支持 HEAD 或会拦截；不作为强失败
            return true;
        }
    }

    private static boolean isHttpUrl(String content) {
        return content != null && (content.startsWith("http://") || content.startsWith("https://"));
    }

    private static boolean isWeixinUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("weixin.qq.com") || lower.contains("mp.weixin.qq.com");
    }

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
