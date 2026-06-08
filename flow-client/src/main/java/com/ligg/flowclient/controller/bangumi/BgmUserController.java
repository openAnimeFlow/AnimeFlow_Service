/**
 * @author Ligg
 * @date 2026/5/31 04:14
 */
package com.ligg.flowclient.controller.bangumi;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.api.bgmtvapi.BgmTvClient;
import com.ligg.api.bgmtvapi.BgmUserPageHtmlParser;
import com.ligg.common.constants.bangumi.BangumiConstants;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.thirdparty.bangumi.response.UserCollectionsDto;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.BgmUserStatisticsVo;
import com.ligg.common.vo.bangumi.UserCollectionsVo;
import com.ligg.common.vo.bangumi.UserProfileVo;
import com.ligg.flowclient.service.BangumiCacheService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.function.Supplier;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bangumi/users")
public class BgmUserController {

    private final BangumiClient bangumiClient;
    private final BgmTvClient bgmTvClient;
    private final BgmUserPageHtmlParser bgmUserPageHtmlParser;
    private final BangumiCacheService bangumiCacheService;

    /**
     * 用户资料。
     * 对应 Bangumi {@code GET /p1/users/{username}}，头像走 CDN 替换，按用户名缓存 5 分钟。
     *
     * @param username Bangumi 用户名
     */
    @GetMapping("/{username}")
    public Result<UserProfileVo> userProfile(@NotBlank @PathVariable String username) {
        String cacheKey = BangumiConstants.BANGUMI_USER_PROFILE_CACHE_KEY_PREFIX + ':' + username;
        UserProfileVo vo = bangumiCacheService.getOrLoad(
                cacheKey,
                UserProfileVo.class,
                BangumiConstants.BANGUMI_USER_PROFILE_CACHE_TTL_SECONDS,
                "获取用户资料超时，请稍后重试",
                "获取用户资料被中断",
                () -> {
                    var dto = bangumiClient.getUser(username);
                    Utils.applyWsrvCdnInPlace(dto.getAvatar());
                    UserProfileVo profileVo = new UserProfileVo();
                    BeanUtils.copyProperties(dto, profileVo);
                    return profileVo;
                },
                () -> log.info("用户资料(命中缓存), username={}", username));
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 用户主页统计（收藏、完成、完成率、平均分等）。
     * 对应 {@code GET https://bgm.tv/user/{username}} 页面中 {@code #userStats_all} 的解析结果。
     *
     * @param username Bangumi 用户名或用户 ID
     */
    @GetMapping("/{username}/statistics")
    public Result<BgmUserStatisticsVo> userStatistics(@NotBlank @PathVariable String username) {
        String cacheKey = BangumiConstants.BANGUMI_USER_STATISTICS_CACHE_KEY_PREFIX + ':' + username;
        BgmUserStatisticsVo vo = bangumiCacheService.getOrLoad(
                cacheKey,
                BgmUserStatisticsVo.class,
                BangumiConstants.BANGUMI_USER_STATISTICS_CACHE_TTL_SECONDS,
                "获取用户统计超时，请稍后重试",
                "获取用户统计被中断",
                () -> bgmUserPageHtmlParser.parseUserStatistics(bgmTvClient.fetchUserPage(username)),
                () -> log.info("用户统计(命中缓存), username={}", username));
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 用户条目收藏。
     * 对应 Bangumi {@code GET /p1/users/{username}/collections/subjects}，封面走 CDN 替换；前 5 页缓存 2 分钟。
     *
     * @param username    Bangumi 用户名或用户 ID
     * @param subjectType 条目大类，默认 2（动画）
     * @param type        收藏状态，默认 2（看过）
     * @param limit       每页条数，默认 20
     * @param offset      偏移量，默认 0
     */
    @GetMapping("/{username}/collections/subjects")
    public Result<UserCollectionsVo> userCollections(
            @NotBlank @PathVariable String username,
            @RequestParam(defaultValue = "2") int subjectType,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Supplier<UserCollectionsVo> loader = () -> {
            UserCollectionsDto dto = bangumiClient.getUserCollections(username, subjectType, type, limit, offset);
            if (dto.getData() != null) {
                for (UserCollectionsDto.Item item : dto.getData()) {
                    if (item != null) {
                        Utils.applyWsrvCdnInPlace(item.getImages());
                    }
                }
            }
            UserCollectionsVo vo = new UserCollectionsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_USER_COLLECTIONS_MAX_CACHE_PAGE) {
            String cacheKey = BangumiConstants.BANGUMI_USER_COLLECTIONS_CACHE_KEY_PREFIX + ':' + username
                    + ':' + subjectType + ':' + type + ':' + limit + ':' + offset;
            UserCollectionsVo vo = bangumiCacheService.getOrLoad(
                    cacheKey,
                    UserCollectionsVo.class,
                    BangumiConstants.BANGUMI_USER_COLLECTIONS_CACHE_TTL_SECONDS,
                    "获取用户收藏超时，请稍后重试",
                    "获取用户收藏被中断",
                    loader,
                    () -> log.info("用户收藏(命中缓存), username={}, subjectType={}, type={}, limit={}, offset={}",
                            username, subjectType, type, limit, offset));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
    }

}
