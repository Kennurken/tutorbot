package dev.kennurken.tutorbot.notification;

import dev.kennurken.tutorbot.common.config.AccountabilityProperties;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Keyboards;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.event.TaskStatusChanged;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserRepository;
import dev.kennurken.tutorbot.verification.VerificationExpired;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Translates task transitions into outbox rows: the escalation ladder when a task becomes
 * NOTIFIED (start -> reminder -> overdue), cancellation of pending nudges as soon as the user
 * acts, and the MISSED message. It runs inside the task transaction, so a task can never be
 * NOTIFIED without its reminders queued.
 */
@Component
public class TaskNotificationListener {

    private final NotificationService notifications;
    private final UserRepository users;
    private final BotMessages msg;
    private final TaskFormatter fmt;
    private final Keyboards keyboards;
    private final AccountabilityProperties props;
    private final Clock clock;

    public TaskNotificationListener(NotificationService notifications, UserRepository users, BotMessages msg,
                                    TaskFormatter fmt, Keyboards keyboards, AccountabilityProperties props, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.msg = msg;
        this.fmt = fmt;
        this.keyboards = keyboards;
        this.props = props;
        this.clock = clock;
    }

    @EventListener
    public void onTaskStatusChanged(TaskStatusChanged event) {
        Task task = event.task();
        User user = users.findById(task.getUserId()).orElse(null);
        if (user == null) {
            return;
        }
        switch (event.to()) {
            case NOTIFIED -> scheduleLadder(user, task);
            case STARTED -> {
                notifications.cancelPendingForTask(task.getId());
                // keep the ladder's OVERDUE out; the user is working
            }
            case REPORTED_DONE, SKIPPED, CANCELLED, SCHEDULED, COMPLETED -> notifications.cancelPendingForTask(task.getId());
            case MISSED -> {
                notifications.cancelPendingForTask(task.getId());
                sendMissed(user, task);
            }
            default -> {
                // no notification for other transitions
            }
        }
    }

    private void scheduleLadder(User user, Task task) {
        Instant base = task.getScheduledAt() != null ? task.getScheduledAt() : clock.instant();
        Instant now = clock.instant();
        Instant startAt = base.isBefore(now) ? now : base;
        String header = fmt.header(task);
        notifications.schedule(user.getId(), task.getId(), NotificationKind.TASK_START, startAt,
                msg.get(user, "task.start", header), keyboards.taskStart(user, task), key(task, "START"));
        notifications.schedule(user.getId(), task.getId(), NotificationKind.REMINDER, startAt.plus(props.reminderAfter()),
                msg.get(user, "task.reminder", header), keyboards.taskStart(user, task), key(task, "REMINDER"));
        notifications.schedule(user.getId(), task.getId(), NotificationKind.OVERDUE, startAt.plus(props.overdueAfter()),
                msg.get(user, "task.overdue", header, props.gracePeriod().toMinutes()), keyboards.taskStart(user, task),
                key(task, "OVERDUE"));
    }

    private void sendMissed(User user, Task task) {
        String text = task.isSystemFault()
                ? msg.get(user, "task.missed.system", fmt.header(task))
                : msg.get(user, "task.missed", fmt.header(task));
        notifications.schedule(user.getId(), task.getId(), NotificationKind.MISSED, clock.instant(), text,
                keyboards.missed(user, task), key(task, "MISSED-" + task.getRescheduleCount()));
    }

    @EventListener
    public void onVerificationExpired(VerificationExpired event) {
        Task task = event.task();
        users.findById(task.getUserId()).ifPresent(user -> notifications.schedule(user.getId(), task.getId(),
                NotificationKind.VERIFICATION_EXPIRED, clock.instant(),
                msg.get(user, "verification.expired", fmt.header(task)), keyboards.verificationPending(user, task), null));
    }

    private static String key(Task task, String kind) {
        return "task:" + task.getId() + ":" + kind + ":" + task.getRescheduleCount();
    }
}
