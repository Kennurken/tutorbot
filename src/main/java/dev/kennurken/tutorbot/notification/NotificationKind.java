package dev.kennurken.tutorbot.notification;

public enum NotificationKind {
    TASK_START, REMINDER, OVERDUE, MISSED, VERIFICATION_EXPIRED, MORNING_PLAN, WEEKLY_REVIEW,
    CONSEQUENCE, OVERLOAD, GENERIC;

    /** Accountability nudges are suppressed while the user is paused; informational ones are not. */
    public boolean isAccountabilityNudge() {
        return this == TASK_START || this == REMINDER || this == OVERDUE || this == MISSED || this == CONSEQUENCE;
    }
}
