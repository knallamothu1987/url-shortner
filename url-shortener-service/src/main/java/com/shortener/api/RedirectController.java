package com.shortener.api;

import com.shortener.application.ClickTrackingService;
import com.shortener.application.RedirectService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class RedirectController {

    private final RedirectService redirectService;
    private final ClickTrackingService clickTrackingService;

    public RedirectController(RedirectService redirectService,
                              ClickTrackingService clickTrackingService) {
        this.redirectService = redirectService;
        this.clickTrackingService = clickTrackingService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestHeader(value = HttpHeaders.REFERER, required = false) String referrer,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        URI destination = URI.create(redirectService.resolve(shortCode));
        clickTrackingService.record(shortCode, referrer, userAgent);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, destination.toASCIIString())
                .build();
    }
}
