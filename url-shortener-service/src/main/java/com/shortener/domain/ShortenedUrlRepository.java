package com.shortener.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShortenedUrlRepository extends JpaRepository<ShortenedUrl, UUID> {

    Optional<ShortenedUrl> findByShortCode(String shortCode);

    Optional<ShortenedUrl> findByOwnerIdAndLongUrl(UUID ownerId, String longUrl);

    boolean existsByShortCode(String shortCode);

    Page<ShortenedUrl> findAllByOwnerId(UUID ownerId, Pageable pageable);

    @Modifying
    @Query("update ShortenedUrl u set u.clickCount = u.clickCount + 1 where u.id = :id")
    void incrementClickCount(@Param("id") UUID id);
}
