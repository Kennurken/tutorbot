package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.planning.DailyPlanner;
import dev.kennurken.tutorbot.planning.PlanRenderer;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TodayCommand implements CommandHandler {

    private final TaskService tasks;
    private final PlanRenderer renderer;
    private final Clock clock;

    public TodayCommand(TaskService tasks, PlanRenderer renderer, Clock clock) {
        this.tasks = tasks;
        this.renderer = renderer;
        this.clock = clock;
    }

    @Override
    public String command() {
        return "today";
    }

    @Override
    public String description() {
        return "Today's plan";
    }

    @Override
    public void handle(CommandContext ctx) {
        Instant now = clock.instant();
        LocalDate today = ctx.user().today(now);
        List<Task> list = tasks.findForLocalDate(ctx.user(), today);
        DailyPlanner.DayPlan plan = DailyPlanner.plan(ctx.user(), list, now);
        ctx.reply().send(renderer.render(ctx.user(), today, plan, now, false));
    }
}
