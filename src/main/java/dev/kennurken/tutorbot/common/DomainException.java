package dev.kennurken.tutorbot.common;

/**
 * A business-rule violation that is safe to show to the user
 * ("task already completed", "no active verification").
 */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
