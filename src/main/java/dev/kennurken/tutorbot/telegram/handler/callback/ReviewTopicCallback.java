package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.common.NotFoundException;
import dev.kennurken.tutorbot.knowledge.KnowledgeService;
import dev.kennurken.tutorbot.knowledge.KnowledgeTopic;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskKind;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskType;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * [Review: topic] under /knowledge: a 15-minute REVIEW task about an hour from now
 * (next morning if that would land in quiet hours).
 */
@Component
public class ReviewTopicCallback implements CallbackHandler {

    private static final int REVIEW_MINUTES = 15;

    private final KnowledgeService knowledge;
    private final TaskService tasks;
    private final TaskFormatter fmt;
    private final BotMessages msg;
    private final Clock clock;

    public ReviewTopicCallback(KnowledgeService knowledge, TaskService tasks, TaskFormatter fmt, BotMessages msg,
                               Clock clock) {
        this.knowledge = knowledge;
        this.tasks = tasks;
        this.fmt = fmt;
        this.msg = msg;
        this.clock = clock;
    }

    @Override
    public String action() {
        return Callbacks.REVIEW_TOPIC;
    }

    @Override
    public void handle(CallbackContext ctx) {
        User user = ctx.user();
        long topicId = ctx.longArg(0);
        KnowledgeTopic topic = knowledge.find(user, topicId).orElseThrow(() -> new NotFoundException("Topic", topicId));
        Instant at = nextSlot(user, clock.instant());
        TaskType type = TaskType.THEORY;
        if (topic.getTaskType() != null) {
            try {
                type = TaskType.valueOf(topic.getTaskType());
            } catch (IllegalArgumentException ignored) {
                // keep THEORY
            }
        }
        Task task = tasks.create(user, new CreateTaskCommand(msg.get(user, "consequence.review.title", topic.getTopic()),
                null, topic.getSubject(), topic.getTopic(), type, Priority.MEDIUM, at, REVIEW_MINUTES, null, true,
                null, null), TaskKind.REVIEW);
        ctx.reply().send(msg.get(user, "knowledge.review.created", fmt.line(user, task, clock.instant())));
    }

    /** One hour from now rounded to 5 minutes; if that is inside quiet hours, 09:00 next morning. */
    Instant nextSlot(User user, Instant now) {
        Instant candidate = now.plus(Duration.ofHours(1)).truncatedTo(ChronoUnit.MINUTES);
        long minute = candidate.atZone(user.zone()).getMinute();
        candidate = candidate.plus(Duration.ofMinutes((5 - minute % 5) % 5));
        if (user.getSettings().isQuietAt(candidate, user.zone())) {
            ZonedDateTime end = user.getSettings().quietWindowEnd(candidate, user.zone()).atZone(user.zone());
            return end.plusHours(1).toInstant();
        }
        return candidate;
    }
}
