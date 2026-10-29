package com.shortener.application;

import com.shortener.domain.ShortenedUrl;
import com.shortener.domain.ShortenedUrlRepository;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlShorteningServiceTest {

    @Mock
    private ShortenedUrlRepository repository;
    @Mock
    private ShortCodeGenerator codeGenerator;
    @Mock
    private UrlValidator urlValidator;
    @Mock
    private UrlCache urlCache;

    private UrlShorteningService service;
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new UrlShorteningService(repository, codeGenerator, urlValidator, urlCache);
    }

    @Test
    void createGeneratesCodeAndCaches() {
        when(urlValidator.validateAndNormalize("https://example.com"))
                .thenReturn("https://example.com");
        when(repository.findByOwnerIdAndLongUrl(ownerId, "https://example.com"))
                .thenReturn(Optional.empty());
        when(codeGenerator.nextCode()).thenReturn("abc123");
        when(repository.existsByShortCode("abc123")).thenReturn(false);
        when(repository.save(any(ShortenedUrl.class))).thenAnswer(inv -> inv.getArgument(0));

        ShortenedUrl url = service.create(ownerId, "https://example.com", null, null);

        assertEquals("abc123", url.getShortCode());
        assertEquals(ownerId, url.getOwnerId());
        verify(urlCache).put("abc123", "https://example.com", null);
    }

    @Test
    void createReturnsExistingActiveDuplicate() {
        ShortenedUrl existing = new ShortenedUrl("dup111", "https://example.com", ownerId,
                null, false);
        when(urlValidator.validateAndNormalize("https://example.com"))
                .thenReturn("https://example.com");
        when(repository.findByOwnerIdAndLongUrl(ownerId, "https://example.com"))
                .thenReturn(Optional.of(existing));

        assertSame(existing, service.create(ownerId, "https://example.com", null, null));
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsPastExpiry() {
        when(urlValidator.validateAndNormalize(anyString())).thenReturn("https://example.com");

        assertThrows(InvalidUrlException.class, () -> service.create(
                ownerId, "https://example.com", null,
                Instant.now().minus(1, ChronoUnit.HOURS)));
    }

    @Test
    void createRejectsTakenCustomAlias() {
        when(urlValidator.validateAndNormalize(anyString())).thenReturn("https://example.com");
        when(repository.findByOwnerIdAndLongUrl(any(), anyString())).thenReturn(Optional.empty());
        when(repository.existsByShortCode("mycode")).thenReturn(true);

        assertThrows(ShortCodeAlreadyExistsException.class,
                () -> service.create(ownerId, "https://example.com", "mycode", null));
    }

    @Test
    void createFailsAfterFiveCodeCollisions() {
        when(urlValidator.validateAndNormalize(anyString())).thenReturn("https://example.com");
        when(repository.findByOwnerIdAndLongUrl(any(), anyString())).thenReturn(Optional.empty());
        when(codeGenerator.nextCode()).thenReturn("taken1");
        when(repository.existsByShortCode("taken1")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> service.create(ownerId, "https://example.com", null, null));
    }

    @Test
    void getOwnedRejectsForeignUrls() {
        ShortenedUrl foreign = new ShortenedUrl("code12", "https://example.com",
                UUID.randomUUID(), null, false);
        when(repository.findByShortCode("code12")).thenReturn(Optional.of(foreign));

        assertThrows(UrlNotFoundException.class, () -> service.getOwned(ownerId, "code12"));
    }

    @Test
    void deleteEvictsCache() {
        ShortenedUrl owned = new ShortenedUrl("code12", "https://example.com", ownerId,
                null, false);
        when(repository.findByShortCode("code12")).thenReturn(Optional.of(owned));

        service.delete(ownerId, "code12");

        verify(repository).delete(owned);
        verify(urlCache).evict("code12");
    }
}
