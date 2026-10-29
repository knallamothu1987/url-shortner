package com.shortener.orchestration.gates;

import com.fasterxml.jackson.databind.JsonNode;
import com.shortener.orchestration.config.OrchestratorProperties;
import com.shortener.orchestration.dag.GateBinding;
import com.shortener.orchestration.tools.MavenToolRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Test verification: the test-report artifact must show zero failures and coverage of at
 * least 80%. When the Maven tool is enabled, {@code mvn test} is additionally executed
 * against the target codebase.
 */
@Component
public class TestGate implements GateEvaluator {

    static final double MIN_COVERAGE = 0.80;

    private final MavenToolRunner maven;
    private final OrchestratorProperties properties;

    public TestGate(MavenToolRunner maven, OrchestratorProperties properties) {
        this.maven = maven;
        this.properties = properties;
    }

    @Override
    public String type() {
        return "test";
    }

    @Override
    public GateResult evaluate(GateBinding binding, GateContext context) {
        Optional<JsonNode> report = context.context()
                .latestJson(context.run().getId(), "test-report");
        if (report.isEmpty()) {
            return GateResult.fail("No test-report artifact in pipeline context");
        }
        JsonNode node = report.get();
        int failed = node.path("failed").asInt(Integer.MAX_VALUE);
        double coverage = node.path("coverage").asDouble(0.0);
        if (failed > 0) {
            return GateResult.fail(failed + " test(s) failed");
        }
        if (coverage < MIN_COVERAGE) {
            return GateResult.fail("Coverage %.0f%% is below the %.0f%% exit bar"
                    .formatted(coverage * 100, MIN_COVERAGE * 100));
        }
        if (maven.isEnabled()) {
            MavenToolRunner.ToolResult result = maven.run("test",
                    com.shortener.orchestration.tools.CodebaseContextService
                            .resolveCodebaseRoot(Path.of(properties.codebaseRoot())));
            if (!result.success()) {
                return GateResult.fail("mvn test failed (exit %d): %s"
                        .formatted(result.exitCode(), result.outputTail()));
            }
            return GateResult.pass("Test report and mvn test passed");
        }
        return GateResult.pass("Test report passed (%d failed, %.0f%% coverage)"
                .formatted(failed, coverage * 100));
    }
}
