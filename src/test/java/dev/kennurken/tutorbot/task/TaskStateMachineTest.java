package dev.kennurken.tutorbot.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kennurken.tutorbot.common.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TaskStateMachineTest {

    @ParameterizedTest
    @CsvSource({
            "CREATED, SCHEDULED",
            "SCHEDULED, NOTIFIED",
            "NOTIFIED, STARTED",
            "STARTED, REPORTED_DONE",
            "REPORTED_DONE, PENDING_VERIFICATION",
            "PENDING_VERIFICATION, VERIFICATION_IN_PROGRESS",
            "VERIFICATION_IN_PROGRESS, COMPLETED",
            "VERIFICATION_IN_PROGRESS, FAILED",
            "FAILED, PENDING_VERIFICATION",
            "NOTIFIED, MISSED",
            "MISSED, SCHEDULED",
            "SKIPPED, SCHEDULED",
            "SCHEDULED, SCHEDULED"
    })
    void happyPathAndRecoveryTransitionsAreAllowed(TaskStatus from, TaskStatus to) {
        assertThat(TaskStateMachine.isAllowed(from, to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "COMPLETED, STARTED",
            "COMPLETED, SCHEDULED",
            "CANCELLED, SCHEDULED",
            "REPORTED_DONE, STARTED",
            "CREATED, COMPLETED",
            "SCHEDULED, COMPLETED",
            "PENDING_VERIFICATION, COMPLETED"
    })
    void shortcutsAndResurrectionsAreRejected(TaskStatus from, TaskStatus to) {
        assertThat(TaskStateMachine.isAllowed(from, to)).isFalse();
        assertThatThrownBy(() -> TaskStateMachine.assertAllowed(from, to))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining(from.name())
                .hasMessageContaining(to.name());
    }

    @Test
    void terminalStatesHaveNoOutgoingTransitionsExceptExpired() {
        for (TaskStatus to : TaskStatus.values()) {
            assertThat(TaskStateMachine.isAllowed(TaskStatus.COMPLETED, to)).isFalse();
            assertThat(TaskStateMachine.isAllowed(TaskStatus.CANCELLED, to)).isFalse();
        }
    }
}
