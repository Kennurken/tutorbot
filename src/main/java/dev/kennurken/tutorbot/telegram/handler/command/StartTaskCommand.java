package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class StartTaskCommand implements CommandHandler {

    private final TaskFlows flows;

    public StartTaskCommand(TaskFlows flows) {
        this.flows = flows;
    }

    @Override
    public String command() {
        return "start_task";
    }

    @Override
    public String description() {
        return "Start working on a task: /start_task [id]";
    }

    @Override
    public void handle(CommandContext ctx) {
        if (ctx.hasArgs()) {
            flows.start(ctx.user(), Ids.parse(ctx.argv()[0]), ctx.reply());
            return;
        }
        flows.resolveSingle(ctx.user(), Callbacks.START, ctx.reply())
                .ifPresent(t -> flows.start(ctx.user(), t.getId(), ctx.reply()));
    }
}
