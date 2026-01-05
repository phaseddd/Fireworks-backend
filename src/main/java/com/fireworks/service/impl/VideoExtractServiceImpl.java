package com.fireworks.service.impl;

import com.fireworks.service.VideoExtractService;
import com.fireworks.videoextract.VideoExtractResult;
import com.fireworks.videoextract.VideoExtractStatus;
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
 * 说明：
 * 1) 支持一张图片多个二维码
 * 2) 优先尝试：直链 → 已知平台API → HtmlUnit渲染提取
 * 3) HtmlUnit渲染后仍无结果：返回 NEED_DYNAMIC_RENDER，并记录目标网址
 */
@Slf4j
@Service
public class VideoExtractServiceImpl implements VideoExtractService {

    private static final int IMAGE_DOWNLOAD_TIMEOUT_MS = 10000;
    private static final int HTTP_TIMEOUT_MS = 10000;
    private static final int VIDEO_URL_VALIDATE_TIMEOUT_MS = 5000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int QR_SCALE_THRESHOLD_PX = 640;
    private static final int HTMLUNIT_JS_WAIT_MS = 8000;
    private static final int HTMLUNIT_BACKGROUND_JS_WAIT_MS = 3000;
    private static final int HTMLUNIT_MAX_FOLLOW_UP_PAGES = 2;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    private static final Pattern JS_LOCATION_ASSIGN_PATTERN =
            Pattern.compile("(?i)window\\.location(?:\\.href)?\\s*=\\s*['\"]([^'\"]+)['\"]");

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

    private VideoExtractResult tryExtractFromHtml(String pageUrl) {
        return tryExtractFromHtmlUnit(pageUrl);
    }

    private String extractVideoFromHtml(String html, String pageUrl) {
        return extractVideoFromText(html, pageUrl);
    }

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

    private static void waitForJs(WebClient webClient) {
        webClient.waitForBackgroundJavaScriptStartingBefore(HTMLUNIT_JS_WAIT_MS);
        webClient.waitForBackgroundJavaScript(HTMLUNIT_BACKGROUND_JS_WAIT_MS);
    }

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

    private static final class HtmlUnitVideoSniffer {
        private volatile String bestVideoUrl;

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

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofMillis(HTTP_TIMEOUT_MS))
            .build();

    private static HttpClient httpClient() {
        return HTTP_CLIENT;
    }

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

    private static HttpResponse<String> httpGetText(String url, int timeoutMs) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        return httpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return StringUtils.hasText(text) ? text : null;
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
