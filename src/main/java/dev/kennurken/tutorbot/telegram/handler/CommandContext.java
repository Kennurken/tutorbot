package dev.kennurken.tutorbot.telegram.handler;

import dev.kennurken.tutorbot.telegram.Replier;
import dev.kennurken.tutorbot.user.User;

public record CommandContext(User user, String args, Replier reply) {

    public boolean hasArgs() {
        return args != null && !args.isBlank();
    }

    public String[] argv() {
        return hasArgs() ? args.trim().split("\\s+") : new String[0];
    }
}
