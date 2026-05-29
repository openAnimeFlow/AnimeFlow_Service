/**
 * @author Ligg
 * @date 2026/5/28 02:07
 */
package com.ligg.flowclient.controller.bangumi;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.constants.bangumi.BangumiConstants;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.thirdparty.bangumi.enums.SubjectBrowseSort;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.*;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.*;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.service.BangumiCacheService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bangumi 数据代理接口：转发 {@code next.bgm.tv} 请求，统一 CDN 图片处理与 Redis 缓存。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/bangumi")
@RequiredArgsConstructor
public class BangumiController {

    private final BangumiClient bangumiClient;
    private final BangumiCacheService bangumiCacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取每日放送。
     * 对应 Bangumi {@code GET /p1/calendar}，结果写入 Redis 全局缓存。
     */
    @GetMapping("/calendar")
    public Result<CalendarVo> calendar() {
        CalendarVo calendarVo = bangumiCacheService.getOrLoad(
                BangumiConstants.BANGUMI_CALENDAR_CACHE_KEY,
                BangumiConstants.BANGUMI_CALENDAR_LOCK_KEY,
                CalendarVo.class,
                BangumiConstants.BANGUMI_CALENDAR_CACHE_TTL_SECONDS,
                "获取每日放送超时，请稍后重试",
                "获取每日放送被中断",
                () -> {
                    CalendarDto calendarDto = bangumiClient.getCalendar();
                    for (List<CalendarDto.Entry> entries : calendarDto.getDays().values()) {
                        if (entries == null) {
                            continue;
                        }
                        for (CalendarDto.Entry entry : entries) {
                            if (entry == null || entry.getSubject() == null) {
                                continue;
                            }
                            Utils.applyWsrvCdnInPlace(entry.getSubject().getImages());
                        }
                    }
                    CalendarVo vo = new CalendarVo();
                    vo.getDays().putAll(calendarDto.getDays());
                    return vo;
                });
        return Result.success(ResponseCode.SUCCESS, calendarVo);
    }

    /**
     * 条目浏览列表。
     * 对应 Bangumi {@code GET /p1/subjects}；前 10 页走缓存。
     *
     * @param sort  排序方式，默认 rank
     * @param page  页码，从 1 开始
     * @param type  条目类型，默认 2（动画）
     * @param year  放送年份，可选
     * @param month 放送月份，可选
     */
    @GetMapping("/subjects")
    public Result<SubjectsVo> subjects(
            @RequestParam(defaultValue = "rank") SubjectBrowseSort sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        Supplier<SubjectsVo> loader = () -> {
            SubjectsDto dto = bangumiClient.getSubjects(sort, page, type, year, month);
            if (dto.getData() != null) {
                for (var subject : dto.getData()) {
                    if (subject == null) {
                        continue;
                    }
                    Utils.applyWsrvCdnInPlace(subject.getImages());
                }
            }
            SubjectsVo vo = new SubjectsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (page >= 1 && page <= BangumiConstants.BANGUMI_SUBJECTS_MAX_CACHE_PAGE) {
            String yearKey = year != null ? year.toString() : "none";
            String monthKey = month != null ? month.toString() : "none";
            String cacheKey = BangumiConstants.BANGUMI_SUBJECTS_CACHE_KEY_PREFIX + ':' + sort.getValue() + ':' + type + ':'
                    + yearKey + ':' + monthKey + ":page:" + page;
            SubjectsVo vo = bangumiCacheService.getOrLoad(
                    cacheKey,
                    SubjectsVo.class,
                    BangumiConstants.BANGUMI_SUBJECTS_CACHE_TTL_SECONDS,
                    "获取条目列表超时，请稍后重试",
                    "获取条目列表被中断",
                    loader,
                    () -> log.info("条目列表(命中缓存), sort={}, page={}, type={}, year={}, month={}", sort, page, type, year, month));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
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
            TrendingSubjectsVo vo = bangumiCacheService.getOrLoad(
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
     * 对应 Bangumi {@code POST /p1/search/subjects}；全站串行限流，两次请求间隔至少 1.5 秒。
     * 携带 Authorization Bearer 时返回当前用户 {@code interest}，否则不含该字段。
     *
     * @param limit       每页条数，1–100
     * @param offset      偏移量
     * @param body        搜索关键词与筛选条件
     * @param accessToken 可选 Bearer，来自可选鉴权拦截器
     */
    @PostMapping("/search/subjects")
    public Result<SubjectsVo> searchSubjects(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestBody @Valid SearchSubjectsBody body,
            @RequestAttribute(name = AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, required = false)
            String accessToken) {
        SubjectsVo vo = executeSearchWithSerialLimit(() -> {
            SubjectsDto dto = bangumiClient.searchSubjects(body, limit, offset, accessToken);
            if (dto.getData() != null) {
                for (var subject : dto.getData()) {
                    if (subject == null) {
                        continue;
                    }
                    Utils.applyWsrvCdnInPlace(subject.getImages());
                }
            }
            SubjectsVo result = new SubjectsVo();
            BeanUtils.copyProperties(dto, result);
            return result;
        });
        log.info("搜索条目 keyword={}, limit={}, offset={}, total={}",
                body.getKeyword(), limit, offset, vo.getTotal());
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 条目详情。
     * 对应 Bangumi {@code GET /p1/subjects/{id}}。未登录时对近期放送条目缓存；登录后带 token 直查以返回 {@code interest}。
     *
     * @param subjectId   Bangumi 条目 ID
     * @param accessToken 可选 Bearer
     */
    @GetMapping("/subjects/{subjectId}")
    public Result<SubjectDetailVo> subjectDetail(
            @NotNull @PathVariable int subjectId,
            @RequestAttribute(name = AuthorizationInterceptor.ACCESS_TOKEN_REQUEST_ATTRIBUTE, required = false)
            String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            String cacheKey = BangumiConstants.BANGUMI_SUBJECT_DETAIL_CACHE_KEY_PREFIX + ':' + subjectId;
            SubjectDetailVo vo = bangumiCacheService.getOrLoad(
                    cacheKey,
                    BangumiCacheService.lockKey(cacheKey),
                    SubjectDetailVo.class,
                    BangumiConstants.BANGUMI_SUBJECT_DETAIL_CACHE_TTL_SECONDS,
                    "获取条目详情超时，请稍后重试",
                    "获取条目详情被中断",
                    () -> {
                        SubjectDetailDto dto = bangumiClient.getSubject(subjectId, null);
                        Utils.applyWsrvCdnInPlace(dto.getImages());
                        SubjectDetailVo detailVo = new SubjectDetailVo();
                        BeanUtils.copyProperties(dto, detailVo);
                        return detailVo;
                    },
                    BangumiController::shouldCacheSubjectDetail,
                    () -> log.info("条目详情(命中缓存), subjectId={}", subjectId));
            return Result.success(ResponseCode.SUCCESS, vo);
        }

        SubjectDetailDto dto = bangumiClient.getSubject(subjectId, accessToken);
        Utils.applyWsrvCdnInPlace(dto.getImages());
        SubjectDetailVo vo = new SubjectDetailVo();
        BeanUtils.copyProperties(dto, vo);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 条目章节列表。
     * 对应 Bangumi {@code GET /p1/subjects/{id}/episodes}，不做 Redis 缓存。
     *
     * @param subjectId 条目 ID
     * @param limit     每页条数
     * @param offset    偏移量
     */
    @GetMapping("/subjects/{subjectId}/episodes")
    public Result<SubjectEpisodesVo> subjectEpisodes(
            @NotNull @PathVariable int subjectId,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        SubjectEpisodesDto dto = bangumiClient.getSubjectEpisodes(subjectId, limit, offset);
        SubjectEpisodesVo vo = new SubjectEpisodesVo();
        BeanUtils.copyProperties(dto, vo);
        return Result.success(ResponseCode.SUCCESS, vo);
    }

    /**
     * 条目角色与声优列表。
     * 对应 Bangumi {@code GET /p1/subjects/{id}/characters}；前 10 页走缓存。
     *
     * @param subjectId 条目 ID
     * @param limit     每页条数，默认 10
     * @param offset    偏移量，默认 0
     * @param type      角色类型筛选，可选
     */
    @GetMapping("/subjects/{subjectId}/characters")
    public Result<SubjectCharactersVo> subjectCharacters(
            @NotNull @PathVariable int subjectId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer type) {
        Supplier<SubjectCharactersVo> loader = () -> {
            SubjectCharactersDto dto = bangumiClient.getSubjectCharacters(subjectId, limit, offset, type);
            if (dto.getData() != null) {
                for (SubjectCharactersDto.Item item : dto.getData()) {
                    if (item == null) {
                        continue;
                    }
                    if (item.getCharacter() != null) {
                        Utils.applyWsrvCdnInPlace(item.getCharacter().getImages());
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
            SubjectCharactersVo vo = new SubjectCharactersVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_SUBJECT_CHARACTERS_MAX_CACHE_PAGE) {
            String typeKey = type != null ? type.toString() : "none";
            String cacheKey = BangumiConstants.BANGUMI_SUBJECT_CHARACTERS_CACHE_KEY_PREFIX + ':' + subjectId
                    + ':' + typeKey + ':' + limit + ':' + offset;
            SubjectCharactersVo vo = bangumiCacheService.getOrLoad(
                    cacheKey,
                    SubjectCharactersVo.class,
                    BangumiConstants.BANGUMI_SUBJECT_CHARACTERS_CACHE_TTL_SECONDS,
                    "获取条目角色超时，请稍后重试",
                    "获取条目角色被中断",
                    loader,
                    () -> log.info("条目角色(命中缓存), subjectId={}, type={}, limit={}, offset={}",
                            subjectId, type, limit, offset));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
    }

    /**
     * 条目制作人员列表。
     * 对应 Bangumi {@code GET /p1/subjects/{id}/staffs/persons}；前 10 页走缓存。
     *
     * @param subjectId 条目 ID
     * @param limit     每页条数，默认 10
     * @param offset    偏移量，默认 0
     */
    @GetMapping("/subjects/{subjectId}/staffs/persons")
    public Result<SubjectStaffPersonsVo> subjectStaffPersons(
            @NotNull @PathVariable int subjectId,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Supplier<SubjectStaffPersonsVo> loader = () -> {
            SubjectStaffPersonsDto dto = bangumiClient.getSubjectStaffPersons(subjectId, limit, offset);
            if (dto.getData() != null) {
                for (SubjectStaffPersonsDto.Item item : dto.getData()) {
                    if (item != null && item.getStaff() != null) {
                        Utils.applyWsrvCdnInPlace(item.getStaff().getImages());
                    }
                }
            }
            SubjectStaffPersonsVo vo = new SubjectStaffPersonsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_SUBJECT_STAFF_PERSONS_MAX_CACHE_PAGE) {
            String cacheKey = BangumiConstants.BANGUMI_SUBJECT_STAFF_PERSONS_CACHE_KEY_PREFIX + ':' + subjectId
                    + ':' + limit + ':' + offset;
            SubjectStaffPersonsVo vo = bangumiCacheService.getOrLoad(
                    cacheKey,
                    SubjectStaffPersonsVo.class,
                    BangumiConstants.BANGUMI_SUBJECT_STAFF_PERSONS_CACHE_TTL_SECONDS,
                    "获取条目制作人员超时，请稍后重试",
                    "获取条目制作人员被中断",
                    loader,
                    () -> log.info("条目制作人员(命中缓存), subjectId={}, limit={}, offset={}",
                            subjectId, limit, offset));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
    }

    /**
     * 条目评论列表。
     * 对应 Bangumi {@code GET /p1/subjects/{id}/comments}；前 10 页走缓存。
     *
     * @param subjectId 条目 ID
     * @param limit     每页条数，默认 20
     * @param offset    偏移量，默认 0
     */
    @GetMapping("/subjects/{subjectId}/comments")
    public Result<SubjectCommentsVo> subjectComments(
            @NotNull @PathVariable int subjectId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Supplier<SubjectCommentsVo> loader = () -> {
            SubjectCommentsDto dto = bangumiClient.getSubjectComments(subjectId, limit, offset);
            if (dto.getData() != null) {
                for (SubjectCommentsDto.Comment comment : dto.getData()) {
                    if (comment != null && comment.getUser() != null) {
                        Utils.applyWsrvCdnInPlace(comment.getUser().getAvatar());
                    }
                }
            }
            SubjectCommentsVo vo = new SubjectCommentsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_SUBJECT_COMMENTS_MAX_CACHE_PAGE) {
            String cacheKey = BangumiConstants.BANGUMI_SUBJECT_COMMENTS_CACHE_KEY_PREFIX + ':' + subjectId
                    + ':' + limit + ':' + offset;
            SubjectCommentsVo vo = bangumiCacheService.getOrLoad(
                    cacheKey,
                    SubjectCommentsVo.class,
                    BangumiConstants.BANGUMI_SUBJECT_COMMENTS_CACHE_TTL_SECONDS,
                    "获取条目评论超时，请稍后重试",
                    "获取条目评论被中断",
                    loader,
                    () -> log.info("条目评论(命中缓存), subjectId={}, limit={}, offset={}",
                            subjectId, limit, offset));
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
        if (comments != null) {
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
        return Result.success(ResponseCode.SUCCESS, comments);
    }

    /**
     * 仅当条目放送日在「今天起往前 4 个月」内时才写入详情缓存，避免陈旧条目长期占位。
     */
    private static boolean shouldCacheSubjectDetail(SubjectDetailVo vo) {
        SubjectDetailDto.Airtime airtime = vo.getAirtime();
        if (airtime == null || !StringUtils.hasText(airtime.getDate())) {
            return false;
        }
        try {
            LocalDate airtimeDate = LocalDate.parse(airtime.getDate());
            LocalDate today = LocalDate.now();
            return !airtimeDate.isBefore(today.minusMonths(4)) && !airtimeDate.isAfter(today);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * 在全局搜索锁与冷却键约束下串行执行 {@code action}，满足 Bangumi 搜索频率限制。
     *
     * @param action 实际搜索逻辑
     * @return 搜索结果
     */
    private <T> T executeSearchWithSerialLimit(Supplier<T> action) {
        long deadline = System.currentTimeMillis() + BangumiConstants.BANGUMI_SEARCH_WAIT_MILLIS;
        while (true) {
            if (redisTemplate.hasKey(BangumiConstants.BANGUMI_SEARCH_COOLDOWN_KEY)) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new BangumiUpstreamException("搜索请求排队超时，请稍后重试");
                }
                sleepForSearchQueue();
                continue;
            }

            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    BangumiConstants.BANGUMI_SEARCH_LOCK_KEY,
                    "1",
                    BangumiConstants.BANGUMI_SEARCH_LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    if (redisTemplate.hasKey(BangumiConstants.BANGUMI_SEARCH_COOLDOWN_KEY)) {
                        continue;
                    }
                    T result = action.get();
                    redisTemplate.opsForValue().set(
                            BangumiConstants.BANGUMI_SEARCH_COOLDOWN_KEY,
                            "1",
                            BangumiConstants.BANGUMI_SEARCH_COOLDOWN_MILLIS,
                            TimeUnit.MILLISECONDS);
                    return result;
                } finally {
                    redisTemplate.delete(BangumiConstants.BANGUMI_SEARCH_LOCK_KEY);
                }
            }

            if (System.currentTimeMillis() >= deadline) {
                throw new BangumiUpstreamException("搜索请求排队超时，请稍后重试");
            }
            sleepForSearchQueue();
        }
    }

    /**
     * 搜索排队等待，被中断时抛出 {@link BangumiUpstreamException}。
     */
    private void sleepForSearchQueue() {
        try {
            Thread.sleep(BangumiConstants.BANGUMI_SEARCH_POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BangumiUpstreamException("搜索请求被中断", e);
        }
    }
}
