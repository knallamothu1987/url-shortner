package com.shortener.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortener.orchestration.agents.Agent;
import com.shortener.orchestration.agents.DesignAgent;
import com.shortener.orchestration.agents.DocumentationAgent;
import com.shortener.orchestration.agents.ImplementationAgent;
import com.shortener.orchestration.agents.RequirementAgent;
import com.shortener.orchestration.agents.ReviewAgent;
import com.shortener.orchestration.agents.StubLlmClient;
import com.shortener.orchestration.agents.TestAgent;
import com.shortener.orchestration.config.OrchestratorProperties;
import com.shortener.orchestration.context.PipelineContextService;
import com.shortener.orchestration.dag.PipelineDefinition;
import com.shortener.orchestration.dag.PipelineDefinitionLoader;
import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ApprovalStatus;
import com.shortener.orchestration.domain.PipelineStatus;
import com.shortener.orchestration.domain.ScenarioType;
import com.shortener.orchestration.domain.StageExecution;
import com.shortener.orchestration.domain.StageStatus;
import com.shortener.orchestration.gates.ApprovalGate;
import com.shortener.orchestration.gates.BuildGate;
import com.shortener.orchestration.gates.GateRegistry;
import com.shortener.orchestration.gates.OpenApiGate;
import com.shortener.orchestration.gates.PolicyGate;
import com.shortener.orchestration.gates.SchemaGate;
import com.shortener.orchestration.gates.TestGate;
import com.shortener.orchestration.orchestrator.OrchestratorService;
import com.shortener.orchestration.policy.PolicyGuardrailService;
import com.shortener.orchestration.tools.CodebaseContextService;
import com.shortener.orchestration.tools.MavenToolRunner;
import com.shortener.orchestration.tools.OpenApiValidator;
import com.shortener.orchestration.tools.SecretScanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests of the DAG engine with the real agents and gates (stub LLM mode,
 * in-memory store, Maven tool disabled) — fully deterministic, no database, no network.
 */
class OrchestratorServiceTest {

    @TempDir
    Path workspace;

    private InMemoryPipelineStore store;
    private OrchestratorService orchestrator;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        store = new InMemoryPipelineStore();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        ObjectMapper objectMapper = new ObjectMapper();
        OrchestratorProperties properties = new OrchestratorProperties(
                new OrchestratorProperties.Llm("stub"),
                new OrchestratorProperties.Retry(3, 5),
                new OrchestratorProperties.Tools(
                        new OrchestratorProperties.Tools.Maven(false, "mvn", 60)),
                workspace.resolve("codebase").toString(),
                workspace.resolve("runs").toString());

        PipelineContextService context = new PipelineContextService(store, objectMapper);
        PipelineDefinition definition = PipelineDefinitionLoader.load("pipeline-definition.yml");

        StubLlmClient llm = new StubLlmClient();
        SecretScanner secretScanner = new SecretScanner();
        CodebaseContextService codebase = new CodebaseContextService(properties);
        List<Agent> agents = List.of(
                new RequirementAgent(llm, objectMapper),
                new DesignAgent(llm, objectMapper, codebase),
                new ImplementationAgent(llm, objectMapper),
                new TestAgent(objectMapper),
                new ReviewAgent(llm, objectMapper, secretScanner),
                new DocumentationAgent(objectMapper));

        MavenToolRunner maven = new MavenToolRunner(properties);
        GateRegistry gates = new GateRegistry(List.of(
                new SchemaGate(),
                new BuildGate(maven, properties),
                new TestGate(maven, properties),
                new OpenApiGate(new OpenApiValidator()),
                new PolicyGate(new PolicyGuardrailService(secretScanner)),
                new ApprovalGate()));

        orchestrator = new OrchestratorService(store, context, definition, agents,
                gates, properties, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------ scenarios

    @Test
    void greenfieldPipelineCompletesAllStagesWithParallelBranches() {
        UUID runId = orchestrator.start(
                "Provide a QR code generation endpoint returning HTTP 200 within 100 ms "
                        + "for each short link, available to 99 percent of requests",
                ScenarioType.GREENFIELD);

        awaitStatus(runId, PipelineStatus.COMPLETED);

        Map<String, StageExecution> stages = stagesByName(runId);
        assertEquals(7, stages.size());
        stages.values().forEach(stage ->
                assertEquals(StageStatus.COMPLETED, stage.getStatus(),
                        "stage " + stage.getStageName()));
        // Final engineering summary exists with full lineage.
        assertTrue(store.findLatestArtifact(runId, "release-summary").isPresent());
        assertTrue(store.findLatestArtifact(runId, "design-docs").isPresent());
        // No approvals should have been requested on the happy path.
        assertTrue(store.findApprovals(runId).isEmpty());
    }

    @Test
    void schemaChangingDesignPausesForHumanApprovalThenResumes() {
        UUID runId = orchestrator.start(
                "Track analytics for every redirect and store click events in a table, "
                        + "exposing the top 5 referrers per short link",
                ScenarioType.BROWNFIELD);

        awaitStatus(runId, PipelineStatus.PAUSED_FOR_APPROVAL);
        ApprovalRequest approval = pendingApproval(runId);
        assertEquals("schema-migration", approval.getGateName());
        assertEquals("design", approval.getStageName());

        orchestrator.approve(approval.getId(), true, "Migration reviewed", "alice");
        awaitStatus(runId, PipelineStatus.COMPLETED);
        assertEquals(StageStatus.COMPLETED, stagesByName(runId).get("design").getStatus());
    }

    @Test
    void rejectedApprovalCancelsThePipeline() {
        UUID runId = orchestrator.start(
                "Store click events in a new table for all 100 percent of redirects",
                ScenarioType.BROWNFIELD);

        awaitStatus(runId, PipelineStatus.PAUSED_FOR_APPROVAL);
        orchestrator.approve(pendingApproval(runId).getId(), false, "Schema freeze", "bob");

        awaitStatus(runId, PipelineStatus.CANCELLED);
        assertTrue(store.findRun(runId).orElseThrow().getFailureReason().contains("rejected"));
    }

    @Test
    void ambiguousRequirementPausesForClarification() {
        UUID runId = orchestrator.start(
                "Make the service better and faster somehow",
                ScenarioType.AMBIGUOUS);

        awaitStatus(runId, PipelineStatus.PAUSED_FOR_APPROVAL);
        ApprovalRequest approval = pendingApproval(runId);
        assertEquals("clarification", approval.getGateName());
        assertEquals("requirements", approval.getStageName());

        orchestrator.approve(approval.getId(), true,
                "Target: p95 redirect latency < 50ms at 1000 rps", "carol");
        awaitStatus(runId, PipelineStatus.COMPLETED);
        // Clarification notes are preserved in the context lineage.
        assertTrue(store.findLatestArtifact(runId, "clarification-notes").isPresent());
    }

    // ------------------------------------------------------------------ reliability

    @Test
    void recoverableFailureIsRetriedWithBoundedAttempts() {
        UUID runId = orchestrator.start(
                "[inject:retry] Provide an export endpoint returning 200 for all 10 columns",
                ScenarioType.GREENFIELD);

        awaitStatus(runId, PipelineStatus.COMPLETED);
        assertEquals(2, stagesByName(runId).get("design").getAttempts());
        assertTrue(store.findDecisions(runId).stream()
                .anyMatch(decision -> "RETRY_SCHEDULED".equals(decision.getDecisionType())));
    }

    @Test
    void unrecoverableTestFailureUsesFallbackAndSafeStopsAtTheGate() {
        UUID runId = orchestrator.start(
                "[inject:fallback-testing] Provide an endpoint returning 200 for all 3 fields",
                ScenarioType.GREENFIELD);

        awaitStatus(runId, PipelineStatus.FAILED);
        StageExecution testing = stagesByName(runId).get("testing");
        assertTrue(testing.isFallbackUsed());
        assertEquals(StageStatus.ROLLED_BACK, testing.getStatus());
        // Fallback produced a skeleton, but the TestGate refused to let untested code pass.
        assertTrue(testing.getLastError().contains("test"));
    }

    @Test
    void policyGateRollsBackImplementationThatLeaksSecrets() {
        UUID runId = orchestrator.start(
                "[inject:secret] Provide an endpoint returning 200 for all 3 fields",
                ScenarioType.GREENFIELD);

        awaitStatus(runId, PipelineStatus.FAILED);
        StageExecution implementation = stagesByName(runId).get("implementation");
        assertEquals(StageStatus.ROLLED_BACK, implementation.getStatus());
        assertTrue(implementation.getLastError().contains("policy"));
        // Rollback removed the generated files containing the secret.
        assertFalse(Files.exists(workspace.resolve("runs")
                .resolve(runId.toString()).resolve("implementation")));
    }

    @Test
    void safeStopCancelsBeforeNewStagesAreScheduled() {
        UUID runId = orchestrator.start(
                "Store click events in a new table for 100 percent of redirects",
                ScenarioType.BROWNFIELD);
        awaitStatus(runId, PipelineStatus.PAUSED_FOR_APPROVAL);

        orchestrator.cancel(runId);
        awaitStatus(runId, PipelineStatus.CANCELLED);
    }

    // ------------------------------------------------------------------ re-planning

    @Test
    void replanInvalidatesDownstreamStagesAndReexecutes() {
        UUID runId = orchestrator.start(
                "Provide a QR code endpoint returning 200 within 100 ms for each link",
                ScenarioType.GREENFIELD);
        awaitStatus(runId, PipelineStatus.COMPLETED);
        String newDesign = store.findLatestArtifact(runId, "design-spec")
                .orElseThrow().getContent();

        orchestrator.replan(runId, "design-spec", newDesign, "Architecture board revision");

        awaitStatus(runId, PipelineStatus.COMPLETED);
        assertEquals(1, store.findRun(runId).orElseThrow().getReplanCount());
        // The human-supplied artifact became a new immutable version (v2); the design
        // stage itself is not downstream of its own artifact so it does not re-run.
        assertEquals(2, store.findLatestArtifact(runId, "design-spec")
                .orElseThrow().getVersion());
        // Downstream stages re-executed: release summary written twice (v2).
        assertTrue(store.findLatestArtifact(runId, "release-summary")
                .orElseThrow().getVersion() >= 2);
    }

    @Test
    void replanWhileRunningIsRejected() {
        UUID runId = orchestrator.start(
                "Provide a QR code endpoint returning 200 within 100 ms for each link",
                ScenarioType.GREENFIELD);
        awaitStatus(runId, PipelineStatus.COMPLETED);
        var run = store.findRun(runId).orElseThrow();
        run.setStatus(PipelineStatus.RUNNING);
        store.saveRun(run);

        assertThrows(IllegalStateException.class,
                () -> orchestrator.replan(runId, "design-spec", "{}", "nope"));
    }

    // ------------------------------------------------------------------ lineage

    @Test
    void decisionLineageCapturesAllActors() {
        UUID runId = orchestrator.start(
                "Store click events in a new table for 100 percent of redirects",
                ScenarioType.BROWNFIELD);
        awaitStatus(runId, PipelineStatus.PAUSED_FOR_APPROVAL);
        orchestrator.approve(pendingApproval(runId).getId(), true, "ok", "dave");
        awaitStatus(runId, PipelineStatus.COMPLETED);

        var actors = store.findDecisions(runId).stream()
                .map(decision -> decision.getActor().name())
                .collect(Collectors.toSet());
        assertTrue(actors.containsAll(List.of("ORCHESTRATOR", "AGENT", "GATE", "HUMAN")),
                "lineage should contain all actors, had: " + actors);
        assertNotNull(store.findLatestArtifact(runId, "release-summary").orElseThrow());
    }

    // ------------------------------------------------------------------ helpers

    private void awaitStatus(UUID runId, PipelineStatus expected) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertEquals(expected, store.findRun(runId).orElseThrow().getStatus()));
    }

    private Map<String, StageExecution> stagesByName(UUID runId) {
        return store.findStages(runId).stream()
                .collect(Collectors.toMap(StageExecution::getStageName, Function.identity()));
    }

    private ApprovalRequest pendingApproval(UUID runId) {
        return store.findApprovals(runId).stream()
                .filter(approval -> approval.getStatus() == ApprovalStatus.PENDING)
                .findFirst()
                .orElseThrow();
    }
}
