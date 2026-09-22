package dev.kennurken.tutorbot.telegram.flow;

import dev.kennurken.tutorbot.ai.AiTutorService;
import dev.kennurken.tutorbot.ai.dto.GoalPlan;
import dev.kennurken.tutorbot.conversation.ConversationService;
import dev.kennurken.tutorbot.conversation.ConversationState;
import dev.kennurken.tutorbot.goal.Goal;
import dev.kennurken.tutorbot.knowledge.KnowledgeService;
import dev.kennurken.tutorbot.knowledge.KnowledgeTopic;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.messaging.InlineKeyboard;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskType;
import dev.kennurken.tutorbot.telegram.Replier;
import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Goal → ordered tasks. The model proposes the steps; nothing is created until the user
 * confirms, and the schedule (one step per day at a chosen time) is decided here, not by the model.
 */
@Component
public class GoalPlanFlow {

    public static final LocalTime DEFAULT_TIME = LocalTime.of(19, 0);

    private final AiTutorService ai;
    private final KnowledgeService knowledge;
    private final TaskService tasks;
    private final ConversationService conversations;
    private final BotMessages msg;
    private final JsonMapper json;
    private final Clock clock;

    public GoalPlanFlow(AiTutorService ai, KnowledgeService knowledge, TaskService tasks,
                        ConversationService conversations, BotMessages msg, JsonMapper json, Clock clock) {
        this.ai = ai;
        this.knowledge = knowledge;
        this.tasks = tasks;
        this.conversations = conversations;
        this.msg = msg;
        this.json = json;
        this.clock = clock;
    }

    public void propose(User user, Goal goal, LocalTime time, Replier reply) {
        List<String> subjects = knowledge.profile(user).stream().map(KnowledgeTopic::getSubject).distinct().toList();
        Optional<GoalPlan> plan = ai.decomposeGoal(user, goal.getTitle(), subjects);
        if (plan.isEmpty()) {
            reply.send(msg.get(user, "goals.plan.unavailable"));
            return;
        }
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("goalId", goal.getId());
        ctx.put("time", time.toString());
        ctx.put("plan", json.convertValue(plan.get(), Map.class));
        conversations.set(user.getId(), ConversationState.AWAITING_GOAL_PLAN_CONFIRMATION, ctx);

        StringBuilder sb = new StringBuilder(msg.get(user, "goals.plan.header", Html.esc(goal.getTitle()))).append("\n");
        if (plan.get().summary() != null) {
            sb.append("<i>").append(Html.esc(plan.get().summary())).append("</i>\n");
        }
        sb.append("\n");
        int i = 1;
        for (GoalPlan.Step step : plan.get().steps()) {
            sb.append(i++).append(". ").append(Html.esc(step.title())).append(" · ")
                    .append(step.minutes() == null ? 30 : step.minutes()).append("m\n");
        }
        sb.append("\n").append(msg.get(user, "goals.plan.schedule", plan.get().steps().size(), time.toString()));
        reply.send(sb.toString(), InlineKeyboard.builder()
                .row(InlineKeyboard.btn(msg.get(user, "btn.goalplan.create", plan.get().steps().size()),
                                Callbacks.of(Callbacks.GOAL_PLAN, "yes")),
                        InlineKeyboard.btn(msg.get(user, "btn.no"), Callbacks.of(Callbacks.GOAL_PLAN, "no")))
                .build());
    }

    public void confirm(User user, boolean yes, Replier reply) {
        ConversationService.Snapshot snapshot = conversations.current(user.getId());
        conversations.clear(user.getId());
        if (snapshot.state() != ConversationState.AWAITING_GOAL_PLAN_CONFIRMATION || snapshot.context().get("plan") == null) {
            reply.send(msg.get(user, "add.nodraft"));
            return;
        }
        if (!yes) {
            reply.send(msg.get(user, "add.cancelled"));
            return;
        }
        GoalPlan plan = json.convertValue(snapshot.context().get("plan"), GoalPlan.class);
        Long goalId = ((Number) snapshot.context().get("goalId")).longValue();
        LocalTime time = LocalTime.parse(snapshot.text("time"));
        ZonedDateTime day = clock.instant().atZone(user.zone()).toLocalDate().plusDays(1).atTime(time).atZone(user.zone());
        int created = 0;
        Task first = null;
        for (GoalPlan.Step step : plan.steps()) {
            TaskType type = parseType(step.type());
            int minutes = step.minutes() == null ? 30 : Math.max(5, Math.min(240, step.minutes()));
            Task task = tasks.create(user, new CreateTaskCommand(step.title(), null, step.subject(), step.topic(), type,
                    Priority.MEDIUM, day.toInstant(), minutes, null, type != TaskType.OTHER, goalId, null));
            if (first == null) {
                first = task;
            }
            created++;
            day = day.plusDays(1);
        }
        reply.send(msg.get(user, "goals.plan.created", created, first == null ? "" : first.getTitle(),
                first == null ? "" : first.getScheduledAt().atZone(user.zone()).toLocalDate().toString()));
    }

    private static TaskType parseType(String raw) {
        if (raw == null) {
            return TaskType.THEORY;
        }
        try {
            return TaskType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return TaskType.THEORY;
        }
    }
}
