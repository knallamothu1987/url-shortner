package com.shortener.orchestration;

import com.shortener.orchestration.context.PipelineStore;
import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import com.shortener.orchestration.domain.ContextArtifact;
import com.shortener.orchestration.domain.DecisionRecord;
import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.domain.StageExecution;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Deterministic in-memory store used to test the DAG engine without a database. */
public class InMemoryPipelineStore implements PipelineStore {

    private final Map<UUID, PipelineRun> runs = new ConcurrentHashMap<>();
    private final Map<UUID, StageExecution> stages = new ConcurrentHashMap<>();
    private final List<ContextArtifact> artifacts = new CopyOnWriteArrayList<>();
    private final List<DecisionRecord> decisions = new CopyOnWriteArrayList<>();
    private final Map<UUID, ApprovalRequest> approvals = new ConcurrentHashMap<>();

    @Override
    public PipelineRun saveRun(PipelineRun run) {
        runs.put(run.getId(), run);
        return run;
    }

    @Override
    public Optional<PipelineRun> findRun(UUID runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public List<PipelineRun> findAllRuns() {
        return List.copyOf(runs.values());
    }

    @Override
    public StageExecution saveStage(StageExecution stage) {
        stages.put(stage.getId(), stage);
        return stage;
    }

    @Override
    public List<StageExecution> findStages(UUID runId) {
        return stages.values().stream()
                .filter(stage -> stage.getRunId().equals(runId))
                .toList();
    }

    @Override
    public ContextArtifact saveArtifact(ContextArtifact artifact) {
        artifacts.add(artifact);
        return artifact;
    }

    @Override
    public Optional<ContextArtifact> findLatestArtifact(UUID runId, String name) {
        return artifacts.stream()
                .filter(artifact -> artifact.getRunId().equals(runId)
                        && artifact.getName().equals(name))
                .max(Comparator.comparingInt(ContextArtifact::getVersion));
    }

    @Override
    public List<ContextArtifact> findArtifacts(UUID runId) {
        return artifacts.stream()
                .filter(artifact -> artifact.getRunId().equals(runId))
                .sorted(Comparator.comparing(ContextArtifact::getCreatedAt))
                .toList();
    }

    @Override
    public DecisionRecord saveDecision(DecisionRecord decision) {
        decisions.add(decision);
        return decision;
    }

    @Override
    public List<DecisionRecord> findDecisions(UUID runId) {
        return decisions.stream()
                .filter(decision -> decision.getRunId().equals(runId))
                .sorted(Comparator.comparing(DecisionRecord::getCreatedAt))
                .toList();
    }

    @Override
    public ApprovalRequest saveApproval(ApprovalRequest approval) {
        approvals.put(approval.getId(), approval);
        return approval;
    }

    @Override
    public Optional<ApprovalRequest> findApproval(UUID approvalId) {
        return Optional.ofNullable(approvals.get(approvalId));
    }

    @Override
    public List<ApprovalRequest> findApprovals(UUID runId) {
        return approvals.values().stream()
                .filter(approval -> approval.getRunId().equals(runId))
                .toList();
    }

    @Override
    public Optional<ApprovalRequest> findApprovalForGate(UUID runId, String stageName,
                                                         String gateName) {
        return approvals.values().stream()
                .filter(approval -> approval.getRunId().equals(runId)
                        && approval.getStageName().equals(stageName)
                        && approval.getGateName().equals(gateName))
                .max(Comparator.comparing(ApprovalRequest::getRequestedAt));
    }

    @Override
    public List<ApprovalRequest> findApprovalsByStatus(ApprovalStatus status) {
        return approvals.values().stream()
                .filter(approval -> approval.getStatus() == status)
                .toList();
    }
}
