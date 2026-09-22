package dev.kennurken.tutorbot.telegram.handler;

import dev.kennurken.tutorbot.telegram.Replier;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.CallbackQuery;
import dev.kennurken.tutorbot.user.User;
import java.util.List;

public record CallbackContext(User user, String action, List<String> args, CallbackQuery query, Replier reply) {

    public String arg(int i) {
        return i < args.size() ? args.get(i) : null;
    }

    public long longArg(int i) {
        return Long.parseLong(arg(i));
    }
}
