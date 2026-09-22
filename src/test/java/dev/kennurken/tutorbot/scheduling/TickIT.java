package dev.kennurken.tutorbot.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import dev.kennurken.tutorbot.notification.NotificationRepository;
import dev.kennurken.tutorbot.notification.NotificationStatus;
import dev.kennurken.tutorbot.support.AbstractIT;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskRepository;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.task.TaskType;
import dev.kennurken.tutorbot.telegram.api.TelegramApiException;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.verification.VerificationService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TickIT extends AbstractIT {

    @Autowired
    TickService tick;
    @Autowired
    TaskService taskService;
    @Autowired
    TaskRepository tasks;
    @Autowired
    NotificationRepository notifications;
    @Autowired
    VerificationService verification;

    @Test
    void recurringRuleMaterialisesOnlyUpcomingOccurrencesOnce() {
        User user = registerUser(); // Asia/Almaty, clock = Monday 15:00 local
        taskService.createRecurring(user, new CreateTaskCommand("Java", null, "Java", null, TaskType.PROGRAMMING,
                Priority.MEDIUM, null, 45, null, true, null,
                new CreateTaskCommand.Recurrence(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY), LocalTime.of(20, 0))));

        Map<String, Integer> first = tick.runTick();
        Map<String, Integer> second = tick.runTick();

        assertThat(first.get("recurrence")).isEqualTo(2); // Mon 20:00 and Tue 20:00 within 36h
        assertThat(second.get("recurrence")).isZero();
        assertThat(tasks.findAll()).hasSize(2).allMatch(t -> t.getRecurrenceRuleId() != null);
    }

    @Test
    void dueTaskIsNotifiedAndDeliveredWithRetryOnTelegramFailure() {
        User user = registerUser();
        taskService.create(user, CreateTaskCommand.simple("English", "English", TaskType.LANGUAGE,
                clock.instant().minusSeconds(30), 20));

        doThrow(new TelegramApiException("boom", true, 0, null)).when(telegram).sendMessage(anyLong(), anyString(), any());
        tick.runTick();
        assertThat(notifications.findAll()).anyMatch(n -> n.getAttempts() == 1 && n.getStatus() == NotificationStatus.SCHEDULED);

        org.mockito.Mockito.reset(telegram);
        clock.advance(Duration.ofMinutes(3));
        tick.runTick();
        // the task start (and the morning plan queued by the routine) are now delivered
        verify(telegram, atLeastOnce()).sendMessage(anyLong(), anyString(), any());
        assertThat(notifications.findAll())
                .anyMatch(n -> n.getKind().name().equals("TASK_START") && n.getStatus() == NotificationStatus.SENT);
    }

    @Test
    void staleVerificationExpiresAndTaskGoesBackToPending() {
        User user = registerUser();
        Task task = taskService.create(user, CreateTaskCommand.simple("Math", "Math", TaskType.MATH,
                clock.instant().minusSeconds(30), 20));
        taskService.reportDone(user, task.getId());
        verification.start(user, taskService.getById(task.getId()));

        clock.advance(Duration.ofHours(1));
        Map<String, Integer> result = tick.runTick();

        assertThat(result.get("verificationExpired")).isEqualTo(1);
        Task after = taskService.getById(task.getId());
        assertThat(after.getStatus()).isEqualTo(TaskStatus.PENDING_VERIFICATION);
        assertThat(verification.activeSession(user)).isEmpty();
        assertThat(notifications.findAll()).anyMatch(n -> n.getKind().name().equals("VERIFICATION_EXPIRED"));
    }

    @Test
    void morningRoutineSweepsYesterdayAndSendsThePlan() {
        User user = registerUser();
        Task yesterday = taskService.create(user, CreateTaskCommand.simple("Old", "Java", TaskType.PROGRAMMING,
                clock.instant().minus(Duration.ofHours(20)), 30));
        taskService.markNotified(yesterday);
        taskService.start(user, yesterday.getId());
        taskService.create(user, CreateTaskCommand.simple("Today", "Java", TaskType.PROGRAMMING,
                clock.instant().plus(Duration.ofHours(2)), 30));

        tick.runTick();

        assertThat(taskService.getById(yesterday.getId()).getStatus()).isEqualTo(TaskStatus.MISSED);
        assertThat(notifications.findAll()).anyMatch(n -> n.getKind().name().equals("MORNING_PLAN"));
        assertThat(userService.get(user.getId()).getLastMorningPlanDate()).isEqualTo(user.today(clock.instant()));
    }
}
