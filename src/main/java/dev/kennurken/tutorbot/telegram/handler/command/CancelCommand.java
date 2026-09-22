package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import org.springframework.stereotype.Component;

@Component
public class CancelCommand implements CommandHandler {

    private final TaskService tasks;
    private final TaskFormatter fmt;
    private final BotMessages msg;

    public CancelCommand(TaskService tasks, TaskFormatter fmt, BotMessages msg) {
        this.tasks = tasks;
        this.fmt = fmt;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "cancel";
    }

    @Override
    public String description() {
        return "Cancel a task: /cancel <id>";
    }

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.hasArgs()) {
            ctx.reply().send(msg.get(ctx.user(), "cancel.usage"));
            return;
        }
        Task task = tasks.cancel(ctx.user(), Ids.parse(ctx.argv()[0]));
        ctx.reply().send(msg.get(ctx.user(), "cancel.done", fmt.header(task)));
    }
}
