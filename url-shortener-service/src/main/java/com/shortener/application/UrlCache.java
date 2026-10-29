package com.shortener.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class UrlCache {

    private static final Logger logger = LoggerFactory.getLogger(UrlCache.class);
    private static final String KEY_PREFIX = "url-short:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UrlCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public CachedUrl get(String shortCode) {
        try {
            String value = redisTemplate.opsForValue().get(key(shortCode));
            return value == null ? null : objectMapper.readValue(value, CachedUrl.class);
        } catch (DataAccessException exception) {
            logger.warn("Redis cache read failed for short code {}", shortCode, exception);
            return null;
        } catch (JsonProcessingException exception) {
            logger.error("Invalid URL cache entry for short code {}", shortCode, exception);
            evict(shortCode);
            return null;
        }
    }

    public void put(String shortCode, String longUrl, Instant expiresAt) {
        Duration ttl = expiresAt == null
                ? Duration.ofHours(24)
                : Duration.between(Instant.now(), expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }

        try {
            String value = objectMapper.writeValueAsString(new CachedUrl(longUrl, expiresAt));
            redisTemplate.opsForValue().set(key(shortCode), value, ttl);
        } catch (DataAccessException exception) {
            logger.warn("Redis cache write failed for short code {}", shortCode, exception);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize URL cache entry", exception);
        }
    }

    public void evict(String shortCode) {
        try {
            redisTemplate.delete(key(shortCode));
        } catch (DataAccessException exception) {
            logger.warn("Redis cache eviction failed for short code {}", shortCode, exception);
        }
    }

    private String key(String shortCode) {
        return KEY_PREFIX + shortCode;
    }

    public record CachedUrl(String longUrl, Instant expiresAt) {
    }
}
