package com.fireworks.util;

/**
 * 脱敏工具
 */
public class MaskUtil {

    private MaskUtil() {
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return phone;
        }
        String value = phone.trim();
        if (value.length() < 7) {
            return value;
        }
        String prefix = value.substring(0, 3);
        String suffix = value.substring(Math.max(3, value.length() - 4));
        return prefix + "****" + suffix;
    }

    public static String maskWechat(String wechat) {
        if (wechat == null || wechat.isBlank()) {
            return null;
        }
        String value = wechat.trim();
        if (value.length() <= 2) {
            return "**";
        }
        return value.substring(0, 2) + "****";
    }
}

