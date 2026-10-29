package com.shortener.orchestration.dag;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads the declarative SDLC DAG from {@code pipeline-definition.yml}. The graph is data,
 * not code: stages, dependencies, and gates can be changed without touching the engine.
 */
@Configuration
public class PipelineDefinitionLoader {

    @Bean
    PipelineDefinition pipelineDefinition() {
        return load("pipeline-definition.yml");
    }

    @SuppressWarnings("unchecked")
    public static PipelineDefinition load(String classpathLocation) {
        try (InputStream input = new ClassPathResource(classpathLocation).getInputStream()) {
            Map<String, Object> root = new Yaml().load(input);
            String name = (String) root.getOrDefault("name", "pipeline");
            List<Map<String, Object>> stagesYaml = (List<Map<String, Object>>) root.get("stages");
            if (stagesYaml == null || stagesYaml.isEmpty()) {
                throw new IllegalArgumentException("Pipeline definition has no stages");
            }
            List<StageDefinition> stages = stagesYaml.stream()
                    .map(PipelineDefinitionLoader::toStage)
                    .toList();
            return new PipelineDefinition(name, stages);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load pipeline definition", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static StageDefinition toStage(Map<String, Object> yaml) {
        String name = (String) yaml.get("name");
        String agent = (String) yaml.get("agent");
        if (name == null || agent == null) {
            throw new IllegalArgumentException("Every stage needs a name and an agent");
        }
        List<String> dependsOn = (List<String>) yaml.getOrDefault("dependsOn", List.of());
        List<String> exitGateSpecs = (List<String>) yaml.getOrDefault("exitGates", List.of());
        List<GateBinding> exitGates = exitGateSpecs.stream().map(GateBinding::parse).toList();
        return new StageDefinition(name, agent, dependsOn, exitGates);
    }
}
