package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class DoneCommand implements CommandHandler {

    private final TaskFlows flows;

    public DoneCommand(TaskFlows flows) {
        this.flows = flows;
    }

    @Override
    public String command() {
        return "done";
    }

    @Override
    public String description() {
        return "Report a task done (verification follows): /done [id]";
    }

    @Override
    public void handle(CommandContext ctx) {
        if (ctx.hasArgs()) {
            flows.reportDone(ctx.user(), Ids.parse(ctx.argv()[0]), ctx.reply());
            return;
        }
        flows.resolveSingle(ctx.user(), Callbacks.DONE, ctx.reply())
                .ifPresent(t -> flows.reportDone(ctx.user(), t.getId(), ctx.reply()));
    }
}
