package com.shortener.orchestration.gates;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.shortener.orchestration.dag.GateBinding;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Validates the stage's output artifact against a JSON Schema. The gate parameter is both
 * the artifact name and the schema file name, e.g. {@code schema:design-spec} validates
 * artifact "design-spec" against {@code schemas/design-spec.schema.json}.
 */
@Component
public class SchemaGate implements GateEvaluator {

    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    @Override
    public String type() {
        return "schema";
    }

    @Override
    public GateResult evaluate(GateBinding binding, GateContext context) {
        String artifactName = binding.param();
        Optional<JsonNode> artifact = context.context()
                .latestJson(context.run().getId(), artifactName);
        if (artifact.isEmpty()) {
            return GateResult.fail("Artifact '" + artifactName + "' not found in pipeline context");
        }
        JsonSchema schema;
        try {
            schema = schemaCache.computeIfAbsent(artifactName, this::loadSchema);
        } catch (IllegalStateException exception) {
            return GateResult.fail(exception.getMessage());
        }
        Set<ValidationMessage> errors = schema.validate(artifact.get());
        if (errors.isEmpty()) {
            return GateResult.pass("Artifact '" + artifactName + "' validates against schema");
        }
        return GateResult.fail("Schema validation failed for '%s': %s".formatted(
                artifactName,
                errors.stream().map(ValidationMessage::getMessage)
                        .collect(Collectors.joining("; "))));
    }

    private JsonSchema loadSchema(String name) {
        String location = "schemas/" + name + ".schema.json";
        try (InputStream input = new ClassPathResource(location).getInputStream()) {
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load JSON schema " + location);
        }
    }
}
