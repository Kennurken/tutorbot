package dev.kennurken.tutorbot.review;

import dev.kennurken.tutorbot.ai.AiInteractionRepository;
import dev.kennurken.tutorbot.ai.AiTutorService;
import dev.kennurken.tutorbot.ai.dto.WeeklyNarrative;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.verification.VerificationService;
import dev.kennurken.tutorbot.verification.VerificationSession;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the weekly review: deterministic statistics always, model narrative when available.
 * The model only ever sees the stats JSON, so every sentence it writes can be checked against numbers.
 */
@Service
public class WeeklyReviewService {

    private final WeeklyReviewRepository reviews;
    private final TaskService taskService;
    private final VerificationService verificationService;
    private final AiInteractionRepository aiInteractions;
    private final AiTutorService ai;
    private final BotMessages msg;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;

    public WeeklyReviewService(WeeklyReviewRepository reviews, TaskService taskService,
                               VerificationService verificationService, AiInteractionRepository aiInteractions,
                               AiTutorService ai, BotMessages msg, TransactionTemplate tx, JsonMapper json, Clock clock) {
        this.reviews = reviews;
        this.taskService = taskService;
        this.verificationService = verificationService;
        this.aiInteractions = aiInteractions;
        this.ai = ai;
        this.msg = msg;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public Optional<WeeklyReview> find(User user, LocalDate weekStart) {
        return reviews.findByUserIdAndWeekStart(user.getId(), weekStart);
    }

    /** Generates (or regenerates) the review for the week containing {@code weekStart}. */
    public WeeklyReview generate(User user, LocalDate weekStart) {
        Instant from = weekStart.atStartOfDay(user.zone()).toInstant();
        Instant to = weekStart.plusDays(7).atStartOfDay(user.zone()).toInstant();

        WeeklyStats stats = tx.execute(status -> {
            List<Task> tasks = taskService.findInWindow(user, from, to);
            List<VerificationSession> sessions = new ArrayList<>();
            for (Task t : tasks) {
                sessions.addAll(verificationService.sessionsOf(t));
            }
            double cost = aiInteractions.sumCostSince(user.getId(), from).doubleValue();
            return WeeklyStatsCalculator.compute(weekStart, user.zone(), tasks, sessions, cost);
        });
        String statsJson = json.writeValueAsString(stats);

        Optional<WeeklyNarrative> narrative = stats.planned() == 0 ? Optional.empty() : ai.weeklyNarrative(user, statsJson);
        String text = narrative.map(this::renderNarrative).orElse(null);

        return tx.execute(status -> {
            Instant now = clock.instant();
            WeeklyReview review = reviews.findByUserIdAndWeekStart(user.getId(), weekStart)
                    .map(existing -> {
                        existing.update(statsJson, text, narrative.isPresent(), now);
                        return existing;
                    })
                    .orElseGet(() -> new WeeklyReview(user.getId(), weekStart, statsJson, text, narrative.isPresent(), now));
            return reviews.save(review);
        });
    }

    private String renderNarrative(WeeklyNarrative n) {
        StringBuilder sb = new StringBuilder(Html.esc(n.summary())).append("\n");
        if (n.observations() != null && !n.observations().isEmpty()) {
            sb.append("\n");
            for (String o : n.observations()) {
                sb.append("• ").append(Html.esc(o)).append("\n");
            }
        }
        if (n.recommendations() != null && !n.recommendations().isEmpty()) {
            sb.append("\n");
            for (WeeklyNarrative.Recommendation r : n.recommendations()) {
                sb.append("→ ").append(Html.esc(r.change()));
                if (r.reason() != null && !r.reason().isBlank()) {
                    sb.append(" — <i>").append(Html.esc(r.reason())).append("</i>");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public String render(User user, WeeklyReview review) {
        WeeklyStats s = json.readValue(review.getStats(), WeeklyStats.class);
        StringBuilder sb = new StringBuilder();
        sb.append(msg.get(user, "review.header", s.weekStart(), s.weekEnd())).append("\n\n");
        sb.append(msg.get(user, "review.tasks", s.planned(), s.started(), s.completed(), s.verified(), s.missed(),
                s.skipped(), s.failedVerifications())).append("\n");
        sb.append(msg.get(user, "review.rates", pct(s.executionRate()), pct(s.verificationPassRate()))).append("\n");
        if (!s.byTimeOfDay().isEmpty()) {
            WeeklyStats.HourBucket worst = s.byTimeOfDay().stream().filter(b -> b.planned() >= 2)
                    .min(java.util.Comparator.comparingDouble(WeeklyStats.HourBucket::rate)).orElse(null);
            WeeklyStats.HourBucket best = s.byTimeOfDay().stream().filter(b -> b.planned() >= 2)
                    .max(java.util.Comparator.comparingDouble(WeeklyStats.HourBucket::rate)).orElse(null);
            if (best != null && worst != null && best != worst) {
                sb.append(msg.get(user, "review.time", best.range(), pct(best.rate()), worst.range(), pct(worst.rate())))
                        .append("\n");
            }
        }
        if (s.longTasks().planned() > 0) {
            sb.append(msg.get(user, "review.long", s.longTasks().completed(), s.longTasks().planned())).append("\n");
        }
        if (!s.subjects().isEmpty()) {
            sb.append("\n").append(msg.get(user, "review.subjects")).append("\n");
            for (WeeklyStats.SubjectStat st : s.subjects()) {
                sb.append("• ").append(Html.esc(st.subject())).append(": ").append(st.completed()).append("/")
                        .append(st.planned());
                if (st.avgScore() != null) {
                    sb.append(" · ").append(pct(st.avgScore()));
                }
                sb.append("\n");
            }
        }
        if (s.mostCommonSkipCategory() != null) {
            sb.append(msg.get(user, "review.skip", msg.get(user, "skip.cat." + s.mostCommonSkipCategory()))).append("\n");
        }
        if (review.getNarrative() != null) {
            sb.append("\n").append(review.getNarrative());
        } else if (s.planned() == 0) {
            sb.append("\n").append(msg.get(user, "review.empty"));
        }
        sb.append("\n").append(msg.get(user, "review.cost", String.format("%.3f", s.aiCostUsd())));
        return sb.toString();
    }

    private static String pct(double v) {
        return Math.round(v * 100) + "%";
    }
}
