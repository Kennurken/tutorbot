package dev.kennurken.tutorbot.task;

import dev.kennurken.tutorbot.user.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Execution streak: consecutive local days (ending today or yesterday) with at least one completed task. */
public final class Streaks {

    private Streaks() {
    }

    public static int consecutiveDays(User user, List<Task> recent, LocalDate today) {
        Set<LocalDate> days = recent.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED && t.getCompletedAt() != null)
                .map(t -> user.today(t.getCompletedAt()))
                .collect(Collectors.toSet());
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
