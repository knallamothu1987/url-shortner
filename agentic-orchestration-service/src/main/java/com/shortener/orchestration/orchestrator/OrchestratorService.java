package com.shortener.orchestration.orchestrator;

import com.shortener.orchestration.agents.Agent;
import com.shortener.orchestration.agents.AgentContext;
import com.shortener.orchestration.agents.AgentExecutionException;
import com.shortener.orchestration.agents.AgentResult;
import com.shortener.orchestration.config.OrchestratorProperties;
import com.shortener.orchestration.context.PipelineContextService;
import com.shortener.orchestration.context.PipelineStore;
import com.shortener.orchestration.dag.GateBinding;
import com.shortener.orchestration.dag.PipelineDefinition;
import com.shortener.orchestration.dag.StageDefinition;
import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import com.shortener.orchestration.domain.DecisionActor;
import com.shortener.orchestration.domain.DecisionRecord;
import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.domain.PipelineStatus;
import com.shortener.orchestration.domain.ScenarioType;
import com.shortener.orchestration.domain.StageExecution;
import com.shortener.orchestration.domain.StageStatus;
import com.shortener.orchestration.gates.GateContext;
import com.shortener.orchestration.gates.GateRegistry;
import com.shortener.orchestration.gates.GateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Supervisor agent: owns the explicit SDLC dependency graph and drives non-linear,
 * stateful execution — parallel branches with synchronization, entry/exit gates, bounded
 * retries with fallback and rollback, human approval checkpoints, safe-stop, and dynamic
 * re-planning when upstream artifacts change. Every decision lands in the audit lineage.
 */
@Service
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final PipelineStore store;
    private final PipelineContextService context;
    private final PipelineDefinition definition;
    private final Map<String, Agent> agentsByName;
    private final GateRegistry gates;
    private final OrchestratorProperties properties;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, Object> runLocks = new ConcurrentHashMap<>();

    public OrchestratorService(PipelineStore store,
                               PipelineContextService context,
                               PipelineDefinition definition,
                               List<Agent> agents,
                               GateRegistry gates,
                               OrchestratorProperties properties,
                               ExecutorService orchestrationExecutor) {
        this.store = store;
        this.context = context;
        this.definition = definition;
        this.agentsByName = agents.stream()
                .collect(Collectors.toMap(Agent::name, Function.identity()));
        this.gates = gates;
        this.properties = properties;
        this.executor = orchestrationExecutor;
    }

    // ------------------------------------------------------------------ lifecycle API

    public UUID start(String requirementText, ScenarioType scenarioType) {
        PipelineRun run = new PipelineRun(requirementText, scenarioType);
        store.saveRun(run);
        for (StageDefinition stage : definition.stages()) {
            store.saveStage(new StageExecution(run.getId(), stage.name(), stage.agent()));
        }
        record(run.getId(), null, DecisionActor.ORCHESTRATOR, "PIPELINE_CREATED",
                "Pipeline '%s' created for %s scenario with %d stages".formatted(
                        definition.name(), scenarioType, definition.stages().size()));
        advanceAsync(run.getId());
        return run.getId();
    }

    public void advanceAsync(UUID runId) {
        executor.submit(() -> {
            try {
                advance(runId);
            } catch (Exception exception) {
                logger.error("Pipeline {} advancement failed", runId, exception);
            }
        });
    }

    /** Core scheduling loop. Synchronized per run; safe to call from any trigger. */
    public void advance(UUID runId) {
        synchronized (runLocks.computeIfAbsent(runId, id -> new Object())) {
            PipelineRun run = requireRun(runId);
            if (run.getStatus().isTerminal()) {
                return;
            }
            run.setStatus(PipelineStatus.RUNNING);
            store.saveRun(run);

            while (true) {
                run = requireRun(runId);
                if (run.isSafeStopRequested()) {
                    run.setStatus(PipelineStatus.CANCELLED);
                    run.setFailureReason("Safe-stop requested by operator");
                    store.saveRun(run);
                    record(runId, null, DecisionActor.ORCHESTRATOR, "SAFE_STOP",
                            "Pipeline halted safely on operator request");
                    return;
                }

                Map<String, StageExecution> stages = stagesByName(runId);
                if (stages.values().stream()
                        .anyMatch(stage -> stage.getStatus() == StageStatus.FAILED
                                || stage.getStatus() == StageStatus.ROLLED_BACK)) {
                    failRun(run, stages);
                    return;
                }
                if (stages.values().stream()
                        .anyMatch(stage -> stage.getStatus() == StageStatus.AWAITING_APPROVAL)) {
                    run.setStatus(PipelineStatus.PAUSED_FOR_APPROVAL);
                    store.saveRun(run);
                    record(runId, null, DecisionActor.ORCHESTRATOR, "PIPELINE_PAUSED",
                            "Awaiting human approval checkpoint");
                    return;
                }
                if (stages.values().stream()
                        .allMatch(stage -> stage.getStatus() == StageStatus.COMPLETED)) {
                    run.setStatus(PipelineStatus.COMPLETED);
                    store.saveRun(run);
                    record(runId, null, DecisionActor.ORCHESTRATOR, "PIPELINE_COMPLETED",
                            "All stages completed; outputs are release-ready");
                    return;
                }

                List<StageDefinition> ready = readyStages(stages);
                if (ready.isEmpty()) {
                    // Nothing runnable and nothing failed/awaiting — defensive exit.
                    return;
                }
                record(runId, null, DecisionActor.ORCHESTRATOR, "WAVE_SCHEDULED",
                        "Executing in parallel: " + ready.stream()
                                .map(StageDefinition::name).collect(Collectors.joining(", ")));
                executeWave(requireRun(runId), ready, stages);
            }
        }
    }

    public void approve(UUID approvalId, boolean approved, String notes, String decidedBy) {
        ApprovalRequest approval = store.findApproval(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown approval " + approvalId));
        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Approval already decided: " + approval.getStatus());
        }
        approval.decide(approved, notes, decidedBy);
        store.saveApproval(approval);
        record(approval.getRunId(), approval.getStageName(), DecisionActor.HUMAN,
                approved ? "APPROVAL_GRANTED" : "APPROVAL_REJECTED",
                "Gate '%s' %s by %s%s".formatted(approval.getGateName(),
                        approved ? "approved" : "rejected", decidedBy,
                        notes == null || notes.isBlank() ? "" : " — " + notes));

        PipelineRun run = requireRun(approval.getRunId());
        Map<String, StageExecution> stages = stagesByName(run.getId());
        StageExecution stage = stages.get(approval.getStageName());

        if ("clarification".equals(approval.getGateName()) && approved && notes != null) {
            context.append(run.getId(), approval.getStageName(), "clarification-notes",
                    "{\"notes\": %s}".formatted(quote(notes)), "human");
        }

        if (approved) {
            // Approval gates are the last gates of a stage (enforced by DAG convention),
            // so a grant completes the stage.
            stage.setStatus(StageStatus.COMPLETED);
            store.saveStage(stage);
            run.setStatus(PipelineStatus.RUNNING);
            store.saveRun(run);
            advanceAsync(run.getId());
        } else {
            stage.setStatus(StageStatus.FAILED);
            stage.setLastError("Approval '%s' rejected".formatted(approval.getGateName()));
            store.saveStage(stage);
            run.setStatus(PipelineStatus.CANCELLED);
            run.setFailureReason("Human rejected approval '%s' at stage '%s'"
                    .formatted(approval.getGateName(), approval.getStageName()));
            store.saveRun(run);
        }
    }

    public void cancel(UUID runId) {
        PipelineRun run = requireRun(runId);
        if (run.getStatus().isTerminal()) {
            throw new IllegalStateException("Pipeline already terminal: " + run.getStatus());
        }
        run.requestSafeStop();
        if (run.getStatus() == PipelineStatus.PAUSED_FOR_APPROVAL
                || run.getStatus() == PipelineStatus.CREATED) {
            run.setStatus(PipelineStatus.CANCELLED);
            run.setFailureReason("Cancelled by operator");
        }
        store.saveRun(run);
        record(runId, null, DecisionActor.HUMAN, "CANCEL_REQUESTED",
                "Operator requested safe-stop; no new stages will be scheduled");
    }

    /**
     * Dynamic re-planning: a human (or upstream system) replaces an artifact; every stage
     * downstream of its producer is invalidated and re-executed with full lineage.
     */
    public void replan(UUID runId, String artifactName, String newContentJson, String rationale) {
        PipelineRun run = requireRun(runId);
        if (run.getStatus() == PipelineStatus.RUNNING) {
            throw new IllegalStateException("Cannot re-plan while the pipeline is actively running");
        }
        var producer = store.findLatestArtifact(runId, artifactName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact '%s' does not exist on this run".formatted(artifactName)));

        context.parse(newContentJson);
        context.append(runId, producer.getStageName(), artifactName, newContentJson, "human-replan");

        Set<String> downstream = definition.transitiveDependents(producer.getStageName());
        Map<String, StageExecution> stages = stagesByName(runId);
        for (String stageName : downstream) {
            StageExecution stage = stages.get(stageName);
            if (stage != null) {
                stage.resetForReplan();
                store.saveStage(stage);
            }
        }
        // The producing stage keeps its completed status: the human supplied its new output.
        StageExecution producerStage = stages.get(producer.getStageName());
        if (producerStage != null && producerStage.getStatus() != StageStatus.COMPLETED) {
            producerStage.setStatus(StageStatus.COMPLETED);
            store.saveStage(producerStage);
        }

        run.incrementReplanCount();
        run.setStatus(PipelineStatus.RUNNING);
        run.setFailureReason(null);
        store.saveRun(run);
        record(runId, producer.getStageName(), DecisionActor.HUMAN, "REPLAN",
                "Artifact '%s' replaced (%s); invalidated downstream stages: %s"
                        .formatted(artifactName, rationale, String.join(", ", downstream)));
        advanceAsync(runId);
    }

    // ------------------------------------------------------------------ stage execution

    private void executeWave(PipelineRun run, List<StageDefinition> ready,
                             Map<String, StageExecution> stages) {
        List<Callable<Void>> tasks = new ArrayList<>();
        for (StageDefinition stageDefinition : ready) {
            StageExecution stage = stages.get(stageDefinition.name());
            tasks.add(() -> {
                executeStage(run, stageDefinition, stage);
                return null;
            });
        }
        try {
            executor.invokeAll(tasks);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stage wave interrupted", exception);
        }
    }

    private void executeStage(PipelineRun run, StageDefinition stageDefinition,
                              StageExecution stage) {
        Agent agent = agentsByName.get(stageDefinition.agent());
        if (agent == null) {
            stage.setStatus(StageStatus.FAILED);
            stage.setLastError("No agent registered with name '" + stageDefinition.agent() + "'");
            store.saveStage(stage);
            return;
        }

        stage.setStatus(StageStatus.RUNNING);
        store.saveStage(stage);
        record(run.getId(), stage.getStageName(), DecisionActor.ORCHESTRATOR, "STAGE_STARTED",
                "Agent '%s' started".formatted(agent.name()));

        AgentContext agentContext = new AgentContext(
                run, stage, context, store, runWorkspace(run.getId()));

        AgentResult result = runWithRetries(agent, agentContext, stage);
        if (result == null) {
            result = applyFallback(agent, agentContext, stage);
        }
        if (result == null) {
            rollbackStage(agent, agentContext, stage, "Agent failed with no fallback available");
            return;
        }

        context.append(run.getId(), stage.getStageName(),
                result.artifactName(), result.artifactJson(), agent.name());
        for (String decision : result.decisions()) {
            record(run.getId(), stage.getStageName(), DecisionActor.AGENT, "AGENT_DECISION", decision);
        }

        GateContext gateContext = new GateContext(run, stageDefinition, stage,
                context, store, runWorkspace(run.getId()));
        for (GateBinding binding : stageDefinition.exitGates()) {
            GateResult gateResult = gates.evaluate(binding, gateContext);
            record(run.getId(), stage.getStageName(), DecisionActor.GATE,
                    "GATE_" + gateResult.outcome(),
                    "[%s] %s".formatted(binding.displayName(), gateResult.message()));
            switch (gateResult.outcome()) {
                case PASS -> {
                    // Continue to the next gate.
                }
                case PENDING_APPROVAL -> {
                    stage.setStatus(StageStatus.AWAITING_APPROVAL);
                    store.saveStage(stage);
                    return;
                }
                case FAIL -> {
                    rollbackStage(agent, agentContext, stage,
                            "Exit gate %s failed: %s".formatted(
                                    binding.displayName(), gateResult.message()));
                    return;
                }
            }
        }

        stage.setStatus(StageStatus.COMPLETED);
        store.saveStage(stage);
        record(run.getId(), stage.getStageName(), DecisionActor.ORCHESTRATOR, "STAGE_COMPLETED",
                "All exit gates passed");
    }

    private AgentResult runWithRetries(Agent agent, AgentContext agentContext,
                                       StageExecution stage) {
        int maxAttempts = Math.max(1, properties.retry().maxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            stage.incrementAttempts();
            store.saveStage(stage);
            try {
                return agent.execute(agentContext);
            } catch (AgentExecutionException exception) {
                stage.setLastError(exception.getMessage());
                store.saveStage(stage);
                record(stage.getRunId(), stage.getStageName(), DecisionActor.AGENT,
                        "ATTEMPT_FAILED", "Attempt %d/%d failed: %s"
                                .formatted(attempt, maxAttempts, exception.getMessage()));
                if (!exception.isRecoverable()) {
                    return null;
                }
                if (attempt < maxAttempts) {
                    backoff(attempt);
                    record(stage.getRunId(), stage.getStageName(), DecisionActor.ORCHESTRATOR,
                            "RETRY_SCHEDULED", "Bounded retry %d/%d with exponential backoff"
                                    .formatted(attempt + 1, maxAttempts));
                }
            }
        }
        return null;
    }

    private AgentResult applyFallback(Agent agent, AgentContext agentContext,
                                      StageExecution stage) {
        Optional<AgentResult> fallback = agent.fallback(agentContext);
        if (fallback.isEmpty()) {
            return null;
        }
        stage.markFallbackUsed();
        store.saveStage(stage);
        record(stage.getRunId(), stage.getStageName(), DecisionActor.ORCHESTRATOR,
                "FALLBACK_APPLIED", "Retries exhausted; degraded fallback output used");
        return fallback.get();
    }

    private void rollbackStage(Agent agent, AgentContext agentContext,
                               StageExecution stage, String reason) {
        try {
            agent.rollback(agentContext);
        } catch (RuntimeException exception) {
            logger.warn("Rollback of stage {} raised", stage.getStageName(), exception);
        }
        stage.setStatus(StageStatus.ROLLED_BACK);
        stage.setLastError(reason);
        store.saveStage(stage);
        record(stage.getRunId(), stage.getStageName(), DecisionActor.ORCHESTRATOR,
                "STAGE_ROLLED_BACK", reason);
    }

    private void failRun(PipelineRun run, Map<String, StageExecution> stages) {
        String reason = stages.values().stream()
                .filter(stage -> stage.getStatus() == StageStatus.FAILED
                        || stage.getStatus() == StageStatus.ROLLED_BACK)
                .map(stage -> stage.getStageName() + ": " + stage.getLastError())
                .collect(Collectors.joining("; "));
        run.setStatus(PipelineStatus.FAILED);
        run.setFailureReason(reason);
        store.saveRun(run);
        record(run.getId(), null, DecisionActor.ORCHESTRATOR, "PIPELINE_FAILED",
                "Safe-stop after stage failure — " + reason);
    }

    // ------------------------------------------------------------------ helpers

    private List<StageDefinition> readyStages(Map<String, StageExecution> stages) {
        return definition.stages().stream()
                .filter(stageDefinition -> {
                    StageExecution stage = stages.get(stageDefinition.name());
                    return stage != null && stage.getStatus() == StageStatus.PENDING
                            && stageDefinition.dependsOn().stream().allMatch(dep ->
                            stages.get(dep) != null
                                    && stages.get(dep).getStatus() == StageStatus.COMPLETED);
                })
                .toList();
    }

    private Map<String, StageExecution> stagesByName(UUID runId) {
        return store.findStages(runId).stream()
                .collect(Collectors.toMap(StageExecution::getStageName, Function.identity()));
    }

    private PipelineRun requireRun(UUID runId) {
        return store.findRun(runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown pipeline run " + runId));
    }

    private Path runWorkspace(UUID runId) {
        return Path.of(properties.workspaceRoot()).resolve(runId.toString());
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(properties.retry().backoffMs() * (1L << (attempt - 1)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void record(UUID runId, String stageName, DecisionActor actor,
                        String type, String rationale) {
        store.saveDecision(new DecisionRecord(runId, stageName, actor, type, rationale));
    }

    private String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
