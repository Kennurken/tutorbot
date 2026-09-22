package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.goal.Goal;
import dev.kennurken.tutorbot.goal.GoalService;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** /goals — list; /goals add <title> — add; /goals done <id> — mark achieved. */
@Component
public class GoalsCommand implements CommandHandler {

    private final GoalService goals;
    private final BotMessages msg;

    public GoalsCommand(GoalService goals, BotMessages msg) {
        this.goals = goals;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "goals";
    }

    @Override
    public String description() {
        return "Long-term goals: /goals, /goals add <title>";
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
            sb.append("• <b>#").append(g.getId()).append("</b> ").append(Html.esc(g.getTitle()))
                    .append(" · ").append(g.getHorizon().name().toLowerCase(Locale.ROOT).replace('_', ' ')).append("\n");
        }
        sb.append("\n").append(msg.get(ctx.user(), "goals.usage"));
        ctx.reply().send(sb.toString());
    }
}
