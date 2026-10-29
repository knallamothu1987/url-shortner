package com.shortener.orchestration.agents;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic, keyless mode: the heuristic baseline is the agent output. */
@Component
@ConditionalOnProperty(name = "orchestrator.llm.mode", havingValue = "stub", matchIfMissing = true)
public class StubLlmClient implements LlmClient {

    @Override
    public String enrich(String systemPrompt, String userPrompt, String deterministicJson) {
        return deterministicJson;
    }
}
