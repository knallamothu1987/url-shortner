package com.shortener.orchestration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator")
public record OrchestratorProperties(
        Llm llm,
        Retry retry,
        Tools tools,
        String codebaseRoot,
        String workspaceRoot) {

    public record Llm(String mode) {
    }

    public record Retry(int maxAttempts, long backoffMs) {
    }

    public record Tools(Maven maven) {
        public record Maven(boolean enabled, String executable, long timeoutSeconds) {
        }
    }
}
