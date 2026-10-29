package com.shortener.application;

import com.shortener.domain.ShortenedUrl;
import com.shortener.domain.ShortenedUrlRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedirectServiceTest {

    @Mock
    private ShortenedUrlRepository repository;
    @Mock
    private UrlCache urlCache;

    private RedirectService service;

    @BeforeEach
    void setUp() {
        service = new RedirectService(repository, urlCache);
    }

    @Test
    void cacheHitSkipsDatabase() {
        when(urlCache.get("abc123"))
                .thenReturn(new UrlCache.CachedUrl("https://example.com", null));

        assertEquals("https://example.com", service.resolve("abc123"));
        verifyNoInteractions(repository);
    }

    @Test
    void expiredCacheEntryIsEvictedAndReportsGone() {
        when(urlCache.get("abc123")).thenReturn(new UrlCache.CachedUrl(
                "https://example.com", Instant.now().minus(1, ChronoUnit.MINUTES)));

        assertThrows(UrlExpiredException.class, () -> service.resolve("abc123"));
        verify(urlCache).evict("abc123");
    }

    @Test
    void cacheMissLoadsFromDatabaseAndRepopulatesCache() {
        ShortenedUrl url = new ShortenedUrl("abc123", "https://example.com",
                UUID.randomUUID(), null, false);
        when(urlCache.get("abc123")).thenReturn(null);
        when(repository.findByShortCode("abc123")).thenReturn(Optional.of(url));

        assertEquals("https://example.com", service.resolve("abc123"));
        verify(urlCache).put("abc123", "https://example.com", null);
    }

    @Test
    void unknownCodeIsNotFound() {
        when(urlCache.get("ghost1")).thenReturn(null);
        when(repository.findByShortCode("ghost1")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> service.resolve("ghost1"));
    }

    @Test
    void expiredDatabaseUrlReportsGone() {
        ShortenedUrl url = new ShortenedUrl("old123", "https://example.com",
                UUID.randomUUID(), Instant.now().minus(1, ChronoUnit.DAYS), false);
        when(urlCache.get("old123")).thenReturn(null);
        when(repository.findByShortCode("old123")).thenReturn(Optional.of(url));

        assertThrows(UrlExpiredException.class, () -> service.resolve("old123"));
        verify(urlCache).evict("old123");
    }

    @Test
    void circuitBreakerFallbackServesCacheHits() {
        when(urlCache.get("abc123"))
                .thenReturn(new UrlCache.CachedUrl("https://example.com", null));

        String result = service.resolveWhenDatabaseDown("abc123", callNotPermitted());

        assertEquals("https://example.com", result);
    }

    @Test
    void circuitBreakerFallbackDegradesCacheMissesTo503() {
        when(urlCache.get("ghost1")).thenReturn(null);

        assertThrows(ServiceTemporarilyUnavailableException.class,
                () -> service.resolveWhenDatabaseDown("ghost1", callNotPermitted()));
    }

    private CallNotPermittedException callNotPermitted() {
        return CallNotPermittedException.createCallNotPermittedException(
                CircuitBreaker.ofDefaults("database"));
    }
}
