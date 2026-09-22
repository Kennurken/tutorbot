package dev.kennurken.tutorbot.telegram.api;

public class TelegramApiException extends RuntimeException {

    private final boolean retryable;
    private final int retryAfterSeconds;

    public TelegramApiException(String message, boolean retryable, int retryAfterSeconds, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
