package com.ligg.flowclient.controller;

import com.ligg.common.exception.AuthorizationException;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.service.PresenceService;
import com.ligg.flowclient.service.PresenceSseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/presence")
public class PresenceController {

    private final PresenceService presenceService;
    private final PresenceSseService presenceSseService;

    @PutMapping("/{presenceId}")
    public ResponseEntity<Void> heartbeat(
            @Valid @RequestBody PresenceHeartbeatDto body,
            @PathVariable String presenceId,
            @RequestAttribute(
                    value = AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE,
                    required = false) String accessToken,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
            String authorization) {
        boolean hasAuthorization = StringUtils.hasText(authorization);
        if (hasAuthorization && !StringUtils.hasText(accessToken)) {
            throw new AuthorizationException("缺少或无效的访问令牌");
        }
        presenceService.heartbeat(presenceId, body, accessToken, hasAuthorization);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{presenceId}")
    @IpEndpointRateLimit(keyPrefix = "animeflow:presence:offline:ip:", seconds = 10, maxRequests = 5)
    public ResponseEntity<Void> offline(@PathVariable String presenceId) {
        presenceService.offline(presenceId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/online-count", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> onlineCount() {
        return sseResponse(presenceSseService.subscribeOnlineCount());
    }

    @GetMapping(value = "/subjects/{subjectId}/online-count",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subjectOnlineCount(
            @PathVariable int subjectId,
            @RequestParam(required = false) String presenceId) {
        return sseResponse(presenceSseService.subscribeSubjectOnlineCount(subjectId, presenceId));
    }

    @GetMapping(value = "/watching-subjects", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> watchingSubjects() {
        return sseResponse(presenceSseService.subscribeWatchingSubjects());
    }

    private static ResponseEntity<SseEmitter> sseResponse(SseEmitter emitter) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}
