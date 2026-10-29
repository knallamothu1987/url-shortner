package com.shortener.orchestration.api;

import com.shortener.orchestration.domain.ScenarioType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StartPipelineRequest(
        @NotBlank String requirementText,
        @NotNull ScenarioType scenarioType) {
}
