package dev.kennurken.tutorbot.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        String provider,
        String baseUrl,
        String apiKey,
        String model,
        String fallbackModel,
        /** low | medium | high for reasoning models (Groq gpt-oss); empty = omit the parameter. */
        String reasoningEffort,
        Duration timeout,
        int maxRetries,
        CircuitBreaker circuitBreaker,
        RateLimit rateLimit,
        Pricing pricing) {

    public record CircuitBreaker(int failureThreshold, Duration openDuration) {
    }

    public record RateLimit(int perUserPerMinute) {
    }

    /** USD per one million tokens. Only used for cost estimates, never for billing. */
    public record Pricing(double inputPerMillion, double outputPerMillion) {
    }

    public boolean hasReasoningEffort() {
        return reasoningEffort != null && !reasoningEffort.isBlank();
    }

    public boolean hasFallbackModel() {
        return fallbackModel != null && !fallbackModel.isBlank();
    }
}
