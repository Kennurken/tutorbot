package dev.kennurken.tutorbot.task;

import java.time.Instant;

/** Test-only door into package-private setters, so unit tests can build tasks in any state. */
public final class TaskTestAccess {

    private TaskTestAccess() {
    }

    public static void markScheduled(Task task, Instant at) {
        task.setScheduledAt(at);
        task.setStatus(TaskStatus.SCHEDULED);
    }
}
