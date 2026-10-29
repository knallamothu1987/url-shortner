package com.shortener.application;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ShortCodeGenerator {

    private static final String COUNTER_KEY = "url-shortener:short-code-sequence";
    private final StringRedisTemplate redisTemplate;
    private final int codeLength;

    public ShortCodeGenerator(
            StringRedisTemplate redisTemplate,
            org.springframework.core.env.Environment environment) {
        this.redisTemplate = redisTemplate;
        this.codeLength = environment.getProperty("url-shortener.code-length", Integer.class, 6);
        if (codeLength < 6 || codeLength > 12) {
            throw new IllegalArgumentException("url-shortener.code-length must be between 6 and 12");
        }
    }

    public String nextCode() {
        Long sequence = redisTemplate.opsForValue().increment(COUNTER_KEY);
        if (sequence == null || sequence < 0) {
            throw new IllegalStateException("Redis did not return a valid short-code sequence");
        }
        String encoded = encodeBase62(sequence);
        if (encoded.length() > codeLength) {
            throw new IllegalStateException("Short-code sequence exceeded the configured code length");
        }
        return "0".repeat(codeLength - encoded.length()) + encoded;
    }

    static String encodeBase62(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Sequence must be non-negative");
        }
        final String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        if (value == 0) {
            return "0";
        }

        StringBuilder encoded = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            encoded.append(alphabet.charAt((int) (remaining % alphabet.length())));
            remaining /= alphabet.length();
        }
        return encoded.reverse().toString();
    }
}
