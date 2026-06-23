package com.ligg.flowclient.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 静态检查：flow-client 中带 Bangumi OAuth token 的 {@code BangumiClient} 调用
 * 必须位于 {@code bangumiOAuthExecutor.execute(...)} 的 lambda 体内。
 */
final class BangumiOAuthExecutorUsageChecker {

    private static final String EXECUTE_MARKER = "bangumiOAuthExecutor.execute";

    private static final Set<String> ALWAYS_TOKEN_METHODS = Set.of(
            "getMe",
            "updateCollection",
            "getMeCollections"
    );

    private static final Pattern ALWAYS_TOKEN_CALL = Pattern.compile(
            "bangumiClient\\.(getMe|updateCollection|getMeCollections)\\s*\\(");

    /**
     * OAuth 授权码换 token 后首次调用 {@code getMe}，此时尚未写入 user_oauth，无法走 executor。
     */
    private static final Pattern OAUTH_EXCHANGE_EXEMPTION = Pattern.compile(
            "bangumiClient\\.getMe\\s*\\(\\s*token\\.getAccess_token\\s*\\(\\)\\s*\\)");

    private record Violation(String file, int line, String snippet) {
        @Override
        public String toString() {
            return file + ":" + line + " -> " + snippet.trim();
        }
    }

    private BangumiOAuthExecutorUsageChecker() {
    }

    static List<String> findViolations(Path sourceRoot) throws IOException {
        List<Violation> violations = new ArrayList<>();
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    scanFile(file, violations);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return violations.stream().map(Violation::toString).toList();
    }

    private static void scanFile(Path file, List<Violation> violations) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.contains("// bangumi-oauth-exempt")) {
            return;
        }

        String relativePath = file.toString().replace('\\', '/');
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.contains("bangumiClient.")) {
                continue;
            }
            if (isOAuthExchangeExemption(line)) {
                continue;
            }
            if (!isTokenizedBangumiCall(line)) {
                continue;
            }
            int callOffset = lineOffset(lines, i, line.indexOf("bangumiClient."));
            if (isInsideOAuthExecutor(content, callOffset)) {
                continue;
            }
            violations.add(new Violation(relativePath, i + 1, line));
        }
    }

    private static boolean isOAuthExchangeExemption(String line) {
        return OAUTH_EXCHANGE_EXEMPTION.matcher(line).find();
    }

    private static boolean isTokenizedBangumiCall(String line) {
        Matcher alwaysToken = ALWAYS_TOKEN_CALL.matcher(line);
        if (alwaysToken.find()) {
            return ALWAYS_TOKEN_METHODS.contains(alwaysToken.group(1));
        }
        if (line.contains("bangumiClient.getSubject(")) {
            return !line.contains(", null");
        }
        if (line.contains("bangumiClient.searchSubjects(")) {
            return !line.contains(", null");
        }
        return false;
    }

    private static int lineOffset(String[] lines, int lineIndex, int columnInLine) {
        int offset = 0;
        for (int i = 0; i < lineIndex; i++) {
            offset += lines[i].length() + 1;
        }
        return offset + columnInLine;
    }

    /**
     * 判断 {@code callOffset} 是否落在某次 {@code bangumiOAuthExecutor.execute(...)} 调用的参数列表内。
     */
    static boolean isInsideOAuthExecutor(String content, int callOffset) {
        int searchFrom = 0;
        while (true) {
            int executeIndex = content.indexOf(EXECUTE_MARKER, searchFrom);
            if (executeIndex < 0 || executeIndex >= callOffset) {
                return false;
            }
            int openParen = content.indexOf('(', executeIndex + EXECUTE_MARKER.length());
            if (openParen < 0) {
                searchFrom = executeIndex + EXECUTE_MARKER.length();
                continue;
            }
            int closeParen = findMatchingCloseParen(content, openParen);
            if (closeParen < 0) {
                return false;
            }
            if (callOffset > openParen && callOffset < closeParen) {
                return true;
            }
            searchFrom = closeParen + 1;
        }
    }

    static int findMatchingCloseParen(String content, int openParenIndex) {
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escape = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = openParenIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                }
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++;
                }
                continue;
            }
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
