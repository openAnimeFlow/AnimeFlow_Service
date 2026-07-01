package com.ligg.common.utils;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bangumi wiki 原始字符串（infobox）解析工具。
 */
public final class InfoboxParser {

    private InfoboxParser() {
    }

    /** info 字段提取的键及其顺序 */
    private static final List<String> INFO_KEY_ORDER = List.of("话数", "放送开始", "导演", "原作", "人物设定");

    /** 话数后缀 */
    private static final String EPISODE_COUNT_SUFFIX = "话";

    /** 匹配 |key= value 行 */
    private static final Pattern INFOBOX_LINE = Pattern.compile("^\\|(.+?)=\\s*(.+)");

    /**
     * 将 infobox wiki 字符串解析为 key→value 映射。
     */
    public static Map<String, String> toMap(String infobox) {
        if (!StringUtils.hasText(infobox)) {
            return Collections.emptyMap();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : infobox.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.equals("}}")) {
                break;
            }
            if (trimmed.startsWith("{{")) {
                continue;
            }
            Matcher m = INFOBOX_LINE.matcher(trimmed);
            if (m.matches()) {
                fields.put(m.group(1).trim(), m.group(2).trim());
            }
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * 从 infobox 中提取摘要信息字符串。
     * 按顺序拼接 话数 / 放送开始 / 导演 / 原作 / 人物设定，缺失的键自动跳过。
     */
    public static String toInfo(String infobox) {
        if (!StringUtils.hasText(infobox)) {
            return "";
        }
        Map<String, String> fields = toMap(infobox);
        return INFO_KEY_ORDER.stream()
                .map(key -> {
                    String value = fields.get(key);
                    if (!StringUtils.hasText(value)) {
                        return null;
                    }
                    if ("话数".equals(key) && !value.endsWith(EPISODE_COUNT_SUFFIX)) {
                        return value + EPISODE_COUNT_SUFFIX;
                    }
                    return value;
                })
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(" / "));
    }

    /**
     * 将 infobox wiki 字符串解析为 key → 多值列表，同时处理单行和多行（{…}）值。
     * <pre>
     * 单行：|话数= 25          → "话数" → ["25"]
     * 多行：|别名={            → "别名" → ["叛逆的鲁路修R2", "Code Geass: ..."]
     *         [叛逆的鲁路修R2]
     *         [Code Geass: ...]
     *       }
     * </pre>
     */
    public static Map<String, List<String>> toEntries(String infobox) {
        if (!StringUtils.hasText(infobox)) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> entries = new LinkedHashMap<>();
        String[] lines = infobox.split("\\R");
        String currentKey = null;
        boolean inBlock = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("}}")) {
                break;
            }
            if (trimmed.startsWith("{{")) {
                continue;
            }
            if (inBlock) {
                if (trimmed.equals("}")) {
                    inBlock = false;
                    currentKey = null;
                    continue;
                }
                // 多行值：去掉首尾的 [ ]
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    String v = trimmed.substring(1, trimmed.length() - 1).trim();
                    if (!v.isEmpty()) {
                        entries.get(currentKey).add(v);
                    }
                }
                continue;
            }
            Matcher m = INFOBOX_LINE.matcher(trimmed);
            if (m.matches()) {
                currentKey = m.group(1).trim();
                String value = m.group(2).trim();
                if (value.equals("{")) {
                    inBlock = true;
                    entries.put(currentKey, new ArrayList<>());
                } else {
                    entries.put(currentKey, new ArrayList<>(List.of(value)));
                    currentKey = null;
                }
            }
        }
        return Collections.unmodifiableMap(entries);
    }
}
