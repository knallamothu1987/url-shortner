package com.shortener.orchestration.api;

import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.domain.StageExecution;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record PipelineRunResponse(
        UUID id,
        String requirementText,
        String scenarioType,
        String status,
        boolean safeStopRequested,
        int replanCount,
        String failureReason,
        Instant createdAt,
        Instant completedAt,
        List<StageView> stages) {

    public record StageView(
            String name,
            String agent,
            String status,
            int attempts,
            boolean fallbackUsed,
            String lastError,
            Instant startedAt,
            Instant finishedAt) {

        public static StageView from(StageExecution stage) {
            return new StageView(stage.getStageName(), stage.getAgentName(),
                    stage.getStatus().name(), stage.getAttempts(), stage.isFallbackUsed(),
                    stage.getLastError(), stage.getStartedAt(), stage.getFinishedAt());
        }
    }

    public static PipelineRunResponse from(PipelineRun run, List<StageExecution> stages,
                                           List<String> stageOrder) {
        List<StageView> views = stages.stream()
                .sorted(Comparator.comparingInt(stage -> stageOrder.indexOf(stage.getStageName())))
                .map(StageView::from)
                .toList();
        return new PipelineRunResponse(run.getId(), run.getRequirementText(),
                run.getScenarioType().name(), run.getStatus().name(), run.isSafeStopRequested(),
                run.getReplanCount(), run.getFailureReason(), run.getCreatedAt(),
                run.getCompletedAt(), views);
    }
}
