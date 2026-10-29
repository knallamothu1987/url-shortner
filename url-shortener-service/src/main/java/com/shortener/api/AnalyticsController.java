package com.shortener.api;

import com.shortener.application.AnalyticsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Validated
@RestController
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/api/v1/urls/{shortCode}/stats")
    public AnalyticsService.UrlStats stats(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String shortCode,
            @RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return analyticsService.stats(UUID.fromString(jwt.getSubject()), shortCode, days);
    }
}
