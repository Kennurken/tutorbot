package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.common.DomainException;

final class Ids {

    private Ids() {
    }

    static long parse(String raw) {
        String s = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            throw new DomainException("Task id must be a number, e.g. /done 12");
        }
    }
}
