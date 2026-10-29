package com.shortener.orchestration.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shortener.orchestration.tools.SecretScanner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reflection-pattern reviewer: critiques the generated output with rule-based scans
 * (secrets, TODOs) and emits a ReviewReport. Unresolved MAJOR/BLOCKER findings trigger
 * the human approval gate.
 */
@Component
public class ReviewAgent implements Agent {

    private final LlmClient llm;
    private final ObjectMapper objectMapper;
    private final SecretScanner secretScanner;

    public ReviewAgent(LlmClient llm, ObjectMapper objectMapper, SecretScanner secretScanner) {
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.secretScanner = secretScanner;
    }

    @Override
    public String name() {
        return "review";
    }

    @Override
    public AgentResult execute(AgentContext context) throws AgentExecutionException {
        String requirement = context.run().getRequirementText();
        List<String> decisions = new ArrayList<>();
        ObjectNode report = objectMapper.createObjectNode();
        ArrayNode findings = report.putArray("findings");
        int counter = 1;

        for (SecretScanner.SecretFinding secret
                : secretScanner.scanDirectory(context.runWorkspace())) {
            addFinding(findings, "REV-%03d".formatted(counter++), "BLOCKER", "security",
                    secret.file(), "Secret pattern detected: " + secret.patternName(), false,
                    "Remove the credential and load it from configuration");
            decisions.add("Security scan found secret pattern " + secret.patternName());
        }

        for (String todoFile : findTodoFiles(context.runWorkspace())) {
            addFinding(findings, "REV-%03d".formatted(counter++), "MINOR", "maintainability",
                    todoFile, "TODO marker left in generated source", true,
                    "Tracked; acceptable for prototype output");
        }

        if (AgentTextUtils.hasMarker(requirement, "major-finding")) {
            addFinding(findings, "REV-%03d".formatted(counter++), "MAJOR", "performance",
                    "generated endpoint", "No load test covers the new endpoint (injected demo)",
                    false, "Requires human accept/reject decision");
            decisions.add("Raised unresolved MAJOR finding — human review required");
        }

        addFinding(findings, "REV-%03d".formatted(counter), "INFO", "style",
                "generated sources", "Generated code follows the service's layered conventions",
                true, null);

        report.put("summary", "%d finding(s); blockers=%d, unresolved major=%d".formatted(
                findings.size(),
                countBySeverity(findings, "BLOCKER", false),
                countBySeverity(findings, "MAJOR", false)));
        decisions.add("Completed reflection review of generated artifacts");

        String json = llm.enrich(
                "You are a meticulous code reviewer (security, performance, maintainability).",
                "Review the generated change for: " + requirement,
                report.toPrettyString());
        return AgentResult.of("review-report", json, decisions);
    }

    @Override
    public Optional<AgentResult> fallback(AgentContext context) {
        // Degrade to rule-based scanning only (no LLM critique).
        ObjectNode report = objectMapper.createObjectNode();
        ArrayNode findings = report.putArray("findings");
        int counter = 1;
        for (SecretScanner.SecretFinding secret
                : secretScanner.scanDirectory(context.runWorkspace())) {
            addFinding(findings, "REV-%03d".formatted(counter++), "BLOCKER", "security",
                    secret.file(), "Secret pattern detected: " + secret.patternName(), false, null);
        }
        report.put("summary", "Fallback rule-based review: %d finding(s)".formatted(findings.size()));
        return Optional.of(AgentResult.of("review-report", report.toPrettyString(),
                List.of("Fallback: degraded to rule-based static review only")));
    }

    private void addFinding(ArrayNode findings, String id, String severity, String category,
                            String file, String description, boolean resolved, String mitigation) {
        ObjectNode finding = findings.addObject();
        finding.put("id", id);
        finding.put("severity", severity);
        finding.put("category", category);
        finding.put("file", file);
        finding.put("description", description);
        finding.put("resolved", resolved);
        if (mitigation != null) {
            finding.put("mitigation", mitigation);
        }
    }

    private int countBySeverity(ArrayNode findings, String severity, boolean resolved) {
        int count = 0;
        for (var finding : findings) {
            if (severity.equals(finding.path("severity").asText())
                    && finding.path("resolved").asBoolean() == resolved) {
                count++;
            }
        }
        return count;
    }

    private List<String> findTodoFiles(Path workspace) {
        if (!Files.isDirectory(workspace)) {
            return List.of();
        }
        List<String> todoFiles = new ArrayList<>();
        try (Stream<Path> files = Files.walk(workspace)) {
            files.filter(Files::isRegularFile)
                    .filter(file -> file.toString().endsWith(".java"))
                    .forEach(file -> {
                        try {
                            if (Files.readString(file).contains("TODO")) {
                                todoFiles.add(workspace.relativize(file).toString().replace('\\', '/'));
                            }
                        } catch (IOException ignored) {
                            // Skip unreadable files.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort scan.
        }
        return todoFiles;
    }
}
