package dev.kennurken.tutorbot.task;

import static dev.kennurken.tutorbot.task.TaskStatus.CANCELLED;
import static dev.kennurken.tutorbot.task.TaskStatus.COMPLETED;
import static dev.kennurken.tutorbot.task.TaskStatus.CREATED;
import static dev.kennurken.tutorbot.task.TaskStatus.EXPIRED;
import static dev.kennurken.tutorbot.task.TaskStatus.FAILED;
import static dev.kennurken.tutorbot.task.TaskStatus.MISSED;
import static dev.kennurken.tutorbot.task.TaskStatus.NOTIFIED;
import static dev.kennurken.tutorbot.task.TaskStatus.PENDING_VERIFICATION;
import static dev.kennurken.tutorbot.task.TaskStatus.REPORTED_DONE;
import static dev.kennurken.tutorbot.task.TaskStatus.SCHEDULED;
import static dev.kennurken.tutorbot.task.TaskStatus.SKIPPED;
import static dev.kennurken.tutorbot.task.TaskStatus.STARTED;
import static dev.kennurken.tutorbot.task.TaskStatus.VERIFICATION_IN_PROGRESS;

import dev.kennurken.tutorbot.common.DomainException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for allowed task transitions. Services call
 * {@link #assertAllowed} before changing status, so an illegal jump (e.g. COMPLETED -> STARTED)
 * fails loudly instead of silently corrupting statistics.
 */
public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = new EnumMap<>(TaskStatus.class);

    static {
        TRANSITIONS.put(CREATED, Set.of(SCHEDULED, CANCELLED));
        TRANSITIONS.put(SCHEDULED, Set.of(SCHEDULED, NOTIFIED, STARTED, REPORTED_DONE, SKIPPED, MISSED, CANCELLED, EXPIRED));
        TRANSITIONS.put(NOTIFIED, Set.of(SCHEDULED, STARTED, REPORTED_DONE, SKIPPED, MISSED, CANCELLED, EXPIRED));
        TRANSITIONS.put(STARTED, Set.of(SCHEDULED, REPORTED_DONE, SKIPPED, MISSED, CANCELLED));
        TRANSITIONS.put(REPORTED_DONE, Set.of(PENDING_VERIFICATION, COMPLETED));
        TRANSITIONS.put(PENDING_VERIFICATION, Set.of(VERIFICATION_IN_PROGRESS, SCHEDULED, CANCELLED));
        TRANSITIONS.put(VERIFICATION_IN_PROGRESS, Set.of(COMPLETED, FAILED, PENDING_VERIFICATION));
        TRANSITIONS.put(FAILED, Set.of(PENDING_VERIFICATION, SCHEDULED, CANCELLED));
        TRANSITIONS.put(MISSED, Set.of(SCHEDULED, CANCELLED));
        TRANSITIONS.put(SKIPPED, Set.of(SCHEDULED, CANCELLED));
        TRANSITIONS.put(COMPLETED, Set.of());
        TRANSITIONS.put(CANCELLED, Set.of());
        TRANSITIONS.put(EXPIRED, Set.of(SCHEDULED));
    }

    private TaskStateMachine() {
    }

    public static boolean isAllowed(TaskStatus from, TaskStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void assertAllowed(TaskStatus from, TaskStatus to) {
        if (!isAllowed(from, to)) {
            throw new DomainException("Transition " + from + " -> " + to + " is not allowed");
        }
    }
}
