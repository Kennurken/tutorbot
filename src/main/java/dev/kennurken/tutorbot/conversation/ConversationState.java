package dev.kennurken.tutorbot.conversation;

/** What the bot is waiting for from the user. Anything not listed means IDLE. */
public enum ConversationState {
    IDLE,
    IN_VERIFICATION,
    AWAITING_SKIP_REASON,
    AWAITING_TASK_TEXT,
    AWAITING_TASK_CONFIRMATION,
    AWAITING_RESCHEDULE_TIME
}
