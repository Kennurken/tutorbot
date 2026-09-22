package dev.kennurken.tutorbot.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.support.AbstractIT;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskRepository;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.CallbackQuery;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Chat;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Message;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.TgUser;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Update;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;

/** The user's point of view: raw Telegram updates in, chat messages out. */
class ChatFlowIT extends AbstractIT {

    private static final TgUser ELDOS = new TgUser(100L, false, "Eldos", "eldos", "ru");
    private static final Chat CHAT = new Chat(100L, "private");

    @Autowired
    UpdateProcessor processor;
    @Autowired
    TaskRepository tasks;

    private long updateId = 1;

    @Test
    void naturalLanguageTaskThenDoneThenVerification() {
        say("/start");
        say("Завтра в 19 английский Present Perfect на 30 минут");
        assertThat(lastReply()).contains("Создать задачу?").contains("English").contains("30 min");

        tap(Callbacks.of(Callbacks.TASK_CONFIRM, "yes"));
        List<Task> created = tasks.findAll();
        assertThat(created).hasSize(1);
        Task task = created.get(0);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.SCHEDULED);
        assertThat(task.getSubject()).isEqualTo("English");
        assertThat(task.getTopic()).isEqualTo("present perfect");
        assertThat(lastReply()).contains("Добавлено");

        // pretend the time came and the user is reporting the task done via the button
        clock.advance(Duration.ofDays(1).plusHours(5));
        tap(Callbacks.of(Callbacks.DONE, task.getId()));
        assertThat(lastReply()).contains("Вопрос 1/4");

        say("Present Perfect связывает прошлое с настоящим: I have lost my keys — ключи всё ещё потеряны, результат важен сейчас.");
        assertThat(lastReply()).contains("Вопрос 2/4");
        say("Примеры: She has lived here since 2019. We have never been to Rome. Past Simple — законченное время: I lost my keys yesterday.");
        assertThat(lastReply()).contains("ПРОЙДЕНО");
        assertThat(tasks.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void duplicateUpdatesAreIgnoredAndUnknownCommandsAnswered() {
        Update update = message("/status");
        processor.process(update);
        processor.process(update);
        verify(telegram, org.mockito.Mockito.times(1)).sendMessage(anyLong(), anyString(), any());

        say("/nope");
        assertThat(lastReply()).contains("/help");
    }

    @Test
    void skipRequiresAReasonAndRecordsIt() {
        say("сегодня в 23:50 java 30 мин");
        tap(Callbacks.of(Callbacks.TASK_CONFIRM, "yes"));
        Task task = tasks.findAll().get(0);

        say("/skip " + task.getId());
        assertThat(lastReply()).contains("Почему?");
        tap(Callbacks.of(Callbacks.SKIP_CATEGORY, task.getId(), "TOO_TIRED"));
        assertThat(lastReply()).contains("Напиши причину");
        say("Весь день был в универе, нет сил.");

        Task skipped = tasks.findById(task.getId()).orElseThrow();
        assertThat(skipped.getStatus()).isEqualTo(TaskStatus.SKIPPED);
        assertThat(skipped.getSkipReason()).contains("универе");
    }

    @Test
    void goalIsDecomposedIntoDailyTasksAfterConfirmation() {
        say("/goals add Стать Java backend разработчиком");
        assertThat(lastReply()).contains("Цель #1");
        say("/goals plan 1 20:00");
        assertThat(lastReply()).contains("3 задач").contains("20:00");

        tap(Callbacks.of(Callbacks.GOAL_PLAN, "yes"));
        assertThat(lastReply()).contains("Создано задач: 3");
        List<Task> created = tasks.findAll();
        assertThat(created).hasSize(3).allMatch(t -> t.getGoalId() == 1L);
        assertThat(created.get(0).getScheduledAt().atZone(java.time.ZoneId.of("Asia/Almaty")).toLocalTime())
                .isEqualTo(java.time.LocalTime.of(20, 0));
        say("/goals");
        assertThat(lastReply()).contains("0/3");
    }

    @Test
    void historyShowsTheEventLog() {
        say("сегодня в 23:00 java 30 мин");
        tap(Callbacks.of(Callbacks.TASK_CONFIRM, "yes"));
        Task task = tasks.findAll().get(0);
        say("/history " + task.getId());
        assertThat(lastReply()).contains("История").contains("создана").contains("запланирована");
    }

    private void say(String text) {
        processor.process(message(text));
    }

    private Update message(String text) {
        return new Update(updateId++, new Message(updateId, ELDOS, CHAT, text, 0), null);
    }

    private void tap(String data) {
        Message origin = new Message(updateId, ELDOS, CHAT, "", 0);
        processor.process(new Update(updateId++, null, new CallbackQuery("cb" + updateId, ELDOS, origin, data)));
    }

    private String lastReply() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegram, org.mockito.Mockito.atLeastOnce()).sendMessage(anyLong(), captor.capture(), any());
        List<String> all = captor.getAllValues();
        return all.get(all.size() - 1);
    }
}
