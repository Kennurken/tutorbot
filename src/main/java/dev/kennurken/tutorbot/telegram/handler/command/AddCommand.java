package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.telegram.UpdateDispatcher;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class AddCommand implements CommandHandler {

    private final TaskFlows flows;
    private final UpdateDispatcher dispatcher;
    private final BotMessages msg;

    public AddCommand(TaskFlows flows, @Lazy UpdateDispatcher dispatcher, BotMessages msg) {
        this.flows = flows;
        this.dispatcher = dispatcher;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "add";
    }

    @Override
    public String description() {
        return "Add a task: /add tomorrow 19:00 English 30 min";
    }

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.hasArgs()) {
            dispatcher.expectTaskText(ctx.user());
            ctx.reply().send(msg.get(ctx.user(), "add.prompt"));
            return;
        }
        flows.proposeFromText(ctx.user(), ctx.args(), ctx.reply());
    }
}
