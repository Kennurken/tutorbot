package dev.kennurken.tutorbot.consequence;

import dev.kennurken.tutorbot.common.config.AccountabilityProperties;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.notification.NotificationKind;
import dev.kennurken.tutorbot.notification.NotificationService;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.task.event.Actor;
import dev.kennurken.tutorbot.task.event.TaskEventRecorder;
import dev.kennurken.tutorbot.task.event.TaskEventType;
import dev.kennurken.tutorbot.task.event.TaskStatusChanged;
import dev.kennurken.tutorbot.user.ConsequencePolicy;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserService;
import dev.kennurken.tutorbot.verification.SessionStatus;
import dev.kennurken.tutorbot.verification.VerificationFinished;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Applies the consequences the user agreed to, within hard bounds:
 * <ul>
 *   <li>everything is off when the policy is disabled, the user is paused, or in recovery mode;</li>
 *   <li>extra minutes are capped by the policy and applied at most once per target task;</li>
 *   <li>repeated misses do not stack (+10, +20, +30 ...): they trip overload detection instead,
 *       which switches the user into a 24-hour recovery protocol.</li>
 * </ul>
 * All consequences are reversible (rescheduling resets extra minutes; a review task can be cancelled).
 */
@Component
public class ConsequenceEngine {

    private static final Logger log = LoggerFactory.getLogger(ConsequenceEngine.class);
    private static final Duration RECOVERY_DURATION = Duration.ofHours(24);
    private static final int REVIEW_TASK_MINUTES = 20;

    private final ConsequenceRepository consequences;
    private final TaskService taskService;
    private final UserService userService;
    private final NotificationService notifications;
    private final TaskEventRecorder events;
    private final BotMessages msg;
    private final TaskFormatter fmt;
    private final AccountabilityProperties props;
    private final Clock clock;

    public ConsequenceEngine(ConsequenceRepository consequences, TaskService taskService, UserService userService,
                             NotificationService notifications, TaskEventRecorder events, BotMessages msg,
                             TaskFormatter fmt, AccountabilityProperties props, Clock clock) {
        this.consequences = consequences;
        this.taskService = taskService;
        this.userService = userService;
        this.notifications = notifications;
        this.events = events;
        this.msg = msg;
        this.fmt = fmt;
        this.props = props;
        this.clock = clock;
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChanged event) {
        if (event.to() == TaskStatus.MISSED && !event.task().isSystemFault()) {
            onMissed(event.task());
        }
    }

    @EventListener
    public void onVerificationFinished(VerificationFinished event) {
        if (event.outcome() == SessionStatus.FAILED) {
            onFailed(event.task());
        }
    }

    void onMissed(Task task) {
        User user = userService.get(task.getUserId());
        Instant now = clock.instant();
        ConsequencePolicy policy = user.getConsequencePolicy();
        if (!policy.isEnabled() || user.isPaused(now) || user.isInRecoveryMode(now)) {
            return;
        }
        if (detectOverload(user, task, now)) {
            return;
        }
        Optional<Task> next = taskService.findNextScheduledForSubject(user, task.subjectOrTitle());
        if (next.isEmpty() || consequences.existsByTargetTaskIdAndType(next.get().getId(), ConsequenceType.EXTRA_MINUTES)) {
            return;
        }
        Task target = next.get();
        int room = policy.getMaxExtraMinutes() - target.getExtraMinutes();
        int minutes = Math.min(policy.getMissedExtraMinutes(), room);
        if (minutes <= 0) {
            return;
        }
        target.setExtraMinutes(target.getExtraMinutes() + minutes);
        taskService.save(target);
        String reason = "Task #" + task.getId() + " missed; policy adds " + minutes + " min to the next "
                + task.subjectOrTitle() + " session (cap " + policy.getMaxExtraMinutes() + ")";
        consequences.save(new Consequence(user.getId(), task.getId(), target.getId(), ConsequenceType.EXTRA_MINUTES,
                minutes, reason, now));
        events.record(target, TaskEventType.CONSEQUENCE_APPLIED, Actor.SYSTEM,
                Map.of("type", "EXTRA_MINUTES", "minutes", minutes, "sourceTaskId", task.getId()));
        notifications.schedule(user.getId(), target.getId(), NotificationKind.CONSEQUENCE, now,
                msg.get(user, "consequence.extra", minutes, fmt.header(target)), null, null);
        log.info("Consequence EXTRA_MINUTES {} applied to task {} (source {})", minutes, target.getId(), task.getId());
    }

    private boolean detectOverload(User user, Task task, Instant now) {
        long missedDay = taskService.countMissedSince(user, now.minus(Duration.ofDays(1)));
        long missedWeek = taskService.countMissedSince(user, now.minus(Duration.ofDays(7)));
        boolean overloaded = missedDay >= props.overload().missedPerDay() || missedWeek >= props.overload().missedPerWeek();
        if (!overloaded) {
            return false;
        }
        if (consequences.existsByUserIdAndTypeAndAppliedAtAfter(user.getId(), ConsequenceType.OVERLOAD_DETECTED,
                now.minus(Duration.ofDays(1)))) {
            return true;
        }
        String reason = "Missed " + missedDay + " in 24h / " + missedWeek + " in 7d: recovery mode for 24h";
        consequences.save(new Consequence(user.getId(), task.getId(), null, ConsequenceType.OVERLOAD_DETECTED, null,
                reason, now));
        userService.enterRecoveryMode(user, RECOVERY_DURATION);
        notifications.schedule(user.getId(), null, NotificationKind.OVERLOAD, now,
                msg.get(user, "consequence.overload", missedDay, missedWeek), null, null);
        log.info("Overload detected for user {}: {}", user.getId(), reason);
        return true;
    }

    void onFailed(Task task) {
        User user = userService.get(task.getUserId());
        Instant now = clock.instant();
        ConsequencePolicy policy = user.getConsequencePolicy();
        if (!policy.isEnabled() || !policy.isReviewTaskOnFail() || user.isInRecoveryMode(now)
                || consequences.existsBySourceTaskIdAndType(task.getId(), ConsequenceType.REVIEW_TASK)) {
            return;
        }
        ZonedDateTime base = (task.getScheduledAt() != null ? task.getScheduledAt() : now).atZone(user.zone());
        Instant reviewAt = base.plusDays(1).toInstant();
        if (reviewAt.isBefore(now)) {
            reviewAt = now.plus(Duration.ofHours(20));
        }
        String title = msg.get(user, "consequence.review.title", task.getTitle());
        Task review = taskService.create(user, new CreateTaskCommand(title, task.getDescription(), task.getSubject(),
                task.getTopic(), task.getType(), Priority.MEDIUM, reviewAt, REVIEW_TASK_MINUTES, null, true,
                task.getGoalId(), null));
        consequences.save(new Consequence(user.getId(), task.getId(), review.getId(), ConsequenceType.REVIEW_TASK,
                REVIEW_TASK_MINUTES, "Verification of task #" + task.getId() + " failed; review scheduled", now));
        events.record(review, TaskEventType.CONSEQUENCE_APPLIED, Actor.SYSTEM,
                Map.of("type", "REVIEW_TASK", "sourceTaskId", task.getId()));
        notifications.schedule(user.getId(), review.getId(), NotificationKind.CONSEQUENCE, now,
                msg.get(user, "consequence.review", fmt.header(review)), null, null);
    }
}
