package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.ai.AiInteractionRepository;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import dev.kennurken.tutorbot.user.User;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** The metric that matters: promised vs. started vs. verified, today and over 7 days. */
@Component
public class StatusCommand implements CommandHandler {

    private final TaskService tasks;
    private final AiInteractionRepository aiInteractions;
    private final TaskFormatter fmt;
    private final BotMessages msg;
    private final Clock clock;

    public StatusCommand(TaskService tasks, AiInteractionRepository aiInteractions, TaskFormatter fmt, BotMessages msg,
                         Clock clock) {
        this.tasks = tasks;
        this.aiInteractions = aiInteractions;
        this.fmt = fmt;
        this.msg = msg;
        this.clock = clock;
    }

    @Override
    public String command() {
        return "status";
    }

    @Override
    public String description() {
        return "Execution numbers: today and last 7 days";
    }

    @Override
    public void handle(CommandContext ctx) {
        User user = ctx.user();
        Instant now = clock.instant();
        LocalDate today = user.today(now);
        List<Task> todayTasks = tasks.findForLocalDate(user, today);
        List<Task> week = tasks.findInWindow(user, now.minus(Duration.ofDays(7)), now);
        int streak = streak(user, tasks.findInWindow(user, now.minus(Duration.ofDays(60)), now), today);
        BigDecimal cost = aiInteractions.sumCostSince(user.getId(), today.withDayOfMonth(1).atStartOfDay(user.zone()).toInstant());

        StringBuilder sb = new StringBuilder();
        sb.append(msg.get(user, "status.today", todayTasks.size(), count(todayTasks, TaskStatus.COMPLETED),
                count(todayTasks, TaskStatus.MISSED), count(todayTasks, TaskStatus.SKIPPED))).append("\n");
        sb.append(msg.get(user, "status.week", week.size(),
                (int) week.stream().filter(t -> t.getStartedAt() != null).count(),
                count(week, TaskStatus.COMPLETED),
                (int) week.stream().filter(t -> t.getVerificationStatus() == dev.kennurken.tutorbot.task.VerificationStatus.PASSED).count(),
                count(week, TaskStatus.MISSED))).append("\n");
        sb.append(msg.get(user, "status.streak", streak)).append("\n");
        if (user.isPaused(now)) {
            sb.append(msg.get(user, "status.paused", dev.kennurken.tutorbot.common.time.TimeFormats.smart(user.getPausedUntil(), user.zone(), now))).append("\n");
        }
        if (user.isInRecoveryMode(now)) {
            sb.append(msg.get(user, "status.recovery")).append("\n");
        }
        List<Task> pending = tasks.findPendingVerification(user);
        if (!pending.isEmpty()) {
            sb.append("\n").append(msg.get(user, "tasks.header.pending")).append("\n").append(fmt.list(user, pending, now));
        }
        sb.append("\n").append(msg.get(user, "status.mode", user.getMode().name(), user.getTimezone()));
        sb.append("\n").append(msg.get(user, "status.cost", String.format("%.3f", cost.doubleValue())));
        ctx.reply().send(sb.toString());
    }

    private static int count(List<Task> list, TaskStatus status) {
        return (int) list.stream().filter(t -> t.getStatus() == status).count();
    }

    /** Consecutive days (ending today or yesterday) with at least one completed task. */
    static int streak(User user, List<Task> recent, LocalDate today) {
        Set<LocalDate> days = recent.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED && t.getCompletedAt() != null)
                .map(t -> user.today(t.getCompletedAt()))
                .collect(Collectors.toSet());
        LocalDate cursor = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}
