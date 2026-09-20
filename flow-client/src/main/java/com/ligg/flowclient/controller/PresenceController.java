package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.exception.AuthorizationException;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.module.vo.OnlineCountVo;
import com.ligg.flowclient.module.vo.WatchingSubjectVo;
import com.ligg.flowclient.service.PresenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/presence")
public class PresenceController {

    private final PresenceService presenceService;

    @PutMapping("/{presenceId}")
//    @IpEndpointRateLimit(keyPrefix = "animeflow:presence:heartbeat:ip:", seconds = 10, maxRequests = 2)
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

    @GetMapping("/online-count")
    public Result<OnlineCountVo> onlineCount() {
        return Result.success(ResponseCode.SUCCESS, presenceService.onlineCount());
    }

    @GetMapping("/subjects/{subjectId}/online-count")
    public Result<OnlineCountVo> subjectOnlineCount(@PathVariable int subjectId) {
        return Result.success(ResponseCode.SUCCESS, presenceService.subjectOnlineCount(subjectId));
    }

    @GetMapping("/watching-subjects")
    public Result<List<WatchingSubjectVo>> watchingSubjects() {
        return Result.success(ResponseCode.SUCCESS, presenceService.watchingSubjects());
    }
}
