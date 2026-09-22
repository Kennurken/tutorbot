package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Keyboards;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import dev.kennurken.tutorbot.verification.VerificationService;
import org.springframework.stereotype.Component;

@Component
public class AbandonCommand implements CommandHandler {

    private final VerificationService verification;
    private final TaskFormatter fmt;
    private final Keyboards keyboards;
    private final BotMessages msg;

    public AbandonCommand(VerificationService verification, TaskFormatter fmt, Keyboards keyboards, BotMessages msg) {
        this.verification = verification;
        this.fmt = fmt;
        this.keyboards = keyboards;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "abandon";
    }

    @Override
    public String description() {
        return "Stop the current verification (retry later)";
    }

    @Override
    public void handle(CommandContext ctx) {
        verification.abandon(ctx.user()).ifPresentOrElse(
                task -> ctx.reply().send(msg.get(ctx.user(), "verification.abandoned", fmt.header(task)),
                        keyboards.verificationPending(ctx.user(), task)),
                () -> ctx.reply().send(msg.get(ctx.user(), "verification.none")));
    }
}
