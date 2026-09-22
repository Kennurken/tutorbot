package dev.kennurken.tutorbot.planning;

import dev.kennurken.tutorbot.ai.AiTutorService;
import dev.kennurken.tutorbot.ai.dto.TaskIntentResult;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.TaskType;
import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * "Turn this message into a task": deterministic parser first, model second. The model's
 * JSON is mapped through the same {@link CreateTaskCommand} the command parser produces, so
 * validation and state handling downstream do not care where the command came from.
 */
@Service
public class TaskIntentService {

    private static final double MIN_CONFIDENCE = 0.6;
    private static final int DEFAULT_MINUTES = 30;

    private final AiTutorService ai;
    private final Clock clock;

    public TaskIntentService(AiTutorService ai, Clock clock) {
        this.ai = ai;
        this.clock = clock;
    }

    public Optional<CreateTaskCommand> interpret(User user, String text) {
        Instant now = clock.instant();
        Optional<CreateTaskCommand> parsed = NaturalLanguageTaskParser.parse(text, now, user.zone());
        if (parsed.isPresent()) {
            return parsed;
        }
        return ai.parseTaskIntent(user, text, now).flatMap(result -> fromModel(user, result));
    }

    Optional<CreateTaskCommand> fromModel(User user, TaskIntentResult result) {
        if ("UNKNOWN".equals(result.intent()) || result.task() == null || result.confidence() < MIN_CONFIDENCE) {
            return Optional.empty();
        }
        TaskIntentResult.TaskDraft d = result.task();
        if (d.title() == null || d.title().isBlank()) {
            return Optional.empty();
        }
        TaskType type = enumOr(TaskType.class, d.type(), TaskType.THEORY);
        Priority priority = enumOr(Priority.class, d.priority(), Priority.MEDIUM);
        int minutes = d.durationMinutes() == null || d.durationMinutes() <= 0 ? DEFAULT_MINUTES : d.durationMinutes();
        boolean verification = type != TaskType.OTHER;

        if ("CREATE_RECURRING".equals(result.intent())) {
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            if (d.recurrenceDays() != null) {
                for (String day : d.recurrenceDays()) {
                    try {
                        days.add(DayOfWeek.valueOf(day.trim().toUpperCase()));
                    } catch (IllegalArgumentException ignored) {
                        // unknown day name from the model: skip it
                    }
                }
            }
            LocalTime time = parseTime(d.recurrenceTime());
            if (days.isEmpty() || time == null) {
                return Optional.empty();
            }
            return Optional.of(new CreateTaskCommand(d.title().trim(), null, d.subject(), d.topic(), type, priority,
                    null, minutes, null, verification, null, new CreateTaskCommand.Recurrence(days, time)));
        }

        Instant scheduledAt = null;
        if (d.scheduledAtLocal() != null && !d.scheduledAtLocal().isBlank()) {
            try {
                scheduledAt = LocalDateTime.parse(d.scheduledAtLocal().trim()).atZone(user.zone()).toInstant();
            } catch (DateTimeParseException e) {
                return Optional.empty();
            }
        }
        Instant deadlineAt = null;
        if (d.deadlineLocal() != null && !d.deadlineLocal().isBlank()) {
            try {
                deadlineAt = java.time.LocalDate.parse(d.deadlineLocal().trim()).plusDays(1)
                        .atStartOfDay(user.zone()).toInstant().minusSeconds(60);
            } catch (DateTimeParseException ignored) {
                // a bad deadline should not block the task
            }
        }
        return Optional.of(new CreateTaskCommand(d.title().trim(), null, d.subject(), d.topic(), type, priority,
                scheduledAt, minutes, deadlineAt, verification, null, null));
    }

    private static LocalTime parseTime(String hhmm) {
        if (hhmm == null) {
            return null;
        }
        try {
            return LocalTime.parse(hhmm.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumOr(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
