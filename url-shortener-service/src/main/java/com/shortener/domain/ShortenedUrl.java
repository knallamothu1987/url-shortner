package com.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "urls")
public class ShortenedUrl {

    @Id
    private UUID id;

    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(name = "long_url", nullable = false, length = 2048)
    private String longUrl;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "click_count", nullable = false)
    private long clickCount;

    @Column(name = "custom_alias", nullable = false)
    private boolean customAlias;

    protected ShortenedUrl() {
    }

    public ShortenedUrl(String shortCode, String longUrl, UUID ownerId,
                        Instant expiresAt, boolean customAlias) {
        this.id = UUID.randomUUID();
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.ownerId = ownerId;
        this.expiresAt = expiresAt;
        this.customAlias = customAlias;
    }

    @PrePersist
    void setCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public long getClickCount() {
        return clickCount;
    }

    public boolean isCustomAlias() {
        return customAlias;
    }
}
