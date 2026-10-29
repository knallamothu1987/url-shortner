package com.shortener.orchestration.api;

import jakarta.validation.constraints.NotNull;

public record ApprovalDecisionRequest(
        @NotNull Boolean approved,
        String notes,
        String decidedBy) {
}
