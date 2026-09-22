package dev.kennurken.tutorbot.planning;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** Renders a {@link DailyPlanner.DayPlan} as a chat message. */
@Component
public class PlanRenderer {

    private final BotMessages msg;
    private final TaskFormatter fmt;

    public PlanRenderer(BotMessages msg, TaskFormatter fmt) {
        this.msg = msg;
        this.fmt = fmt;
    }

    public String render(User user, LocalDate date, DailyPlanner.DayPlan plan, Instant now, boolean morning) {
        StringBuilder sb = new StringBuilder();
        sb.append(msg.get(user, morning ? "plan.morning.header" : "plan.header", date.toString())).append("\n\n");
        if (plan.all().isEmpty()) {
            sb.append(msg.get(user, "plan.empty"));
            return sb.toString();
        }
        if (plan.recovery()) {
            sb.append(msg.get(user, "plan.recovery")).append("\n\n");
        }
        List<Task> shown = plan.overloaded() || plan.recovery() ? plan.recommended() : plan.all();
        sb.append(fmt.list(user, shown, now));
        sb.append("\n").append(msg.get(user, "plan.total", shown.size(), plan.recommendedMinutes()));
        if (plan.overloaded() && !plan.deferred().isEmpty()) {
            sb.append("\n\n").append(msg.get(user, "plan.overloaded", plan.deferred().size(), plan.totalMinutes(),
                    user.getSettings().getDailyTaskLimit(), user.getSettings().getMaxDailyStudyMinutes())).append("\n");
            sb.append(fmt.list(user, plan.deferred(), now));
        }
        return sb.toString();
    }
}
