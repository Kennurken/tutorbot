package dev.kennurken.tutorbot.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable {@link Clock} instead of {@code Instant.now()} scattered around.
 * Tests replace it with a fixed clock, which makes time-dependent logic deterministic.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
