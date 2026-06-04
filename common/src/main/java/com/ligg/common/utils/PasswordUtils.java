/**
 * @author Ligg
 * @date 2026/6/5 05:27
 */
package com.ligg.common.utils;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码加密工具类
 * 使用 BCrypt 算法进行密码哈希和验证
 */
public final class PasswordUtils {

    private PasswordUtils() {
    }

    /**
     * BCrypt 默认强度（cost factor）
     * 范围：4-31，值越大越安全但计算越慢
     * 推荐值：10-12
     */
    private static final int DEFAULT_STRENGTH = 12;

    /**
     * 对明文密码加密
     */
    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(DEFAULT_STRENGTH));
    }

    /**
     * 验证密码是否匹配
     *
     * @param rawPassword    明文密码
     * @param hashedPassword 已哈希的密码
     * @return 是否匹配
     */
    public static boolean verify(String rawPassword, String hashedPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return false;
        }
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // 防止无效的哈希字符串导致异常
            return false;
        }
    }

    /**
     * 检查哈希字符串是否为有效的 BCrypt 格式
     *
     * @param hashedPassword BCrypt 哈希值
     * @return 是否有效
     */
    public static boolean isValidHash(String hashedPassword) {
        if (hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        // BCrypt 哈希格式: $2a$XX$... （长度为60）
        return hashedPassword.startsWith("$2") && hashedPassword.length() == 60;
    }
}
