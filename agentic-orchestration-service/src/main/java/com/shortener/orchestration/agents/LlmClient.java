package com.shortener.orchestration.agents;

/**
 * LLM abstraction used by agents. Agents always compute a deterministic baseline output;
 * in {@code openai} mode the LLM may enrich/replace it, in {@code stub} mode the baseline
 * is used as-is. This keeps the pipeline runnable, testable, and demoable without keys,
 * while providing a single integration point for a real model.
 */
public interface LlmClient {

    /**
     * @param systemPrompt      role instruction for the agent
     * @param userPrompt        task input
     * @param deterministicJson heuristic baseline output (also the fallback)
     * @return a JSON document honoring the agent's output contract
     */
    String enrich(String systemPrompt, String userPrompt, String deterministicJson);
}
