package dev.kennurken.tutorbot.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.kennurken.tutorbot.knowledge.KnowledgeModel.State;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class KnowledgeModelTest {

    private static final State FRESH = new State(0, 0, 0, KnowledgeModel.INITIAL_STABILITY_DAYS);

    @Test
    void firstStrongAnswerMovesMasteryALotButNotToCertainty() {
        State s = KnowledgeModel.update(FRESH, 0.9, 0.9);

        assertThat(s.sampleCount()).isEqualTo(1);
        assertThat(s.mastery()).isBetween(0.7, 0.9);
        assertThat(s.confidence()).isLessThan(0.5);
    }

    @Test
    void confidenceNeverReachesOne() {
        State s = FRESH;
        for (int i = 0; i < 50; i++) {
            s = KnowledgeModel.update(s, 0.95, 1.0);
        }
        assertThat(s.confidence()).isLessThanOrEqualTo(KnowledgeModel.MAX_CONFIDENCE);
        assertThat(s.mastery()).isCloseTo(0.95, within(0.02));
    }

    @Test
    void lowExaminerConfidenceMovesTheEstimateLess() {
        State confident = KnowledgeModel.update(FRESH, 0.9, 1.0);
        State unsure = KnowledgeModel.update(FRESH, 0.9, 0.2);

        assertThat(unsure.mastery()).isLessThan(confident.mastery());
    }

    @Test
    void passesLengthenStabilityAndFailuresShortenIt() {
        State passed = KnowledgeModel.update(FRESH, 0.9, 0.9);
        State failed = KnowledgeModel.update(FRESH, 0.2, 0.9);

        assertThat(passed.stabilityDays()).isGreaterThan(KnowledgeModel.INITIAL_STABILITY_DAYS);
        assertThat(failed.stabilityDays()).isLessThan(KnowledgeModel.INITIAL_STABILITY_DAYS);
    }

    @Test
    void retentionDecaysWithTimeAndTriggersReview() {
        State s = KnowledgeModel.update(FRESH, 0.9, 0.9);
        Instant verified = Instant.parse("2026-09-01T00:00:00Z");

        double sameDay = KnowledgeModel.retention(s, verified, verified);
        double later = KnowledgeModel.retention(s, verified, verified.plus(Duration.ofDays(30)));

        assertThat(sameDay).isCloseTo(s.mastery(), within(0.001));
        assertThat(later).isLessThan(sameDay / 2);
        assertThat(KnowledgeModel.reviewRecommended(s, verified, verified)).isFalse();
        assertThat(KnowledgeModel.reviewRecommended(s, verified, verified.plus(Duration.ofDays(30)))).isTrue();
    }
}
