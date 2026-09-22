package dev.kennurken.tutorbot.task;

import java.util.Set;

/**
 * Lifecycle of a task. RESCHEDULED is deliberately an event, not a state: a rescheduled task is
 * simply SCHEDULED again with {@code rescheduleCount + 1}. COMPLETED is the single success state;
 * {@link VerificationStatus} says whether it was verified or verification was not required.
 */
public enum TaskStatus {
    CREATED,
    SCHEDULED,
    NOTIFIED,
    STARTED,
    REPORTED_DONE,
    PENDING_VERIFICATION,
    VERIFICATION_IN_PROGRESS,
    COMPLETED,
    FAILED,
    MISSED,
    SKIPPED,
    CANCELLED,
    EXPIRED;

    /** States in which the user is expected to act on the task today. */
    public static final Set<TaskStatus> ACTIVE = Set.of(SCHEDULED, NOTIFIED, STARTED);

    /** States that still allow /done. */
    public static final Set<TaskStatus> REPORTABLE = Set.of(SCHEDULED, NOTIFIED, STARTED);

    public static final Set<TaskStatus> TERMINAL = Set.of(COMPLETED, CANCELLED, EXPIRED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
