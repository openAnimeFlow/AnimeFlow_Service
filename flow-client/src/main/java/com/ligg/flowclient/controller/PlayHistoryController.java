package com.ligg.flowclient.controller;

import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.SavePlayHistoryDto;
import com.ligg.flowclient.module.vo.PlayHistoryVo;
import com.ligg.flowclient.service.UserPlayHistoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/play-history")
public class PlayHistoryController {

    private final UserPlayHistoryService userPlayHistoryService;

    @PutMapping
    public Result<Void> save(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @Valid @RequestBody SavePlayHistoryDto body) {
        userPlayHistoryService.save(accessToken, body);
        return Result.success();
    }

    @GetMapping
    public Result<List<PlayHistoryVo>> list(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return Result.success(ResponseCode.SUCCESS,
                userPlayHistoryService.list(accessToken, limit, offset));
    }

    @GetMapping("/subjects/{subjectId}")
    public Result<PlayHistoryVo> getBySubject(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @PathVariable int subjectId) {
        return Result.success(ResponseCode.SUCCESS,
                userPlayHistoryService.getBySubject(accessToken, subjectId));
    }

    @DeleteMapping("/subjects/{subjectId}/progress")
    public Result<Void> clearProgress(
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken,
            @PathVariable int subjectId) {
        userPlayHistoryService.clearProgress(accessToken, subjectId);
        return Result.success();
    }
}
