package com.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "click_events")
public class ClickEvent {

    @Id
    private UUID id;

    @Column(name = "url_id", nullable = false)
    private UUID urlId;

    @Column(name = "short_code", nullable = false, length = 20)
    private String shortCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(length = 512)
    private String referrer;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    protected ClickEvent() {
    }

    public ClickEvent(UUID urlId, String shortCode, String referrer, String userAgent) {
        this.id = UUID.randomUUID();
        this.urlId = urlId;
        this.shortCode = shortCode;
        this.occurredAt = Instant.now();
        this.referrer = truncate(referrer);
        this.userAgent = truncate(userAgent);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUrlId() {
        return urlId;
    }

    public String getShortCode() {
        return shortCode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getReferrer() {
        return referrer;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
