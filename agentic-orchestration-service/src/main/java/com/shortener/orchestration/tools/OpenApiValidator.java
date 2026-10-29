package com.shortener.orchestration.tools;

import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/** Structural validator for generated OpenAPI fragments. */
@Component
public class OpenApiValidator {

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult failed(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    @SuppressWarnings("unchecked")
    public ValidationResult validate(String openApiYaml) {
        if (openApiYaml == null || openApiYaml.isBlank()) {
            return ValidationResult.failed(List.of("OpenAPI fragment is empty"));
        }
        Map<String, Object> root;
        try {
            Object parsed = new Yaml().load(openApiYaml);
            if (!(parsed instanceof Map)) {
                return ValidationResult.failed(List.of("OpenAPI fragment is not a YAML mapping"));
            }
            root = (Map<String, Object>) parsed;
        } catch (Exception exception) {
            return ValidationResult.failed(List.of("Invalid YAML: " + exception.getMessage()));
        }

        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        Object version = root.get("openapi");
        if (!(version instanceof String versionText) || !versionText.startsWith("3.")) {
            errors.add("'openapi' must declare a 3.x version");
        }
        if (!(root.get("paths") instanceof Map<?, ?> paths) || paths.isEmpty()) {
            errors.add("'paths' must be a non-empty mapping");
        }
        if (!(root.get("info") instanceof Map<?, ?> info) || info.get("title") == null) {
            errors.add("'info.title' is required");
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.failed(List.copyOf(errors));
    }
}
