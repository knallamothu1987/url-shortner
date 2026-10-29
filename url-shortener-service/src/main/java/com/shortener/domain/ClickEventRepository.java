package com.shortener.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ClickEventRepository extends JpaRepository<ClickEvent, UUID> {

    interface DailyClicks {
        LocalDate getDay();

        long getClicks();
    }

    long countByUrlId(UUID urlId);

    @Query(value = """
            SELECT CAST(occurred_at AS date) AS day, COUNT(*) AS clicks
            FROM click_events
            WHERE url_id = :urlId AND occurred_at >= :since
            GROUP BY CAST(occurred_at AS date)
            ORDER BY day
            """, nativeQuery = true)
    List<DailyClicks> countDailyClicks(@Param("urlId") UUID urlId, @Param("since") Instant since);

    @Query("""
            select ce.referrer as referrer, count(ce) as clicks
            from ClickEvent ce
            where ce.urlId = :urlId and ce.referrer is not null
            group by ce.referrer
            order by count(ce) desc
            """)
    List<ReferrerClicks> topReferrers(@Param("urlId") UUID urlId, Pageable pageable);

    interface ReferrerClicks {
        String getReferrer();

        long getClicks();
    }
}
