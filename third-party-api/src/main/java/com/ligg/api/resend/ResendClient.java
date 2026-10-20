/**
 * @author Ligg
 * @date 2026/6/4 12:48
 */
package com.ligg.api.resend;

import com.ligg.common.exception.EmailSendException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class ResendClient {

    private static final String TEMPLATE_PATH = "templates/email-verification-code.html";

    private final WebClient webClient;

    /**
     * 发送邮件域名
     */
    private final String domain;

    public ResendClient(WebClient.Builder builder,
                        @Value("${anime-flow.resend.api_key}") String apiKey,
                        @Value("${anime-flow.resend.domain}") String domain) {
        this.webClient = builder.clone().baseUrl("https://api.resend.com")
                .defaultHeaders(headers -> headers.setBearerAuth(apiKey)).build();
        this.domain = domain;
    }

    /// 发送邮件
    public String sendEmail(String email, String code, int expireMinutes)  {

        final String emailHtml = renderVerificationEmail(code, expireMinutes);
        try {
            EmailResponse response = webClient.post().uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("from", "AnimeFlow <noreply@" + domain + ">",
                            "to", List.of(email), "subject", "【AnimeFlow】邮箱验证码", "html", emailHtml))
                    .retrieve().bodyToMono(EmailResponse.class).block(Duration.ofSeconds(30));
            if (response == null || !StringUtils.hasText(response.id())) {
                throw new IllegalStateException("Resend 未返回邮件 ID");
            }
            return response.id();
        } catch (RuntimeException e) {
            throw new EmailSendException("验证码邮件发送失败，请稍后再试", e);
        }
    }

    private record EmailResponse(String id) {
    }

    private String renderVerificationEmail(String code, int expireMinutes) {

        String template = loadTemplate();
        return template
                .replace("{{code}}", code)
                .replace("{{expireMinutes}}", String.valueOf(expireMinutes))
                .replace("{{headerBgUrl}}", "https://wsrv.nl/?url=https://raw.githubusercontent.com/openAnimeFlow/animeFlow-assets/main/image/email_bg.webp")
                .replace("{{logoUrl}}", "https://wsrv.nl/?url=https://raw.githubusercontent.com/openAnimeFlow/animeFlow-assets/main/image/logo1.webp")
                .replace("{{supportWeb}}", "https://ligg.top")
                .replace("{{year}}", String.valueOf(Year.now().getValue()));
    }

    private String loadTemplate() {
        try (InputStream in = ResendClient.class.getClassLoader().getResourceAsStream(ResendClient.TEMPLATE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("未找到邮件模板: " + ResendClient.TEMPLATE_PATH);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取邮件模板失败: " + ResendClient.TEMPLATE_PATH, e);
        }
    }
}
