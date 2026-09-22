package dev.kennurken.tutorbot.messaging;

import dev.kennurken.tutorbot.common.time.TimeFormats;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Consistent one-line and multi-line renderings of a task, in the user's zone. */
@Component
public class TaskFormatter {

    private static final Map<TaskStatus, String> ICONS = Map.ofEntries(
            Map.entry(TaskStatus.CREATED, "▫️"),
            Map.entry(TaskStatus.SCHEDULED, "🕒"),
            Map.entry(TaskStatus.NOTIFIED, "🔔"),
            Map.entry(TaskStatus.STARTED, "▶️"),
            Map.entry(TaskStatus.REPORTED_DONE, "☑️"),
            Map.entry(TaskStatus.PENDING_VERIFICATION, "🧪"),
            Map.entry(TaskStatus.VERIFICATION_IN_PROGRESS, "🧪"),
            Map.entry(TaskStatus.COMPLETED, "✅"),
            Map.entry(TaskStatus.FAILED, "❌"),
            Map.entry(TaskStatus.MISSED, "⛔"),
            Map.entry(TaskStatus.SKIPPED, "⏭"),
            Map.entry(TaskStatus.CANCELLED, "🚫"),
            Map.entry(TaskStatus.EXPIRED, "⌛"));

    public String line(User user, Task task, Instant now) {
        String when = task.getScheduledAt() == null ? "—" : TimeFormats.smart(task.getScheduledAt(), user.zone(), now);
        return "%s %s <b>#%d</b> %s · %dm".formatted(icon(task.getStatus()), when, task.getId(),
                Html.esc(task.getTitle()), task.effectiveMinutes());
    }

    public String list(User user, List<Task> tasks, Instant now) {
        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            sb.append(line(user, t, now)).append('\n');
        }
        return sb.toString();
    }

    public String header(Task task) {
        StringBuilder sb = new StringBuilder("<b>#").append(task.getId()).append("</b> ").append(Html.esc(task.getTitle()));
        if (task.getSubject() != null) {
            sb.append(" · ").append(Html.esc(task.getSubject()));
        }
        sb.append(" · ").append(task.effectiveMinutes()).append("m");
        if (task.getExtraMinutes() > 0) {
            sb.append(" (+").append(task.getExtraMinutes()).append(")");
        }
        return sb.toString();
    }

    public static String icon(TaskStatus status) {
        return ICONS.getOrDefault(status, "•");
    }
}
