package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.common.NotFoundException;
import dev.kennurken.tutorbot.goal.Goal;
import dev.kennurken.tutorbot.goal.GoalService;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.telegram.flow.GoalPlanFlow;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * /goals — list with progress; /goals add <title>; /goals plan <id> [HH:mm] — AI decomposition into
 * daily tasks; /goals done|drop <id>.
 */
@Component
public class GoalsCommand implements CommandHandler {

    private final GoalService goals;
    private final TaskService tasks;
    private final GoalPlanFlow planFlow;
    private final BotMessages msg;

    public GoalsCommand(GoalService goals, TaskService tasks, GoalPlanFlow planFlow, BotMessages msg) {
        this.goals = goals;
        this.tasks = tasks;
        this.planFlow = planFlow;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "goals";
    }

    @Override
    public String description() {
        return "Goals: /goals, /goals add <title>, /goals plan <id>";
    }

    @Override
    public void handle(CommandContext ctx) {
        String[] argv = ctx.argv();
        if (argv.length >= 2 && argv[0].equalsIgnoreCase("add")) {
            String title = ctx.args().substring(argv[0].length()).trim();
            Goal goal = goals.add(ctx.user(), title, Goal.Horizon.MONTHS_3);
            ctx.reply().send(msg.get(ctx.user(), "goals.added", goal.getId(), Html.esc(goal.getTitle())));
            return;
        }
        if (argv.length >= 2 && argv[0].equalsIgnoreCase("plan")) {
            long id = Ids.parse(argv[1]);
            Goal goal = goals.active(ctx.user()).stream().filter(g -> g.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new NotFoundException("Goal", id));
            LocalTime time = GoalPlanFlow.DEFAULT_TIME;
            if (argv.length >= 3) {
                try {
                    time = LocalTime.parse(argv[2]);
                } catch (DateTimeParseException e) {
                    throw new DomainException("Time must look like 19:00");
                }
            }
            ctx.reply().send(msg.get(ctx.user(), "goals.plan.thinking"));
            planFlow.propose(ctx.user(), goal, time, ctx.reply());
            return;
        }
        if (argv.length == 2 && (argv[0].equalsIgnoreCase("done") || argv[0].equalsIgnoreCase("drop"))) {
            Goal.Status status = argv[0].toLowerCase(Locale.ROOT).equals("done") ? Goal.Status.ACHIEVED : Goal.Status.DROPPED;
            Goal goal = goals.close(ctx.user(), Ids.parse(argv[1]), status);
            ctx.reply().send(msg.get(ctx.user(), "goals.closed", goal.getId(), Html.esc(goal.getTitle()), status.name()));
            return;
        }
        List<Goal> active = goals.active(ctx.user());
        if (active.isEmpty()) {
            ctx.reply().send(msg.get(ctx.user(), "goals.empty"));
            return;
        }
        StringBuilder sb = new StringBuilder(msg.get(ctx.user(), "goals.header")).append("\n");
        for (Goal g : active) {
            long[] progress = tasks.goalProgress(g.getId());
            sb.append("• <b>#").append(g.getId()).append("</b> ").append(Html.esc(g.getTitle()))
                    .append(" · ").append(g.getHorizon().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            if (progress[0] > 0) {
                sb.append(" · ").append(progress[1]).append("/").append(progress[0]);
            }
            sb.append("\n");
        }
        sb.append("\n").append(msg.get(ctx.user(), "goals.usage"));
        ctx.reply().send(sb.toString());
    }
}
