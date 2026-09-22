package dev.kennurken.tutorbot.scheduling;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scheduler")
public record SchedulerProperties(boolean enabled, Duration tickInterval, String tickSecret, Duration recurrenceLookahead) {

    public boolean hasTickSecret() {
        return tickSecret != null && !tickSecret.isBlank();
    }
}
