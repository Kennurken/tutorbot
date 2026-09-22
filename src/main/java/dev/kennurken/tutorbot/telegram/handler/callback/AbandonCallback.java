package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import dev.kennurken.tutorbot.verification.VerificationService;
import org.springframework.stereotype.Component;

@Component
public class AbandonCallback implements CallbackHandler {

    private final VerificationService verification;
    private final TaskFormatter fmt;
    private final BotMessages msg;

    public AbandonCallback(VerificationService verification, TaskFormatter fmt, BotMessages msg) {
        this.verification = verification;
        this.fmt = fmt;
        this.msg = msg;
    }

    @Override
    public String action() {
        return Callbacks.ABANDON;
    }

    @Override
    public void handle(CallbackContext ctx) {
        verification.abandon(ctx.user()).ifPresentOrElse(
                task -> ctx.reply().send(msg.get(ctx.user(), "verification.abandoned", fmt.header(task))),
                () -> ctx.reply().send(msg.get(ctx.user(), "verification.none")));
    }
}
