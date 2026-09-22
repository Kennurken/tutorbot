package dev.kennurken.tutorbot.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kennurken.tutorbot.planning.DailyPlanner;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskTestAccess;
import dev.kennurken.tutorbot.task.TaskType;
import dev.kennurken.tutorbot.user.User;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DailyPlannerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");

    private static User user(int limit, int minutes) {
        User u = new User(1L, 1L, "t", "t", "en", "UTC");
        u.getSettings().setDailyTaskLimit(limit);
        u.getSettings().setMaxDailyStudyMinutes(minutes);
        return u;
    }

    private static Task task(String title, Priority priority, int minutes) {
        Task t = new Task(1L, title, TaskType.THEORY, priority, minutes, true);
        return t;
    }

    @Test
    void keepsPlanWithinLimitsAndDefersLowPriorityFirst() {
        User u = user(3, 90);
        // schedule() is package-private on Task; planner only needs ACTIVE statuses, use reflection-free path:
        List<Task> tasks = List.of(
                scheduled(task("Critical thing", Priority.CRITICAL, 60)),
                scheduled(task("Optional reading", Priority.OPTIONAL, 40)),
                scheduled(task("High java", Priority.HIGH, 30)),
                scheduled(task("Low chores", Priority.LOW, 20)));

        DailyPlanner.DayPlan plan = DailyPlanner.plan(u, tasks, NOW);

        assertThat(plan.overloaded()).isTrue();
        assertThat(plan.recommended()).extracting(Task::getTitle).containsExactlyInAnyOrder("Critical thing", "High java");
        assertThat(plan.recommendedMinutes()).isEqualTo(90);
        assertThat(plan.deferred()).extracting(Task::getTitle).containsExactlyInAnyOrder("Optional reading", "Low chores");
    }

    @Test
    void recoveryModeShrinksTheDayToOneSmallTask() {
        User u = user(6, 180);
        u.setRecoveryModeUntil(NOW.plusSeconds(3600));
        List<Task> tasks = List.of(
                scheduled(task("Big", Priority.HIGH, 60)),
                scheduled(task("Small", Priority.MEDIUM, 20)));

        DailyPlanner.DayPlan plan = DailyPlanner.plan(u, tasks, NOW);

        assertThat(plan.recovery()).isTrue();
        assertThat(plan.recommended()).extracting(Task::getTitle).containsExactly("Small");
    }

    private static Task scheduled(Task t) {
        // tasks are created CREATED; the planner only looks at ACTIVE statuses
        TaskTestAccess.markScheduled(t, NOW.plusSeconds(3600));
        return t;
    }
}
