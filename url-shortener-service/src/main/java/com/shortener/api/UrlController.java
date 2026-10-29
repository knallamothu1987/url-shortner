package com.shortener.api;

import com.shortener.application.UrlShorteningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
public class UrlController {

    private final UrlShorteningService service;
    private final String baseUrl;

    public UrlController(
            UrlShorteningService service,
            @Value("${url-shortener.public-base-url}") String baseUrl) {
        this.service = service;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @PostMapping("/api/v1/urls")
    @ResponseStatus(HttpStatus.CREATED)
    public UrlResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUrlRequest request) {
        var url = service.create(
                ownerId(jwt),
                request.longUrl(),
                request.customAlias(),
                request.expiresAt());
        return UrlResponse.from(url, baseUrl);
    }

    @GetMapping("/api/v1/urls")
    public UrlPageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order) {
        var results = service.list(ownerId(jwt), page, size, sort, order);
        return new UrlPageResponse(
                results.getContent().stream().map(url -> UrlResponse.from(url, baseUrl)).toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages());
    }

    @GetMapping("/api/v1/urls/{shortCode}")
    public UrlResponse get(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String shortCode) {
        return UrlResponse.from(service.getOwned(ownerId(jwt), shortCode), baseUrl);
    }

    @DeleteMapping("/api/v1/urls/{shortCode}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String shortCode) {
        service.delete(ownerId(jwt), shortCode);
    }

    private UUID ownerId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
