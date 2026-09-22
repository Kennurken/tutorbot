package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class HelpCommand implements CommandHandler {

    private final BotMessages msg;

    public HelpCommand(BotMessages msg) {
        this.msg = msg;
    }

    @Override
    public String command() {
        return "help";
    }

    @Override
    public String description() {
        return "All commands";
    }

    @Override
    public void handle(CommandContext ctx) {
        ctx.reply().send(msg.get(ctx.user(), "help.text"));
    }
}
