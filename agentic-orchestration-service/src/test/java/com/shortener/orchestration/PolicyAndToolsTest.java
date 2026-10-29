package com.shortener.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortener.orchestration.policy.PolicyGuardrailService;
import com.shortener.orchestration.tools.OpenApiValidator;
import com.shortener.orchestration.tools.SecretScanner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyAndToolsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecretScanner scanner = new SecretScanner();
    private final PolicyGuardrailService guardrails = new PolicyGuardrailService(scanner);
    private final OpenApiValidator openApi = new OpenApiValidator();

    @Test
    void secretScannerFlagsCredentialPatterns() {
        assertFalse(scanner.scanText("f", "String key = \"AKIAIOSFODNN7EXAMPLE\";").isEmpty());
        assertFalse(scanner.scanText("f", "password = \"hunter22\"").isEmpty());
        assertTrue(scanner.scanText("f", "int shortCode = 42;").isEmpty());
    }

    @Test
    void secretScannerWalksDirectories(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("leak.properties"), "api_key = \"abcdefabcdef12345678\"");
        List<SecretScanner.SecretFinding> findings = scanner.scanDirectory(dir);
        assertEquals(1, findings.size());
        assertEquals("hardcoded-secret", findings.get(0).patternName());
    }

    @Test
    void bannedDependenciesAndLicensesAreRejected() throws Exception {
        String artifact = """
                {"newDependencies": [
                  {"coordinates": "log4j:log4j:1.2.17", "license": "Apache-2.0"},
                  {"coordinates": "com.example:closed:1.0", "license": "Proprietary"}
                ]}""";
        var violations = guardrails.check(artifact, objectMapper.readTree(artifact), null);
        assertEquals(2, violations.size());
        assertTrue(violations.stream().anyMatch(violation ->
                violation.policy().equals("no-banned-dependencies")));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.policy().equals("license-compliance")));
    }

    @Test
    void cleanArtifactPassesGuardrails() throws Exception {
        String artifact = """
                {"newDependencies": [
                  {"coordinates": "org.apache.commons:commons-lang3:3.14.0", "license": "Apache-2.0"}
                ]}""";
        assertTrue(guardrails.check(artifact, objectMapper.readTree(artifact), null).isEmpty());
    }

    @Test
    void openApiValidatorRequires3xWithPathsAndTitle() {
        String valid = """
                openapi: 3.1.0
                info:
                  title: Demo
                paths:
                  /api/v1/demo:
                    get: {}
                """;
        assertTrue(openApi.validate(valid).valid());
        assertFalse(openApi.validate("openapi: 2.0\npaths: {}").valid());
        assertFalse(openApi.validate("not yaml: [").valid());
        assertFalse(openApi.validate("").valid());
    }
}
