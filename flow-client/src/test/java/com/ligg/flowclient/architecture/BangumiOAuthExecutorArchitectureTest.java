package com.ligg.flowclient.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 架构约束：凡向 Bangumi 上游携带 OAuth access token 的 {@code BangumiClient} 调用，
 * 必须通过 {@link com.ligg.flowclient.service.BangumiOAuthExecutor#execute} 执行，
 * 以便在 token 过期时自动 refresh 并重试。
 *
 * <p>豁免场景：
 * <ul>
 *   <li>{@code UserOauthServiceImpl} 中 OAuth 授权码换 token 后、写入 user_oauth 前的首次 {@code getMe}</li>
 *   <li>{@code getSubject}/{@code searchSubjects} 第二/末参为 {@code null} 的匿名请求</li>
 *   <li>源文件顶部标注 {@code // bangumi-oauth-exempt} 的遗留代码（应逐步消除）</li>
 * </ul>
 */
class BangumiOAuthExecutorArchitectureTest {

    @Test
    void bangumiTokenCallsMustUseOAuthExecutor() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        List<String> violations = BangumiOAuthExecutorUsageChecker.findViolations(sourceRoot);

        assertTrue(
                violations.isEmpty(),
                () -> """
                        以下 BangumiClient 调用携带 token 但未经过 bangumiOAuthExecutor.execute：
                        %s
                        请将调用移入 execute 的 lambda，或参照 BangumiOAuthExecutor 文档处理豁免。
                        """.formatted(String.join(System.lineSeparator(), violations)));
    }
}
