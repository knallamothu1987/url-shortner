package com.shortener.orchestration.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shortener.orchestration.domain.ApprovalRequest;
import com.shortener.orchestration.domain.ContextArtifact;
import com.shortener.orchestration.domain.DecisionRecord;
import com.shortener.orchestration.domain.StageExecution;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Produces documentation artifacts. Runs twice in the DAG: once in parallel with
 * implementation (design-level docs) and once as the final synchronization stage, where it
 * compiles the Final Engineering Summary (plan/rationale, artifacts, risks, assumptions,
 * limitations, reliability stats) from the full decision lineage.
 */
@Component
public class DocumentationAgent implements Agent {

    private final ObjectMapper objectMapper;

    public DocumentationAgent(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "documentation";
    }

    @Override
    public AgentResult execute(AgentContext context) throws AgentExecutionException {
        if ("design-docs".equals(context.stage().getStageName())) {
            return designDocs(context);
        }
        return releaseSummary(context);
    }

    @Override
    public Optional<AgentResult> fallback(AgentContext context) {
        ObjectNode artifact = objectMapper.createObjectNode();
        artifact.putArray("documents");
        artifact.put("summaryMarkdown", "Documentation generation degraded — minimal summary only.");
        return Optional.of(AgentResult.of(
                "design-docs".equals(context.stage().getStageName()) ? "design-docs" : "release-summary",
                artifact.toPrettyString(),
                List.of("Fallback: emitted minimal documentation stub")));
    }

    private AgentResult designDocs(AgentContext context) throws AgentExecutionException {
        JsonNode design = context.context()
                .latestJson(context.run().getId(), "design-spec")
                .orElseThrow(() -> new AgentExecutionException(
                        "design-spec missing from context (entry gate)", false));
        StringBuilder markdown = new StringBuilder("# Design Documentation\n\n");
        markdown.append("## Components\n");
        for (JsonNode component : design.path("components")) {
            markdown.append("- **").append(component.path("name").asText()).append("** — ")
                    .append(component.path("responsibility").asText()).append('\n');
        }
        markdown.append("\n## API Changes\n");
        for (JsonNode api : design.path("apiChanges")) {
            markdown.append("- `").append(api.path("method").asText()).append(' ')
                    .append(api.path("path").asText()).append("` — ")
                    .append(api.path("description").asText()).append('\n');
        }
        markdown.append("\n## Architecture Decision Records\n");
        for (JsonNode adr : design.path("adrs")) {
            markdown.append("- **").append(adr.path("title").asText()).append("**: ")
                    .append(adr.path("decision").asText()).append(" — ")
                    .append(adr.path("rationale").asText()).append('\n');
        }
        String file = writeDocument(context, "DESIGN.md", markdown.toString());
        return documentArtifact("design-docs", file, markdown.toString(),
                "Generated design-level documentation in parallel with implementation");
    }

    private AgentResult releaseSummary(AgentContext context) {
        var runId = context.run().getId();
        List<StageExecution> stages = context.store().findStages(runId);
        List<DecisionRecord> decisions = context.store().findDecisions(runId);
        List<ContextArtifact> artifacts = context.store().findArtifacts(runId);
        List<ApprovalRequest> approvals = context.store().findApprovals(runId);

        StringBuilder md = new StringBuilder("# Final Engineering Summary\n\n");
        md.append("**Requirement:** ").append(context.run().getRequirementText()).append("\n\n");
        md.append("**Scenario:** ").append(context.run().getScenarioType()).append("\n\n");

        md.append("## Plan & Execution (reliability)\n\n");
        md.append("| Stage | Status | Attempts | Fallback | Duration |\n");
        md.append("|---|---|---|---|---|\n");
        for (StageExecution stage : stages) {
            String duration = (stage.getStartedAt() != null && stage.getFinishedAt() != null)
                    ? Duration.between(stage.getStartedAt(), stage.getFinishedAt()).toMillis() + " ms"
                    : "—";
            md.append("| ").append(stage.getStageName())
                    .append(" | ").append(stage.getStatus())
                    .append(" | ").append(stage.getAttempts())
                    .append(" | ").append(stage.isFallbackUsed() ? "yes" : "no")
                    .append(" | ").append(duration).append(" |\n");
        }

        md.append("\n## Artifacts\n\n");
        for (ContextArtifact artifact : artifacts) {
            md.append("- `").append(artifact.getName()).append("` v").append(artifact.getVersion())
                    .append(" (stage: ").append(artifact.getStageName())
                    .append(", by: ").append(artifact.getCreatedBy()).append(")\n");
        }

        md.append("\n## Human Oversight\n\n");
        if (approvals.isEmpty()) {
            md.append("No approval checkpoints were triggered for this run.\n");
        }
        for (ApprovalRequest approval : approvals) {
            md.append("- [").append(approval.getStatus()).append("] ")
                    .append(approval.getGateName()).append(" at stage '")
                    .append(approval.getStageName()).append("': ").append(approval.getReason());
            if (approval.getNotes() != null) {
                md.append(" — notes: ").append(approval.getNotes());
            }
            md.append('\n');
        }

        md.append("\n## Decision Lineage (").append(decisions.size()).append(" records)\n\n");
        for (DecisionRecord decision : decisions) {
            md.append("- ").append(decision.getCreatedAt()).append(" [")
                    .append(decision.getActor()).append('/')
                    .append(decision.getDecisionType()).append("] ")
                    .append(decision.getRationale()).append('\n');
        }

        md.append("\n## Risks & Trade-offs\n\n")
                .append("- Generated code lands in a sandboxed workspace; promotion to the live ")
                .append("codebase is an explicit human release decision.\n")
                .append("- Simulated build/test results are used when the Maven tool is disabled ")
                .append("(speed vs. fidelity trade-off; enable `orchestrator.tools.maven.enabled`).\n")
                .append("- Keyword-based impact analysis may miss semantically related files ")
                .append("(vector RAG is the documented upgrade path).\n");

        md.append("\n## Assumptions & Limitations\n\n")
                .append("- Stub LLM mode produces deterministic heuristics; openai mode refines them.\n")
                .append("- Single-node orchestration; pipeline state is persisted but execution is not clustered.\n");

        String file = writeDocumentQuietly(context, "RUN-SUMMARY.md", md.toString());
        return documentArtifact("release-summary", file, md.toString(),
                "Compiled final engineering summary from full decision lineage");
    }

    private String writeDocument(AgentContext context, String name, String content)
            throws AgentExecutionException {
        try {
            Files.createDirectories(context.stageWorkspace());
            Path file = context.stageWorkspace().resolve(name);
            Files.writeString(file, content);
            return context.runWorkspace().relativize(file).toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new AgentExecutionException(
                    "Could not write document " + name + ": " + exception.getMessage(), true, exception);
        }
    }

    private String writeDocumentQuietly(AgentContext context, String name, String content) {
        try {
            return writeDocument(context, name, content);
        } catch (AgentExecutionException exception) {
            return name + " (write failed: " + exception.getMessage() + ")";
        }
    }

    private AgentResult documentArtifact(String artifactName, String file, String markdown,
                                         String decision) {
        ObjectNode artifact = objectMapper.createObjectNode();
        ArrayNode documents = artifact.putArray("documents");
        documents.add(file);
        artifact.put("summaryMarkdown", markdown);
        return new AgentResult(artifactName, artifact.toPrettyString(),
                List.of(decision), List.of(file));
    }
}
