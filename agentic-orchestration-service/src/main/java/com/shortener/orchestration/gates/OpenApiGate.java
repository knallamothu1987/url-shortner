package com.shortener.orchestration.gates;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortener.orchestration.dag.GateBinding;
import com.shortener.orchestration.tools.OpenApiValidator;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Validates the OpenAPI fragment embedded in the design-spec artifact. */
@Component
public class OpenApiGate implements GateEvaluator {

    private final OpenApiValidator validator;

    public OpenApiGate(OpenApiValidator validator) {
        this.validator = validator;
    }

    @Override
    public String type() {
        return "openapi";
    }

    @Override
    public GateResult evaluate(GateBinding binding, GateContext context) {
        Optional<JsonNode> designSpec = context.context()
                .latestJson(context.run().getId(), "design-spec");
        if (designSpec.isEmpty()) {
            return GateResult.fail("No design-spec artifact in pipeline context");
        }
        String fragment = designSpec.get().path("openApiFragment").asText("");
        OpenApiValidator.ValidationResult result = validator.validate(fragment);
        if (result.valid()) {
            return GateResult.pass("OpenAPI fragment is structurally valid");
        }
        return GateResult.fail("OpenAPI validation failed: " + String.join("; ", result.errors()));
    }
}
