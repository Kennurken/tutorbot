package dev.kennurken.tutorbot.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.knowledge.KnowledgeService;
import dev.kennurken.tutorbot.notification.NotificationRepository;
import dev.kennurken.tutorbot.notification.NotificationStatus;
import dev.kennurken.tutorbot.support.AbstractIT;
import dev.kennurken.tutorbot.task.event.TaskEventRepository;
import dev.kennurken.tutorbot.task.event.TaskEventType;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.verification.SessionStatus;
import dev.kennurken.tutorbot.verification.VerificationService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The core loop end to end: create -> notify -> start -> done -> verify -> pass -> knowledge. */
class TaskLifecycleIT extends AbstractIT {

    @Autowired
    TaskService tasks;
    @Autowired
    TaskLifecycleService lifecycle;
    @Autowired
    VerificationService verification;
    @Autowired
    KnowledgeService knowledge;
    @Autowired
    TaskEventRepository events;
    @Autowired
    NotificationRepository notifications;

    @Test
    void fullLoopEndsInCompletedTaskAndKnowledgeRecord() {
        User user = registerUser();
        Instant at = clock.instant().plus(Duration.ofMinutes(5));
        Task task = tasks.create(user, new CreateTaskCommand("English — Present Perfect", null, "English",
                "Present Perfect", TaskType.LANGUAGE, Priority.MEDIUM, at, 20, null, true, null, null));
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);

        // time comes: the tick notifies and queues the escalation ladder
        clock.advance(Duration.ofMinutes(6));
        assertThat(lifecycle.notifyDue()).isEqualTo(1);
        Task notified = tasks.getById(task.getId());
        assertThat(notified.getStatus()).isEqualTo(TaskStatus.NOTIFIED);
        assertThat(notifications.findAll()).extracting(n -> n.getKind().name())
                .containsExactlyInAnyOrder("TASK_START", "REMINDER", "OVERDUE");

        // user starts: pending nudges are cancelled
        tasks.start(user, task.getId());
        assertThat(notifications.findAll()).allMatch(n -> n.getStatus() == NotificationStatus.CANCELLED);

        // /done -> pending verification; second /done is rejected by the state machine
        Task reported = tasks.reportDone(user, task.getId());
        assertThat(reported.getStatus()).isEqualTo(TaskStatus.PENDING_VERIFICATION);
        assertThatThrownBy(() -> tasks.reportDone(user, task.getId())).isInstanceOf(DomainException.class);

        // verification with the fake examiner: two good answers -> PASS
        VerificationService.StartResult start = verification.start(user, tasks.getById(task.getId()));
        assertThat(start.question()).isNotBlank();
        assertThat(tasks.getById(task.getId()).getStatus()).isEqualTo(TaskStatus.VERIFICATION_IN_PROGRESS);

        VerificationService.AnswerResult tooShort = verification.answer(user, "ok");
        assertThat(tooShort.kind()).isEqualTo(VerificationService.AnswerKind.TOO_SHORT);

        String good = "Present Perfect links a past action to now: I have lost my keys means they are still lost. "
                + "Past Simple is finished time: I lost my keys yesterday.";
        VerificationService.AnswerResult first = verification.answer(user, good);
        assertThat(first.kind()).isEqualTo(VerificationService.AnswerKind.NEXT_QUESTION);
        VerificationService.AnswerResult second = verification.answer(user, good + " Another example: she has lived here since 2019.");
        assertThat(second.kind()).isEqualTo(VerificationService.AnswerKind.FINISHED);
        assertThat(second.verdict().status()).isEqualTo(SessionStatus.PASSED);

        Task done = tasks.getById(task.getId());
        assertThat(done.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(done.getVerificationStatus()).isEqualTo(VerificationStatus.PASSED);
        assertThat(knowledge.profile(user)).singleElement().satisfies(t -> {
            assertThat(t.getSubject()).isEqualTo("English");
            assertThat(t.getTopic()).isEqualTo("Present Perfect");
            assertThat(t.getEstimatedMastery()).isGreaterThan(0.5);
            assertThat(t.getSampleCount()).isEqualTo(1);
        });
        assertThat(events.findByTaskIdOrderByOccurredAt(task.getId())).extracting(e -> e.getEventType())
                .contains(TaskEventType.TASK_CREATED, TaskEventType.TASK_SCHEDULED, TaskEventType.TASK_NOTIFIED,
                        TaskEventType.TASK_STARTED, TaskEventType.TASK_COMPLETION_REPORTED,
                        TaskEventType.VERIFICATION_STARTED, TaskEventType.VERIFICATION_PASSED, TaskEventType.TASK_COMPLETED);
    }

    @Test
    void weakAnswersFailAndScheduleAReviewTask() {
        User user = registerUser();
        Task task = tasks.create(user, CreateTaskCommand.simple("Java HashMap", "Java", TaskType.THEORY,
                clock.instant().minus(Duration.ofMinutes(1)), 30));
        tasks.reportDone(user, task.getId());
        verification.start(user, tasks.getById(task.getId()));
        verification.answer(user, "I read it and I understood everything about it, really.");
        verification.answer(user, "I understood everything, trust me, there is nothing to add here.");
        Task failed = tasks.getById(task.getId());
        assertThat(failed.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(failed.getVerificationStatus()).isEqualTo(VerificationStatus.FAILED);
        // consequence engine: a 20-minute review task tomorrow, same subject
        assertThat(tasks.findByStatuses(user, java.util.List.of(TaskStatus.SCHEDULED)))
                .singleElement().satisfies(review -> {
                    assertThat(review.getTitle()).startsWith("Повторение:");
                    assertThat(review.getSubject()).isEqualTo("Java");
                    assertThat(review.getEstimatedMinutes()).isEqualTo(20);
                });
    }

    @Test
    void missedAfterGraceAddsMinutesToNextSessionOfSameSubject() {
        User user = registerUser();
        Instant now = clock.instant();
        Task missed = tasks.create(user, CreateTaskCommand.simple("Java", "Java", TaskType.PROGRAMMING, now, 30));
        Task next = tasks.create(user, CreateTaskCommand.simple("Java", "Java", TaskType.PROGRAMMING,
                now.plus(Duration.ofDays(1)), 45));

        lifecycle.notifyDue();
        clock.advance(Duration.ofMinutes(31));
        assertThat(lifecycle.markMissedAfterGrace()).isEqualTo(1);

        assertThat(tasks.getById(missed.getId()).getStatus()).isEqualTo(TaskStatus.MISSED);
        assertThat(tasks.getById(next.getId()).getExtraMinutes()).isEqualTo(10);
        assertThat(tasks.getById(next.getId()).effectiveMinutes()).isEqualTo(55);
        assertThat(notifications.findAll()).anyMatch(n -> n.getKind().name().equals("MISSED"))
                .anyMatch(n -> n.getKind().name().equals("CONSEQUENCE"));
    }

    @Test
    void downtimeMissIsSystemFaultWithoutConsequence() {
        User user = registerUser();
        Instant now = clock.instant();
        Task task = tasks.create(user, CreateTaskCommand.simple("Math", "Math", TaskType.MATH,
                now.minus(Duration.ofHours(3)), 30));
        tasks.create(user, CreateTaskCommand.simple("Math", "Math", TaskType.MATH, now.plus(Duration.ofDays(1)), 30));

        lifecycle.notifyDue();

        Task missed = tasks.getById(task.getId());
        assertThat(missed.getStatus()).isEqualTo(TaskStatus.MISSED);
        assertThat(missed.isSystemFault()).isTrue();
        assertThat(tasks.findNextScheduledForSubject(user, "Math")).get()
                .extracting(Task::getExtraMinutes).isEqualTo(0);
    }
}
