package com.shortener.application;

import com.shortener.domain.ShortenedUrl;
import com.shortener.domain.ShortenedUrlRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UrlShorteningService {

    private static final int MAX_GENERATION_ATTEMPTS = 5;
    private final ShortenedUrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final UrlValidator urlValidator;
    private final UrlCache urlCache;

    public UrlShorteningService(
            ShortenedUrlRepository repository,
            ShortCodeGenerator codeGenerator,
            UrlValidator urlValidator,
            UrlCache urlCache) {
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.urlValidator = urlValidator;
        this.urlCache = urlCache;
    }

    @Transactional
    public ShortenedUrl create(UUID ownerId, String rawUrl, String customAlias, Instant expiresAt) {
        String longUrl = urlValidator.validateAndNormalize(rawUrl);
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new InvalidUrlException("Expiry time must be in the future");
        }

        var existing = repository.findByOwnerIdAndLongUrl(ownerId, longUrl);
        if (existing.isPresent()
                && existing.get().isActive()
                && (existing.get().getExpiresAt() == null
                || existing.get().getExpiresAt().isAfter(Instant.now()))) {
            return existing.get();
        }

        boolean isCustomAlias = customAlias != null;
        String shortCode;
        if (isCustomAlias) {
            shortCode = UrlValidator.normalizeAlias(customAlias);
            if (repository.existsByShortCode(shortCode)) {
                throw new ShortCodeAlreadyExistsException(shortCode);
            }
        } else {
            shortCode = generateAvailableCode();
        }

        ShortenedUrl saved = repository.save(
                new ShortenedUrl(shortCode, longUrl, ownerId, expiresAt, isCustomAlias));
        urlCache.put(saved.getShortCode(), saved.getLongUrl(), saved.getExpiresAt());
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<ShortenedUrl> list(
            UUID ownerId, int page, int size, String sortBy, String direction) {
        Sort sort = Sort.by("createdAt").descending();
        if ("clickCount".equals(sortBy)) {
            sort = Sort.by("clickCount");
            if ("desc".equalsIgnoreCase(direction)) {
                sort = sort.descending();
            } else if ("asc".equalsIgnoreCase(direction)) {
                sort = sort.ascending();
            } else {
                throw new IllegalArgumentException("order must be asc or desc");
            }
        } else if (!"createdAt".equals(sortBy)) {
            throw new IllegalArgumentException("sort must be createdAt or clickCount");
        } else if ("asc".equalsIgnoreCase(direction)) {
            sort = sort.ascending();
        } else if (!"desc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException("order must be asc or desc");
        }
        Pageable pageable = PageRequest.of(page, size, sort);
        return repository.findAllByOwnerId(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public ShortenedUrl getOwned(UUID ownerId, String shortCode) {
        return repository.findByShortCode(shortCode)
                .filter(url -> url.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new UrlNotFoundException(shortCode));
    }

    @Transactional
    public void delete(UUID ownerId, String shortCode) {
        ShortenedUrl url = getOwned(ownerId, shortCode);
        repository.delete(url);
        urlCache.evict(shortCode);
    }

    private String generateAvailableCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = codeGenerator.nextCode();
            if (!repository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not allocate a unique short code after five attempts");
    }
}
