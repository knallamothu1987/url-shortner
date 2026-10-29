package com.shortener.orchestration.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Real-LLM mode. The model is asked to refine the deterministic baseline while honoring
 * the same JSON contract; any invalid response degrades gracefully to the baseline.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.llm.mode", havingValue = "openai")
public class OpenAiLlmClient implements LlmClient {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public OpenAiLlmClient(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String enrich(String systemPrompt, String userPrompt, String deterministicJson) {
        try {
            String response = chatClient.prompt()
                    .system(systemPrompt + "\nRespond ONLY with a JSON document matching the "
                            + "structure of the baseline. No markdown fences, no prose.")
                    .user(userPrompt + "\n\nBaseline JSON to refine:\n" + deterministicJson)
                    .call()
                    .content();
            String cleaned = stripFences(response);
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception exception) {
            logger.warn("LLM enrichment failed, using deterministic baseline", exception);
            return deterministicJson;
        }
    }

    private String stripFences(String response) {
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }
}
