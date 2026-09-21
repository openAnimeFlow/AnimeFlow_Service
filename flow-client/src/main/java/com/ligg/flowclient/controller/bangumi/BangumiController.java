/**
 * @author Ligg
 * @date 2026/5/28 02:07
 */
package com.ligg.flowclient.controller.bangumi;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.constants.BangumiConstants;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.*;
import com.ligg.common.utils.BangumiSeasonUtils;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.*;
import com.ligg.flowclient.annotation.IpEndpointRateLimit;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.annotation.QuarterMonth;
import com.ligg.flowclient.annotation.RecentYear;
import com.ligg.flowclient.service.CacheService;
import com.ligg.flowclient.service.BangumiService;
import com.ligg.flowclient.service.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

/**
 * Bangumi
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/bangumi")
@RequiredArgsConstructor
public class BangumiController {

    private final BangumiClient bangumiClient;
    private final CacheService cacheService;
    private final BangumiService bangumiService;
    private final JwtTokenService jwtTokenService;

    /**
     * 获取季番新番周表。
     * 再按放送星期分组为周一至周日；季度根据当前时间自动计算。
     */
    @GetMapping("/calendar")
    public Result<SeasonCalendarVo> seasonCalendar(
            @RequestParam(required = false)
            @RecentYear
            Integer year,
            @RequestParam(required = false)
            @Min(value = 1, message = "月份必须在 1-12 之间")
            @Max(value = 12, message = "月份必须在 1-12 之间")
            @QuarterMonth
            Integer month) {
        if ((year == null) != (month == null)) {
            throw new IllegalArgumentException("year 和 month 必须同时传入");
        }
        LocalDate now = LocalDate.now();
        int targetYear = year != null ? year : now.getYear();
        int targetMonth = month != null
                ? month
                : BangumiSeasonUtils.startMonthOf(now.getMonthValue());
        String monthPrefix = BangumiSeasonUtils.seasonMonthPrefix(targetYear, targetMonth);
        String cacheKey = BangumiConstants.BANGUMI_SEASON_CALENDAR_CACHE_KEY_PREFIX + ':'
                + monthPrefix;
        SeasonCalendarVo vo = cacheService.getOrLoad(
                cacheKey,
                SeasonCalendarVo.class,
                BangumiConstants.BANGUMI_SEASON_CALENDAR_CACHE_TTL_SECONDS,
                "获取季番周表超时，请稍后重试",
                "获取季番周表被中断",
                () -> bangumiService.getSeasonCalendar(false, targetYear, targetMonth),
                () -> log.info("季番周表(命中缓存), cacheKey={}", cacheKey));
        return Result.success(ResponseCode.SUCCESS, vo);
    }


    /**
     * 获取趋势条目。
     * 对应 Bangumi {@code GET /p1/trending/subjects}；前 10 页走缓存。
     *
     * @param type   条目类型，默认 2（动画）
     * @param limit  每页条数
     * @param offset 偏移量
     */
    @GetMapping("/trending/subjects")
    public Result<TrendingSubjectsVo> trendingSubjects(
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Supplier<TrendingSubjectsVo> loader = () -> {
            TrendingSubjectsDto dto = bangumiClient.getTrendingSubjects(type, limit, offset);
            if (dto.getData() != null) {
                for (TrendingSubjectsDto.Item item : dto.getData()) {
                    if (item == null || item.getSubject() == null) {
                        continue;
                    }
                    Utils.applyWsrvCdnInPlace(item.getSubject().getImages());
                }
            }
            TrendingSubjectsVo vo = new TrendingSubjectsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_TRENDING_MAX_CACHE_PAGE) {
            String cacheKey = BangumiConstants.BANGUMI_TRENDING_CACHE_KEY_PREFIX + ':' + type + ':' + limit + ':' + offset;
            TrendingSubjectsVo vo = cacheService.getOrLoad(
                    cacheKey,
                    TrendingSubjectsVo.class,
                    BangumiConstants.BANGUMI_TRENDING_CACHE_TTL_SECONDS,
                    "获取趋势条目超时，请稍后重试",
                    "获取趋势条目被中断",
                    loader,
                    () -> log.info("趋势条目(命中缓存), type={}, limit={}, offset={}", type, limit, offset));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
    }

    /**
     * 搜索条目。
     *
     * @param limit       每页条数，1–100
     * @param offset      偏移量
     * @param body        搜索关键词与筛选条件
     * @param accessToken 从 Authorization 请求头解析的可选令牌
     */
    @PostMapping("/search/subjects")
    public Result<SubjectsVo> searchSubjects(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestBody @Valid SearchSubjectsBody body,
            @RequestAttribute(name = AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, required = false)
            String accessToken) {
        Long userId = StringUtils.hasText(accessToken) ? jwtTokenService.validateAccessToken(accessToken) : null;
        SubjectsDto dto = bangumiService.searchSubjects(body, limit, offset, userId);
        SubjectsVo vo = new SubjectsVo();
        BeanUtils.copyProperties(dto, vo);
        log.info("搜索条目 keyword={}, limit={}, offset={}, total={}",
                body.getKeyword(), limit, offset, vo.getTotal());
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 搜索建议。
     *
     * @param keyword 搜索关键词
     * @param type    条目类型，默认 2（动画）
     * @param limit   返回条数，1–50
     */
    @GetMapping("/search/suggestions")
    @IpEndpointRateLimit(keyPrefix = "animeflow:bangumi:search-suggestions:ip:", seconds = 60, maxRequests = 30)
    public Result<SearchSuggestionsVo> searchSuggestions(
            @RequestParam @NotBlank String keyword,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        SearchSuggestionsVo vo = bangumiService.getSearchSuggestions(keyword.trim(), type, limit);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 角色详情。
     * 对应 Bangumi {@code GET /p1/characters/{id}}，结果缓存 2 分钟。
     *
     * @param characterId Bangumi 角色 ID
     */
    @GetMapping("/characters/{characterId}")
    public Result<CharacterDetailVo> characterDetail(@NotNull @PathVariable int characterId) {
        String cacheKey = BangumiConstants.BANGUMI_CHARACTER_DETAIL_CACHE_KEY_PREFIX + ':' + characterId;
        CharacterDetailVo vo = cacheService.getOrLoad(
                cacheKey,
                CharacterDetailVo.class,
                BangumiConstants.BANGUMI_CHARACTER_DETAIL_CACHE_TTL_SECONDS,
                "获取角色详情超时，请稍后重试",
                "获取角色详情被中断",
                () -> {
                    CharacterDetailDto dto = bangumiClient.getCharacter(characterId);
                    Utils.applyWsrvCdnInPlace(dto.getImages());
                    CharacterDetailVo detailVo = new CharacterDetailVo();
                    BeanUtils.copyProperties(dto, detailVo);
                    return detailVo;
                },
                () -> log.info("角色详情(命中缓存), characterId={}", characterId));
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 角色吐槽列表（含嵌套回复）。
     * 对应 Bangumi {@code GET /p1/characters/{id}/comments}；前 2 页走缓存，缓存 1 分钟。
     *
     * @param characterId 角色 ID
     * @param limit       每页条数，默认 20
     * @param offset      偏移量，默认 0
     */
    @GetMapping("/characters/{characterId}/comments")
    public Result<CharacterCommentsVo> characterComments(
            @NotNull @PathVariable int characterId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Supplier<CharacterCommentsVo> loader = () -> {
            CharacterCommentsDto dto = bangumiClient.getCharacterComments(characterId, limit, offset);
            applyCharacterCommentAvatarCdn(dto.getData());
            CharacterCommentsVo vo = new CharacterCommentsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_CHARACTER_COMMENTS_MAX_CACHE_PAGE) {
            String cacheKey = BangumiConstants.BANGUMI_CHARACTER_COMMENTS_CACHE_KEY_PREFIX + ':' + characterId
                    + ':' + limit + ':' + offset;
            CharacterCommentsVo vo = cacheService.getOrLoad(
                    cacheKey,
                    CharacterCommentsVo.class,
                    BangumiConstants.BANGUMI_CHARACTER_COMMENTS_CACHE_TTL_SECONDS,
                    "获取角色吐槽超时，请稍后重试",
                    "获取角色吐槽被中断",
                    loader,
                    () -> log.info("角色吐槽(命中缓存), characterId={}, limit={}, offset={}",
                            characterId, limit, offset));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
    }

    /**
     * 角色出演作品列表。
     * 对应 Bangumi {@code GET /p1/characters/{id}/casts}；前 2 页走缓存。
     *
     * @param characterId 角色 ID
     * @param subjectType 条目类型，默认 2（动画）
     * @param limit       每页条数，默认 20
     * @param offset      偏移量，默认 0
     */
    @GetMapping("/characters/{characterId}/casts")
    public Result<CharacterCastsVo> characterCasts(
            @NotNull @PathVariable int characterId,
            @RequestParam(defaultValue = "2") int subjectType,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Supplier<CharacterCastsVo> loader = () -> {
            CharacterCastsDto dto = bangumiClient.getCharacterCasts(characterId, limit, offset, subjectType);
            if (dto.getData() != null) {
                for (CharacterCastsDto.Item item : dto.getData()) {
                    if (item == null) {
                        continue;
                    }
                    if (item.getSubject() != null) {
                        Utils.applyWsrvCdnInPlace(item.getSubject().getImages());
                    }
                    if (item.getCasts() != null) {
                        for (SubjectCharactersDto.Cast cast : item.getCasts()) {
                            if (cast != null && cast.getPerson() != null) {
                                Utils.applyWsrvCdnInPlace(cast.getPerson().getImages());
                            }
                        }
                    }
                }
            }
            CharacterCastsVo vo = new CharacterCastsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_CHARACTER_CASTS_MAX_CACHE_PAGE) {
            String cacheKey = BangumiConstants.BANGUMI_CHARACTER_CASTS_CACHE_KEY_PREFIX + ':' + characterId
                    + ':' + subjectType + ':' + limit + ':' + offset;
            CharacterCastsVo vo = cacheService.getOrLoad(
                    cacheKey,
                    CharacterCastsVo.class,
                    BangumiConstants.BANGUMI_CHARACTER_CASTS_CACHE_TTL_SECONDS,
                    "获取角色出演作品超时，请稍后重试",
                    "获取角色出演作品被中断",
                    loader,
                    () -> log.info("角色出演作品(命中缓存), characterId={}, subjectType={}, limit={}, offset={}",
                            characterId, subjectType, limit, offset));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
    }

    /**
     * 章节评论列表（含嵌套回复）。
     * 对应 Bangumi {@code GET /p1/episodes/{id}/comments}，用户头像走 CDN 替换。
     *
     * @param episodeId Bangumi 章节 ID
     */
    @GetMapping("/episodes/{episodeId}/comments")
    public Result<List<EpisodeCommentDto>> episodeComments(@NotNull @PathVariable long episodeId) {
        List<EpisodeCommentDto> comments = bangumiClient.getEpisodeComments(episodeId).getData();
        applyEpisodeCommentAvatarCdn(comments);
        return Result.success(ResponseCode.SUCCESS, comments);
    }

    private static void applyEpisodeCommentAvatarCdn(List<EpisodeCommentDto> comments) {
        if (comments == null) {
            return;
        }
        Deque<EpisodeCommentDto> pending = new ArrayDeque<>(comments);
        while (!pending.isEmpty()) {
            EpisodeCommentDto comment = pending.pop();
            if (comment.getUser() != null) {
                Utils.applyWsrvCdnInPlace(comment.getUser().getAvatar());
            }
            if (comment.getReplies() != null) {
                pending.addAll(comment.getReplies());
            }
        }
    }

    private static void applyCharacterCommentAvatarCdn(List<CharacterCommentDto> comments) {
        if (comments == null) {
            return;
        }
        for (CharacterCommentDto comment : comments) {
            if (comment == null) {
                continue;
            }
            if (comment.getUser() != null) {
                Utils.applyWsrvCdnInPlace(comment.getUser().getAvatar());
            }
            if (comment.getReplies() != null) {
                for (CharacterCommentDto.Reply reply : comment.getReplies()) {
                    if (reply != null && reply.getUser() != null) {
                        Utils.applyWsrvCdnInPlace(reply.getUser().getAvatar());
                    }
                }
            }
        }
    }


}
