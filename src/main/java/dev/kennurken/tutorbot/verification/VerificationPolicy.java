package dev.kennurken.tutorbot.verification;

import dev.kennurken.tutorbot.ai.dto.VerificationStep;
import java.util.List;

/**
 * Backend rules that constrain the examiner. Pure functions: the model proposes,
 * this class decides what is admissible.
 */
public final class VerificationPolicy {

    static final int MIN_DIFFICULTY = 1;
    static final int MAX_DIFFICULTY = 4;
    static final double PASS_SCORE = 0.7;
    static final double UNCERTAIN_SCORE = 0.5;
    static final double LOW_CONFIDENCE = 0.55;

    private VerificationPolicy() {
    }

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    public static int adjustDifficulty(int current, Integer delta) {
        int d = delta == null ? 0 : Math.max(-1, Math.min(1, delta));
        return Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, current + d));
    }

    /** Whether the session must end after this answer regardless of what the model says. */
    public static boolean mustFinish(int answeredIncludingThis, int maxQuestions) {
        return answeredIncludingThis >= maxQuestions;
    }

    public static boolean mustContinue(int answeredIncludingThis, int minQuestions) {
        return answeredIncludingThis < minQuestions;
    }

    /**
     * Final outcome. The model's verdict is used but sanity-checked against the per-answer
     * scores it produced itself: a PASS on answers it scored 0.3 is inconsistent and becomes UNCERTAIN.
     */
    public static Verdict decide(VerificationStep.FinalVerdict modelVerdict, List<Double> turnScores) {
        double avg = turnScores.stream().filter(s -> s != null).mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (modelVerdict == null) {
            SessionStatus status = avg >= PASS_SCORE ? SessionStatus.PASSED
                    : avg >= UNCERTAIN_SCORE ? SessionStatus.UNCERTAIN : SessionStatus.FAILED;
            return new Verdict(status, avg, 0.5, List.of(), List.of(), null);
        }
        SessionStatus status = switch (modelVerdict.verdict()) {
            case "PASS" -> SessionStatus.PASSED;
            case "FAIL" -> SessionStatus.FAILED;
            default -> SessionStatus.UNCERTAIN;
        };
        if (status == SessionStatus.PASSED && avg < UNCERTAIN_SCORE) {
            status = SessionStatus.UNCERTAIN;
        } else if (status == SessionStatus.FAILED && avg >= 0.85) {
            status = SessionStatus.UNCERTAIN;
        } else if (status != SessionStatus.UNCERTAIN && modelVerdict.confidence() < LOW_CONFIDENCE) {
            status = SessionStatus.UNCERTAIN;
        }
        return new Verdict(status, modelVerdict.score(), modelVerdict.confidence(),
                modelVerdict.knowledgeGaps() == null ? List.of() : modelVerdict.knowledgeGaps(),
                modelVerdict.strengths() == null ? List.of() : modelVerdict.strengths(),
                modelVerdict.summary());
    }

    public record Verdict(SessionStatus status, double score, double confidence, List<String> knowledgeGaps,
                          List<String> strengths, String summary) {
    }
}
