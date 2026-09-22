package dev.kennurken.tutorbot.quiz;

import dev.kennurken.tutorbot.conversation.ConversationService;
import dev.kennurken.tutorbot.conversation.ConversationState;
import dev.kennurken.tutorbot.knowledge.KnowledgeService;
import dev.kennurken.tutorbot.knowledge.KnowledgeTopic;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.notification.NotificationKind;
import dev.kennurken.tutorbot.notification.NotificationService;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskKind;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskType;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.verification.VerificationService;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Daily retrieval practice: one short exam on the topic whose estimated retention has dropped
 * the most. Reuses the verification engine (a quiz is a 5-minute QUIZ task that goes straight
 * to its exam), so a passed quiz refreshes the knowledge profile exactly like a passed task.
 * Ignoring the quiz costs nothing: when the session expires the task is cancelled.
 */
@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);
    static final double RETENTION_THRESHOLD = 0.65;
    static final Duration MIN_GAP = Duration.ofHours(20);
    static final int QUIZ_QUESTIONS = 3;
    static final int QUIZ_MINUTES = 5;

    private final KnowledgeService knowledge;
    private final TaskService taskService;
    private final VerificationService verification;
    private final ConversationService conversations;
    private final NotificationService notifications;
    private final BotMessages msg;
    private final Clock clock;

    public QuizService(KnowledgeService knowledge, TaskService taskService, VerificationService verification,
                       ConversationService conversations, NotificationService notifications, BotMessages msg,
                       Clock clock) {
        this.knowledge = knowledge;
        this.taskService = taskService;
        this.verification = verification;
        this.conversations = conversations;
        this.notifications = notifications;
        this.msg = msg;
        this.clock = clock;
    }

    /** @return the quizzed topic, or empty when nothing needs practice or the user is busy/paused. */
    public Optional<KnowledgeTopic> startDailyQuiz(User user) {
        if (user.isPaused(clock.instant()) || user.isInRecoveryMode(clock.instant())) {
            return Optional.empty();
        }
        if (conversations.current(user.getId()).state() != ConversationState.IDLE
                || verification.activeSession(user).isPresent()) {
            return Optional.empty();
        }
        Optional<KnowledgeTopic> candidate = knowledge.quizCandidate(user, RETENTION_THRESHOLD, MIN_GAP);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        KnowledgeTopic topic = candidate.get();
        TaskType type = parseType(topic.getTaskType());
        Task task = taskService.create(user, new CreateTaskCommand(
                msg.get(user, "quiz.title", topic.getTopic()),
                msg.get(user, "quiz.description", topic.getTopic()),
                topic.getSubject(), topic.getTopic(), type, Priority.LOW, clock.instant(), QUIZ_MINUTES, null,
                true, null, null), TaskKind.QUIZ);
        task = taskService.reportDone(user, task.getId());
        VerificationService.StartResult started = verification.start(user, task, QUIZ_QUESTIONS);
        knowledge.markQuizzed(topic);

        String text = msg.get(user, "quiz.start", Html.esc(topic.getSubject()), Html.esc(topic.getTopic()),
                Math.round(knowledge.retentionNow(topic) * 100))
                + "\n\n" + msg.get(user, "verification.question", 1, started.session().getMaxQuestions())
                + "\n" + Html.esc(started.question());
        notifications.schedule(user.getId(), task.getId(), NotificationKind.QUIZ, clock.instant(), text, null,
                "quiz:" + user.getId() + ":" + user.today(clock.instant()));
        log.info("Quiz started for user {} on {}/{}", user.getId(), topic.getSubject(), topic.getTopic());
        return Optional.of(topic);
    }

    private static TaskType parseType(String raw) {
        if (raw == null) {
            return TaskType.THEORY;
        }
        try {
            return TaskType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return TaskType.THEORY;
        }
    }
}
