package com.ligg.flowclient.service.impl;

import com.ligg.common.constants.Constants;
import com.ligg.flowclient.module.dto.PresenceHeartbeatDto;
import com.ligg.flowclient.module.vo.OnlineCountVo;
import com.ligg.flowclient.module.vo.PresenceContext;
import com.ligg.flowclient.service.JwtTokenService;
import com.ligg.flowclient.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private static final long TTL_SECONDS = 90L;
    private static final long EXPIRED_BEFORE_SECONDS = TTL_SECONDS;

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final JwtTokenService jwtTokenService;

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

        // 单设备上下文使用 RedisConfig 中统一配置的对象序列化器，并随 TTL 过期。
        redisTemplate.opsForValue().set(recordKey, context, TTL_SECONDS, TimeUnit.SECONDS);
        stringRedisTemplate.opsForZSet().add(Constants.PRESENCE_DEVICES_KEY, presenceId, now);
        stringRedisTemplate.opsForZSet().add(Constants.PRESENCE_USERS_KEY, dedupeKey, now);
        updateSubjectIndexes(previous, context, now);

    }

    @Override
    public void offline(String presenceId) {
        if (!StringUtils.hasText(presenceId)) {
            return;
        }
        String key = recordKey(presenceId);
        redisTemplate.delete(key);
        stringRedisTemplate.opsForZSet().remove(Constants.PRESENCE_DEVICES_KEY, presenceId);
    }

    @Override
    public OnlineCountVo onlineCount() {
        long now = Instant.now().getEpochSecond();
        long expiredBefore = now - EXPIRED_BEFORE_SECONDS;
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
    public OnlineCountVo subjectOnlineCount(int subjectId) {
        if (subjectId <= 0) {
            return new OnlineCountVo(0, 0, 0, 0);
        }
        long now = Instant.now().getEpochSecond();
        long expiredBefore = now - EXPIRED_BEFORE_SECONDS;
        String devicesKey = subjectDevicesKey(subjectId);
        String usersKey = subjectUsersKey(subjectId);
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                devicesKey, Double.NEGATIVE_INFINITY, expiredBefore);
        stringRedisTemplate.opsForZSet().removeRangeByScore(
                usersKey, Double.NEGATIVE_INFINITY, expiredBefore);

        Set<String> users = stringRedisTemplate.opsForZSet()
                .rangeByScore(usersKey, expiredBefore + 1, now);
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
        Long devices = stringRedisTemplate.opsForZSet().zCard(devicesKey);
        return new OnlineCountVo(anonymous + loggedIn, devices == null ? 0 : devices, anonymous, loggedIn);
    }

    private void updateSubjectIndexes(PresenceContext previous, PresenceContext current, long now) {
        if (previous != null && isWatching(previous)
                && (!isWatching(current) || !sameSubject(previous, current))) {
            stringRedisTemplate.opsForZSet().remove(
                    subjectDevicesKey(previous.getSubjectId()), current.getPresenceId());
        }
        if (isWatching(current)) {
            String devicesKey = subjectDevicesKey(current.getSubjectId());
            String usersKey = subjectUsersKey(current.getSubjectId());
            stringRedisTemplate.opsForZSet().add(devicesKey, current.getPresenceId(), now);
            stringRedisTemplate.opsForZSet().add(usersKey, current.getDedupeKey(), now);
        }
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

    private static boolean isWatching(PresenceContext context) {
        return "watching".equals(context.getStatus())
                && context.getSubjectId() != null
                && context.getSubjectId() > 0;
    }

    private static boolean sameSubject(PresenceContext first, PresenceContext second) {
        return first.getSubjectId() != null
                && first.getSubjectId().equals(second.getSubjectId());
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
