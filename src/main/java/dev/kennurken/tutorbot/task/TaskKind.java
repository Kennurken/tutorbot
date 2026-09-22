package dev.kennurken.tutorbot.task;

/** Who made the task and how it should be treated when ignored. */
public enum TaskKind {
    /** Created by the user (command, free text, recurrence). Missing it has consequences. */
    REGULAR,
    /** Created by the consequence engine after a failed exam. */
    REVIEW,
    /** Daily retrieval-practice quiz. Ignoring it is free: the task is cancelled when the exam expires. */
    QUIZ
}
