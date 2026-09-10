package com.ligg.flowclient.service;

import com.ligg.common.constants.Constants;
import com.ligg.common.exception.RefreshTokenInvalidException;
import com.ligg.common.exception.AccessTokenExpiredException;
import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.statuenum.Platform;
import com.ligg.flowclient.config.JwtProperties;
import com.ligg.flowclient.module.dto.AuthSessionDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_SESSION = "sid";
    private static final String CLAIM_PLATFORM = "platform";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    private SecretKey secretKey;
    private static final DefaultRedisScript<byte[]> REFRESH_SCRIPT = new DefaultRedisScript<>();
    private static final DefaultRedisScript<Long> SESSION_CAS_SCRIPT = new DefaultRedisScript<>();
    static {
        REFRESH_SCRIPT.setLocation(new ClassPathResource("lua/auth_refresh.lua"));
        REFRESH_SCRIPT.setResultType(byte[].class);
        SESSION_CAS_SCRIPT.setLocation(new ClassPathResource("lua/auth_session_cas.lua"));
        SESSION_CAS_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(jwtProperties.getSecret()) || jwtProperties.getSecret().length() < 32) {
            throw new IllegalStateException("anime-flow.jwt.secret 未配置或长度不足 32 字符");
        }
        if (jwtProperties.getExpireSeconds() <= 0 || jwtProperties.getRefreshExpireSeconds() <= 0
                || jwtProperties.getRefreshRetrySeconds() <= 0
                || jwtProperties.getRefreshRetrySeconds() > 120) {
            throw new IllegalStateException("JWT 有效期必须为正，刷新重试窗口必须为 1-120 秒");
        }
        secretKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为指定平台创建独立会话并签发 token 对。
     */
    public FlowTokenVo issueToken(Long userId, String email, Platform platform) {
        AuthSessionDto session = new AuthSessionDto();
        session.setSessionId(newJti());
        session.setUserId(userId);
        session.setEmail(email);
        session.setPlatform(platform);
        return issueTokenPair(session);
    }

    /**
     * 刷新当前会话的 token 对，不影响该用户在其他平台的会话。
     */
    public FlowTokenVo refreshToken(String refreshToken) {
        Claims claims = parseRefreshClaims(refreshToken);
        String refreshJti = claims.getId();
        String sessionId = claims.get(CLAIM_SESSION, String.class);

        Long userId = parseUserId(claims.getSubject());
        for (int attempt = 0; attempt < 8; attempt++) {
            byte[] snapshot = readSessionBytes(sessionId);
            AuthSessionDto session = deserializeSession(snapshot);
            if (session == null || !userId.equals(session.getUserId())) {
                throw new RefreshTokenInvalidException();
            }
            String oldAccessJti = session.getAccessJti();
            String oldRefreshJti = session.getRefreshJti();
            FlowTokenVo candidate = buildTokenPair(session);
            String replayKey = Constants.AUTH_REFRESH_TOKEN_KEY + ":retry:" + refreshJti;
            long grace = Math.min(jwtProperties.getRefreshRetrySeconds(),
                    Math.min(jwtProperties.getExpireSeconds(), jwtProperties.getRefreshExpireSeconds()));
            byte[] result = redisTemplate.execute(REFRESH_SCRIPT,
                    RedisSerializer.byteArray(), RedisSerializer.byteArray(),
                    List.of(sessionRedisKey(sessionId), replayKey, replayKey + ":jti",
                            accessRedisKey(session.getAccessJti()), refreshRedisKey(session.getRefreshJti()),
                            accessRedisKey(oldAccessJti), refreshRedisKey(oldRefreshJti), userSessionsRedisKey(userId)),
                    snapshot, serialize(session), serialize(candidate), bytes(refreshJti),
                    bytes(session.getRefreshJti()), serialize(sessionId),
                    bytes(jwtProperties.getExpireSeconds()), bytes(jwtProperties.getRefreshExpireSeconds()), bytes(grace));
            if (result == null) throw new RefreshTokenInvalidException();
            if (result.length == 0) continue; // Concurrent email update/rotation: reload and retry CAS.
            FlowTokenVo token = (FlowTokenVo) redisTemplate.getValueSerializer().deserialize(result);
            // Cached responses must not advertise a fresh lifetime on every retry.
            token.setExpiresIn(remainingSeconds(token.getAccessToken()));
            token.setRefreshExpiresIn(remainingSeconds(token.getRefreshToken()));
            return token;
        }
        throw new IllegalStateException("会话正在更新，请重试刷新");
    }

    /**
     * 登出当前会话：删除 Redis 中的 session、access/refresh 索引及用户会话集合项。
     * 令牌已失效或会话不存在时幂等成功。
     */
    public void logout(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        Claims claims = parseAccessClaimsLenient(accessToken);
        if (claims == null || !TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            return;
        }

        String sessionId = claims.get(CLAIM_SESSION, String.class);
        AuthSessionDto session = loadSession(sessionId);
        if (session != null) {
            revokeSession(session);
            return;
        }

        String accessJti = claims.getId();
        if (StringUtils.hasText(accessJti)) {
            redisTemplate.delete(accessRedisKey(accessJti));
        }
    }

    /**
     * 校验 AnimeFlow access_token：JWT 签名/过期 + Redis 缓存存在。
     *
     * @return 当前登录用户 ID
     * @throws AccessTokenExpiredException token 过期或 Redis 中不存在时
     */
    public Long validateAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new AccessTokenExpiredException();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new AccessTokenExpiredException();
            }

            String accessJti = claims.getId();
            Object cachedSessionId = redisTemplate.opsForValue().get(accessRedisKey(accessJti));
            String sessionId = claims.get(CLAIM_SESSION, String.class);
            AuthSessionDto session = loadSession(sessionId);
            if (cachedSessionId == null || !cachedSessionId.equals(sessionId) || session == null
                    || !parseUserIdOrExpired(claims.getSubject()).equals(session.getUserId())) {
                throw new AccessTokenExpiredException();
            }

            return parseUserIdOrExpired(claims.getSubject());
        } catch (JwtException e) {
            throw new AccessTokenExpiredException(e);
        }
    }

    /**
     * 绑定邮箱后同步更新 Redis 中该用户所有活跃会话的 email，刷新 token 时新 JWT 会携带最新邮箱。
     */
    public void updateUserEmail(Long userId, String email) {
        String userSessionsKey = userSessionsRedisKey(userId);
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return;
        }
        for (Object sessionIdObj : sessionIds) {
            if (sessionIdObj == null) {
                continue;
            }
            String sessionId = sessionIdObj.toString();
            for (int attempt = 0; attempt < 8; attempt++) {
                byte[] snapshot = readSessionBytes(sessionId);
                AuthSessionDto session = deserializeSession(snapshot);
                if (session == null) {
                    redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
                    break;
                }
                session.setEmail(email);
                Long updated = redisTemplate.execute(SESSION_CAS_SCRIPT,
                        RedisSerializer.byteArray(), new org.springframework.data.redis.serializer.GenericToStringSerializer<>(Long.class),
                        List.of(sessionRedisKey(sessionId)), snapshot, serialize(session));
                if (Long.valueOf(1).equals(updated)) break;
                if (attempt == 7) throw new IllegalStateException("会话正在更新，请重试邮箱更新");
            }
        }
    }

    private static Long parseUserIdOrExpired(String subject) {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new AccessTokenExpiredException();
        }
    }

    private FlowTokenVo issueTokenPair(AuthSessionDto session) {
        FlowTokenVo token = buildTokenPair(session);
        persistSession(session, jwtProperties.getExpireSeconds(), jwtProperties.getRefreshExpireSeconds());
        return token;
    }

    private FlowTokenVo buildTokenPair(AuthSessionDto session) {
        long accessExpireSeconds = jwtProperties.getExpireSeconds();
        long refreshExpireSeconds = jwtProperties.getRefreshExpireSeconds();
        Instant now = Instant.now();

        String accessJti = newJti();
        String refreshJti = newJti();
        session.setAccessJti(accessJti);
        session.setRefreshJti(refreshJti);

        String accessToken = buildToken(
                session,
                accessJti,
                TYPE_ACCESS,
                now,
                now.plusSeconds(accessExpireSeconds)
        );
        String refreshToken = buildToken(
                session,
                refreshJti,
                TYPE_REFRESH,
                now,
                now.plusSeconds(refreshExpireSeconds)
        );


        return new FlowTokenVo(
                accessToken,
                "Bearer",
                accessExpireSeconds,
                refreshToken,
                refreshExpireSeconds,
                session.getSessionId()
        );
    }

    private void persistSession(AuthSessionDto session, long accessExpireSeconds, long refreshExpireSeconds) {
        redisTemplate.opsForValue().set(
                sessionRedisKey(session.getSessionId()),
                session,
                refreshExpireSeconds,
                TimeUnit.SECONDS
        );
        redisTemplate.opsForValue().set(
                accessRedisKey(session.getAccessJti()),
                session.getSessionId(),
                accessExpireSeconds,
                TimeUnit.SECONDS
        );
        redisTemplate.opsForValue().set(
                refreshRedisKey(session.getRefreshJti()),
                session.getSessionId(),
                refreshExpireSeconds,
                TimeUnit.SECONDS
        );
        String userSessionsKey = userSessionsRedisKey(session.getUserId());
        redisTemplate.opsForSet().add(userSessionsKey, session.getSessionId());
        redisTemplate.expire(userSessionsKey, refreshExpireSeconds, TimeUnit.SECONDS);
    }

    private void removeTokenIndexes(AuthSessionDto session) {
        redisTemplate.delete(accessRedisKey(session.getAccessJti()));
        redisTemplate.delete(refreshRedisKey(session.getRefreshJti()));
    }

    private void revokeSession(AuthSessionDto session) {
        redisTemplate.delete(sessionRedisKey(session.getSessionId()));
        removeTokenIndexes(session);
        redisTemplate.opsForSet().remove(userSessionsRedisKey(session.getUserId()), session.getSessionId());
    }

    private AuthSessionDto loadSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return null;
        }
        Object value = redisTemplate.opsForValue().get(sessionRedisKey(sessionId));
        if (value instanceof AuthSessionDto authSessionDto) {
            return authSessionDto;
        }
        return null;
    }

    private Claims parseRefreshClaims(String refreshToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(refreshToken)
                    .getPayload();

            if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))
                    || !StringUtils.hasText(claims.getId())
                    || !StringUtils.hasText(claims.get(CLAIM_SESSION, String.class))) {
                throw new RefreshTokenInvalidException();
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw new RefreshTokenInvalidException();
        }
    }

    /**
     * 解析 access_token 声明；签名仍校验，过期令牌也返回 claims（用于登出）。
     */
    private Claims parseAccessClaimsLenient(String accessToken) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        } catch (JwtException e) {
            return null;
        }
    }

    private String buildToken(
            AuthSessionDto session,
            String jti,
            String type,
            Instant issuedAt,
            Instant expiresAt
    ) {
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(session.getUserId()))
                .claim(CLAIM_EMAIL, session.getEmail())
                .claim(CLAIM_SESSION, session.getSessionId())
                .claim(CLAIM_PLATFORM, session.getPlatform().name())
                .claim(CLAIM_TYPE, type)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    private long remainingSeconds(String token) {
        Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
        return Math.max(0, claims.getExpiration().toInstant().getEpochSecond() - Instant.now().getEpochSecond());
    }

    private byte[] readSessionBytes(String sessionId) {
        return redisTemplate.execute((RedisCallback<byte[]>) connection ->
                connection.stringCommands().get(bytes(sessionRedisKey(sessionId))));
    }

    private AuthSessionDto deserializeSession(byte[] value) {
        if (value == null) return null;
        Object decoded = redisTemplate.getValueSerializer().deserialize(value);
        return decoded instanceof AuthSessionDto session ? session : null;
    }

    @SuppressWarnings("unchecked")
    private byte[] serialize(Object value) {
        return ((RedisSerializer<Object>) redisTemplate.getValueSerializer()).serialize(value);
    }

    private static byte[] bytes(Object value) {
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String newJti() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String sessionRedisKey(String sessionId) {
        return Constants.AUTH_SESSION_KEY + ':' + sessionId;
    }

    private static String accessRedisKey(String accessJti) {
        return Constants.AUTH_TOKEN_KEY + ':' + accessJti;
    }

    private static String refreshRedisKey(String refreshJti) {
        return Constants.AUTH_REFRESH_TOKEN_KEY + ':' + refreshJti;
    }

    private static String userSessionsRedisKey(Long userId) {
        return Constants.AUTH_USER_SESSIONS_KEY + ':' + userId;
    }

    private static Long parseUserId(String subject) {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new RefreshTokenInvalidException();
        }
    }
}
