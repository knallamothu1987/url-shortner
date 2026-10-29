package com.shortener.application;

import com.shortener.domain.ClickEventRepository;
import com.shortener.domain.ShortenedUrl;
import com.shortener.domain.ShortenedUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private ShortenedUrlRepository urlRepository;
    @Mock
    private ClickEventRepository clickEventRepository;

    private AnalyticsService service;
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnalyticsService(urlRepository, clickEventRepository);
    }

    @Test
    void statsAggregateTotalsDailyCountsAndReferrers() {
        ShortenedUrl url = new ShortenedUrl("abc123", "https://example.com", ownerId, null, false);
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(url));
        when(clickEventRepository.countByUrlId(url.getId())).thenReturn(42L);
        when(clickEventRepository.countDailyClicks(eq(url.getId()), any(Instant.class)))
                .thenReturn(List.of(daily(LocalDate.of(2026, 10, 28), 40L),
                        daily(LocalDate.of(2026, 10, 29), 2L)));
        when(clickEventRepository.topReferrers(url.getId(), PageRequest.of(0, 5)))
                .thenReturn(List.of(referrer("https://news.example", 30L)));

        AnalyticsService.UrlStats stats = service.stats(ownerId, "abc123", 7);

        assertEquals(42L, stats.totalClicks());
        assertEquals(2, stats.dailyClicks().size());
        assertEquals(40L, stats.dailyClicks().get(0).clicks());
        assertEquals("https://news.example", stats.topReferrers().get(0).referrer());
    }

    @Test
    void statsRejectForeignUrls() {
        ShortenedUrl foreign = new ShortenedUrl("abc123", "https://example.com",
                UUID.randomUUID(), null, false);
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(foreign));

        assertThrows(UrlNotFoundException.class, () -> service.stats(ownerId, "abc123", 7));
    }

    @Test
    void statsRejectUnknownCodes() {
        when(urlRepository.findByShortCode("ghost1")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> service.stats(ownerId, "ghost1", 7));
    }

    private ClickEventRepository.DailyClicks daily(LocalDate day, long clicks) {
        return new ClickEventRepository.DailyClicks() {
            @Override
            public LocalDate getDay() {
                return day;
            }

            @Override
            public long getClicks() {
                return clicks;
            }
        };
    }

    private ClickEventRepository.ReferrerClicks referrer(String referrer, long clicks) {
        return new ClickEventRepository.ReferrerClicks() {
            @Override
            public String getReferrer() {
                return referrer;
            }

            @Override
            public long getClicks() {
                return clicks;
            }
        };
    }
}
