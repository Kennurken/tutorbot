package dev.kennurken.tutorbot.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kennurken.tutorbot.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserAiRateLimiterTest {

    @Test
    void limitsPerUserPerSlidingMinute() {
        MutableClock clock = new MutableClock(Instant.parse("2026-09-21T10:00:00Z"));
        AiProperties props = new AiProperties("fake", "", "", "m", "", "", Duration.ofSeconds(1), 0,
                new AiProperties.CircuitBreaker(1, Duration.ofSeconds(1)), new AiProperties.RateLimit(2),
                new AiProperties.Pricing(0, 0));
        UserAiRateLimiter limiter = new UserAiRateLimiter(props, clock);

        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isTrue();
        assertThat(limiter.tryAcquire(1L)).isFalse();
        assertThat(limiter.tryAcquire(2L)).isTrue();

        clock.advance(Duration.ofSeconds(61));
        assertThat(limiter.tryAcquire(1L)).isTrue();
    }
}
