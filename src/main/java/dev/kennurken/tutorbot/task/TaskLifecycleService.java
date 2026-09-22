package dev.kennurken.tutorbot.task;

import dev.kennurken.tutorbot.common.config.AccountabilityProperties;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Time-driven transitions, executed by the scheduler tick. Everything is derived from the
 * database state and "now", never from in-memory timers, which is what makes a restart (or
 * a second instance) harmless: the next tick simply re-evaluates what is due.
 */
@Service
public class TaskLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(TaskLifecycleService.class);

    private final TaskRepository tasks;
    private final TaskService taskService;
    private final UserRepository users;
    private final AccountabilityProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TaskLifecycleService(TaskRepository tasks, TaskService taskService, UserRepository users,
                                AccountabilityProperties props, TransactionTemplate tx, Clock clock) {
        this.tasks = tasks;
        this.taskService = taskService;
        this.users = users;
        this.props = props;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * SCHEDULED tasks whose time has come. If the bot was down past the grace period the task is
     * marked MISSED with {@code systemFault=true}: no consequence, the user is offered a reschedule.
     */
    public int notifyDue() {
        Instant now = clock.instant();
        int count = 0;
        for (Task candidate : tasks.findDueForNotification(now)) {
            count += Boolean.TRUE.equals(tx.execute(status -> {
                Task task = tasks.findById(candidate.getId()).orElse(null);
                if (task == null || task.getStatus() != TaskStatus.SCHEDULED) {
                    return false;
                }
                User user = users.findById(task.getUserId()).orElse(null);
                if (user == null) {
                    return false;
                }
                Instant graceEnd = task.getScheduledAt().plus(props.gracePeriod());
                if (user.isPaused(now)) {
                    if (graceEnd.isBefore(now)) {
                        taskService.markMissed(task, true, "paused");
                        return true;
                    }
                    return false;
                }
                if (graceEnd.isBefore(now)) {
                    taskService.markMissed(task, true, "downtime");
                } else {
                    taskService.markNotified(task);
                }
                return true;
            })) ? 1 : 0;
        }
        return count;
    }

    /** NOTIFIED tasks that were never started within the grace period. */
    public int markMissedAfterGrace() {
        Instant threshold = clock.instant().minus(props.gracePeriod());
        int count = 0;
        for (Task candidate : tasks.findNotifiedNotStartedBefore(threshold)) {
            count += Boolean.TRUE.equals(tx.execute(status -> {
                Task task = tasks.findById(candidate.getId()).orElse(null);
                if (task == null || task.getStatus() != TaskStatus.NOTIFIED) {
                    return false;
                }
                User user = users.findById(task.getUserId()).orElse(null);
                boolean paused = user != null && user.isPaused(clock.instant());
                taskService.markMissed(task, paused, paused ? "paused" : "grace_period_expired");
                return true;
            })) ? 1 : 0;
        }
        return count;
    }

    /** Yesterday's tasks that were started (or notified) and never reported: closed as MISSED at day start. */
    public int sweepUnresolved(User user, LocalDate today) {
        Instant startOfToday = today.atStartOfDay(user.zone()).toInstant();
        List<Task> unresolved = tasks.findUnresolvedBefore(user.getId(), startOfToday);
        int count = 0;
        for (Task candidate : unresolved) {
            try {
                tx.executeWithoutResult(status -> {
                    Task task = tasks.findById(candidate.getId()).orElseThrow();
                    if (task.getStatus() == TaskStatus.NOTIFIED || task.getStatus() == TaskStatus.STARTED) {
                        taskService.markMissed(task, false, "not_reported_by_end_of_day");
                    }
                });
                count++;
            } catch (RuntimeException e) {
                log.error("Failed to sweep task {}", candidate.getId(), e);
            }
        }
        return count;
    }
}
