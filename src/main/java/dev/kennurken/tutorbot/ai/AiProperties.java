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

    public boolean hasFallbackModel() {
        return fallbackModel != null && !fallbackModel.isBlank();
    }
}
