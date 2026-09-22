package dev.kennurken.tutorbot.ai.provider;

public record AiResponse(String content, String model, Integer inputTokens, Integer outputTokens, long latencyMs) {
}
