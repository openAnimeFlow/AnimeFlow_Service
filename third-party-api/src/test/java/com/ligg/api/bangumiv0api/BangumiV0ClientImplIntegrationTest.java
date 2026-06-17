package com.ligg.api.bangumiv0api;

import com.ligg.common.apipath.BangumiApiPath;
import com.ligg.common.thirdparty.bangumi.enums.SubjectImageType;
import org.junit.jupiter.api.Test;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手动集成测试：请求 Bangumi 真实上游。
 * 默认不纳入 {@code mvn test}，需要时单独运行：
 * {@code mvn test -Dtest=BangumiV0ClientImplIntegrationTest -pl third-party-api}
 */
class BangumiV0ClientImplIntegrationTest {

    private static final int SUBJECT_ID = 501958;

    @Test
    void getSubjectImageUrl_printsRealUrlFromBangumi() {
        WebClient webClient = WebClient.builder()
                .baseUrl(BangumiApiPath.BANGUMI_API_BASE_URL)
                .build();
        BangumiV0ClientImpl client = new BangumiV0ClientImpl(webClient, 30);

        String url = client.getSubjectImageUrl(SUBJECT_ID, SubjectImageType.LARGE);
        System.out.println("[integration] subjectId=" + SUBJECT_ID + " imageUrl=" + url);

        assertTrue(StringUtils.hasText(url));
        assertTrue(url.startsWith("http"));
    }
}
