package com.shortener.application;

import com.shortener.domain.ClickEvent;
import com.shortener.domain.ClickEventRepository;
import com.shortener.domain.ShortenedUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records click analytics off the redirect hot path. Failures are logged and swallowed —
 * analytics must never break a redirect.
 */
@Service
public class ClickTrackingService {

    private static final Logger logger = LoggerFactory.getLogger(ClickTrackingService.class);

    private final ShortenedUrlRepository urlRepository;
    private final ClickEventRepository clickEventRepository;

    public ClickTrackingService(ShortenedUrlRepository urlRepository,
                                ClickEventRepository clickEventRepository) {
        this.urlRepository = urlRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Async
    @Transactional
    public void record(String shortCode, String referrer, String userAgent) {
        try {
            urlRepository.findByShortCode(shortCode).ifPresent(url -> {
                clickEventRepository.save(
                        new ClickEvent(url.getId(), shortCode, referrer, userAgent));
                urlRepository.incrementClickCount(url.getId());
            });
        } catch (RuntimeException exception) {
            logger.warn("Click tracking failed for short code {}", shortCode, exception);
        }
    }
}
