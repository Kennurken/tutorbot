package dev.kennurken.tutorbot.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** Structured output of the natural-language task parser prompt. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskIntentResult(
        @NotNull @Pattern(regexp = "CREATE_TASK|CREATE_RECURRING|UNKNOWN") String intent,
        @DecimalMin("0") @DecimalMax("1") double confidence,
        @Valid TaskDraft task) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskDraft(
            String title,
            String subject,
            String topic,
            @Pattern(regexp = "THEORY|PROGRAMMING|LANGUAGE|MATH|READING|PROJECT|OTHER") String type,
            @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW|OPTIONAL") String priority,
            /** ISO local date-time in the user's zone, e.g. 2026-09-22T19:00 */
            String scheduledAtLocal,
            Integer durationMinutes,
            /** Upper-case DayOfWeek names for recurring tasks */
            List<String> recurrenceDays,
            /** HH:mm for recurring tasks */
            String recurrenceTime) {
    }
}
