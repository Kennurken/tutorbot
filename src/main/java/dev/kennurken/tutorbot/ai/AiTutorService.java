package dev.kennurken.tutorbot.ai;

import dev.kennurken.tutorbot.ai.dto.SkipAnalysis;
import dev.kennurken.tutorbot.ai.dto.TaskIntentResult;
import dev.kennurken.tutorbot.ai.dto.VerificationContext;
import dev.kennurken.tutorbot.ai.dto.VerificationStep;
import dev.kennurken.tutorbot.ai.dto.WeeklyNarrative;
import dev.kennurken.tutorbot.ai.prompt.Prompts;
import dev.kennurken.tutorbot.user.User;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Typed facade over {@link AiGateway}: one method per job, each returning {@link Optional}
 * so callers are forced to handle "no AI available" explicitly with a deterministic fallback.
 */
@Service
public class AiTutorService {

    private static final Logger log = LoggerFactory.getLogger(AiTutorService.class);
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm (EEEE)");

    private final AiGateway gateway;

    public AiTutorService(AiGateway gateway) {
        this.gateway = gateway;
    }

    public Optional<TaskIntentResult> parseTaskIntent(User user, String text, Instant now) {
        ZonedDateTime local = now.atZone(user.zone());
        String userPrompt = """
                now_local: %s
                timezone: %s
                message: %s
                """.formatted(LOCAL.format(local), user.getTimezone(), text);
        return call(AiCall.of("task_intent", Prompts.TASK_INTENT_VERSION, Prompts.TASK_INTENT_SYSTEM, userPrompt,
                TaskIntentResult.class, user.getId()));
    }

    public Optional<VerificationStep> verificationStep(User user, VerificationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("user_language: ").append(ctx.userLanguage()).append('\n');
        sb.append("task_title: ").append(ctx.taskTitle()).append('\n');
        sb.append("task_type: ").append(ctx.taskType()).append('\n');
        sb.append("subject: ").append(nullSafe(ctx.subject())).append('\n');
        sb.append("topic: ").append(nullSafe(ctx.topic())).append('\n');
        sb.append("description: ").append(nullSafe(ctx.description())).append('\n');
        sb.append("known_gaps: ").append(ctx.knownGaps()).append('\n');
        sb.append("difficulty: ").append(ctx.difficulty()).append(" (1=basic, 4=expert)\n");
        sb.append("min_questions: ").append(ctx.minQuestions()).append('\n');
        sb.append("max_questions: ").append(ctx.maxQuestions()).append('\n');
        sb.append("answered_questions: ").append(ctx.answeredQuestions()).append('\n');
        sb.append("must_finish_now: ").append(ctx.mustFinishNow()).append('\n');
        sb.append("must_continue: ").append(ctx.mustContinue()).append('\n');
        sb.append("\nTRANSCRIPT:\n");
        int i = 1;
        for (VerificationContext.Turn turn : ctx.transcript()) {
            sb.append("Q").append(i).append(": ").append(turn.question()).append('\n');
            if (turn.answer() != null) {
                sb.append("A").append(i).append(": ").append(turn.answer()).append('\n');
            }
            i++;
        }
        if (ctx.lastAnswer() != null) {
            sb.append("\nLAST_ANSWER: ").append(ctx.lastAnswer()).append(" END_LAST_ANSWER\n");
        }
        return call(new AiCall<>("verification_step", Prompts.VERIFICATION_VERSION, Prompts.VERIFICATION_SYSTEM,
                sb.toString(), VerificationStep.class, user.getId(), 700, 0.3));
    }

    public Optional<WeeklyNarrative> weeklyNarrative(User user, String statsJson) {
        String userPrompt = "user_language: " + user.getLanguage() + "\nstats: " + statsJson + "\n";
        return call(new AiCall<>("weekly_review", Prompts.WEEKLY_REVIEW_VERSION, Prompts.WEEKLY_REVIEW_SYSTEM,
                userPrompt, WeeklyNarrative.class, user.getId(), 900, 0.4));
    }

    public Optional<SkipAnalysis> analyzeSkip(User user, String taskTitle, String reason) {
        String userPrompt = "user_language: " + user.getLanguage() + "\ntask: " + taskTitle + "\nreason: " + reason + "\n";
        return call(AiCall.of("skip_analysis", Prompts.SKIP_ANALYSIS_VERSION, Prompts.SKIP_ANALYSIS_SYSTEM,
                userPrompt, SkipAnalysis.class, user.getId()));
    }

    private <T> Optional<T> call(AiCall<T> call) {
        try {
            return Optional.of(gateway.callJson(call));
        } catch (AiUnavailableException e) {
            log.warn("AI unavailable for {}: {}", call.purpose(), e.getMessage());
            return Optional.empty();
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "-" : s;
    }
}
