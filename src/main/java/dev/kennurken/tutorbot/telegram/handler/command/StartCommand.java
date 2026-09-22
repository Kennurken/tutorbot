package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class StartCommand implements CommandHandler {

    private final BotMessages msg;

    public StartCommand(BotMessages msg) {
        this.msg = msg;
    }

    @Override
    public String command() {
        return "start";
    }

    @Override
    public String description() {
        return "Welcome and quick start";
    }

    @Override
    public void handle(CommandContext ctx) {
        ctx.reply().send(msg.get(ctx.user(), "start.welcome", ctx.user().getTimezone()));
        ctx.reply().send(msg.get(ctx.user(), "help.text"));
    }
}
