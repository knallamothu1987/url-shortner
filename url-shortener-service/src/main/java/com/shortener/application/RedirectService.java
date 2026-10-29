package com.shortener.application;

import com.shortener.domain.ShortenedUrlRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

    @CircuitBreaker(name = "database", fallbackMethod = "resolveWhenDatabaseDown")
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

    /**
     * Circuit-breaker fallback: when the database circuit is open, serve cache hits only
     * and degrade everything else to 503 instead of hammering a failing database.
     */
    String resolveWhenDatabaseDown(String shortCode, CallNotPermittedException exception) {
        UrlCache.CachedUrl cached = urlCache.get(shortCode);
        if (cached != null
                && (cached.expiresAt() == null || cached.expiresAt().isAfter(Instant.now()))) {
            return cached.longUrl();
        }
        throw new ServiceTemporarilyUnavailableException(
                "Redirect lookups are temporarily degraded; please retry shortly");
    }
}
