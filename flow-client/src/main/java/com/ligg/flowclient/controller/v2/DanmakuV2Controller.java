package com.ligg.flowclient.controller.v2;

import com.ligg.common.exception.AuthorizationException;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.vo.dandanplay.DandanplayCommentVo;
import com.ligg.flowclient.annotation.DanmakuSendRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.module.dto.DanmakuDto;
import com.ligg.flowclient.service.DanmakuService;
import com.ligg.flowclient.service.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

/**
 * v2 弹幕接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/danmaku")
public class DanmakuV2Controller {

    private final DanmakuService danmakuService;
    private final JwtTokenService jwtTokenService;

    /**
     * 并行获取弹弹play和本站弹幕，等待两者完成后合并。
     */
    @GetMapping(version = "2")
    public Result<DandanplayCommentVo> getDanmaku(
            @RequestParam @Min(value = 1, message = "subjectId 必须大于 0") int subjectId,
            @RequestParam @Min(value = 1, message = "episodeId 必须大于 0") int episodeId,
            @RequestParam(defaultValue = "false") Boolean withRelated,
            @RequestParam(defaultValue = "0") int chConvert) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var upstreamFuture = CompletableFuture.supplyAsync(
                    () -> danmakuService.getDandanplayDanmaku(subjectId, episodeId, withRelated, chConvert), executor);
            var localFuture = CompletableFuture.supplyAsync(
                    () -> danmakuService.getLocalDanmaku(subjectId, episodeId), executor);
            CompletableFuture.allOf(upstreamFuture, localFuture).join();

            var upstream = upstreamFuture.join();
            var comments = new ArrayList<DandanplayCommentVo.DanmakuVo>();
            if (upstream.comments() != null) comments.addAll(upstream.comments());
            comments.addAll(localFuture.join());
            return Result.success(ResponseCode.SUCCESS, new DandanplayCommentVo(comments.size(), comments));
        } catch (CompletionException e) {
            // 保留业务异常类型，交由现有全局异常处理器返回对应错误码。
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw e;
        }
    }

    @PostMapping(version = "2")
    @DanmakuSendRateLimit
    public Result<String> addDanmaku(
            @Valid @RequestBody DanmakuDto danmakuDto,
            @RequestAttribute(AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE) String accessToken) {
        Long userId = jwtTokenService.validateAccessToken(accessToken);
        if (userId == null || userId <= 0) {
            throw new AuthorizationException("用户身份无效");
        }
        danmakuService.saveLocalDanmaku(danmakuDto, userId);
        return Result.success();
    }
}
