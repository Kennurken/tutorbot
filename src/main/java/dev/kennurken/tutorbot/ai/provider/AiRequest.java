package dev.kennurken.tutorbot.ai.provider;

/**
 * Provider-agnostic request. One system prompt + one user prompt is enough for every use case
 * in this bot; conversation history is embedded in the user prompt as text, which keeps
 * context under our control (see docs/AI.md, "token policy").
 */
public record AiRequest(
        String purpose,
        String systemPrompt,
        String userPrompt,
        int maxTokens,
        double temperature,
        boolean jsonMode,
        String modelOverride) {

    public AiRequest withModel(String model) {
        return new AiRequest(purpose, systemPrompt, userPrompt, maxTokens, temperature, jsonMode, model);
    }
}
