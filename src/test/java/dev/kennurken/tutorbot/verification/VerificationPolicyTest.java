package dev.kennurken.tutorbot.verification;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kennurken.tutorbot.ai.dto.VerificationStep.FinalVerdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationPolicyTest {

    @Test
    void difficultyStaysWithinBounds() {
        assertThat(VerificationPolicy.adjustDifficulty(4, 1)).isEqualTo(4);
        assertThat(VerificationPolicy.adjustDifficulty(1, -1)).isEqualTo(1);
        assertThat(VerificationPolicy.adjustDifficulty(2, 5)).isEqualTo(3);
        assertThat(VerificationPolicy.adjustDifficulty(2, null)).isEqualTo(2);
    }

    @Test
    void minAndMaxQuestionsAreEnforcedByTheBackend() {
        assertThat(VerificationPolicy.mustContinue(1, 2)).isTrue();
        assertThat(VerificationPolicy.mustContinue(2, 2)).isFalse();
        assertThat(VerificationPolicy.mustFinish(4, 4)).isTrue();
        assertThat(VerificationPolicy.mustFinish(3, 4)).isFalse();
    }

    @Test
    void modelPassOnLowScoredAnswersBecomesUncertain() {
        FinalVerdict inconsistent = new FinalVerdict("PASS", 0.9, 0.9, List.of(), List.of(), "");
        VerificationPolicy.Verdict v = VerificationPolicy.decide(inconsistent, List.of(0.3, 0.4));
        assertThat(v.status()).isEqualTo(SessionStatus.UNCERTAIN);
    }

    @Test
    void lowModelConfidenceBecomesUncertain() {
        FinalVerdict unsure = new FinalVerdict("FAIL", 0.4, 0.3, List.of("x"), List.of(), "");
        assertThat(VerificationPolicy.decide(unsure, List.of(0.4, 0.4)).status()).isEqualTo(SessionStatus.UNCERTAIN);
    }

    @Test
    void consistentVerdictsPassThrough() {
        FinalVerdict pass = new FinalVerdict("PASS", 0.85, 0.9, List.of(), List.of("clear"), "good");
        FinalVerdict fail = new FinalVerdict("FAIL", 0.2, 0.9, List.of("basics"), List.of(), "weak");
        assertThat(VerificationPolicy.decide(pass, List.of(0.8, 0.9)).status()).isEqualTo(SessionStatus.PASSED);
        assertThat(VerificationPolicy.decide(fail, List.of(0.2, 0.3)).status()).isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void missingVerdictFallsBackToAverageScore() {
        assertThat(VerificationPolicy.decide(null, List.of(0.8, 0.9)).status()).isEqualTo(SessionStatus.PASSED);
        assertThat(VerificationPolicy.decide(null, List.of(0.5, 0.6)).status()).isEqualTo(SessionStatus.UNCERTAIN);
        assertThat(VerificationPolicy.decide(null, List.of(0.1, 0.2)).status()).isEqualTo(SessionStatus.FAILED);
    }

    @Test
    void wordCountIgnoresWhitespace() {
        assertThat(VerificationPolicy.wordCount("  one   two three ")).isEqualTo(3);
        assertThat(VerificationPolicy.wordCount(null)).isZero();
    }
}
