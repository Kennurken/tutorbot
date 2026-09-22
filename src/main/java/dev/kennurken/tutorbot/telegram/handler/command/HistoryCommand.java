package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.common.time.TimeFormats;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.event.TaskEvent;
import dev.kennurken.tutorbot.task.event.TaskEventRepository;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import dev.kennurken.tutorbot.user.User;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * /history <id>: the task's event log, i.e. the answer to "why did the bot do that".
 * Reads {@code task_events} only; nothing is computed on the fly, so what you see is what happened.
 */
@Component
public class HistoryCommand implements CommandHandler {

    private final TaskService tasks;
    private final TaskEventRepository events;
    private final TaskFormatter fmt;
    private final BotMessages msg;
    private final JsonMapper json;

    public HistoryCommand(TaskService tasks, TaskEventRepository events, TaskFormatter fmt, BotMessages msg,
                          JsonMapper json) {
        this.tasks = tasks;
        this.events = events;
        this.fmt = fmt;
        this.msg = msg;
        this.json = json;
    }

    @Override
    public String command() {
        return "history";
    }

    @Override
    public String description() {
        return "Event log of a task: /history <id>";
    }

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.hasArgs()) {
            ctx.reply().send(msg.get(ctx.user(), "history.usage"));
            return;
        }
        User user = ctx.user();
        Task task = tasks.requireOwned(user, Ids.parse(ctx.argv()[0]));
        List<TaskEvent> log = events.findByTaskIdOrderByOccurredAt(task.getId());
        StringBuilder sb = new StringBuilder(msg.get(user, "history.header", fmt.header(task))).append("\n\n");
        ZoneId zone = user.zone();
        for (TaskEvent e : log) {
            sb.append(TimeFormats.dateTime(e.getOccurredAt(), zone)).append(" · ")
                    .append(eventName(user, e.getEventType().name()));
            String details = details(e.getPayload());
            if (!details.isEmpty()) {
                sb.append(" — <i>").append(Html.esc(details)).append("</i>");
            }
            sb.append("\n");
        }
        ctx.reply().send(sb.toString());
    }

    private String eventName(User user, String type) {
        try {
            return msg.get(user, "event." + type);
        } catch (NoSuchMessageException e) {
            return type.toLowerCase().replace('_', ' ');
        }
    }

    /** Compact "key: value" rendering of the payload, skipping noise. */
    private String details(String payload) {
        if (payload == null || payload.isBlank()) {
            return "";
        }
        Map<String, Object> map = json.readValue(payload, new TypeReference<Map<String, Object>>() { });
        StringBuilder sb = new StringBuilder();
        map.forEach((k, v) -> {
            if (v == null || String.valueOf(v).isBlank() || k.equals("title")) {
                return;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(k).append(": ").append(shorten(String.valueOf(v)));
        });
        return sb.toString();
    }

    private static String shorten(String v) {
        if (v.length() > 60) {
            return v.substring(0, 57) + "...";
        }
        return v;
    }
}
