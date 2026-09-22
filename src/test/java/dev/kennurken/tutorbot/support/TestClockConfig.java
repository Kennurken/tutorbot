package dev.kennurken.tutorbot.support;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

    /** Monday 2026-09-21 10:00 UTC (15:00 in Asia/Almaty). */
    public static final Instant START = Instant.parse("2026-09-21T10:00:00Z");

    /** Wins over the production Clock bean for every injection point of type Clock. */
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock(START);
    }
}
