package dev.kennurken.tutorbot.ai.provider;

/** Failure talking to a model. {@code retryable} distinguishes 429/5xx/timeouts from bad requests. */
public class AiException extends RuntimeException {

    private final boolean retryable;

    public AiException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AiException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
