package dev.kennurken.tutorbot.planning;

import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Heuristic day planner. It does not create tasks; it ranks what is already scheduled for the
 * day and says which subset fits the user's limits (task count, study minutes, recovery mode).
 * Score = priority weight * 10 + deadline urgency + a small bonus for short tasks, so a
 * CRITICAL 20-minute task always beats an OPTIONAL 90-minute one.
 */
public final class DailyPlanner {

    static final int RECOVERY_TASK_LIMIT = 1;
    static final int RECOVERY_MINUTES_LIMIT = 30;

    private DailyPlanner() {
    }

    public record DayPlan(List<Task> all, List<Task> recommended, List<Task> deferred, int totalMinutes,
                          int recommendedMinutes, boolean overloaded, boolean recovery) {
    }

    public static DayPlan plan(User user, List<Task> dayTasks, Instant now) {
        boolean recovery = user.isInRecoveryMode(now);
        int taskLimit = recovery ? RECOVERY_TASK_LIMIT : user.getSettings().getDailyTaskLimit();
        int minuteLimit = recovery ? RECOVERY_MINUTES_LIMIT : user.getSettings().getMaxDailyStudyMinutes();

        List<Task> open = dayTasks.stream()
                .filter(t -> TaskStatus.ACTIVE.contains(t.getStatus()) || t.getStatus() == TaskStatus.PENDING_VERIFICATION)
                .toList();
        int total = open.stream().mapToInt(Task::effectiveMinutes).sum();

        List<Task> ranked = new ArrayList<>(open);
        ranked.sort(Comparator.comparingDouble((Task t) -> score(t, now)).reversed()
                .thenComparing(t -> t.getScheduledAt() == null ? Instant.MAX : t.getScheduledAt()));

        List<Task> recommended = new ArrayList<>();
        List<Task> deferred = new ArrayList<>();
        int minutes = 0;
        for (Task t : ranked) {
            boolean fits = recommended.size() < taskLimit && minutes + t.effectiveMinutes() <= minuteLimit;
            if (fits || t.getPriority() == Priority.CRITICAL && recommended.size() < taskLimit) {
                recommended.add(t);
                minutes += t.effectiveMinutes();
            } else {
                deferred.add(t);
            }
        }
        recommended.sort(Comparator.comparing(t -> t.getScheduledAt() == null ? Instant.MAX : t.getScheduledAt()));
        boolean overloaded = !deferred.isEmpty();
        return new DayPlan(dayTasks, recommended, deferred, total, minutes, overloaded, recovery);
    }

    static double score(Task t, Instant now) {
        double score = t.getPriority().weight() * 10.0;
        if (t.getDeadlineAt() != null) {
            long hours = Duration.between(now, t.getDeadlineAt()).toHours();
            if (hours <= 24) {
                score += 15;
            } else if (hours <= 72) {
                score += 8;
            }
        }
        if (t.effectiveMinutes() <= 30) {
            score += 3;
        }
        if (t.getRecurrenceRuleId() != null) {
            score += 2;
        }
        return score;
    }
}
