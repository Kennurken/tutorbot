package dev.kennurken.tutorbot.task;

/** Why a task was skipped. Collected, never judged: it feeds failure analysis, not blame. */
public enum SkipCategory {
    OBJECTIVE_REASON, TOO_TIRED, FORGOT, TOO_DIFFICULT, DID_NOT_WANT_TO, BAD_SCHEDULE, EMERGENCY, OTHER
}
