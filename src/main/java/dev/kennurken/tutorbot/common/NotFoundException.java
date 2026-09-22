package dev.kennurken.tutorbot.common;

public class NotFoundException extends DomainException {

    public NotFoundException(String entity, Object id) {
        super(entity + " " + id + " not found");
    }
}
