package com.shortener.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Fixed-window rate limiter backed by Redis (INCR + EXPIRE). Fails open on Redis errors:
 * availability of URL creation is preferred over strict throttling.
 */
@Component
public class RedisRateLimiter {

    private static final Logger logger = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "rate-limit:";

    private final StringRedisTemplate redisTemplate;
    private final int limit;
    private final long windowSeconds;

    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${url-shortener.rate-limit.limit:30}") int limit,
            @Value("${url-shortener.rate-limit.window-seconds:60}") long windowSeconds) {
        this.redisTemplate = redisTemplate;
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    public boolean tryConsume(String clientKey) {
        long window = Instant.now().getEpochSecond() / windowSeconds;
        String key = KEY_PREFIX + clientKey + ":" + window;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
            }
            return count == null || count <= limit;
        } catch (DataAccessException exception) {
            logger.warn("Rate limiter Redis access failed — failing open", exception);
            return true;
        }
    }
}
