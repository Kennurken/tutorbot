package dev.kennurken.tutorbot.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kennurken.tutorbot.knowledge.KnowledgeService;
import dev.kennurken.tutorbot.knowledge.KnowledgeTopic;
import dev.kennurken.tutorbot.notification.Notification;
import dev.kennurken.tutorbot.notification.NotificationKind;
import dev.kennurken.tutorbot.notification.NotificationRepository;
import dev.kennurken.tutorbot.notification.NotificationStatus;
import dev.kennurken.tutorbot.support.AbstractIT;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskKind;
import dev.kennurken.tutorbot.task.TaskRepository;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.task.TaskType;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.verification.VerificationService;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Quiet hours, the daily quiz and the evening summary, driven through the scheduler tick. */
class RoutinesIT extends AbstractIT {

    @Autowired
    TickService tick;
    @Autowired
    TaskService taskService;
    @Autowired
    TaskRepository tasks;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    KnowledgeService knowledge;
    @Autowired
    VerificationService verification;

    @Test
    void remindersAreHeldDuringQuietHoursButTheStartIsNot() {
        User user = registerUser(); // clock: Monday 15:00 Asia/Almaty
        user.getSettings().setQuietHours(LocalTime.of(15, 5), LocalTime.of(15, 25)); // ends before the 30-min grace
        user.getSettings().setQuizTime(null);
        userService.save(user);
        taskService.create(user, CreateTaskCommand.simple("English", "English", TaskType.LANGUAGE, clock.instant(), 20));

        tick.runTick();
        assertThat(byKind(NotificationKind.TASK_START).getStatus()).isEqualTo(NotificationStatus.SENT);

        clock.advance(Duration.ofMinutes(11)); // 15:11: reminder due, inside quiet window
        tick.runTick();
        Notification reminder = byKind(NotificationKind.REMINDER);
        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
        assertThat(reminder.getAttempts()).isZero();
        assertThat(reminder.getNextAttemptAt()).isEqualTo(user.getSettings().quietWindowEnd(clock.instant(), user.zone()));

        clock.advance(Duration.ofMinutes(15)); // 15:26: window over, task not yet missed
        tick.runTick();
        assertThat(byKind(NotificationKind.REMINDER).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void dailyQuizTargetsTheMostForgottenTopicAndIsCancelledWhenIgnored() {
        User user = registerUser();
        user.getSettings().setQuietHours(null, null);
        user.getSettings().setQuizTime(LocalTime.of(15, 0));
        userService.save(user);
        knowledge.recordAssessment(user.getId(), "Java", "Streams", 0.9, 0.9, "PROGRAMMING");
        knowledge.recordAssessment(user.getId(), "Java", "Generics", 0.9, 0.9, "THEORY");
        KnowledgeTopic streams = knowledge.profile(user).get(1);
        assertThat(streams.getTopic()).isEqualTo("Streams");

        clock.advance(Duration.ofDays(40)); // retention has decayed well below the threshold
        tick.runTick();

        List<Task> quizzes = tasks.findAll().stream().filter(t -> t.getKind() == TaskKind.QUIZ).toList();
        assertThat(quizzes).hasSize(1);
        Task quiz = quizzes.get(0);
        assertThat(quiz.getStatus()).isEqualTo(TaskStatus.VERIFICATION_IN_PROGRESS);
        assertThat(quiz.getType()).isIn(TaskType.PROGRAMMING, TaskType.THEORY);
        assertThat(byKind(NotificationKind.QUIZ).getText()).contains("Java");
        assertThat(verification.activeSession(user)).isPresent();

        // a second tick the same day does not start another quiz
        tick.runTick();
        assertThat(tasks.findAll().stream().filter(t -> t.getKind() == TaskKind.QUIZ)).hasSize(1);

        // ignored: session expires, task is cancelled, no "pending verification" left behind
        clock.advance(Duration.ofHours(1));
        tick.runTick();
        assertThat(taskService.getById(quiz.getId()).getStatus()).isEqualTo(TaskStatus.CANCELLED);
        assertThat(verification.activeSession(user)).isEmpty();
        assertThat(notifications.findAll()).noneMatch(n -> n.getKind() == NotificationKind.VERIFICATION_EXPIRED);
    }

    @Test
    void eveningSummaryReportsTodayAndTomorrow() {
        User user = registerUser();
        user.getSettings().setEveningSummaryTime(LocalTime.of(15, 30));
        user.getSettings().setQuizTime(null);
        userService.save(user);
        Task done = taskService.create(user, CreateTaskCommand.simple("Math", "Math", TaskType.MATH,
                clock.instant().minus(Duration.ofHours(2)), 30));
        taskService.reportDone(user, done.getId());
        verification.start(user, taskService.getById(done.getId()));
        verification.answer(user, "Substantive first answer with enough words to be scored by the fake examiner.");
        verification.answer(user, "Substantive second answer with enough words to be scored by the fake examiner.");
        taskService.create(user, CreateTaskCommand.simple("Java", "Java", TaskType.PROGRAMMING,
                clock.instant().plus(Duration.ofDays(1)), 45));

        tick.runTick();
        assertThat(notifications.findAll()).noneMatch(n -> n.getKind() == NotificationKind.EVENING_SUMMARY);

        clock.advance(Duration.ofMinutes(31));
        tick.runTick();
        Notification summary = byKind(NotificationKind.EVENING_SUMMARY);
        assertThat(summary.getText()).contains("1 выполнено").contains("Java");
    }

    private Notification byKind(NotificationKind kind) {
        return notifications.findAll().stream().filter(n -> n.getKind() == kind).findFirst().orElseThrow();
    }
}
