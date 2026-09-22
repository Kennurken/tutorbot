package dev.kennurken.tutorbot.telegram.flow;

import dev.kennurken.tutorbot.ai.AiTutorService;
import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.conversation.ConversationService;
import dev.kennurken.tutorbot.conversation.ConversationState;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.messaging.InlineKeyboard;
import dev.kennurken.tutorbot.messaging.Keyboards;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.planning.TaskIntentService;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.RecurrenceRule;
import dev.kennurken.tutorbot.task.SkipCategory;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.telegram.Replier;
import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Task interactions shared by commands and buttons, so "/done 12" and tapping [Done] run the
 * exact same code path.
 */
@Component
public class TaskFlows {

    private final TaskService taskService;
    private final TaskIntentService intents;
    private final VerificationFlow verificationFlow;
    private final ConversationService conversations;
    private final AiTutorService ai;
    private final BotMessages msg;
    private final TaskFormatter fmt;
    private final Keyboards keyboards;
    private final JsonMapper json;
    private final Clock clock;

    public TaskFlows(TaskService taskService, TaskIntentService intents, VerificationFlow verificationFlow,
                     ConversationService conversations, AiTutorService ai, BotMessages msg, TaskFormatter fmt,
                     Keyboards keyboards, JsonMapper json, Clock clock) {
        this.taskService = taskService;
        this.intents = intents;
        this.verificationFlow = verificationFlow;
        this.conversations = conversations;
        this.ai = ai;
        this.msg = msg;
        this.fmt = fmt;
        this.keyboards = keyboards;
        this.json = json;
        this.clock = clock;
    }

    // -------------------------------------------------------------- creation

    /** Parses free text; on success asks for confirmation and parks the draft in conversation state. */
    public void proposeFromText(User user, String text, Replier reply) {
        Optional<CreateTaskCommand> cmd = intents.interpret(user, text);
        if (cmd.isEmpty()) {
            conversations.clear(user.getId());
            reply.send(msg.get(user, "add.notunderstood"));
            return;
        }
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("draft", json.convertValue(cmd.get(), Map.class));
        conversations.set(user.getId(), ConversationState.AWAITING_TASK_CONFIRMATION, ctx);
        reply.send(describeDraft(user, cmd.get()), keyboards.confirmTask(user));
    }

    public void confirmDraft(User user, boolean yes, Replier reply) {
        ConversationService.Snapshot snapshot = conversations.current(user.getId());
        conversations.clear(user.getId());
        if (snapshot.state() != ConversationState.AWAITING_TASK_CONFIRMATION || snapshot.context().get("draft") == null) {
            reply.send(msg.get(user, "add.nodraft"));
            return;
        }
        if (!yes) {
            reply.send(msg.get(user, "add.cancelled"));
            return;
        }
        CreateTaskCommand cmd = json.convertValue(snapshot.context().get("draft"), CreateTaskCommand.class);
        if (cmd.isRecurring()) {
            RecurrenceRule rule = taskService.createRecurring(user, cmd);
            reply.send(msg.get(user, "add.created.recurring", Html.esc(rule.getTitle()), days(user, rule.days()),
                    rule.getTimeOfDay().toString(), rule.getEstimatedMinutes()));
        } else {
            Task task = taskService.create(user, cmd);
            reply.send(msg.get(user, "add.created", fmt.line(user, task, clock.instant())));
        }
    }

    private String describeDraft(User user, CreateTaskCommand cmd) {
        StringBuilder sb = new StringBuilder(msg.get(user, "add.confirm")).append("\n\n");
        sb.append("<b>").append(Html.esc(cmd.title())).append("</b>\n");
        if (cmd.subject() != null) {
            sb.append(msg.get(user, "add.field.subject")).append(": ").append(Html.esc(cmd.subject()));
            if (cmd.topic() != null) {
                sb.append(" · ").append(Html.esc(cmd.topic()));
            }
            sb.append("\n");
        }
        sb.append(msg.get(user, "add.field.type")).append(": ").append(cmd.type()).append("\n");
        if (cmd.isRecurring()) {
            sb.append(msg.get(user, "add.field.when")).append(": ").append(days(user, cmd.recurrence().days()))
                    .append(" ").append(cmd.recurrence().timeOfDay()).append("\n");
        } else if (cmd.scheduledAt() != null) {
            ZonedDateTime at = cmd.scheduledAt().atZone(user.zone());
            sb.append(msg.get(user, "add.field.when")).append(": ")
                    .append(at.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale(user))).append(" ")
                    .append(at.toLocalDate()).append(" ").append(at.toLocalTime()).append("\n");
        }
        sb.append(msg.get(user, "add.field.duration")).append(": ").append(cmd.estimatedMinutes()).append(" min\n");
        if (cmd.deadlineAt() != null) {
            ZonedDateTime d = cmd.deadlineAt().atZone(user.zone());
            sb.append(msg.get(user, "add.field.deadline")).append(": ")
                    .append(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale(user))).append(" ")
                    .append(d.toLocalDate()).append("\n");
        }
        sb.append(msg.get(user, "add.field.verification")).append(": ")
                .append(msg.get(user, cmd.verificationRequired() ? "yes" : "no"));
        return sb.toString();
    }

    private String days(User user, Set<DayOfWeek> days) {
        return days.stream().sorted().map(d -> d.getDisplayName(TextStyle.SHORT, locale(user)))
                .collect(Collectors.joining(", "));
    }

    private static Locale locale(User user) {
        return Locale.forLanguageTag(user.getLanguage());
    }

    // ---------------------------------------------------------------- actions

    public void start(User user, long taskId, Replier reply) {
        Task task = taskService.start(user, taskId);
        reply.send(msg.get(user, "start_task.started", fmt.header(task)), keyboards.started(user, task));
    }

    public void reportDone(User user, long taskId, Replier reply) {
        Task task = taskService.reportDone(user, taskId);
        if (task.getStatus() == TaskStatus.COMPLETED) {
            reply.send(msg.get(user, "done.completed", fmt.header(task)));
            return;
        }
        reply.send(msg.get(user, "done.reported", fmt.header(task)));
        verificationFlow.start(user, task, reply);
    }

    public void askSkipCategory(User user, long taskId, Replier reply) {
        Task task = taskService.requireOwned(user, taskId);
        if (!TaskStatus.ACTIVE.contains(task.getStatus()) && task.getStatus() != TaskStatus.MISSED) {
            throw new DomainException(msg.get(user, "skip.notactive", task.getId(), task.getStatus()));
        }
        reply.send(msg.get(user, "skip.choose", fmt.header(task)), keyboards.skipCategories(user, taskId));
    }

    /** Category chosen: either finish immediately or ask for a free-text reason (policy dependent). */
    public void skipWithCategory(User user, long taskId, SkipCategory category, Replier reply) {
        boolean needsText = user.getConsequencePolicy().isRequireSkipReason()
                || category == SkipCategory.OTHER || category == SkipCategory.OBJECTIVE_REASON;
        if (needsText) {
            conversations.set(user.getId(), ConversationState.AWAITING_SKIP_REASON,
                    Map.of("taskId", taskId, "category", category.name()));
            reply.send(msg.get(user, "skip.reason.prompt"));
            return;
        }
        finishSkip(user, taskId, category, null, reply);
    }

    public void finishSkip(User user, long taskId, SkipCategory category, String reason, Replier reply) {
        conversations.clear(user.getId());
        Task task = taskService.requireOwned(user, taskId);
        if (task.getStatus() == TaskStatus.MISSED) {
            // Already missed by the system: just record why (goes to failure analysis), no new transition.
            reply.send(msg.get(user, "skip.reason.recorded", fmt.header(task)));
        } else {
            task = taskService.skip(user, taskId, category, reason);
            reply.send(msg.get(user, "skip.done", fmt.header(task), msg.get(user, "skip.cat." + category.name())));
        }
        if (reason != null && !reason.isBlank()) {
            String title = task.getTitle();
            ai.analyzeSkip(user, title, reason).ifPresent(a -> reply.send(
                    msg.get(user, "skip.insight", Html.esc(a.insight()), Html.esc(a.suggestion()))));
        }
    }

    public void reschedulePreset(User user, long taskId, String preset, Replier reply) {
        Instant now = clock.instant();
        Task task = taskService.requireOwned(user, taskId);
        Instant base = task.getScheduledAt() != null && task.getScheduledAt().isAfter(now) ? task.getScheduledAt() : now;
        Instant target = switch (preset) {
            case "30m" -> base.plus(Duration.ofMinutes(30));
            case "2h" -> base.plus(Duration.ofHours(2));
            case "eve" -> {
                ZonedDateTime evening = now.atZone(user.zone()).with(LocalTime.of(19, 0));
                yield evening.toInstant().isAfter(now) ? evening.toInstant() : evening.plusDays(1).toInstant();
            }
            case "tmrw" -> {
                ZonedDateTime original = (task.getScheduledAt() != null ? task.getScheduledAt() : now).atZone(user.zone());
                ZonedDateTime tomorrow = now.atZone(user.zone()).plusDays(1).with(original.toLocalTime());
                yield tomorrow.toInstant();
            }
            default -> throw new DomainException("Unknown preset " + preset);
        };
        rescheduleTo(user, taskId, target, reply);
    }

    public void rescheduleTo(User user, long taskId, Instant target, Replier reply) {
        conversations.clear(user.getId());
        Task task = taskService.reschedule(user, taskId, target);
        reply.send(msg.get(user, "resch.done", fmt.line(user, task, clock.instant())));
    }

    public void askRescheduleTime(User user, long taskId, Replier reply) {
        conversations.set(user.getId(), ConversationState.AWAITING_RESCHEDULE_TIME, Map.of("taskId", taskId));
        reply.send(msg.get(user, "resch.prompt"), keyboards.reschedulePresets(user, taskId));
    }

    /** Picks the task a bare /done or /start_task refers to, or lists candidates with buttons. */
    public Optional<Task> resolveSingle(User user, String action, Replier reply) {
        List<Task> candidates = taskService.findActionable(user);
        Instant now = clock.instant();
        List<Task> started = candidates.stream().filter(t -> t.getStatus() == TaskStatus.STARTED).toList();
        List<Task> notified = candidates.stream().filter(t -> t.getStatus() == TaskStatus.NOTIFIED).toList();
        List<Task> today = candidates.stream()
                .filter(t -> t.getScheduledAt() != null && user.today(now).equals(user.today(t.getScheduledAt())))
                .toList();
        List<Task> pool = !started.isEmpty() ? started : !notified.isEmpty() ? notified : today;
        if (pool.size() == 1) {
            return Optional.of(pool.get(0));
        }
        if (pool.isEmpty()) {
            reply.send(msg.get(user, action + ".none"));
            return Optional.empty();
        }
        InlineKeyboard.Builder kb = InlineKeyboard.builder();
        for (Task t : pool) {
            kb.row(InlineKeyboard.btn("#" + t.getId() + " " + t.getTitle(), Callbacks.of(action, t.getId())));
        }
        reply.send(msg.get(user, action + ".which"), kb.build());
        return Optional.empty();
    }
}
