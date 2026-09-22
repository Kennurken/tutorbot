package dev.kennurken.tutorbot.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kennurken.tutorbot.ai.AiProperties;
import dev.kennurken.tutorbot.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientAiProviderTest {

    private static final AiRequest REQUEST = new AiRequest("test", "sys", "user", 100, 0.0, true, null);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-21T10:00:00Z"));
    private final List<String> modelsUsed = new ArrayList<>();

    private AiProperties props(int maxRetries, String fallback, int threshold) {
        return new AiProperties("gateway", "http://x", "k", "primary", fallback, Duration.ofSeconds(5), maxRetries,
                new AiProperties.CircuitBreaker(threshold, Duration.ofMinutes(2)),
                new AiProperties.RateLimit(10), new AiProperties.Pricing(0.2, 0.5));
    }

    private AiProvider failing(int failuresBeforeSuccess, boolean retryable) {
        AtomicInteger calls = new AtomicInteger();
        return new AiProvider() {
            @Override
            public AiResponse complete(AiRequest request) {
                modelsUsed.add(request.modelOverride() == null ? "primary" : request.modelOverride());
                if (calls.incrementAndGet() <= failuresBeforeSuccess) {
                    throw new AiException("boom", retryable);
                }
                return new AiResponse("{}", "m", 1, 1, 1);
            }

            @Override
            public String name() {
                return "stub";
            }
        };
    }

    @Test
    void retriesRetryableFailuresThenSucceeds() {
        ResilientAiProvider p = new ResilientAiProvider(failing(2, true), props(2, "", 10), clock, millis -> { });
        assertThat(p.complete(REQUEST).content()).isEqualTo("{}");
        assertThat(modelsUsed).hasSize(3);
    }

    @Test
    void lastAttemptUsesFallbackModel() {
        ResilientAiProvider p = new ResilientAiProvider(failing(2, true), props(2, "cheap", 10), clock, millis -> { });
        p.complete(REQUEST);
        assertThat(modelsUsed).containsExactly("primary", "primary", "cheap");
    }

    @Test
    void nonRetryableFailureIsNotRetried() {
        ResilientAiProvider p = new ResilientAiProvider(failing(5, false), props(3, "", 10), clock, millis -> { });
        assertThatThrownBy(() -> p.complete(REQUEST)).isInstanceOf(AiException.class);
        assertThat(modelsUsed).hasSize(1);
    }

    @Test
    void circuitOpensAfterThresholdAndClosesAfterCooldown() {
        ResilientAiProvider p = new ResilientAiProvider(failing(100, true), props(0, "", 2), clock, millis -> { });
        assertThatThrownBy(() -> p.complete(REQUEST)).isInstanceOf(AiException.class);
        assertThatThrownBy(() -> p.complete(REQUEST)).isInstanceOf(AiException.class);
        int callsBefore = modelsUsed.size();

        assertThatThrownBy(() -> p.complete(REQUEST)).hasMessageContaining("circuit open");
        assertThat(modelsUsed).hasSize(callsBefore);

        clock.advance(Duration.ofMinutes(3));
        assertThatThrownBy(() -> p.complete(REQUEST)).hasMessage("boom");
        assertThat(modelsUsed).hasSize(callsBefore + 1);
    }
}
