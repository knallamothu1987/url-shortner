package com.shortener.orchestration.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shortener.orchestration.domain.ScenarioType;
import com.shortener.orchestration.tools.CodebaseContextService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Produces the DesignSpec: components, API contract (OpenAPI fragment), ADRs, schema
 * change flag, and a brownfield impact analysis over the real target codebase.
 */
@Component
public class DesignAgent implements Agent {

    private static final Pattern SCHEMA_CHANGE_HINT = Pattern.compile(
            "(?i)\\b(persist|store|table|database|analytics|track|record|audit|history|report)\\b");

    private final LlmClient llm;
    private final ObjectMapper objectMapper;
    private final CodebaseContextService codebase;

    public DesignAgent(LlmClient llm, ObjectMapper objectMapper, CodebaseContextService codebase) {
        this.llm = llm;
        this.objectMapper = objectMapper;
        this.codebase = codebase;
    }

    @Override
    public String name() {
        return "design";
    }

    @Override
    public AgentResult execute(AgentContext context) throws AgentExecutionException {
        String requirement = context.run().getRequirementText();
        // Demonstrable bounded-retry path: first attempt fails recoverably when injected.
        if (AgentTextUtils.hasMarker(requirement, "retry") && context.stage().getAttempts() <= 1) {
            throw new AgentExecutionException(
                    "Transient design model failure (injected for retry demonstration)", true);
        }

        JsonNode requirementSpec = context.context()
                .latestJson(context.run().getId(), "requirement-spec")
                .orElseThrow(() -> new AgentExecutionException(
                        "requirement-spec missing from context (entry gate)", false));

        List<String> decisions = new ArrayList<>();
        String feature = AgentTextUtils.featureName(requirement);
        String slug = AgentTextUtils.featureSlug(requirement);
        boolean schemaChanges = SCHEMA_CHANGE_HINT.matcher(requirement).find();

        ObjectNode spec = objectMapper.createObjectNode();
        ArrayNode components = spec.putArray("components");
        addComponent(components, feature + "Service",
                "Application service implementing: " + requirementSpec.path("intent").asText());
        addComponent(components, feature + "Controller", "REST adapter exposing the new API");
        if (schemaChanges) {
            addComponent(components, feature + "Repository", "JPA persistence for new data");
            decisions.add("Detected persistence need — design includes repository and migration");
        }

        ArrayNode apiChanges = spec.putArray("apiChanges");
        ObjectNode api = apiChanges.addObject();
        api.put("method", "GET");
        api.put("path", "/api/v1/" + slug);
        api.put("description", "New endpoint for: " + requirementSpec.path("intent").asText());

        spec.put("openApiFragment", openApiFragment(feature, slug));
        spec.put("schemaChanges", schemaChanges);
        ArrayNode migrations = spec.putArray("migrations");
        if (schemaChanges) {
            migrations.add("V9__create_" + slug.replace('-', '_') + ".sql");
        }

        ArrayNode adrs = spec.putArray("adrs");
        ObjectNode adr = adrs.addObject();
        adr.put("title", "ADR: Extend url-shortener-service in place for '" + feature + "'");
        adr.put("decision", "Implement inside the existing service following its layered "
                + "api/application/domain structure");
        adr.put("rationale", "Matches existing conventions, avoids premature service extraction, "
                + "and keeps the change reviewable");
        decisions.add("Recorded ADR for in-place extension");

        ObjectNode impact = spec.putObject("impactAnalysis");
        ArrayNode impactedFiles = impact.putArray("impactedFiles");
        List<CodebaseContextService.ImpactedFile> impacted =
                codebase.findImpactedFiles(AgentTextUtils.keywords(requirement, 8));
        for (CodebaseContextService.ImpactedFile file : impacted) {
            ObjectNode node = impactedFiles.addObject();
            node.put("path", file.path());
            node.put("reason", file.reason());
        }
        impact.putArray("impactedApis").add("GET /api/v1/" + slug);
        impact.putArray("dataFlows")
                .add("Controller -> Service -> " + (schemaChanges ? "Repository -> PostgreSQL" : "existing domain"));
        if (context.run().getScenarioType() == ScenarioType.BROWNFIELD) {
            decisions.add("Brownfield impact analysis identified %d candidate file(s)"
                    .formatted(impacted.size()));
        }

        ArrayNode newDependencies = spec.putArray("newDependencies");
        if (AgentTextUtils.hasMarker(requirement, "banned-dep")) {
            // Injected policy-violation demo: the PolicyGate must stop this design.
            ObjectNode dep = newDependencies.addObject();
            dep.put("coordinates", "log4j:log4j:1.2.17");
            dep.put("license", "Apache-2.0");
        }

        String json = llm.enrich(
                "You are a software architect designing a Spring Boot change.",
                "Design for requirement: " + requirement,
                spec.toPrettyString());
        return AgentResult.of("design-spec", json, decisions);
    }

    @Override
    public Optional<AgentResult> fallback(AgentContext context) {
        // Partial design with the gaps explicitly marked for human fill-in.
        ObjectNode spec = objectMapper.createObjectNode();
        ArrayNode components = spec.putArray("components");
        addComponent(components, "FeatureService", "GAP: component design incomplete — needs human input");
        spec.putArray("apiChanges");
        spec.put("openApiFragment", openApiFragment("Feature", "feature"));
        spec.put("schemaChanges", false);
        spec.putArray("migrations");
        ArrayNode adrs = spec.putArray("adrs");
        ObjectNode adr = adrs.addObject();
        adr.put("title", "ADR: design degraded to fallback");
        adr.put("decision", "Partial design emitted with gaps marked");
        adr.put("rationale", "Design agent exhausted retries; human completion required");
        spec.putObject("impactAnalysis").putArray("impactedFiles");
        return Optional.of(AgentResult.of("design-spec", spec.toPrettyString(),
                List.of("Fallback: partial design with gaps marked for human fill-in")));
    }

    private void addComponent(ArrayNode components, String name, String responsibility) {
        ObjectNode component = components.addObject();
        component.put("name", name);
        component.put("responsibility", responsibility);
    }

    private String openApiFragment(String feature, String slug) {
        return """
                openapi: 3.1.0
                info:
                  title: %s API fragment
                  version: 1.0.0
                paths:
                  /api/v1/%s:
                    get:
                      summary: %s
                      operationId: get%s
                      responses:
                        "200":
                          description: Success
                """.formatted(feature, slug, feature.toLowerCase(Locale.ROOT), feature);
    }
}
