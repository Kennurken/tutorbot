package dev.kennurken.tutorbot.ai;

/**
 * Thrown to callers when no usable model output could be obtained (network, rate limit,
 * malformed JSON, schema violation). Callers must have a non-AI fallback path.
 */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message) {
        super(message);
    }

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
