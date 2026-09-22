package dev.kennurken.tutorbot.scheduling;

import dev.kennurken.tutorbot.notification.NotificationService;
import dev.kennurken.tutorbot.task.RecurrenceService;
import dev.kennurken.tutorbot.task.TaskLifecycleService;
import dev.kennurken.tutorbot.verification.VerificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One tick = one pass over everything time-driven. Steps are independent and each is
 * isolated in its own try/catch, so a failing step (say Telegram is down) never blocks the
 * others (say materialising tomorrow's recurring tasks). A single-instance lock prevents
 * overlapping ticks from the internal scheduler and the external cron ping.
 */
@Service
public class TickService {

    private static final Logger log = LoggerFactory.getLogger(TickService.class);

    private final RecurrenceService recurrence;
    private final TaskLifecycleService lifecycle;
    private final VerificationService verification;
    private final DailyRoutineService dailyRoutine;
    private final NotificationService notifications;
    private final SchedulerProperties props;
    private final ReentrantLock lock = new ReentrantLock();

    public TickService(RecurrenceService recurrence, TaskLifecycleService lifecycle, VerificationService verification,
                       DailyRoutineService dailyRoutine, NotificationService notifications, SchedulerProperties props) {
        this.recurrence = recurrence;
        this.lifecycle = lifecycle;
        this.verification = verification;
        this.dailyRoutine = dailyRoutine;
        this.notifications = notifications;
        this.props = props;
    }

    /** @return per-step counts, or an empty map if another tick was already running. */
    public Map<String, Integer> runTick() {
        if (!lock.tryLock()) {
            log.debug("Tick skipped: previous tick still running");
            return Map.of();
        }
        try {
            Map<String, Integer> result = new LinkedHashMap<>();
            step(result, "recurrence", () -> recurrence.materialize(props.recurrenceLookahead()));
            step(result, "notifyDue", lifecycle::notifyDue);
            step(result, "missed", lifecycle::markMissedAfterGrace);
            step(result, "verificationExpired", verification::expireStale);
            step(result, "dailyRoutine", dailyRoutine::run);
            step(result, "dispatched", notifications::dispatchDue);
            if (result.values().stream().anyMatch(v -> v > 0)) {
                log.info("Tick: {}", result);
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private static void step(Map<String, Integer> result, String name, IntSupplier action) {
        try {
            result.put(name, action.getAsInt());
        } catch (RuntimeException e) {
            log.error("Tick step '{}' failed", name, e);
            result.put(name, -1);
        }
    }
}
