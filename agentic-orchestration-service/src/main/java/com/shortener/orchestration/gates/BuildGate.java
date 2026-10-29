package com.shortener.orchestration.gates;

import com.shortener.orchestration.config.OrchestratorProperties;
import com.shortener.orchestration.dag.GateBinding;
import com.shortener.orchestration.tools.MavenToolRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Build verification. Always performs structural validation of generated Java sources;
 * when the Maven tool is enabled it additionally runs {@code mvn compile} against the
 * target codebase to prove the brownfield build is intact.
 */
@Component
public class BuildGate implements GateEvaluator {

    private final MavenToolRunner maven;
    private final OrchestratorProperties properties;

    public BuildGate(MavenToolRunner maven, OrchestratorProperties properties) {
        this.maven = maven;
        this.properties = properties;
    }

    @Override
    public String type() {
        return "build";
    }

    @Override
    public GateResult evaluate(GateBinding binding, GateContext context) {
        List<String> structuralErrors = validateGeneratedSources(context.stageWorkspace());
        if (!structuralErrors.isEmpty()) {
            return GateResult.fail("Generated sources failed structural validation: "
                    + String.join("; ", structuralErrors));
        }
        if (maven.isEnabled()) {
            MavenToolRunner.ToolResult result = maven.run("compile",
                    com.shortener.orchestration.tools.CodebaseContextService
                            .resolveCodebaseRoot(Path.of(properties.codebaseRoot())));
            if (!result.success()) {
                return GateResult.fail("mvn compile failed (exit %d): %s"
                        .formatted(result.exitCode(), result.outputTail()));
            }
            return GateResult.pass("Structural validation and mvn compile passed");
        }
        return GateResult.pass("Structural validation passed (Maven tool disabled — simulated build)");
    }

    private List<String> validateGeneratedSources(Path stageWorkspace) {
        List<String> errors = new ArrayList<>();
        if (!Files.isDirectory(stageWorkspace)) {
            errors.add("No generated sources found in " + stageWorkspace);
            return errors;
        }
        try (Stream<Path> files = Files.walk(stageWorkspace)) {
            files.filter(file -> file.toString().endsWith(".java")).forEach(file -> {
                try {
                    String content = Files.readString(file);
                    if (!content.contains("package ")) {
                        errors.add(file.getFileName() + ": missing package declaration");
                    }
                    long open = content.chars().filter(c -> c == '{').count();
                    long close = content.chars().filter(c -> c == '}').count();
                    if (open != close) {
                        errors.add(file.getFileName() + ": unbalanced braces");
                    }
                } catch (IOException exception) {
                    errors.add(file.getFileName() + ": unreadable");
                }
            });
        } catch (IOException exception) {
            errors.add("Could not walk workspace: " + exception.getMessage());
        }
        return errors;
    }
}
