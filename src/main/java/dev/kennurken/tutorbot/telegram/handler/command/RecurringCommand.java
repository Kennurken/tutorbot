package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.task.RecurrenceRule;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.util.List;
import org.springframework.stereotype.Component;

/** /recurring — list rules; /recurring stop <id> — deactivate. */
@Component
public class RecurringCommand implements CommandHandler {

    private final TaskService tasks;
    private final BotMessages msg;

    public RecurringCommand(TaskService tasks, BotMessages msg) {
        this.tasks = tasks;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "recurring";
    }

    @Override
    public String description() {
        return "Recurring rules: /recurring, /recurring stop <id>";
    }

    @Override
    public void handle(CommandContext ctx) {
        String[] argv = ctx.argv();
        if (argv.length == 2 && argv[0].equalsIgnoreCase("stop")) {
            tasks.deactivateRecurrence(ctx.user(), Ids.parse(argv[1]));
            ctx.reply().send(msg.get(ctx.user(), "recurring.stopped", argv[1]));
            return;
        }
        List<RecurrenceRule> rules = tasks.findRecurrenceRules(ctx.user());
        if (rules.isEmpty()) {
            ctx.reply().send(msg.get(ctx.user(), "recurring.empty"));
            return;
        }
        StringBuilder sb = new StringBuilder(msg.get(ctx.user(), "recurring.header")).append("\n");
        for (RecurrenceRule r : rules) {
            sb.append("• <b>#").append(r.getId()).append("</b> ").append(Html.esc(r.getTitle()))
                    .append(" · ").append(r.getDaysOfWeek().toLowerCase().replace(",", ", "))
                    .append(" · ").append(r.getTimeOfDay()).append(" · ").append(r.getEstimatedMinutes()).append("m\n");
        }
        sb.append("\n").append(msg.get(ctx.user(), "recurring.usage"));
        ctx.reply().send(sb.toString());
    }
}
