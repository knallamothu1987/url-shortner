package com.shortener.api;

import com.shortener.domain.ShortenedUrl;

import java.time.Instant;

public record UrlResponse(
        String shortCode,
        String shortUrl,
        String longUrl,
        long clickCount,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        boolean customAlias) {

    public static UrlResponse from(ShortenedUrl url, String baseUrl) {
        return new UrlResponse(
                url.getShortCode(),
                baseUrl + "/" + url.getShortCode(),
                url.getLongUrl(),
                url.getClickCount(),
                url.getCreatedAt(),
                url.getExpiresAt(),
                url.isActive(),
                url.isCustomAlias());
    }
}
