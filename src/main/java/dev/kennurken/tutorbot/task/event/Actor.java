package dev.kennurken.tutorbot.task.event;

/** Who caused a state change. Needed to audit AI-initiated decisions separately. */
public enum Actor {
    USER, SYSTEM, AI
}
