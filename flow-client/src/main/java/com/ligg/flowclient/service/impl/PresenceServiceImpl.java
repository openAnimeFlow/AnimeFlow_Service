package com.ligg.flowclient.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligg.common.constants.Constants;
import com.ligg.common.entity.BangumiSubjectEntity;
import com.ligg.common.model.CoverImages;
import com.ligg.common.utils.Utils;
import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.module.vo.OnlineCountVo;
import com.ligg.flowclient.module.vo.PresenceContext;
import com.ligg.flowclient.module.vo.WatchingSubjectVo;
import com.ligg.flowclient.mapper.BangumiSubjectMapper;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceServiceImpl implements PresenceService {

    private static final long TTL_SECONDS = 180L;
    private static final int MAX_WATCHING_SUBJECTS = 100;
    private static final DefaultRedisScript<List> TOP_WATCHING_SUBJECTS_SCRIPT =
            new DefaultRedisScript<>();

    static {
        TOP_WATCHING_SUBJECTS_SCRIPT.setLocation(
                new ClassPathResource("lua/presence_top_subjects.lua"));
        TOP_WATCHING_SUBJECTS_SCRIPT.setResultType(List.class);
    }

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtTokenService jwtTokenService;
    private final BangumiSubjectMapper bangumiSubjectMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void heartbeat(String presenceId, PresenceHeartbeatDto dto, String accessToken,
                          boolean hasAuthorization) {
        Long userId = hasAuthorization
                ? jwtTokenService.validateAccessToken(accessToken)
                : null;
        long now = Instant.now().getEpochSecond();
        String dedupeKey = userId == null
                ? "visitor:" + dto.getVisitorId()
                : "user:" + userId;
        String recordKey = recordKey(presenceId);
        PresenceContext previous = readPreviousContext(recordKey);
        PresenceContext context = new PresenceContext(
                presenceId,
                dto.getVisitorId(),
                userId,
                dedupeKey,
                dto.getClientType(),
                dto.getAppVersion(),
                dto.getStatus(),
                dto.getSubjectId(),
                dto.getEpisodeId(),
                dto.getPositionSeconds(),
                now);

        redisTemplate.opsForValue().set(recordKey, context, TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForZSet().add(Constants.PRESENCE_DEVICES_KEY, presenceId, now);
        stringRedisTemplate.opsForZSet().add(Constants.PRESENCE_USERS_KEY, dedupeKey, now);
        updateSubjectIndexes(previous, context, now);
        if (presenceChanged(previous, context, now)) {
            publishPresenceChanged();
        }
    }

    @Override
    public void offline(String presenceId) {
        if (!StringUtils.hasText(presenceId)) {
            return;
        }
        String key = recordKey(presenceId);
        PresenceContext previous = readPreviousContext(key);
        long now = Instant.now().getEpochSecond();
        redisTemplate.delete(key);
        stringRedisTemplate.opsForZSet().remove(Constants.PRESENCE_DEVICES_KEY, presenceId);
        if (previous != null) {
            removeGlobalUserIndexIfUnused(previous, now);
        }
        if (previous != null && hasPlaybackContext(previous)) {
            removeSubjectPresenceIndex(previous, now);
            refreshSubjectRanking(previous.getSubjectId(), now);
            removeSubjectFromGlobalIndexIfOffline(previous.getSubjectId());
        }
        if (previous != null && isActive(previous, now)) {
            publishPresenceChanged();
        }
    }

    @Override
    public OnlineCountVo onlineCount() {
        long now = Instant.now().getEpochSecond();
        long expiredBefore = now - TTL_SECONDS;
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                Constants.PRESENCE_DEVICES_KEY, Double.NEGATIVE_INFINITY, expiredBefore);
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                Constants.PRESENCE_USERS_KEY, Double.NEGATIVE_INFINITY, expiredBefore);

        Set<String> users = stringRedisTemplate.opsForZSet().rangeByScore(
                Constants.PRESENCE_USERS_KEY, expiredBefore + 1, now);
        long anonymous = 0;
        long loggedIn = 0;
        if (users != null) {
            for (String user : users) {
                if (user.startsWith("user:")) {
                    loggedIn++;
                } else if (user.startsWith("visitor:")) {
                    anonymous++;
                }
            }
        }
        Long devices = stringRedisTemplate.opsForZSet().zCard(Constants.PRESENCE_DEVICES_KEY);
        return new OnlineCountVo(anonymous + loggedIn, devices == null ? 0 : devices, anonymous, loggedIn);
    }

    @Override
    public OnlineCountVo subjectOnlineCount(int subjectId, String excludePresenceId) {
        Set<String> exclusions = excludePresenceId == null
                ? Set.of() : Set.of(excludePresenceId);
        return subjectOnlineCounts(subjectId, exclusions).forPresence(excludePresenceId);
    }

    @Override
    public PresenceService.SubjectOnlineCounts subjectOnlineCounts(
            int subjectId, Set<String> excludePresenceIds) {
        if (subjectId <= 0) {
            return new PresenceService.SubjectOnlineCounts(
                    new OnlineCountVo(0, 0, 0, 0), Map.of());
        }
        long now = Instant.now().getEpochSecond();
        long expiredBefore = now - TTL_SECONDS;
        String devicesKey = subjectDevicesKey(subjectId);
        String usersKey = subjectUsersKey(subjectId);
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                devicesKey, Double.NEGATIVE_INFINITY, expiredBefore);
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                usersKey, Double.NEGATIVE_INFINITY, expiredBefore);

        Set<String> users = stringRedisTemplate.opsForZSet()
                .rangeByScore(usersKey, expiredBefore + 1, now);
        if (users == null) {
            users = Set.of();
        }
        long anonymous = 0;
        long loggedIn = 0;
        for (String user : users) {
            if (user.startsWith("user:")) {
                loggedIn++;
            } else if (user.startsWith("visitor:")) {
                anonymous++;
            }
        }
        Long devices = stringRedisTemplate.opsForZSet().zCard(devicesKey);
        long onlineDevices = devices == null ? 0 : devices;
        OnlineCountVo total = new OnlineCountVo(
                anonymous + loggedIn, onlineDevices, anonymous, loggedIn);
        Map<String, OnlineCountVo> excluded = new HashMap<>();
        for (String presenceId : excludePresenceIds) {
            PresenceContext context = resolveExcludedPresence(subjectId, presenceId, now);
            if (context == null) {
                excluded.put(presenceId, total);
                continue;
            }
            long withoutAnonymous = anonymous;
            long withoutLoggedIn = loggedIn;
            String excludedUser = context.getDedupeKey();
            if (excludedUser != null && users.contains(excludedUser)) {
                if (excludedUser.startsWith("user:")) {
                    withoutLoggedIn--;
                } else if (excludedUser.startsWith("visitor:")) {
                    withoutAnonymous--;
                }
            }
            long withoutDevices = onlineDevices;
            if (stringRedisTemplate.opsForZSet().score(devicesKey, presenceId) != null) {
                withoutDevices = Math.max(0, withoutDevices - 1);
            }
            excluded.put(presenceId, new OnlineCountVo(
                    withoutAnonymous + withoutLoggedIn, withoutDevices,
                    withoutAnonymous, withoutLoggedIn));
        }
        return new PresenceService.SubjectOnlineCounts(total, Map.copyOf(excluded));
    }

    private PresenceContext resolveExcludedPresence(
            int subjectId, String presenceId, long now) {
        if (!StringUtils.hasText(presenceId)) {
            return null;
        }
        PresenceContext context = readPreviousContext(recordKey(presenceId));
        if (context == null
                || !Objects.equals(context.getSubjectId(), subjectId)
                || !isActive(context, now)) {
            return null;
        }
        return context;
    }

    private void updateSubjectIndexes(PresenceContext previous, PresenceContext current, long now) {
        if (previous != null && hasPlaybackContext(previous)
                && (!hasPlaybackContext(current)
                || !sameSubject(previous, current)
                || !Objects.equals(previous.getDedupeKey(), current.getDedupeKey()))) {
            removeSubjectPresenceIndex(previous, now);
        }
        if (hasPlaybackContext(current)) {
            String devicesKey = subjectDevicesKey(current.getSubjectId());
            String usersKey = subjectUsersKey(current.getSubjectId());
            stringRedisTemplate.opsForZSet().add(devicesKey, current.getPresenceId(), now);
            stringRedisTemplate.opsForZSet().add(usersKey, current.getDedupeKey(), now);
            stringRedisTemplate.opsForZSet().add(
                    Constants.PRESENCE_WATCHING_SUBJECTS_KEY,
                    String.valueOf(current.getSubjectId()), now);
        }
        if (previous != null && hasPlaybackContext(previous)
                && (!hasPlaybackContext(current) || !sameSubject(previous, current))) {
            removeSubjectFromGlobalIndexIfOffline(previous.getSubjectId());
        }
        if (previous != null && hasPlaybackContext(previous)
                && (!hasPlaybackContext(current)
                || !sameSubject(previous, current)
                || !Objects.equals(previous.getDedupeKey(), current.getDedupeKey()))) {
            refreshSubjectRanking(previous.getSubjectId(), now);
        }
        if (hasPlaybackContext(current)) {
            refreshSubjectRanking(current.getSubjectId(), now);
        }
        if (previous != null
                && !Objects.equals(previous.getDedupeKey(), current.getDedupeKey())) {
            removeGlobalUserIndexIfUnused(previous, now);
        }
    }

    @Override
    public List<WatchingSubjectVo> watchingSubjects() {
        long now = Instant.now().getEpochSecond();
        long expiredBefore = now - TTL_SECONDS;
        String indexKey = Constants.PRESENCE_WATCHING_SUBJECTS_KEY;
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                indexKey, Double.NEGATIVE_INFINITY, expiredBefore);
        Comparator<SubjectPresence> bestFirst = Comparator.comparingLong(
                        (SubjectPresence item) -> item.online().getOnlineUsers())
                .reversed()
                .thenComparingInt(SubjectPresence::subjectId);
        List<Integer> candidateIds = topWatchingSubjectIds(expiredBefore);
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<SubjectPresence> rankedSubjects = new java.util.ArrayList<>(candidateIds.size());
        for (Integer subjectId : candidateIds) {
            OnlineCountVo online = subjectOnlineCount(subjectId, null);
            if (online.getOnlineDevices() <= 0 || online.getOnlineUsers() <= 0) {
                stringRedisTemplate.opsForZSet().remove(
                        Constants.PRESENCE_WATCHING_SUBJECTS_RANK_KEY,
                        String.valueOf(subjectId));
                continue;
            }
            rankedSubjects.add(new SubjectPresence(subjectId, online));
        }
        rankedSubjects = rankedSubjects.stream().sorted(bestFirst).toList();
        if (rankedSubjects.isEmpty()) {
            return List.of();
        }

        List<Integer> topSubjectIds = rankedSubjects.stream()
                .map(SubjectPresence::subjectId)
                .toList();
        Map<Integer, BangumiSubjectEntity> subjectsById = new HashMap<>();
        for (BangumiSubjectEntity subject : bangumiSubjectMapper.selectByIds(topSubjectIds)) {
            subjectsById.put(subject.getId(), subject);
        }
        return rankedSubjects.stream()
                .map(item -> {
                    BangumiSubjectEntity subject = subjectsById.get(item.subjectId());
                    if (subject == null) {
                        return null;
                    }
                    CoverImages images = parseImages(subject.getImages());
                    Utils.applyWsrvCdnInPlace(images);
                    return new WatchingSubjectVo(
                            subject.getId(),
                            subject.getName(),
                            subject.getNameCn(),
                            images,
                            item.online());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private record SubjectPresence(int subjectId, OnlineCountVo online) {
    }

    private List<Integer> topWatchingSubjectIds(long expiredBefore) {
        List<?> values = stringRedisTemplate.execute(
                TOP_WATCHING_SUBJECTS_SCRIPT,
                List.of(
                        Constants.PRESENCE_WATCHING_SUBJECTS_RANK_KEY,
                        Constants.PRESENCE_WATCHING_SUBJECTS_KEY),
                String.valueOf(expiredBefore),
                String.valueOf(MAX_WATCHING_SUBJECTS));
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(Object::toString)
                .map(this::parseSubjectId)
                .filter(id -> id != null && id > 0)
                .limit(MAX_WATCHING_SUBJECTS)
                .toList();
    }

    private PresenceContext readPreviousContext(String recordKey) {
        try {
            Object value = redisTemplate.opsForValue().get(recordKey);
            return value instanceof PresenceContext context ? context : null;
        } catch (RuntimeException ignored) {
            // 兼容从旧字符串占位记录迁移到对象序列化期间的短暂脏数据。
            return null;
        }
    }

    /** 播放页仍打开时，短暂暂停也属于有人正在观看该番剧。 */
    private static boolean hasPlaybackContext(PresenceContext context) {
        return ("watching".equals(context.getStatus()) || "paused".equals(context.getStatus()))
                && context.getSubjectId() != null
                && context.getSubjectId() > 0
                && context.getEpisodeId() != null
                && context.getEpisodeId() > 0;
    }

    private static boolean sameSubject(PresenceContext first, PresenceContext second) {
        return first.getSubjectId() != null
                && first.getSubjectId().equals(second.getSubjectId());
    }

    private static boolean presenceChanged(PresenceContext previous,
                                           PresenceContext current,
                                           long now) {
        if (previous == null || !isActive(previous, now)) {
            return true;
        }
        boolean onlineUsersChanged = !Objects.equals(
                previous.getDedupeKey(), current.getDedupeKey());
        boolean watchingSubjectsChanged = !samePlaybackContext(previous, current);
        return onlineUsersChanged || watchingSubjectsChanged;
    }

    private static boolean samePlaybackContext(PresenceContext first, PresenceContext second) {
        return hasPlaybackContext(first)
                && hasPlaybackContext(second)
                && sameSubject(first, second);
    }

    private static boolean isActive(PresenceContext context, long now) {
        return context.getLastSeenAt() > now - TTL_SECONDS;
    }

    private void publishPresenceChanged() {
        try {
            stringRedisTemplate.convertAndSend(Constants.PRESENCE_CHANGED_CHANNEL, "changed");
        } catch (RuntimeException error) {
            // SSE 有低频兜底刷新，通知失败不应影响在线状态写入。
            log.warn("Presence SSE change notification unavailable", error);
        }
    }

    private void removeSubjectFromGlobalIndexIfOffline(Integer subjectId) {
        if (subjectId == null || subjectId <= 0) {
            return;
        }
        Long devices = stringRedisTemplate.opsForZSet()
                .zCard(subjectDevicesKey(subjectId));
        if (devices == null || devices == 0) {
            stringRedisTemplate.opsForZSet().remove(
                    Constants.PRESENCE_WATCHING_SUBJECTS_KEY, String.valueOf(subjectId));
            stringRedisTemplate.opsForZSet().remove(
                    Constants.PRESENCE_WATCHING_SUBJECTS_RANK_KEY, String.valueOf(subjectId));
        }
    }

    private void removeSubjectPresenceIndex(PresenceContext previous, long now) {
        String devicesKey = subjectDevicesKey(previous.getSubjectId());
        stringRedisTemplate.opsForZSet().remove(devicesKey, previous.getPresenceId());
        removeDedupeIndexIfUnused(
                devicesKey,
                subjectUsersKey(previous.getSubjectId()),
                previous.getDedupeKey(),
                previous.getPresenceId(),
                now);
    }

    private void refreshSubjectRanking(Integer subjectId, long now) {
        if (subjectId == null || subjectId <= 0) {
            return;
        }
        String usersKey = subjectUsersKey(subjectId);
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                usersKey, Double.NEGATIVE_INFINITY, now - TTL_SECONDS);
        Long onlineUsers = stringRedisTemplate.opsForZSet().zCard(usersKey);
        if (onlineUsers == null || onlineUsers <= 0) {
            stringRedisTemplate.opsForZSet().remove(
                    Constants.PRESENCE_WATCHING_SUBJECTS_RANK_KEY,
                    String.valueOf(subjectId));
            return;
        }
        stringRedisTemplate.opsForZSet().add(
                Constants.PRESENCE_WATCHING_SUBJECTS_RANK_KEY,
                String.valueOf(subjectId),
                onlineUsers.doubleValue());
    }

    private void removeGlobalUserIndexIfUnused(PresenceContext previous, long now) {
        removeDedupeIndexIfUnused(
                Constants.PRESENCE_DEVICES_KEY,
                Constants.PRESENCE_USERS_KEY,
                previous.getDedupeKey(),
                previous.getPresenceId(),
                now);
    }

    private void removeDedupeIndexIfUnused(
            String devicesKey,
            String usersKey,
            String dedupeKey,
            String excludedPresenceId,
            long now) {
        if (!StringUtils.hasText(dedupeKey)) {
            return;
        }
        Set<String> activePresenceIds = stringRedisTemplate.opsForZSet()
                .rangeByScore(devicesKey, now - TTL_SECONDS + 1, now);
        if (activePresenceIds != null) {
            for (String presenceId : activePresenceIds) {
                if (Objects.equals(presenceId, excludedPresenceId)) {
                    continue;
                }
                PresenceContext context = readPreviousContext(recordKey(presenceId));
                if (context != null
                        && isActive(context, now)
                        && Objects.equals(dedupeKey, context.getDedupeKey())) {
                    return;
                }
            }
        }
        stringRedisTemplate.opsForZSet().remove(usersKey, dedupeKey);
    }

    private Integer parseSubjectId(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private CoverImages parseImages(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, CoverImages.class);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private static String subjectDevicesKey(Integer subjectId) {
        return Constants.PRESENCE_SUBJECT_KEY + ":" + subjectId + ":devices";
    }

    private static String subjectUsersKey(Integer subjectId) {
        return Constants.PRESENCE_SUBJECT_KEY + ":" + subjectId + ":users";
    }

    private static String recordKey(String presenceId) {
        return Constants.PRESENCE_KEY + ":" + presenceId;
    }
}
