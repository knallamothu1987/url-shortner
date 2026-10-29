package com.shortener.orchestration.context;

import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import com.shortener.orchestration.domain.ContextArtifact;
import com.shortener.orchestration.domain.DecisionRecord;
import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.domain.StageExecution;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class JpaPipelineStore implements PipelineStore {

    private final PipelineRunRepository runs;
    private final StageExecutionRepository stages;
    private final ContextArtifactRepository artifacts;
    private final DecisionRecordRepository decisions;
    private final ApprovalRequestRepository approvals;

    public JpaPipelineStore(PipelineRunRepository runs,
                            StageExecutionRepository stages,
                            ContextArtifactRepository artifacts,
                            DecisionRecordRepository decisions,
                            ApprovalRequestRepository approvals) {
        this.runs = runs;
        this.stages = stages;
        this.artifacts = artifacts;
        this.decisions = decisions;
        this.approvals = approvals;
    }

    @Override
    public PipelineRun saveRun(PipelineRun run) {
        return runs.save(run);
    }

    @Override
    public Optional<PipelineRun> findRun(UUID runId) {
        return runs.findById(runId);
    }

    @Override
    public List<PipelineRun> findAllRuns() {
        return runs.findAll();
    }

    @Override
    public StageExecution saveStage(StageExecution stage) {
        return stages.save(stage);
    }

    @Override
    public List<StageExecution> findStages(UUID runId) {
        return stages.findByRunId(runId);
    }

    @Override
    public ContextArtifact saveArtifact(ContextArtifact artifact) {
        return artifacts.save(artifact);
    }

    @Override
    public Optional<ContextArtifact> findLatestArtifact(UUID runId, String name) {
        return artifacts.findFirstByRunIdAndNameOrderByVersionDesc(runId, name);
    }

    @Override
    public List<ContextArtifact> findArtifacts(UUID runId) {
        return artifacts.findByRunIdOrderByCreatedAtAsc(runId);
    }

    @Override
    public DecisionRecord saveDecision(DecisionRecord decision) {
        return decisions.save(decision);
    }

    @Override
    public List<DecisionRecord> findDecisions(UUID runId) {
        return decisions.findByRunIdOrderByCreatedAtAsc(runId);
    }

    @Override
    public ApprovalRequest saveApproval(ApprovalRequest approval) {
        return approvals.save(approval);
    }

    @Override
    public Optional<ApprovalRequest> findApproval(UUID approvalId) {
        return approvals.findById(approvalId);
    }

    @Override
    public List<ApprovalRequest> findApprovals(UUID runId) {
        return approvals.findByRunId(runId);
    }

    @Override
    public Optional<ApprovalRequest> findApprovalForGate(UUID runId, String stageName, String gateName) {
        return approvals.findFirstByRunIdAndStageNameAndGateNameOrderByRequestedAtDesc(
                runId, stageName, gateName);
    }

    @Override
    public List<ApprovalRequest> findApprovalsByStatus(ApprovalStatus status) {
        return approvals.findByStatus(status);
    }
}
