package dev.kennurken.tutorbot.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timing rules of the accountability loop. Kept in configuration (not code) so the
 * escalation ladder can be tuned without a redeploy of logic.
 */
@ConfigurationProperties(prefix = "accountability")
public record AccountabilityProperties(
        Duration reminderAfter,
        Duration overdueAfter,
        Duration gracePeriod,
        Verification verification,
        Overload overload) {

    public record Verification(int minQuestions, int maxQuestions, Duration sessionTimeout, int minAnswerWords) {
    }

    public record Overload(int missedPerDay, int missedPerWeek) {
    }
}
