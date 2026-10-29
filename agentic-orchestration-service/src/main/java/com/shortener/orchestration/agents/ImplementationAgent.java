package com.shortener.orchestration.agents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Generates implementation sources from the DesignSpec into the sandboxed run workspace
 * (never directly into the live codebase — promotion is a human release decision).
 */
@Component
public class ImplementationAgent implements Agent {

    private final LlmClient llm;
    private final ObjectMapper objectMapper;

    public ImplementationAgent(LlmClient llm, ObjectMapper objectMapper) {
        this.llm = llm;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "implementation";
    }

    @Override
    public AgentResult execute(AgentContext context) throws AgentExecutionException {
        JsonNode design = context.context()
                .latestJson(context.run().getId(), "design-spec")
                .orElseThrow(() -> new AgentExecutionException(
                        "design-spec missing from context (entry gate)", false));

        String requirement = context.run().getRequirementText();
        String feature = AgentTextUtils.featureName(requirement);
        String slug = AgentTextUtils.featureSlug(requirement).replace('-', '_');
        Path sources = context.stageWorkspace().resolve("src");
        List<String> files = new ArrayList<>();
        List<String> decisions = new ArrayList<>();

        try {
            Files.createDirectories(sources);
            Path service = sources.resolve(feature + "Service.java");
            Files.writeString(service, serviceSource(feature, design));
            files.add(relative(context, service));

            Path controller = sources.resolve(feature + "Controller.java");
            Files.writeString(controller, controllerSource(feature, slug));
            files.add(relative(context, controller));

            if (design.path("schemaChanges").asBoolean(false)) {
                Path migration = context.stageWorkspace()
                        .resolve("migration").resolve("V9__create_" + slug + ".sql");
                Files.createDirectories(migration.getParent());
                Files.writeString(migration, migrationSource(slug));
                files.add(relative(context, migration));
                decisions.add("Generated Flyway migration for schema change");
            }

            if (AgentTextUtils.hasMarker(requirement, "secret")) {
                // Injected guardrail demo: PolicyGate must catch this and roll the stage back.
                Path config = context.stageWorkspace().resolve("application-generated.properties");
                Files.writeString(config, "datasource.password=\"sup3rSecretValue!\"\n");
                files.add(relative(context, config));
            }
        } catch (IOException exception) {
            throw new AgentExecutionException(
                    "Could not write generated sources: " + exception.getMessage(), true, exception);
        }

        decisions.add("Generated %d file(s) into sandboxed workspace".formatted(files.size()));

        ObjectNode manifest = objectMapper.createObjectNode();
        ArrayNode fileNodes = manifest.putArray("files");
        for (String file : files) {
            ObjectNode node = fileNodes.addObject();
            node.put("path", file);
            node.put("purpose", "generated implementation artifact");
        }
        manifest.set("newDependencies",
                design.path("newDependencies").isArray()
                        ? design.get("newDependencies").deepCopy()
                        : objectMapper.createArrayNode());
        manifest.put("notes", "Sources generated following existing api/application layering");

        String json = llm.enrich(
                "You are a senior Java engineer generating Spring Boot code.",
                "Summarize the implementation for: " + requirement,
                manifest.toPrettyString());
        return new AgentResult("implementation-manifest", json, decisions, files);
    }

    @Override
    public Optional<AgentResult> fallback(AgentContext context) {
        // TODO-annotated stub instead of a full implementation.
        try {
            Path sources = context.stageWorkspace().resolve("src");
            Files.createDirectories(sources);
            String feature = AgentTextUtils.featureName(context.run().getRequirementText());
            Path stub = sources.resolve(feature + "Service.java");
            Files.writeString(stub, """
                    package com.shortener.generated;

                    // TODO: implementation agent exhausted retries — human completion required
                    public class %sService {
                    }
                    """.formatted(feature));
            ObjectNode manifest = objectMapper.createObjectNode();
            ArrayNode files = manifest.putArray("files");
            ObjectNode node = files.addObject();
            node.put("path", relative(context, stub));
            node.put("purpose", "TODO stub (fallback)");
            manifest.put("notes", "Fallback: TODO-annotated stubs only");
            return Optional.of(new AgentResult("implementation-manifest",
                    manifest.toPrettyString(),
                    List.of("Fallback: emitted TODO-annotated stubs"),
                    List.of(relative(context, stub))));
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    @Override
    public void rollback(AgentContext context) {
        deleteRecursively(context.stageWorkspace());
    }

    static void deleteRecursively(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    private String relative(AgentContext context, Path file) {
        return context.runWorkspace().relativize(file).toString().replace('\\', '/');
    }

    private String serviceSource(String feature, JsonNode design) {
        return """
                package com.shortener.generated;

                import org.springframework.stereotype.Service;

                /**
                 * Generated by ImplementationAgent.
                 * Responsibility: %s
                 */
                @Service
                public class %sService {

                    public String describe() {
                        return "%s";
                    }
                }
                """.formatted(
                design.path("components").path(0).path("responsibility").asText("feature logic"),
                feature, feature);
    }

    private String controllerSource(String feature, String slug) {
        return """
                package com.shortener.generated;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                /** Generated by ImplementationAgent. */
                @RestController
                public class %sController {

                    private final %sService service;

                    public %sController(%sService service) {
                        this.service = service;
                    }

                    @GetMapping("/api/v1/%s")
                    public String get() {
                        return service.describe();
                    }
                }
                """.formatted(feature, feature, feature, feature, slug.replace('_', '-'));
    }

    private String migrationSource(String slug) {
        return """
                CREATE TABLE %s (
                    id UUID PRIMARY KEY,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
                );
                """.formatted(slug);
    }
}
