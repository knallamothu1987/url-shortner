package com.shortener.orchestration.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortener.orchestration.domain.ContextArtifact;
import com.shortener.orchestration.domain.DecisionActor;
import com.shortener.orchestration.domain.DecisionRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared cross-stage context store. Artifacts are immutable and versioned; every write
 * appends a new version and is recorded in the decision lineage.
 */
@Service
public class PipelineContextService {

    private final PipelineStore store;
    private final ObjectMapper objectMapper;

    public PipelineContextService(PipelineStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public ContextArtifact append(UUID runId, String stageName, String name,
                                  String contentJson, String createdBy) {
        int nextVersion = store.findLatestArtifact(runId, name)
                .map(artifact -> artifact.getVersion() + 1)
                .orElse(1);
        ContextArtifact artifact = new ContextArtifact(
                runId, stageName, name, nextVersion, contentJson, createdBy);
        store.saveArtifact(artifact);
        store.saveDecision(new DecisionRecord(runId, stageName, DecisionActor.ORCHESTRATOR,
                "ARTIFACT_WRITTEN",
                "Artifact '%s' v%d written by %s".formatted(name, nextVersion, createdBy)));
        return artifact;
    }

    public Optional<String> latestContent(UUID runId, String name) {
        return store.findLatestArtifact(runId, name).map(ContextArtifact::getContent);
    }

    public Optional<JsonNode> latestJson(UUID runId, String name) {
        return latestContent(runId, name).map(this::parse);
    }

    public List<ContextArtifact> allArtifacts(UUID runId) {
        return store.findArtifacts(runId);
    }

    public JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Artifact content is not valid JSON", exception);
        }
    }
}
