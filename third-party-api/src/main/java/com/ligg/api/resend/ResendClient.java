/**
 * @author Ligg
 * @date 2026/6/4 12:48
 */
package com.ligg.api.resend;

import com.ligg.common.exception.EmailSendException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;

@Service
@RequiredArgsConstructor
public class ResendClient {

    private static final String TEMPLATE_PATH = "templates/email-verification-code.html";

    @Value("${anime-flow.resend.api_key}")
    private String API_KEY;

    /**
     * 发送邮件域名
     */
    @Value("${anime-flow.resend.domain}")
    private String DOMAIN;

    /// 发送邮件
    public String sendEmail(String email, String code, int expireMinutes)  {

        final String emailHtml = renderVerificationEmail(code, expireMinutes);
        Resend resend = new Resend(API_KEY);

        var domain = "AnimeFlow <noreply@%>".replace("%", DOMAIN);
        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(domain)
                .to(email)
                .subject("【AnimeFlow】邮箱验证码")
                .html(emailHtml)
                .build();
        try {
            CreateEmailResponse response = resend.emails().send(params);
            return response.getId();
        } catch (ResendException e) {
            throw new EmailSendException("验证码邮件发送失败，请稍后再试", e);
        }
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
