package dev.kennurken.tutorbot.task;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Set;

/**
 * Everything needed to create a task, produced by the command parser, the natural-language
 * parser or the AI intent extractor. Validated by {@link TaskService}, never trusted blindly.
 */
public record CreateTaskCommand(
        String title,
        String description,
        String subject,
        String topic,
        TaskType type,
        Priority priority,
        Instant scheduledAt,
        int estimatedMinutes,
        Instant deadlineAt,
        boolean verificationRequired,
        Long goalId,
        Recurrence recurrence) {

    public record Recurrence(Set<DayOfWeek> days, LocalTime timeOfDay) {
    }

    public boolean isRecurring() {
        return recurrence != null && recurrence.days() != null && !recurrence.days().isEmpty();
    }

    public static CreateTaskCommand simple(String title, String subject, TaskType type, Instant scheduledAt, int minutes) {
        return new CreateTaskCommand(title, null, subject, null, type, Priority.MEDIUM, scheduledAt, minutes,
                null, true, null, null);
    }
}
