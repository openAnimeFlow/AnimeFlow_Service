package com.ligg.api.resend;

import com.ligg.common.exception.EmailSendException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ResendClientTest {

    @Test
    void usesJackson3ForEmailRequestAndResponseWithoutSendingMail() {
        AtomicReference<String> body = new AtomicReference<>();
        var builder = WebClient.builder().exchangeFunction(request -> {
            assertEquals("/emails", request.url().getPath());
            assertEquals("Bearer test-token", request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            var output = new MockClientHttpRequest(request.method(), request.url());
            return request.writeTo(output, ExchangeStrategies.withDefaults())
                    .then(Mono.defer(output::getBodyAsString)).doOnNext(body::set)
                    .thenReturn(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"id\":\"email-123\"}").build());
        });
        var client = new ResendClient(builder, "test-token", "example.test");
        assertEquals("email-123", client.sendEmail("recipient@example.test", "123456", 10));
        var json = JsonMapper.builder().build().readTree(body.get());
        assertEquals("AnimeFlow <noreply@example.test>", json.path("from").asText());
        assertEquals("recipient@example.test", json.path("to").get(0).asText());
        assertTrue(json.path("html").asText().contains("123456"));
    }

    @Test
    void upstreamErrorAndMissingEmailIdAreReportedAsEmailFailures() {
        var failing = new ResendClient(WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.TOO_MANY_REQUESTS).build())), "test", "example.test");
        assertThrows(EmailSendException.class, () -> failing.sendEmail("recipient@example.test", "123456", 10));
        var empty = new ResendClient(WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body("{}").build())),
                "test", "example.test");
        assertThrows(EmailSendException.class, () -> empty.sendEmail("recipient@example.test", "123456", 10));
    }
}
