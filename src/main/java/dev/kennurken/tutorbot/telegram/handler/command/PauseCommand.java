package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Keyboards;
import dev.kennurken.tutorbot.telegram.flow.PauseFlow;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.stereotype.Component;

/** Human override: /pause (menu), /pause 1h | today | 24h | off. */
@Component
public class PauseCommand implements CommandHandler {

    private final PauseFlow pauseFlow;
    private final Keyboards keyboards;
    private final BotMessages msg;

    public PauseCommand(PauseFlow pauseFlow, Keyboards keyboards, BotMessages msg) {
        this.pauseFlow = pauseFlow;
        this.keyboards = keyboards;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "pause";
    }

    @Override
    public String description() {
        return "Pause accountability: /pause 1h | today | 24h | off";
    }

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.hasArgs()) {
            ctx.reply().send(msg.get(ctx.user(), "pause.prompt"), keyboards.pausePresets(ctx.user()));
            return;
        }
        pauseFlow.apply(ctx.user(), ctx.argv()[0], ctx.reply());
    }
}
