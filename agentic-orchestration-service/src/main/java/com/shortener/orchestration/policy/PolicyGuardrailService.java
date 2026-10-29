package com.shortener.orchestration.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortener.orchestration.tools.SecretScanner;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Policy guardrails enforced before any exit gate finalizes: no secrets, no banned
 * dependencies, and only allow-listed licenses for newly introduced dependencies.
 */
@Service
public class PolicyGuardrailService {

    public record PolicyViolation(String policy, String detail) {
    }

    private static final List<String> BANNED_DEPENDENCY_PREFIXES = List.of(
            "log4j:log4j",
            "commons-collections:commons-collections:3",
            "org.apache.struts:struts2-core:2.3");

    private static final Set<String> APPROVED_LICENSES = Set.of(
            "Apache-2.0", "MIT", "EPL-2.0", "BSD-3-Clause", "BSD-2-Clause");

    private final SecretScanner secretScanner;

    public PolicyGuardrailService(SecretScanner secretScanner) {
        this.secretScanner = secretScanner;
    }

    public List<PolicyViolation> check(String artifactJson, JsonNode artifact, Path stageWorkspace) {
        List<PolicyViolation> violations = new ArrayList<>();

        secretScanner.scanText("artifact", artifactJson).forEach(finding ->
                violations.add(new PolicyViolation("no-hardcoded-secrets",
                        "Secret pattern '%s' found in artifact".formatted(finding.patternName()))));

        if (stageWorkspace != null) {
            secretScanner.scanDirectory(stageWorkspace).forEach(finding ->
                    violations.add(new PolicyViolation("no-hardcoded-secrets",
                            "Secret pattern '%s' found in %s".formatted(
                                    finding.patternName(), finding.file()))));
        }

        if (artifact != null && artifact.has("newDependencies")) {
            for (JsonNode dependency : artifact.get("newDependencies")) {
                String coordinates = dependency.path("coordinates").asText("");
                String license = dependency.path("license").asText("");
                for (String banned : BANNED_DEPENDENCY_PREFIXES) {
                    if (coordinates.startsWith(banned)) {
                        violations.add(new PolicyViolation("no-banned-dependencies",
                                "Dependency '%s' is on the blocklist".formatted(coordinates)));
                    }
                }
                if (!license.isBlank() && !APPROVED_LICENSES.contains(license)) {
                    violations.add(new PolicyViolation("license-compliance",
                            "License '%s' of '%s' is not on the approved SPDX allowlist"
                                    .formatted(license, coordinates)));
                }
            }
        }
        return violations;
    }
}
