package com.shortener.orchestration.context;

import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import com.shortener.orchestration.domain.ContextArtifact;
import com.shortener.orchestration.domain.DecisionRecord;
import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.domain.StageExecution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence boundary for the orchestration engine. The production implementation is
 * JPA/PostgreSQL backed; tests use an in-memory implementation so the DAG engine can be
 * verified deterministically without a database.
 */
public interface PipelineStore {

    PipelineRun saveRun(PipelineRun run);

    Optional<PipelineRun> findRun(UUID runId);

    List<PipelineRun> findAllRuns();

    StageExecution saveStage(StageExecution stage);

    List<StageExecution> findStages(UUID runId);

    ContextArtifact saveArtifact(ContextArtifact artifact);

    Optional<ContextArtifact> findLatestArtifact(UUID runId, String name);

    List<ContextArtifact> findArtifacts(UUID runId);

    DecisionRecord saveDecision(DecisionRecord decision);

    List<DecisionRecord> findDecisions(UUID runId);

    ApprovalRequest saveApproval(ApprovalRequest approval);

    Optional<ApprovalRequest> findApproval(UUID approvalId);

    List<ApprovalRequest> findApprovals(UUID runId);

    Optional<ApprovalRequest> findApprovalForGate(UUID runId, String stageName, String gateName);

    List<ApprovalRequest> findApprovalsByStatus(ApprovalStatus status);
}
