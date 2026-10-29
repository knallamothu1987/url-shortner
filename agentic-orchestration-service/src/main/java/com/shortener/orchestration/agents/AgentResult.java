package com.shortener.orchestration.agents;

import java.util.List;

/**
 * Output of one agent execution: the artifact it produced plus the decisions it made
 * (recorded in the audit lineage) and any files it wrote to the run workspace.
 */
public record AgentResult(
        String artifactName,
        String artifactJson,
        List<String> decisions,
        List<String> filesWritten) {

    public static AgentResult of(String artifactName, String artifactJson, List<String> decisions) {
        return new AgentResult(artifactName, artifactJson, decisions, List.of());
    }
}
