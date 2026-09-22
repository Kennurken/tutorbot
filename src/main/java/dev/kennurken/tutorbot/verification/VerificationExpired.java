package dev.kennurken.tutorbot.verification;

import dev.kennurken.tutorbot.task.Task;

/** The user started a verification and disappeared. Notification module tells them how to resume. */
public record VerificationExpired(Task task) {
}
