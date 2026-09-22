package dev.kennurken.tutorbot.notification;

public enum NotificationKind {
    TASK_START, REMINDER, OVERDUE, MISSED, VERIFICATION_EXPIRED, MORNING_PLAN, EVENING_SUMMARY, QUIZ,
    WEEKLY_REVIEW, CONSEQUENCE, OVERLOAD, GENERIC;

    /** Accountability nudges are suppressed while the user is paused; informational ones are not. */
    public boolean isAccountabilityNudge() {
        return this == TASK_START || this == REMINDER || this == OVERDUE || this == MISSED || this == CONSEQUENCE
                || this == QUIZ;
    }

    /**
     * Held back during the user's quiet hours. A TASK_START is never held: the user chose that
     * time deliberately. Morning plan and evening summary have their own user-chosen times.
     */
    public boolean isDeferrableInQuietHours() {
        return this != TASK_START && this != MORNING_PLAN && this != EVENING_SUMMARY;
    }
}
