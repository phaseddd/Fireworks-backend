package com.fireworks.util;

import java.security.SecureRandom;

/**
 * 随机码生成工具
 */
public class RandomCodeUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    // 去掉容易混淆的字符：0/O、1/I
    private static final char[] ALPHANUM = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private RandomCodeUtil() {
    }

    /**
     * 生成 shareCode（不可枚举，默认 16 bytes -> 32 hex）
     */
    public static String generateShareCode() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /**
     * 生成代理商绑定码（短码，便于人工输入）
     */
    public static String generateBindCode() {
        return "FW-AGENT-" + randomText(6);
    }

    private static String randomText(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM[RANDOM.nextInt(ALPHANUM.length)]);
        }
        return sb.toString();
    }

    private static String toHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = Character.forDigit(v >>> 4, 16);
            hexChars[j * 2 + 1] = Character.forDigit(v & 0x0F, 16);
        }
        return new String(hexChars);
    }
}

