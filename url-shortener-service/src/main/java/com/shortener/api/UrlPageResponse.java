package com.shortener.api;

import java.util.List;

public record UrlPageResponse(
        List<UrlResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
