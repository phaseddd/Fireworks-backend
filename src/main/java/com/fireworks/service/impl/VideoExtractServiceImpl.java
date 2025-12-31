package com.fireworks.service.impl;

import com.fireworks.service.VideoExtractService;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频提取服务实现
 * 支持从虎城烟花H5页面提取燃放效果视频
 */
@Slf4j
@Service
public class VideoExtractServiceImpl implements VideoExtractService {

    // 虎城烟花视频API地址
    private static final String VIDEO_API_TEMPLATE = "https://htglhy.huchengfireworks.com/addons/shopro/goods.goods/video_list?id=%s";

    // 从H5 URL中提取商品ID的正则
    private static final Pattern ID_PATTERN = Pattern.compile("[?&]id=(\\d+)");

    @Override
    public String parseQrCode(String imageUrl) {
        try {
            log.info("开始解析二维码: {}", imageUrl);

            // 下载图片
            BufferedImage image = ImageIO.read(new URL(imageUrl));
            if (image == null) {
                log.warn("无法读取图片: {}", imageUrl);
                return null;
            }

            // 使用ZXing解析二维码
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Map<DecodeHintType, Object> hints = new HashMap<>();
            hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);

            Result result = new MultiFormatReader().decode(bitmap, hints);
            String qrContent = result.getText();

            log.info("二维码解析成功: {}", qrContent);
            return qrContent;

        } catch (NotFoundException e) {
            log.warn("图片中未找到二维码: {}", imageUrl);
            return null;
        } catch (Exception e) {
            log.error("解析二维码失败: {}", imageUrl, e);
            return null;
        }
    }

    @Override
    public String extractVideoUrl(String h5Url) {
        try {
            log.info("开始从H5页面提取视频: {}", h5Url);

            // 从URL中提取商品ID
            String productId = extractProductId(h5Url);
            if (productId == null) {
                log.warn("无法从URL中提取商品ID: {}", h5Url);
                return null;
            }

            // 调用视频API
            String apiUrl = String.format(VIDEO_API_TEMPLATE, productId);
            log.info("调用视频API: {}", apiUrl);

            String response = HttpUtil.get(apiUrl, 10000);
            log.debug("API响应: {}", response);

            // 解析JSON响应
            JSONObject json = JSONUtil.parseObj(response);
            if (json.getInt("code", -1) != 1) {
                log.warn("API返回错误: {}", json.getStr("msg"));
                return null;
            }

            // 提取视频URL
            JSONObject data = json.getJSONObject("data");
            if (data == null) {
                log.warn("API响应中无data字段");
                return null;
            }

            JSONArray list = data.getJSONArray("list");
            if (list == null || list.isEmpty()) {
                log.warn("视频列表为空");
                return null;
            }

            // 获取第一个视频的URL
            JSONObject firstVideo = list.getJSONObject(0);
            String videoUrl = firstVideo.getStr("video_url");

            if (videoUrl == null || videoUrl.isEmpty()) {
                videoUrl = firstVideo.getStr("url");
            }

            log.info("成功提取视频URL: {}", videoUrl);
            return videoUrl;

        } catch (Exception e) {
            log.error("提取视频URL失败: {}", h5Url, e);
            return null;
        }
    }

    @Override
    public String extractVideoFromQrCode(String qrCodeImageUrl) {
        // 第一步：解析二维码获取H5 URL
        String h5Url = parseQrCode(qrCodeImageUrl);
        if (h5Url == null) {
            log.warn("二维码解析失败，无法提取视频");
            return null;
        }

        // 第二步：从H5 URL提取视频直链
        return extractVideoUrl(h5Url);
    }

    /**
     * 从H5 URL中提取商品ID
     */
    private String extractProductId(String url) {
        Matcher matcher = ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
