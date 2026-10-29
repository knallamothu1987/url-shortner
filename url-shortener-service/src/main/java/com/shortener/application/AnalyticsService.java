package com.shortener.application;

import com.shortener.domain.ClickEventRepository;
import com.shortener.domain.ShortenedUrl;
import com.shortener.domain.ShortenedUrlRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsService {

    public record DailyCount(LocalDate day, long clicks) {
    }

    public record ReferrerCount(String referrer, long clicks) {
    }

    public record UrlStats(
            String shortCode,
            long totalClicks,
            List<DailyCount> dailyClicks,
            List<ReferrerCount> topReferrers) {
    }

    private static final int TOP_REFERRERS = 5;

    private final ShortenedUrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;

    public AnalyticsService(ShortenedUrlRepository urlRepository,
                            ClickEventRepository clickEventRepository) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Transactional(readOnly = true)
    public UrlStats stats(UUID ownerId, String shortCode, int days) {
        ShortenedUrl url = urlRepository.findByShortCode(shortCode)
                .filter(candidate -> candidate.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new UrlNotFoundException(shortCode));

        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<DailyCount> daily = clickEventRepository.countDailyClicks(url.getId(), since).stream()
                .map(row -> new DailyCount(row.getDay(), row.getClicks()))
                .toList();
        List<ReferrerCount> referrers = clickEventRepository
                .topReferrers(url.getId(), PageRequest.of(0, TOP_REFERRERS)).stream()
                .map(row -> new ReferrerCount(row.getReferrer(), row.getClicks()))
                .toList();
        return new UrlStats(shortCode,
                clickEventRepository.countByUrlId(url.getId()), daily, referrers);
    }
}
