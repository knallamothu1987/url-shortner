package com.shortener.orchestration.gates;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortener.orchestration.dag.GateBinding;
import com.shortener.orchestration.policy.PolicyGuardrailService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Enforces the policy guardrails (secret scan, banned dependencies, license allowlist)
 * on the stage's output artifact and generated files before the stage may complete.
 */
@Component
public class PolicyGate implements GateEvaluator {

    private final PolicyGuardrailService guardrails;

    public PolicyGate(PolicyGuardrailService guardrails) {
        this.guardrails = guardrails;
    }

    @Override
    public String type() {
        return "policy";
    }

    @Override
    public GateResult evaluate(GateBinding binding, GateContext context) {
        String stageName = context.stageDefinition().name();
        // The stage's own artifact is the most recent one it wrote; fall back to scanning
        // just the workspace when the stage produced no artifact.
        String artifactJson = context.store().findArtifacts(context.run().getId()).stream()
                .filter(artifact -> artifact.getStageName().equals(stageName))
                .reduce((first, second) -> second)
                .map(artifact -> artifact.getContent())
                .orElse("{}");
        JsonNode artifact = context.context().parse(artifactJson);
        List<PolicyGuardrailService.PolicyViolation> violations =
                guardrails.check(artifactJson, artifact, context.stageWorkspace());
        if (violations.isEmpty()) {
            return GateResult.pass("All policy guardrails satisfied");
        }
        return GateResult.fail("Policy violations: " + violations.stream()
                .map(violation -> "[%s] %s".formatted(violation.policy(), violation.detail()))
                .collect(Collectors.joining("; ")));
    }
}
