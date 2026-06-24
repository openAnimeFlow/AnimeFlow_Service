package com.ligg.common.utils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

/**
 * 图片文件安全校验与清洗工具。
 * <p>
 * 五层防护：
 * <ol>
 *   <li>文件扩展名白名单</li>
 *   <li>魔术字节（Magic Bytes）签名验证</li>
 *   <li>ImageIO 真实解码验证（非图片无法解码）</li>
 *   <li>像素尺寸上限（防御解压炸弹）</li>
 *   <li>重写剥离 EXIF/元数据（隐私保护）</li>
 * </ol>
 */
public final class ImageValidator {

    private ImageValidator() {
    }

    // ======================== 魔术字节签名 ========================

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF87_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF89_MAGIC = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] RIFF_MAGIC = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP_MAGIC = {0x57, 0x45, 0x42, 0x50};

    private static final int HEADER_SIZE = 12;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 单边最大像素，防御解压炸弹 */
    private static final int MAX_DIMENSION = 4096;

    /** JPEG 重写质量（0.95 ≈ 视觉无损） */
    private static final float JPEG_REWRITE_QUALITY = 0.95f;

    // ======================== 清洗结果 ========================

    /**
     * 图片清洗结果，包含去除元数据后的字节和真实 MIME 类型。
     */
    public record SanitizeResult(byte[] bytes, String mimeType) {
    }

    // ======================== 扩展名校验 ========================

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

    // ======================== 魔术字节检测 ========================

    /**
     * 通过文件头部魔术字节检测真实 MIME 类型。
     *
     * @return 检测到的 MIME 类型；非合法图片返回 {@code null}
     */
    public static String detectMimeType(byte[] data) {
        if (data == null || data.length < 3) {
            return null;
        }
        int len = Math.min(data.length, HEADER_SIZE);

        if (startsWith(data, len, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(data, len, PNG_MAGIC)) return "image/png";
        if (startsWith(data, len, GIF87_MAGIC) || startsWith(data, len, GIF89_MAGIC)) return "image/gif";
        if (len >= 12
                && startsWith(data, len, RIFF_MAGIC)
                && Arrays.equals(Arrays.copyOfRange(data, 8, 12), WEBP_MAGIC)) {
            return "image/webp";
        }
        return null;
    }

    // ======================== 核心：校验 + 清洗 ========================

    /**
     * 对图片执行完整的安全校验与元数据清洗。
     * <ol>
     *   <li>魔术字节检测真实文件类型</li>
     *   <li>ImageIO 解码验证（非图片无法通过）</li>
     *   <li>像素尺寸上限检查（防御解压炸弹，上限 4096×4096）</li>
     *   <li>JPEG/PNG 重写以剥离 EXIF 等元数据；GIF/WebP 保留原始字节</li>
     * </ol>
     *
     * @param rawBytes 原始文件字节
     * @return 清洗结果；若文件不是合法图片返回 {@code null}
     * @throws IllegalArgumentException 像素尺寸超限
     * @throws IOException              解码 / 编码异常
     */
    public static SanitizeResult sanitize(byte[] rawBytes) throws IOException {
        String mime = detectMimeType(rawBytes);
        if (mime == null) {
            return null;
        }

        // WebP: Java ImageIO 不原生支持，魔术字节校验后直接放行
        if ("image/webp".equals(mime)) {
            return new SanitizeResult(rawBytes, mime);
        }

        // GIF: 保留原始字节以维持动画帧，仅做解码验证 + 尺寸检查
        if ("image/gif".equals(mime)) {
            validateDecode(rawBytes);
            return new SanitizeResult(rawBytes, mime);
        }

        // JPEG / PNG: 解码验证 + 尺寸检查 + 重写剥离元数据
        BufferedImage image = validateDecode(rawBytes);
        byte[] cleanBytes = rewrite(image, mime);
        return new SanitizeResult(cleanBytes, mime);
    }

    /**
     * 用 ImageIO 真实解码图片，同时校验像素尺寸上限。
     * 先通过 ImageReader 读取头部尺寸（不解码像素），超限则直接拒绝。
     */
    private static BufferedImage validateDecode(byte[] rawBytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(rawBytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new IOException("无法识别的图片格式");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new IllegalArgumentException(
                            "图片尺寸超限，单边最大 " + MAX_DIMENSION + " 像素，当前 " + width + "×" + height);
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("图片解码失败");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    /**
     * 将 BufferedImage 重新编码为干净的字节数组（不携带任何 EXIF / 元数据）。
     * JPEG 使用 0.95 质量参数，PNG 无损重写。
     */
    private static byte[] rewrite(BufferedImage image, String mime) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if ("image/jpeg".equals(mime)) {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(mime);
            if (!writers.hasNext()) {
                throw new IOException("缺少 JPEG 编码器");
            }
            ImageWriter writer = writers.next();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_REWRITE_QUALITY);

                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
        } else {
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("PNG 编码失败");
            }
        }

        return out.toByteArray();
    }

    private static boolean startsWith(byte[] data, int dataLen, byte[] prefix) {
        if (dataLen < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }
}
