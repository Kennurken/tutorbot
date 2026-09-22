package dev.kennurken.tutorbot.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * One turn of the examiner: how good was the last answer, and what happens next.
 * The backend can override {@code decision} (min/max questions) but never the scores.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificationStep(
        @Valid Evaluation evaluation,
        @NotNull @Pattern(regexp = "CONTINUE|FINISH") String decision,
        String nextQuestion,
        Integer difficultyDelta,
        @Valid FinalVerdict finalVerdict) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Evaluation(
            @DecimalMin("0") @DecimalMax("1") double score,
            String feedback,
            boolean evidenceOfUnderstanding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FinalVerdict(
            @NotNull @Pattern(regexp = "PASS|FAIL|UNCERTAIN") String verdict,
            @DecimalMin("0") @DecimalMax("1") double score,
            @DecimalMin("0") @DecimalMax("1") double confidence,
            List<String> knowledgeGaps,
            List<String> strengths,
            String summary) {
    }

    public boolean wantsToFinish() {
        return "FINISH".equals(decision);
    }
}
