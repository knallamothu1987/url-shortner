package com.shortener.orchestration.api;

import com.shortener.orchestration.context.PipelineContextService;
import com.shortener.orchestration.context.PipelineStore;
import com.shortener.orchestration.dag.PipelineDefinition;
import com.shortener.orchestration.dag.StageDefinition;
import com.shortener.orchestration.domain.ContextArtifact;
import com.shortener.orchestration.domain.DecisionRecord;
import com.shortener.orchestration.domain.PipelineRun;
import com.shortener.orchestration.orchestrator.OrchestratorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class PipelineController {

    private final OrchestratorService orchestrator;
    private final PipelineStore store;
    private final PipelineContextService context;
    private final List<String> stageOrder;

    public PipelineController(OrchestratorService orchestrator,
                              PipelineStore store,
                              PipelineContextService context,
                              PipelineDefinition definition) {
        this.orchestrator = orchestrator;
        this.store = store;
        this.context = context;
        this.stageOrder = definition.stages().stream().map(StageDefinition::name).toList();
    }

    @PostMapping("/api/v1/pipelines")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> start(@Valid @RequestBody StartPipelineRequest request) {
        UUID runId = orchestrator.start(request.requirementText(), request.scenarioType());
        return Map.of("runId", runId, "status", "ACCEPTED");
    }

    @GetMapping("/api/v1/pipelines")
    public List<PipelineRunResponse> list() {
        return store.findAllRuns().stream()
                .map(run -> PipelineRunResponse.from(run, store.findStages(run.getId()), stageOrder))
                .toList();
    }

    @GetMapping("/api/v1/pipelines/{runId}")
    public PipelineRunResponse get(@PathVariable UUID runId) {
        PipelineRun run = store.findRun(runId)
                .orElseThrow(() -> new PipelineNotFoundException(runId));
        return PipelineRunResponse.from(run, store.findStages(runId), stageOrder);
    }

    @GetMapping("/api/v1/pipelines/{runId}/context")
    public List<Map<String, Object>> context(@PathVariable UUID runId) {
        requireRun(runId);
        return context.allArtifacts(runId).stream()
                .map(this::artifactView)
                .toList();
    }

    @GetMapping("/api/v1/pipelines/{runId}/decisions")
    public List<Map<String, Object>> decisions(@PathVariable UUID runId) {
        requireRun(runId);
        return store.findDecisions(runId).stream()
                .map(this::decisionView)
                .toList();
    }

    @PostMapping("/api/v1/pipelines/{runId}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void cancel(@PathVariable UUID runId) {
        requireRun(runId);
        orchestrator.cancel(runId);
    }

    @PostMapping("/api/v1/pipelines/{runId}/replan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void replan(@PathVariable UUID runId, @Valid @RequestBody ReplanRequest request) {
        requireRun(runId);
        orchestrator.replan(runId, request.artifactName(), request.content(),
                request.rationale() == null ? "no rationale given" : request.rationale());
    }

    private void requireRun(UUID runId) {
        store.findRun(runId).orElseThrow(() -> new PipelineNotFoundException(runId));
    }

    private Map<String, Object> artifactView(ContextArtifact artifact) {
        return Map.of(
                "name", artifact.getName(),
                "version", artifact.getVersion(),
                "stage", artifact.getStageName(),
                "createdBy", artifact.getCreatedBy(),
                "createdAt", artifact.getCreatedAt(),
                "content", context.parse(artifact.getContent()));
    }

    private Map<String, Object> decisionView(DecisionRecord decision) {
        return Map.of(
                "at", decision.getCreatedAt(),
                "stage", decision.getStageName() == null ? "(pipeline)" : decision.getStageName(),
                "actor", decision.getActor().name(),
                "type", decision.getDecisionType(),
                "rationale", decision.getRationale());
    }
}
