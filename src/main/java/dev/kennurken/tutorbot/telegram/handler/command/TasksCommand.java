package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TasksCommand implements CommandHandler {

    private final TaskService tasks;
    private final TaskFormatter fmt;
    private final BotMessages msg;
    private final Clock clock;

    public TasksCommand(TaskService tasks, TaskFormatter fmt, BotMessages msg, Clock clock) {
        this.tasks = tasks;
        this.fmt = fmt;
        this.msg = msg;
        this.clock = clock;
    }

    @Override
    public String command() {
        return "tasks";
    }

    @Override
    public String description() {
        return "Upcoming tasks and pending verifications";
    }

    @Override
    public void handle(CommandContext ctx) {
        Instant now = clock.instant();
        List<Task> upcoming = tasks.findInWindow(ctx.user(), now.minus(Duration.ofHours(12)), now.plus(Duration.ofDays(7)))
                .stream().filter(t -> TaskStatus.ACTIVE.contains(t.getStatus())).toList();
        List<Task> pending = tasks.findPendingVerification(ctx.user());
        List<Task> backlog = tasks.findByStatuses(ctx.user(), List.of(TaskStatus.CREATED));
        StringBuilder sb = new StringBuilder();
        if (upcoming.isEmpty() && pending.isEmpty() && backlog.isEmpty()) {
            sb.append(msg.get(ctx.user(), "tasks.empty"));
        }
        if (!upcoming.isEmpty()) {
            sb.append(msg.get(ctx.user(), "tasks.header.upcoming")).append("\n").append(fmt.list(ctx.user(), upcoming, now));
        }
        if (!pending.isEmpty()) {
            sb.append("\n").append(msg.get(ctx.user(), "tasks.header.pending")).append("\n").append(fmt.list(ctx.user(), pending, now));
        }
        if (!backlog.isEmpty()) {
            sb.append("\n").append(msg.get(ctx.user(), "tasks.header.backlog")).append("\n").append(fmt.list(ctx.user(), backlog, now));
        }
        ctx.reply().send(sb.toString());
    }
}
