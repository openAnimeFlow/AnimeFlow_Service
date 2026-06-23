package com.ligg.flowclient.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BangumiOAuthExecutorUsageCheckerTest {

    @Test
    void isInsideOAuthExecutor_detectsLambdaBody() {
        String source = """
                bangumiOAuthExecutor.execute(userId, token -> {
                    bangumiClient.updateCollection(token, subjectId, body);
                    return bangumiClient.getSubject(subjectId, token);
                });
                """;

        int updateOffset = source.indexOf("bangumiClient.updateCollection");
        int subjectOffset = source.indexOf("bangumiClient.getSubject");

        assertTrue(BangumiOAuthExecutorUsageChecker.isInsideOAuthExecutor(source, updateOffset));
        assertTrue(BangumiOAuthExecutorUsageChecker.isInsideOAuthExecutor(source, subjectOffset));
    }

    @Test
    void isInsideOAuthExecutor_rejectsDirectCall() {
        String source = """
                BangumiUserinfoVO me = bangumiClient.getMe(accessToken);
                """;

        int offset = source.indexOf("bangumiClient.getMe");
        assertFalse(BangumiOAuthExecutorUsageChecker.isInsideOAuthExecutor(source, offset));
    }

    @Test
    void isInsideOAuthExecutor_allowsNullTokenOutsideExecutor() {
        String source = """
                () -> toSubjectDetailVo(bangumiClient.getSubject(subjectId, null)),
                """;

        int offset = source.indexOf("bangumiClient.getSubject");
        assertFalse(BangumiOAuthExecutorUsageChecker.isInsideOAuthExecutor(source, offset));
    }

    @Test
    void findMatchingCloseParen_handlesNestedParens() {
        String snippet = "execute(userId, token -> bangumiClient.getMe(token))";
        int open = snippet.indexOf('(');
        int close = BangumiOAuthExecutorUsageChecker.findMatchingCloseParen(snippet, open);
        assertTrue(close > open);
        assertEquals(')', snippet.charAt(close));
    }

    private static void assertEquals(char expected, char actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
