/**
 * @author Ligg
 * @date 2026/5/5 11:08
 */
package com.ligg.api.bangumiapi;

import com.ligg.common.apipath.BangumiApiPath;
import com.ligg.common.constants.ApiConstant;
import com.ligg.common.exception.BangumiUpstreamException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.thirdparty.bangumi.enums.SubjectBrowseSort;
import com.ligg.common.thirdparty.bangumi.request.SearchSubjectsBody;
import com.ligg.common.thirdparty.bangumi.response.CharacterCommentDto;
import com.ligg.common.thirdparty.bangumi.response.CharacterCommentsDto;
import com.ligg.common.thirdparty.bangumi.response.CharacterCastsDto;
import com.ligg.common.thirdparty.bangumi.response.CharacterDetailDto;
import com.ligg.common.thirdparty.bangumi.response.CalendarDto;
import com.ligg.common.thirdparty.bangumi.response.EpisodeCommentDto;
import com.ligg.common.thirdparty.bangumi.response.EpisodeCommentsDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectDetailDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectCharactersDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectEpisodesDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectCommentsDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectStaffPersonsDto;
import com.ligg.common.thirdparty.bangumi.response.SubjectsDto;
import com.ligg.common.thirdparty.bangumi.response.TrendingSubjectsDto;
import com.ligg.common.vo.BangumiUserinfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.netty.handler.timeout.ReadTimeoutException;

@Slf4j
@Service
public class BangumiClientImpl implements BangumiClient {

    private final Duration requestTimeout;
    private final WebClient bangumiNextClient;

    private static final int MAX_IN_MEMORY_BODY_BYTES = 10 * 1024 * 1024;

    private static final ExchangeStrategies DANDAN_EXCHANGE_STRATEGIES = ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_BODY_BYTES))
            .build();

    public BangumiClientImpl(
            @Value("${anime-flow.bangumi.user-agent}") String bangumiUserAgent,
            @Value("${anime-flow.bangumi.request-timeout-seconds:30}") int requestTimeoutSeconds) {
        this.requestTimeout = Duration.ofSeconds(Math.max(5, requestTimeoutSeconds));
        HttpClient reactorHttpClient = HttpClient.create().responseTimeout(this.requestTimeout);
        this.bangumiNextClient = WebClient.builder()
                .exchangeStrategies(DANDAN_EXCHANGE_STRATEGIES)
                .clientConnector(new ReactorClientHttpConnector(reactorHttpClient))
                .baseUrl(BangumiApiPath.BANGUMI_NEXT_API_BASE_URL)
                .defaultHeader(HttpHeaders.USER_AGENT, bangumiUserAgent)
                .build();
    }

    /**
     * 获取当前用户信息
     */
    @Override
    public BangumiUserinfoVO getMe(String accessToken) {
        return blockBangumi(bangumiNextClient.get()
                .uri(ApiConstant.ME)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(BangumiUserinfoVO.class));
    }

    /**
     * 获取每日放送
     */
    @Override
    public CalendarDto getCalendar() {
        log.info("获取每日放送");
        return blockBangumi(bangumiNextClient.get()
                .uri(BangumiApiPath.P1_CALENDAR)
                .retrieve()
                .bodyToMono(CalendarDto.class));
    }

    @Override
    public TrendingSubjectsDto getTrendingSubjects(int type, int limit, int offset) {
        log.info("获取趋势条目 type={} limit={} offset={}", type, limit, offset);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.P1_TRENDING_SUBJECTS)
                        .queryParam("type", type)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build())
                .retrieve()
                .bodyToMono(TrendingSubjectsDto.class));
    }

    @Override
    public SubjectsDto getSubjects(SubjectBrowseSort sort, int page, int type, Integer year, Integer month) {
        log.info("获取条目列表 sort={} page={} type={} year={} month={}", sort.getValue(), page, type, year, month);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path(BangumiApiPath.P1_SUBJECTS)
                            .queryParam("sort", sort.getValue())
                            .queryParam("page", page)
                            .queryParam("type", type);
                    if (year != null) {
                        builder.queryParam("year", year);
                    }
                    if (month != null) {
                        builder.queryParam("month", month);
                    }
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(SubjectsDto.class));
    }

    @Override
    public SubjectsDto searchSubjects(SearchSubjectsBody body, int limit, int offset, String accessToken) {
        SearchSubjectsBody upstreamBody = body.toUpstreamBody();
        log.info("搜索条目 keyword={} limit={} offset={} withAuth={}",
                upstreamBody.getKeyword(), limit, offset, StringUtils.hasText(accessToken));
        var request = bangumiNextClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.P1_SEARCH_SUBJECTS)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build())
                .header(HttpHeaders.ACCEPT, "application/json")
                .bodyValue(upstreamBody);
        if (StringUtils.hasText(accessToken)) {
            request = request.headers(headers -> headers.setBearerAuth(accessToken));
        }
        return blockBangumi(request.retrieve().bodyToMono(SubjectsDto.class));
    }

    @Override
    public SubjectDetailDto getSubject(int subjectId, String accessToken) {
        log.info("获取条目详情 subjectId={} withAuth={}", subjectId, StringUtils.hasText(accessToken));
        var request = bangumiNextClient.get().uri(BangumiApiPath.P1_SUBJECTS + '/' + subjectId);
        if (StringUtils.hasText(accessToken)) {
            request = request.headers(headers -> headers.setBearerAuth(accessToken));
        }
        return blockBangumi(request.retrieve().bodyToMono(SubjectDetailDto.class));
    }

    @Override
    public SubjectEpisodesDto getSubjectEpisodes(int subjectId, int limit, int offset) {
        log.info("获取条目章节 subjectId={} limit={} offset={}", subjectId, limit, offset);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.P1_SUBJECT_EPISODES)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build(subjectId))
                .retrieve()
                .bodyToMono(SubjectEpisodesDto.class));
    }

    @Override
    public SubjectCharactersDto getSubjectCharacters(int subjectId, int limit, int offset, Integer type) {
        log.info("获取条目角色 subjectId={} limit={} offset={} type={}", subjectId, limit, offset, type);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path(BangumiApiPath.P1_SUBJECT_CHARACTERS)
                            .queryParam("limit", limit)
                            .queryParam("offset", offset);
                    if (type != null) {
                        builder.queryParam("type", type);
                    }
                    return builder.build(subjectId);
                })
                .retrieve()
                .bodyToMono(SubjectCharactersDto.class));
    }

    @Override
    public CharacterDetailDto getCharacter(int characterId) {
        log.info("获取角色详情 characterId={}", characterId);
        return blockBangumi(bangumiNextClient.get()
                .uri(BangumiApiPath.P1_CHARACTER, characterId)
                .retrieve()
                .bodyToMono(CharacterDetailDto.class));
    }

    @Override
    public CharacterCommentsDto getCharacterComments(int characterId, int limit, int offset) {
        log.info("获取角色吐槽 characterId={} limit={} offset={}", characterId, limit, offset);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.P1_CHARACTER_COMMENTS)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build(characterId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<CharacterCommentDto>>() {})
                .map(comments -> {
                    CharacterCommentsDto dto = new CharacterCommentsDto();
                    dto.setData(comments);
                    return dto;
                }));
    }

    @Override
    public CharacterCastsDto getCharacterCasts(int characterId, int limit, int offset, Integer subjectType) {
        log.info("获取角色出演作品 characterId={} limit={} offset={} subjectType={}",
                characterId, limit, offset, subjectType);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path(BangumiApiPath.P1_CHARACTER_CASTS)
                            .queryParam("limit", limit)
                            .queryParam("offset", offset);
                    if (subjectType != null) {
                        builder.queryParam("subjectType", subjectType);
                    }
                    return builder.build(characterId);
                })
                .retrieve()
                .bodyToMono(CharacterCastsDto.class));
    }

    @Override
    public SubjectStaffPersonsDto getSubjectStaffPersons(int subjectId, int limit, int offset) {
        log.info("获取条目制作人员 subjectId={} limit={} offset={}", subjectId, limit, offset);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.P1_SUBJECT_STAFF_PERSONS)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build(subjectId))
                .retrieve()
                .bodyToMono(SubjectStaffPersonsDto.class));
    }

    @Override
    public SubjectCommentsDto getSubjectComments(int subjectId, int limit, int offset) {
        log.info("获取条目评论 subjectId={} limit={} offset={}", subjectId, limit, offset);
        return blockBangumi(bangumiNextClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BangumiApiPath.P1_SUBJECT_COMMENTS)
                        .queryParam("limit", limit)
                        .queryParam("offset", offset)
                        .build(subjectId))
                .retrieve()
                .bodyToMono(SubjectCommentsDto.class));
    }

    @Override
    public EpisodeCommentsDto getEpisodeComments(long episodeId) {
        log.info("获取章节评论 episodeId={}", episodeId);
        return blockBangumi(bangumiNextClient.get()
                .uri(BangumiApiPath.P1_EPISODE_COMMENTS, episodeId)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<EpisodeCommentDto>>() {})
                .map(comments -> {
                    EpisodeCommentsDto dto = new EpisodeCommentsDto();
                    dto.setData(comments);
                    return dto;
                }));
    }

    private <T> T blockBangumi(Mono<T> mono) {
        try {
            return mono.timeout(requestTimeout).block();
        } catch (WebClientResponseException.Unauthorized e) {
            throw new LoginExpiredException(e);
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                throw new LoginExpiredException(e);
            }
            if (e.getStatusCode().is4xxClientError()) {
                String responseBody = e.getResponseBodyAsString();
                log.warn("Bangumi 请求被拒绝 status={} body={}", e.getStatusCode().value(), responseBody);
                throw new BangumiUpstreamException("Bangumi 请求参数无效: " + responseBody, e);
            }
            throw e;
        } catch (WebClientRequestException e) {
            throw toBangumiUpstreamException(e);
        } catch (RuntimeException e) {
            if (isTimeoutCause(e)) {
                throw new BangumiUpstreamException("Bangumi 服务响应超时，请稍后重试", e);
            }
            throw e;
        }
    }

    private static BangumiUpstreamException toBangumiUpstreamException(WebClientRequestException e) {
        if (isTimeoutCause(e)) {
            return new BangumiUpstreamException("Bangumi 服务响应超时，请稍后重试", e);
        }
        return new BangumiUpstreamException("暂时无法连接 Bangumi，请检查网络后重试", e);
    }

    private static boolean isTimeoutCause(Throwable e) {
        return hasCauseOfType(e, TimeoutException.class)
                || hasCauseOfType(e, SocketTimeoutException.class)
                || hasCauseOfType(e, ReadTimeoutException.class);
    }

    private static boolean hasCauseOfType(Throwable e, Class<? extends Throwable> type) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
