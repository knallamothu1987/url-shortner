package com.shortener.application;

import com.shortener.domain.ShortenedUrlRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RedirectService {

    private final ShortenedUrlRepository repository;
    private final UrlCache urlCache;

    public RedirectService(ShortenedUrlRepository repository, UrlCache urlCache) {
        this.repository = repository;
        this.urlCache = urlCache;
    }

    @Transactional(readOnly = true)
    public String resolve(String shortCode) {
        UrlCache.CachedUrl cached = urlCache.get(shortCode);
        if (cached != null) {
            if (cached.expiresAt() != null && !cached.expiresAt().isAfter(Instant.now())) {
                urlCache.evict(shortCode);
                throw new UrlExpiredException(shortCode);
            }
            return cached.longUrl();
        }

        var url = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
        if (!url.isActive()) {
            throw new UrlNotFoundException(shortCode);
        }
        if (url.getExpiresAt() != null && !url.getExpiresAt().isAfter(Instant.now())) {
            urlCache.evict(shortCode);
            throw new UrlExpiredException(shortCode);
        }

        urlCache.put(shortCode, url.getLongUrl(), url.getExpiresAt());
        return url.getLongUrl();
    }
}
