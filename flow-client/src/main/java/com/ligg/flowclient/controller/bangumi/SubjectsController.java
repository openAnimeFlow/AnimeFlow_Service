/**
 * @author Ligg
 * @date 2026/5/31 04:18
 */
package com.ligg.flowclient.controller.bangumi;

import com.ligg.api.bangumiapi.BangumiClient;
import com.ligg.common.constants.bangumi.BangumiConstants;
import com.ligg.common.response.Result;
import com.ligg.common.statuenum.ResponseCode;
import com.ligg.common.thirdparty.bangumi.enums.SubjectBrowseSort;
import com.ligg.common.thirdparty.bangumi.response.*;
import com.ligg.common.utils.Utils;
import com.ligg.common.vo.bangumi.*;
import com.ligg.flowclient.interceptor.AuthorizationInterceptor;
import com.ligg.flowclient.service.BangumiCacheService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bangumi/subjects")
public class SubjectsController {

    private final BangumiClient bangumiClient;
    private final BangumiCacheService bangumiCacheService;

    /**
     * 条目章节列表。
     * 对应 Bangumi {@code GET /p1/subjects/{id}/episodes}，不做 Redis 缓存。
     *
     * @param subjectId 条目 ID
     * @param limit     每页条数
     * @param offset    偏移量
     */
    @GetMapping("/{subjectId}/episodes")
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
    @GetMapping("/{subjectId}/characters")
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
    @GetMapping("/{subjectId}/staffs/persons")
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
    @GetMapping("/{subjectId}/comments")
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
     * 条目关联列表。
     * 对应 Bangumi {@code GET /p1/subjects/{id}/relations}；前 1 页走缓存。
     *
     * @param subjectId 条目 ID
     * @param type      条目类型筛选，默认 2（动画）
     * @param limit     每页条数，默认 20
     * @param offset    偏移量，默认 0
     */
    @GetMapping("/{subjectId}/relations")
    public Result<SubjectRelationsVo> subjectRelations(
            @NotNull @PathVariable int subjectId,
            @RequestParam(defaultValue = "2") int type,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        Supplier<SubjectRelationsVo> loader = () -> {
            SubjectRelationsDto dto = bangumiClient.getSubjectRelations(subjectId, limit, offset, type);
            if (dto.getData() != null) {
                for (SubjectRelationsDto.Item item : dto.getData()) {
                    if (item != null && item.getSubject() != null) {
                        Utils.applyWsrvCdnInPlace(item.getSubject().getImages());
                    }
                }
            }
            SubjectRelationsVo vo = new SubjectRelationsVo();
            BeanUtils.copyProperties(dto, vo);
            return vo;
        };
        if (limit > 0 && offset / limit + 1 <= BangumiConstants.BANGUMI_SUBJECT_RELATIONS_MAX_CACHE_PAGE) {
            String cacheKey = BangumiConstants.BANGUMI_SUBJECT_RELATIONS_CACHE_KEY_PREFIX + ':' + subjectId
                    + ':' + type + ':' + limit + ':' + offset;
            SubjectRelationsVo vo = bangumiCacheService.getOrLoad(
                    cacheKey,
                    SubjectRelationsVo.class,
                    BangumiConstants.BANGUMI_SUBJECT_RELATIONS_CACHE_TTL_SECONDS,
                    "获取条目关联超时，请稍后重试",
                    "获取条目关联被中断",
                    loader,
                    () -> log.info("条目关联(命中缓存), subjectId={}, type={}, limit={}, offset={}",
                            subjectId, type, limit, offset));
            return Result.success(ResponseCode.SUCCESS, vo);
        }
        return Result.success(ResponseCode.SUCCESS, loader.get());
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
    @GetMapping
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
     * 条目详情。
     * 对应 Bangumi {@code GET /p1/subjects/{id}}。未登录时对近期放送条目缓存；登录后带 token 直查以返回 {@code interest}。
     *
     * @param subjectId   Bangumi 条目 ID
     * @param accessToken 可选 Bearer
     */
    @GetMapping("/{subjectId}")
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
                    SubjectsController::shouldCacheSubjectDetail,
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

}
