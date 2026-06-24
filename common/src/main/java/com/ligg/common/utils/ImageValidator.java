package com.ligg.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Set;

/**
 * 图片文件安全校验工具，通过文件魔术字节（Magic Bytes）验证文件真实类型，
 * 防止攻击者伪造 Content-Type 上传恶意文件。
 */
public final class ImageValidator {

    private ImageValidator() {
    }

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF87_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61}; // GIF87a
    private static final byte[] GIF89_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}; // GIF89a
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};              // RIFF
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50};              // WEBP

    private static final int HEADER_SIZE = 12;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /**
     * 校验文件名扩展名是否为允许的图片格式。
     *
     * @return 小写扩展名；若不合法则返回 {@code null}
     */
    public static String validateExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        int dotIdx = filename.lastIndexOf('.');
        if (dotIdx < 0 || dotIdx == filename.length() - 1) {
            return null;
        }
        String ext = filename.substring(dotIdx + 1).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(ext) ? ext : null;
    }

    /**
     * 通过读取文件头部魔术字节判断是否为真实的图片文件。
     *
     * @param inputStream 文件输入流（需支持 mark/reset，或为首次读取）
     * @return 检测到的 MIME 类型；若不是合法图片则返回 {@code null}
     */
    public static String detectMimeType(InputStream inputStream) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        int bytesRead = 0;
        while (bytesRead < HEADER_SIZE) {
            int r = inputStream.read(header, bytesRead, HEADER_SIZE - bytesRead);
            if (r == -1) break;
            bytesRead += r;
        }

        if (bytesRead < 3) {
            return null;
        }

        if (startsWith(header, bytesRead, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(header, bytesRead, PNG_MAGIC)) {
            return "image/png";
        }
        if (startsWith(header, bytesRead, GIF87_MAGIC) || startsWith(header, bytesRead, GIF89_MAGIC)) {
            return "image/gif";
        }
        if (bytesRead >= 12
                && startsWith(header, bytesRead, RIFF_MAGIC)
                && Arrays.equals(Arrays.copyOfRange(header, 8, 12), WEBP_MAGIC)) {
            return "image/webp";
        }

        return null;
    }

    private static boolean startsWith(byte[] data, int dataLen, byte[] prefix) {
        if (dataLen < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
