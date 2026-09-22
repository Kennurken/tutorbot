package dev.kennurken.tutorbot.verification;

import dev.kennurken.tutorbot.ai.AiTutorService;
import dev.kennurken.tutorbot.ai.dto.VerificationContext;
import dev.kennurken.tutorbot.ai.dto.VerificationStep;
import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.common.config.AccountabilityProperties;
import dev.kennurken.tutorbot.conversation.ConversationService;
import dev.kennurken.tutorbot.conversation.ConversationState;
import dev.kennurken.tutorbot.knowledge.KnowledgeService;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The verification engine. Each user action is split into: (1) a transaction that reads
 * state, (2) a model call with no transaction open, (3) a transaction that applies the result.
 * Programmatic {@link TransactionTemplate} makes those boundaries explicit; a single
 * {@code @Transactional} method would hold a DB connection for the whole model round-trip.
 */
@Service
public class VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);

    private final VerificationSessionRepository sessions;
    private final VerificationTurnRepository turns;
    private final TaskService taskService;
    private final AiTutorService ai;
    private final KnowledgeService knowledge;
    private final ConversationService conversations;
    private final AccountabilityProperties props;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;

    public VerificationService(VerificationSessionRepository sessions, VerificationTurnRepository turns,
                               TaskService taskService, AiTutorService ai, KnowledgeService knowledge,
                               ConversationService conversations, AccountabilityProperties props,
                               ApplicationEventPublisher publisher, TransactionTemplate tx, JsonMapper json,
                               Clock clock) {
        this.sessions = sessions;
        this.turns = turns;
        this.taskService = taskService;
        this.ai = ai;
        this.knowledge = knowledge;
        this.conversations = conversations;
        this.props = props;
        this.publisher = publisher;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    public record StartResult(VerificationSession session, String question, boolean aiAvailable) {
    }

    public enum AnswerKind { TOO_SHORT, AI_UNAVAILABLE, NEXT_QUESTION, FINISHED }

    public record AnswerResult(AnswerKind kind, String feedback, String nextQuestion, VerificationPolicy.Verdict verdict,
                               int questionNo, int maxQuestions, Task task) {
    }

    // ------------------------------------------------------------------ start

    public StartResult start(User user, Task task) {
        VerificationSession session = tx.execute(status -> openSession(user, task));
        int maxQuestions = session.getMaxQuestions();

        Optional<VerificationStep> step = ai.verificationStep(user, context(user, task, session, List.of(), null,
                0, maxQuestions));
        String question = step.map(VerificationStep::nextQuestion)
                .filter(q -> q != null && !q.isBlank())
                .orElseGet(() -> FallbackQuestions.question(task.getType(), 0, user.getLanguage(), topicOrTitle(task)));

        tx.executeWithoutResult(status -> {
            VerificationSession s = sessions.findById(session.getId()).orElseThrow();
            addQuestion(s, question);
        });
        return new StartResult(session, question, step.isPresent());
    }

    private VerificationSession openSession(User user, Task task) {
        Optional<VerificationSession> active = sessions.findByUserIdAndStatus(user.getId(), SessionStatus.IN_PROGRESS);
        if (active.isPresent() && !active.get().getTaskId().equals(task.getId())) {
            throw new DomainException("Another verification is in progress (task #" + active.get().getTaskId()
                    + "). Finish it or use /abandon.");
        }
        if (active.isPresent()) {
            throw new DomainException("Verification for task #" + task.getId() + " is already running. Just answer.");
        }
        Task fresh = taskService.requireOwned(user, task.getId());
        if (fresh.getStatus() == TaskStatus.FAILED) {
            fresh = taskService.retryVerification(user, fresh.getId());
        }
        if (fresh.getStatus() != TaskStatus.PENDING_VERIFICATION) {
            throw new DomainException("Task #" + fresh.getId() + " is " + fresh.getStatus() + "; nothing to verify");
        }
        fresh = taskService.beginVerification(fresh);

        Instant now = clock.instant();
        int maxQuestions = Math.max(props.verification().minQuestions(),
                Math.min(user.getSettings().getVerificationMaxQuestions(), props.verification().maxQuestions()));
        VerificationSession session = sessions.save(new VerificationSession(fresh.getId(), user.getId(),
                fresh.getVerificationAttempts(), initialDifficulty(user, fresh), maxQuestions, now,
                now.plus(props.verification().sessionTimeout())));
        conversations.set(user.getId(), ConversationState.IN_VERIFICATION,
                Map.of("taskId", fresh.getId(), "sessionId", session.getId()));
        return session;
    }

    private int initialDifficulty(User user, Task task) {
        if (task.getSubject() == null || task.getTopic() == null) {
            return 2;
        }
        return knowledge.profile(user).stream()
                .filter(t -> t.getSubject().equalsIgnoreCase(task.getSubject()) && t.getTopic().equalsIgnoreCase(task.getTopic()))
                .findFirst()
                .map(t -> t.getSampleCount() >= 2 && t.getEstimatedMastery() >= 0.75 ? 3 : 2)
                .orElse(2);
    }

    private void addQuestion(VerificationSession session, String question) {
        Instant now = clock.instant();
        turns.save(new VerificationTurn(session.getId(), session.getQuestionCount() + 1, question, now));
        session.setQuestionCount(session.getQuestionCount() + 1);
        session.touch(now, now.plus(props.verification().sessionTimeout()));
        sessions.save(session);
    }

    // ----------------------------------------------------------------- answer

    public AnswerResult answer(User user, String text) {
        Prepared prepared = tx.execute(status -> prepare(user, text));
        if (prepared.result() != null) {
            return prepared.result();
        }
        Optional<VerificationStep> step = ai.verificationStep(user, prepared.context());
        if (step.isEmpty()) {
            tx.executeWithoutResult(status -> sessions.findById(prepared.session().getId()).ifPresent(s -> {
                Instant now = clock.instant();
                s.touch(now, now.plus(props.verification().sessionTimeout()));
                sessions.save(s);
            }));
            return new AnswerResult(AnswerKind.AI_UNAVAILABLE, null, null, null,
                    prepared.session().getQuestionCount(), prepared.session().getMaxQuestions(), null);
        }
        return tx.execute(status -> apply(user, prepared, step.get(), text));
    }

    private record Prepared(VerificationSession session, Task task, List<VerificationTurn> turns,
                            VerificationContext context, AnswerResult result) {
    }

    private Prepared prepare(User user, String text) {
        VerificationSession session = sessions.findByUserIdAndStatus(user.getId(), SessionStatus.IN_PROGRESS)
                .orElseThrow(() -> new DomainException("No verification in progress. Use /done first."));
        Task task = taskService.requireOwned(user, session.getTaskId());
        List<VerificationTurn> history = turns.findBySessionIdOrderBySeq(session.getId());
        VerificationTurn current = history.stream().filter(t -> !t.isAnswered()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Session " + session.getId() + " has no open question"));

        int words = VerificationPolicy.wordCount(text);
        if (words < props.verification().minAnswerWords() && session.getShortAnswerStrikes() == 0) {
            session.setShortAnswerStrikes(1);
            sessions.save(session);
            return new Prepared(session, task, history, null, new AnswerResult(AnswerKind.TOO_SHORT, null,
                    current.getQuestion(), null, current.getSeq(), session.getMaxQuestions(), task));
        }

        int answered = (int) history.stream().filter(VerificationTurn::isAnswered).count();
        VerificationContext ctx = context(user, task, session, history, text, answered, session.getMaxQuestions());
        return new Prepared(session, task, history, ctx, null);
    }

    private VerificationContext context(User user, Task task, VerificationSession session, List<VerificationTurn> history,
                                        String lastAnswer, int answered, int maxQuestions) {
        int includingThis = lastAnswer == null ? answered : answered + 1;
        int minQuestions = props.verification().minQuestions();
        List<VerificationContext.Turn> transcript = new ArrayList<>();
        for (VerificationTurn t : history) {
            transcript.add(new VerificationContext.Turn(t.getQuestion(), t.getAnswer()));
        }
        return new VerificationContext(user.getLanguage(), task.getTitle(), task.getType().name(), task.getSubject(),
                task.getTopic(), task.getDescription(), knowledge.knownGaps(user.getId(), task.getSubject()),
                session.getDifficulty(), minQuestions, maxQuestions, answered,
                lastAnswer != null && VerificationPolicy.mustFinish(includingThis, maxQuestions),
                lastAnswer == null || VerificationPolicy.mustContinue(includingThis, minQuestions),
                transcript, lastAnswer);
    }

    private AnswerResult apply(User user, Prepared prepared, VerificationStep step, String text) {
        VerificationSession session = sessions.findById(prepared.session().getId()).orElseThrow();
        Task task = taskService.requireOwned(user, session.getTaskId());
        List<VerificationTurn> history = turns.findBySessionIdOrderBySeq(session.getId());
        VerificationTurn current = history.stream().filter(t -> !t.isAnswered()).findFirst().orElseThrow();
        Instant now = clock.instant();

        VerificationStep.Evaluation eval = step.evaluation();
        Double score = eval == null ? null : eval.score();
        String feedback = eval == null ? null : eval.feedback();
        current.answer(text, score, feedback, now);
        turns.save(current);
        session.setDifficulty(VerificationPolicy.adjustDifficulty(session.getDifficulty(), step.difficultyDelta()));

        int answeredIncludingThis = (int) history.stream().filter(VerificationTurn::isAnswered).count();
        boolean finish = VerificationPolicy.mustFinish(answeredIncludingThis, session.getMaxQuestions())
                || (step.wantsToFinish() && !VerificationPolicy.mustContinue(answeredIncludingThis,
                props.verification().minQuestions()));

        if (!finish) {
            String next = step.nextQuestion() != null && !step.nextQuestion().isBlank()
                    ? step.nextQuestion()
                    : FallbackQuestions.question(task.getType(), answeredIncludingThis, user.getLanguage(), topicOrTitle(task));
            addQuestion(session, next);
            return new AnswerResult(AnswerKind.NEXT_QUESTION, feedback, next, null, session.getQuestionCount(),
                    session.getMaxQuestions(), task);
        }

        List<Double> scores = history.stream().map(VerificationTurn::getScore).toList();
        VerificationPolicy.Verdict verdict = VerificationPolicy.decide(step.finalVerdict(), scores);
        session.finish(verdict.status(), verdict.score(), verdict.confidence(), json.writeValueAsString(verdict), now);
        sessions.save(session);

        Task updated = switch (verdict.status()) {
            case PASSED -> taskService.passVerification(task, verdict.score(), verdict.confidence());
            case FAILED -> taskService.failVerification(task, verdict.score(), verdict.confidence());
            default -> taskService.uncertainVerification(task, verdict.score(), verdict.confidence());
        };
        conversations.clear(user.getId());
        publisher.publishEvent(new VerificationFinished(updated, verdict.status(), verdict.score(), verdict.confidence(),
                verdict.knowledgeGaps(), verdict.strengths(), verdict.summary()));
        log.info("Verification {} for task {} -> {} (score {}, confidence {})", session.getId(), task.getId(),
                verdict.status(), verdict.score(), verdict.confidence());
        return new AnswerResult(AnswerKind.FINISHED, feedback, null, verdict, session.getQuestionCount(),
                session.getMaxQuestions(), updated);
    }

    // ------------------------------------------------------------ maintenance

    public int expireStale() {
        List<VerificationSession> stale = sessions.findByStatusAndExpiresAtBefore(SessionStatus.IN_PROGRESS, clock.instant());
        int count = 0;
        for (VerificationSession s : stale) {
            try {
                tx.executeWithoutResult(status -> {
                    VerificationSession session = sessions.findById(s.getId()).orElseThrow();
                    if (session.getStatus() != SessionStatus.IN_PROGRESS) {
                        return;
                    }
                    session.finish(SessionStatus.EXPIRED, null, null, null, clock.instant());
                    sessions.save(session);
                    Task task = taskService.expireVerification(taskService.getById(session.getTaskId()));
                    conversations.clear(session.getUserId());
                    publisher.publishEvent(new VerificationExpired(task));
                });
                count++;
            } catch (RuntimeException e) {
                log.error("Failed to expire verification session {}", s.getId(), e);
            }
        }
        return count;
    }

    public Optional<Task> abandon(User user) {
        return tx.execute(status -> {
            Optional<VerificationSession> active = sessions.findByUserIdAndStatus(user.getId(), SessionStatus.IN_PROGRESS);
            if (active.isEmpty()) {
                return Optional.empty();
            }
            VerificationSession session = active.get();
            session.finish(SessionStatus.ABANDONED, null, null, null, clock.instant());
            sessions.save(session);
            Task task = taskService.abandonVerification(taskService.requireOwned(user, session.getTaskId()));
            conversations.clear(user.getId());
            return Optional.of(task);
        });
    }

    public Optional<VerificationSession> activeSession(User user) {
        return sessions.findByUserIdAndStatus(user.getId(), SessionStatus.IN_PROGRESS);
    }

    public List<VerificationSession> sessionsOf(Task task) {
        return sessions.findByTaskIdOrderByAttemptNo(task.getId());
    }

    public List<VerificationTurn> turnsOf(VerificationSession session) {
        return turns.findBySessionIdOrderBySeq(session.getId());
    }

    private static String topicOrTitle(Task task) {
        return task.getTopic() != null && !task.getTopic().isBlank() ? task.getTopic() : task.getTitle();
    }
}
