package com.ligg.flowclient.service;

import com.ligg.common.constants.Constants;
import com.ligg.common.exception.AuthenticationFailedException;
import com.ligg.common.exception.LoginExpiredException;
import com.ligg.common.response.FlowTokenVo;
import com.ligg.common.statuenum.Platform;
import com.ligg.flowclient.config.JwtProperties;
import com.ligg.flowclient.module.dto.AuthSessionDto;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
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

    @PostConstruct
    void init() {
        if (!StringUtils.hasText(jwtProperties.getSecret()) || jwtProperties.getSecret().length() < 32) {
            throw new IllegalStateException("anime-flow.jwt.secret 未配置或长度不足 32 字符");
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

        AuthSessionDto session = loadSession(sessionId);
        if (session == null
                || !refreshJti.equals(session.getRefreshJti())
                || !parseUserId(claims.getSubject()).equals(session.getUserId())) {
            throw new AuthenticationFailedException("刷新令牌无效或已过期");
        }

        removeTokenIndexes(session);
        return issueTokenPair(session);
    }

    /**
     * 校验 AnimeFlow access_token：JWT 签名/过期 + Redis 缓存存在。
     *
     * @return 当前登录用户 ID
     * @throws LoginExpiredException token 过期或 Redis 中不存在时
     */
    public Long validateAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new LoginExpiredException();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new LoginExpiredException();
            }

            String accessJti = claims.getId();
            Object cachedSessionId = redisTemplate.opsForValue().get(accessRedisKey(accessJti));
            if (cachedSessionId == null) {
                throw new LoginExpiredException();
            }

            return parseUserIdOrExpired(claims.getSubject());
        } catch (JwtException e) {
            throw new LoginExpiredException(e);
        }
    }

    private static Long parseUserIdOrExpired(String subject) {
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new LoginExpiredException();
        }
    }

    private FlowTokenVo issueTokenPair(AuthSessionDto session) {
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

        persistSession(session, accessExpireSeconds, refreshExpireSeconds);

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
        redisTemplate.opsForSet().add(userSessionsRedisKey(session.getUserId()), session.getSessionId());
    }

    private void removeTokenIndexes(AuthSessionDto session) {
        redisTemplate.delete(accessRedisKey(session.getAccessJti()));
        redisTemplate.delete(refreshRedisKey(session.getRefreshJti()));
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

            if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new AuthenticationFailedException("刷新令牌无效或已过期");
            }
            return claims;
        } catch (JwtException e) {
            throw new AuthenticationFailedException("刷新令牌无效或已过期");
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
            throw new AuthenticationFailedException("刷新令牌无效或已过期");
        }
    }
}
