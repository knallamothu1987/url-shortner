package com.shortener.orchestration.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.shortener.orchestration.config.OrchestratorProperties;

/**
 * Executes real Maven goals (compile/test) against the target codebase when enabled.
 * Disabled by default so pipelines stay fast and deterministic in demos; gates fall back
 * to structural validation and the simulated test report.
 */
@Component
public class MavenToolRunner {

    public record ToolResult(boolean success, int exitCode, String outputTail) {
    }

    private static final Logger logger = LoggerFactory.getLogger(MavenToolRunner.class);
    private static final int OUTPUT_TAIL_LINES = 60;

    private final OrchestratorProperties properties;

    public MavenToolRunner(OrchestratorProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.tools().maven().enabled();
    }

    public ToolResult run(String goal, Path workingDirectory) {
        String executable = properties.tools().maven().executable();
        List<String> command = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            command.addAll(List.of("cmd.exe", "/c", executable));
        } else {
            command.add(executable);
        }
        command.addAll(List.of("-B", "-q", goal));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            Deque<String> tail = new ArrayDeque<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (tail.size() >= OUTPUT_TAIL_LINES) {
                        tail.removeFirst();
                    }
                    tail.addLast(line);
                }
            }
            boolean finished = process.waitFor(
                    properties.tools().maven().timeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(false, -1, "Maven timed out after "
                        + properties.tools().maven().timeoutSeconds() + "s");
            }
            int exit = process.exitValue();
            return new ToolResult(exit == 0, exit, String.join("\n", tail));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new ToolResult(false, -1, "Maven execution interrupted");
        } catch (Exception exception) {
            logger.warn("Maven tool execution failed", exception);
            return new ToolResult(false, -1, "Maven execution failed: " + exception.getMessage());
        }
    }
}
