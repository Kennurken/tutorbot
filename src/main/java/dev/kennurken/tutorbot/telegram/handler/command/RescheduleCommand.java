package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.planning.NaturalLanguageTaskParser;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RescheduleCommand implements CommandHandler {

    private final TaskFlows flows;
    private final BotMessages msg;
    private final Clock clock;

    public RescheduleCommand(TaskFlows flows, BotMessages msg, Clock clock) {
        this.flows = flows;
        this.msg = msg;
        this.clock = clock;
    }

    @Override
    public String command() {
        return "reschedule";
    }

    @Override
    public String description() {
        return "Move a task: /reschedule <id> tomorrow 19:00";
    }

    @Override
    public void handle(CommandContext ctx) {
        String[] argv = ctx.argv();
        if (argv.length == 0) {
            ctx.reply().send(msg.get(ctx.user(), "resch.usage"));
            return;
        }
        long id = Ids.parse(argv[0]);
        if (argv.length == 1) {
            flows.askRescheduleTime(ctx.user(), id, ctx.reply());
            return;
        }
        String when = ctx.args().substring(argv[0].length()).trim();
        Optional<CreateTaskCommand> parsed = NaturalLanguageTaskParser.parse(when + " task", clock.instant(), ctx.user().zone());
        if (parsed.isEmpty() || parsed.get().scheduledAt() == null) {
            ctx.reply().send(msg.get(ctx.user(), "resch.badtime"));
            return;
        }
        flows.rescheduleTo(ctx.user(), id, parsed.get().scheduledAt(), ctx.reply());
    }
}
