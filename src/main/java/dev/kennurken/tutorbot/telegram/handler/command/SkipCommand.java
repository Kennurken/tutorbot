package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class SkipCommand implements CommandHandler {

    private final TaskFlows flows;

    public SkipCommand(TaskFlows flows) {
        this.flows = flows;
    }

    @Override
    public String command() {
        return "skip";
    }

    @Override
    public String description() {
        return "Skip a task with a reason: /skip [id]";
    }

    @Override
    public void handle(CommandContext ctx) {
        if (ctx.hasArgs()) {
            flows.askSkipCategory(ctx.user(), Ids.parse(ctx.argv()[0]), ctx.reply());
            return;
        }
        flows.resolveSingle(ctx.user(), Callbacks.SKIP, ctx.reply())
                .ifPresent(t -> flows.askSkipCategory(ctx.user(), t.getId(), ctx.reply()));
    }
}
