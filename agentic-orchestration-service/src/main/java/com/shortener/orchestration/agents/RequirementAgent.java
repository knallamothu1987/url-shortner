package com.shortener.orchestration.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shortener.orchestration.domain.ApprovalStatus;
import com.shortener.orchestration.domain.ScenarioType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Interprets raw requirement text, surfaces ambiguities with severities, and normalizes
 * the request into a structured RequirementSpec. BLOCKER ambiguities trigger the
 * clarification approval gate (human-in-the-loop) instead of letting the pipeline guess.
 */
@Component
public class RequirementAgent implements Agent {

    private static final List<String> VAGUE_TERMS = List.of(
            "fast", "better", "improve", "nice", "soon", "several", "appropriate",
            "user-friendly", "performant", "quick", "scalable", "robust", "simple", "etc");

    private final LlmClient llm;
    private final ObjectMapper objectMapper;

    public RequirementAgent(LlmClient llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "requirement";
    }

    @Override
    public AgentResult execute(AgentContext context) throws AgentExecutionException {
        String requirement = context.run().getRequirementText();
        if (requirement == null || requirement.isBlank()) {
            throw new AgentExecutionException("Requirement text is empty (entry gate)", false);
        }

        List<String> decisions = new ArrayList<>();
        ObjectNode spec = objectMapper.createObjectNode();
        String intent = firstSentence(requirement);
        spec.put("intent", intent);
        spec.put("normalizedProblem", normalize(requirement, context.run().getScenarioType()));

        ArrayNode functional = spec.putArray("functionalRequirements");
        for (String item : splitRequirements(requirement)) {
            functional.add(item);
        }

        ArrayNode ambiguities = spec.putArray("ambiguities");
        detectAmbiguities(requirement, context.run().getScenarioType(), ambiguities, decisions);
        applyClarifications(context, ambiguities, decisions);

        ArrayNode criteria = spec.putArray("acceptanceCriteria");
        criteria.add("All functional requirements are covered by passing tests");
        criteria.add("No policy guardrail violations (secrets, banned dependencies, licenses)");
        criteria.add("Generated API changes are documented in the OpenAPI fragment");

        spec.putArray("dependencyHints")
                .add("design depends on this spec")
                .add("testing must assert the acceptance criteria");

        decisions.add("Interpreted intent as: " + intent);
        String json = llm.enrich(
                "You are a requirements analyst for an SDLC pipeline.",
                "Analyze this requirement and refine the spec: " + requirement,
                spec.toPrettyString());
        return AgentResult.of("requirement-spec", json, decisions);
    }

    @Override
    public Optional<AgentResult> fallback(AgentContext context) {
        // Per design: instead of fabricating a spec, return clarification questions.
        ObjectNode spec = objectMapper.createObjectNode();
        spec.put("intent", "UNCLEAR — human clarification required");
        spec.put("normalizedProblem", "The requirement could not be normalized automatically.");
        spec.putArray("functionalRequirements").add("To be defined after clarification");
        ArrayNode ambiguities = spec.putArray("ambiguities");
        ObjectNode ambiguity = ambiguities.addObject();
        ambiguity.put("id", "AMB-FALLBACK");
        ambiguity.put("question", "Requirement analysis failed — please restate the requirement");
        ambiguity.put("severity", "BLOCKER");
        spec.putArray("acceptanceCriteria");
        return Optional.of(AgentResult.of("requirement-spec", spec.toPrettyString(),
                List.of("Fallback: returned clarification questions instead of a spec")));
    }

    private void detectAmbiguities(String requirement, ScenarioType scenario,
                                   ArrayNode ambiguities, List<String> decisions) {
        String lower = requirement.toLowerCase(Locale.ROOT);
        int counter = 1;
        for (String term : VAGUE_TERMS) {
            if (lower.matches(".*\\b" + term + "\\b.*")) {
                ObjectNode ambiguity = ambiguities.addObject();
                ambiguity.put("id", "AMB-%03d".formatted(counter++));
                ambiguity.put("question",
                        "'%s' is subjective — what measurable target defines it?".formatted(term));
                ambiguity.put("severity", "MAJOR");
                ambiguity.put("proposedResolution",
                        "Agree on a quantified acceptance criterion with the requester");
                decisions.add("Flagged vague term '%s' as MAJOR ambiguity".formatted(term));
            }
        }
        boolean hasNumbers = lower.matches(".*\\d.*");
        if (scenario == ScenarioType.AMBIGUOUS || (!hasNumbers && !ambiguities.isEmpty())) {
            ObjectNode blocker = ambiguities.addObject();
            blocker.put("id", "AMB-%03d".formatted(counter));
            blocker.put("question",
                    "Scope and success metrics are unspecified — which users, what volume, what SLO?");
            blocker.put("severity", "BLOCKER");
            blocker.put("proposedResolution",
                    "Pause for human clarification before design (controlled autonomy)");
            decisions.add("Raised BLOCKER ambiguity: scope/metrics unspecified");
        }
    }

    private void applyClarifications(AgentContext context, ArrayNode ambiguities,
                                     List<String> decisions) {
        context.store().findApprovalForGate(
                        context.run().getId(), "requirements", "clarification")
                .filter(approval -> approval.getStatus() == ApprovalStatus.APPROVED)
                .ifPresent(approval -> {
                    for (var ambiguity : ambiguities) {
                        if ("BLOCKER".equals(ambiguity.path("severity").asText())) {
                            ((ObjectNode) ambiguity).put("severity", "MINOR");
                            ((ObjectNode) ambiguity).put("proposedResolution",
                                    "Clarified by human: " + approval.getNotes());
                        }
                    }
                    decisions.add("Applied human clarification: " + approval.getNotes());
                });
    }

    private String firstSentence(String text) {
        String cleaned = text.replaceAll("\\[inject:[^]]+]", "").trim();
        int end = cleaned.indexOf('.');
        return end > 0 ? cleaned.substring(0, end + 1) : cleaned;
    }

    private String normalize(String requirement, ScenarioType scenario) {
        return "[%s] Engineering problem: %s".formatted(
                scenario, requirement.replaceAll("\\[inject:[^]]+]", "").trim());
    }

    private List<String> splitRequirements(String text) {
        List<String> items = new ArrayList<>();
        for (String sentence : text.replaceAll("\\[inject:[^]]+]", "").split("[.;\\n]")) {
            String trimmed = sentence.trim();
            if (trimmed.length() > 10) {
                items.add(trimmed);
            }
        }
        if (items.isEmpty()) {
            items.add(text.trim());
        }
        return items;
    }
}
