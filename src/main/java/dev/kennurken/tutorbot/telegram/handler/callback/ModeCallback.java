package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import dev.kennurken.tutorbot.user.StrictnessMode;
import dev.kennurken.tutorbot.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class ModeCallback implements CallbackHandler {

    private final UserService users;
    private final BotMessages msg;

    public ModeCallback(UserService users, BotMessages msg) {
        this.users = users;
        this.msg = msg;
    }

    @Override
    public String action() {
        return Callbacks.MODE;
    }

    @Override
    public void handle(CallbackContext ctx) {
        StrictnessMode mode = StrictnessMode.valueOf(ctx.arg(0));
        users.setMode(ctx.user(), mode);
        ctx.reply().send(msg.get(ctx.user(), "settings.mode.set", mode.name()));
    }
}
