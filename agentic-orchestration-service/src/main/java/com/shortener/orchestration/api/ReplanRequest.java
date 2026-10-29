package com.shortener.orchestration.api;

import jakarta.validation.constraints.NotBlank;

public record ReplanRequest(
        @NotBlank String artifactName,
        @NotBlank String content,
        String rationale) {
}
