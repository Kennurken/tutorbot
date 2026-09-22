package dev.kennurken.tutorbot.ai.provider;

import dev.kennurken.tutorbot.ai.AiProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Retry with exponential backoff + jitter, a fallback model for the last attempt, and a
 * small circuit breaker. Hand-written instead of Resilience4j on purpose: one external
 * dependency, one policy, ~80 lines, fully unit-testable. Revisit when a second external
 * service with different policies appears.
 */
public class ResilientAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(ResilientAiProvider.class);
    private static final long BASE_BACKOFF_MS = 600;

    private final AiProvider delegate;
    private final AiProperties properties;
    private final Clock clock;
    private final Sleeper sleeper;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant openUntil = Instant.EPOCH;

    public ResilientAiProvider(AiProvider delegate, AiProperties properties, Clock clock) {
        this(delegate, properties, clock, Thread::sleep);
    }

    ResilientAiProvider(AiProvider delegate, AiProperties properties, Clock clock, Sleeper sleeper) {
        this.delegate = delegate;
        this.properties = properties;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public AiResponse complete(AiRequest request) {
        if (isOpen()) {
            throw new AiException("AI circuit open until " + openUntil, false);
        }
        int attempts = properties.maxRetries() + 1;
        AiException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            AiRequest effective = request;
            boolean lastAttempt = attempt == attempts;
            if (lastAttempt && attempt > 1 && properties.hasFallbackModel() && request.modelOverride() == null) {
                effective = request.withModel(properties.fallbackModel());
                log.info("AI: switching to fallback model {}", properties.fallbackModel());
            }
            try {
                AiResponse response = delegate.complete(effective);
                consecutiveFailures.set(0);
                return response;
            } catch (AiException e) {
                last = e;
                recordFailure();
                if (!e.isRetryable() || lastAttempt) {
                    break;
                }
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1)) + ThreadLocalRandom.current().nextLong(250);
                log.info("AI attempt {}/{} failed ({}); retrying in {} ms", attempt, attempts, e.getMessage(), backoff);
                pause(backoff);
            }
        }
        throw last;
    }

    private void pause(long millis) {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Interrupted while waiting to retry", false, e);
        }
    }

    private boolean isOpen() {
        return clock.instant().isBefore(openUntil);
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= properties.circuitBreaker().failureThreshold()) {
            openUntil = clock.instant().plus(properties.circuitBreaker().openDuration());
            consecutiveFailures.set(0);
            log.warn("AI circuit opened until {} after {} consecutive failures", openUntil, failures);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
