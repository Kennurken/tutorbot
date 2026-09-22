package dev.kennurken.tutorbot.knowledge;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure arithmetic of the knowledge profile. No Spring, no I/O: unit-tested in isolation.
 *
 * <ul>
 *   <li>Mastery update: exponential moving average whose step shrinks with the number of samples
 *       (early samples move the estimate a lot, later ones refine it) and with the examiner's
 *       confidence (an uncertain verdict moves it less).</li>
 *   <li>Confidence: grows toward 1 with every sample, capped at 0.95 because four good answers
 *       are not proof of mastery.</li>
 *   <li>Forgetting: Ebbinghaus-style {@code retention = mastery * exp(-days / stability)}.
 *       Each successful verification lengthens stability (spaced-repetition intuition:
 *       the more often you recall, the slower you forget); a failure shortens it.</li>
 * </ul>
 */
public final class KnowledgeModel {

    public static final double INITIAL_STABILITY_DAYS = 7.0;
    static final double MAX_STABILITY_DAYS = 120.0;
    static final double MIN_STABILITY_DAYS = 3.0;
    static final double MAX_CONFIDENCE = 0.95;
    static final double PASS_THRESHOLD = 0.7;
    static final double REVIEW_THRESHOLD = 0.5;

    private KnowledgeModel() {
    }

    public record State(double mastery, double confidence, int sampleCount, double stabilityDays) {
    }

    public static State update(State current, double score, double examinerConfidence) {
        double clampedScore = clamp01(score);
        double clampedConf = clamp01(examinerConfidence);
        int n = current.sampleCount();

        double step = Math.max(0.2, 1.0 / (n + 1)) * (0.5 + 0.5 * clampedConf);
        double mastery = current.mastery() + step * (clampedScore - current.mastery());

        double confidence = Math.min(MAX_CONFIDENCE, 1 - (1 - current.confidence()) * (1 - 0.3 * clampedConf));

        double stability = clampedScore >= PASS_THRESHOLD
                ? Math.min(MAX_STABILITY_DAYS, current.stabilityDays() * 1.6)
                : Math.max(MIN_STABILITY_DAYS, current.stabilityDays() * 0.7);

        return new State(clamp01(mastery), clamp01(confidence), n + 1, stability);
    }

    /** What we expect the user still remembers now. */
    public static double retention(State state, Instant lastVerifiedAt, Instant now) {
        if (lastVerifiedAt == null) {
            return 0.0;
        }
        double days = Math.max(0, Duration.between(lastVerifiedAt, now).toMinutes() / 1440.0);
        return state.mastery() * Math.exp(-days / state.stabilityDays());
    }

    public static boolean reviewRecommended(State state, Instant lastVerifiedAt, Instant now) {
        return state.sampleCount() > 0
                && state.mastery() >= 0.3
                && retention(state, lastVerifiedAt, now) < REVIEW_THRESHOLD;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
