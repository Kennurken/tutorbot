package dev.kennurken.tutorbot.scheduling;

import dev.kennurken.tutorbot.notification.NotificationKind;
import dev.kennurken.tutorbot.notification.NotificationService;
import dev.kennurken.tutorbot.planning.DailyPlanner;
import dev.kennurken.tutorbot.planning.PlanRenderer;
import dev.kennurken.tutorbot.review.WeeklyReview;
import dev.kennurken.tutorbot.review.WeeklyReviewService;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskLifecycleService;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Per-user, per-local-day routines: the morning plan (which also closes yesterday's loose
 * ends) and the Sunday-evening weekly review. Idempotent via users.last_morning_plan_date and
 * the (user, week_start) uniqueness of weekly reviews.
 */
@Service
public class DailyRoutineService {

    private static final Logger log = LoggerFactory.getLogger(DailyRoutineService.class);
    private static final LocalTime WEEKLY_REVIEW_TIME = LocalTime.of(20, 0);

    private final UserService userService;
    private final TaskService taskService;
    private final TaskLifecycleService lifecycle;
    private final NotificationService notifications;
    private final PlanRenderer planRenderer;
    private final WeeklyReviewService weeklyReviews;
    private final Clock clock;

    public DailyRoutineService(UserService userService, TaskService taskService, TaskLifecycleService lifecycle,
                               NotificationService notifications, PlanRenderer planRenderer,
                               WeeklyReviewService weeklyReviews, Clock clock) {
        this.userService = userService;
        this.taskService = taskService;
        this.lifecycle = lifecycle;
        this.notifications = notifications;
        this.planRenderer = planRenderer;
        this.weeklyReviews = weeklyReviews;
        this.clock = clock;
    }

    public int run() {
        int actions = 0;
        Instant now = clock.instant();
        for (User user : userService.findAll()) {
            try {
                ZonedDateTime local = now.atZone(user.zone());
                if (dueMorningPlan(user, local)) {
                    morningPlan(user, local.toLocalDate(), now);
                    actions++;
                }
                if (dueWeeklyReview(user, local)) {
                    weeklyReview(user, local.toLocalDate());
                    actions++;
                }
            } catch (RuntimeException e) {
                log.error("Daily routine failed for user {}", user.getId(), e);
            }
        }
        return actions;
    }

    private boolean dueMorningPlan(User user, ZonedDateTime local) {
        LocalDate today = local.toLocalDate();
        return !today.equals(user.getLastMorningPlanDate())
                && !local.toLocalTime().isBefore(user.getSettings().getMorningPlanTime());
    }

    private void morningPlan(User user, LocalDate today, Instant now) {
        lifecycle.sweepUnresolved(user, today);
        List<Task> tasks = taskService.findForLocalDate(user, today);
        DailyPlanner.DayPlan plan = DailyPlanner.plan(user, tasks, now);
        if (!plan.all().isEmpty() && !user.isPaused(now)) {
            notifications.schedule(user.getId(), null, NotificationKind.MORNING_PLAN, now,
                    planRenderer.render(user, today, plan, now, true), null, "plan:" + user.getId() + ":" + today);
        }
        user.setLastMorningPlanDate(today);
        userService.save(user);
    }

    private boolean dueWeeklyReview(User user, ZonedDateTime local) {
        if (local.getDayOfWeek() != DayOfWeek.SUNDAY || local.toLocalTime().isBefore(WEEKLY_REVIEW_TIME)) {
            return false;
        }
        LocalDate weekStart = WeeklyReviewService.weekStartOf(local.toLocalDate());
        return weeklyReviews.find(user, weekStart).isEmpty();
    }

    private void weeklyReview(User user, LocalDate today) {
        LocalDate weekStart = WeeklyReviewService.weekStartOf(today);
        WeeklyReview review = weeklyReviews.generate(user, weekStart);
        String text = weeklyReviews.render(user, review);
        notifications.schedule(user.getId(), null, NotificationKind.WEEKLY_REVIEW, clock.instant(),
                text, null, "review:" + user.getId() + ":" + weekStart);
        log.info("Weekly review generated for user {} ({})", user.getId(), weekStart);
    }
}
