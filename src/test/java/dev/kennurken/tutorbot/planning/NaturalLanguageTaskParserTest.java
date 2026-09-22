package dev.kennurken.tutorbot.planning;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.TaskType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NaturalLanguageTaskParserTest {

    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");
    // Monday 2026-09-21 15:00 local
    private static final Instant NOW = LocalDateTime.of(2026, 9, 21, 15, 0).atZone(ALMATY).toInstant();

    @Test
    void parsesRussianTomorrowWithDuration() {
        CreateTaskCommand cmd = parse("Завтра в 19 английский на 30 минут");

        assertThat(cmd.subject()).isEqualTo("English");
        assertThat(cmd.type()).isEqualTo(TaskType.LANGUAGE);
        assertThat(cmd.estimatedMinutes()).isEqualTo(30);
        assertThat(cmd.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 19, 0).atZone(ALMATY).toInstant());
        assertThat(cmd.isRecurring()).isFalse();
    }

    @Test
    void parsesTodayWithMinutesAndTopic() {
        CreateTaskCommand cmd = parse("сегодня в 20:30 java streams 45 мин");

        assertThat(cmd.subject()).isEqualTo("Java");
        assertThat(cmd.topic()).isEqualTo("streams");
        assertThat(cmd.estimatedMinutes()).isEqualTo(45);
        assertThat(cmd.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 21, 20, 30).atZone(ALMATY).toInstant());
    }

    @Test
    void parsesRecurringRussianDays() {
        CreateTaskCommand cmd = parse("Каждый понедельник среду и пятницу Java в 20:00");

        assertThat(cmd.isRecurring()).isTrue();
        assertThat(cmd.recurrence().days()).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        assertThat(cmd.recurrence().timeOfDay()).isEqualTo(LocalTime.of(20, 0));
        assertThat(cmd.estimatedMinutes()).isEqualTo(30);
    }

    @Test
    void parsesEnglishEveryWeekday() {
        CreateTaskCommand cmd = parse("every weekday at 8:30 reading 15 min");

        assertThat(cmd.isRecurring()).isTrue();
        assertThat(cmd.recurrence().days()).hasSize(5).doesNotContain(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(cmd.type()).isEqualTo(TaskType.READING);
        assertThat(cmd.estimatedMinutes()).isEqualTo(15);
    }

    @Test
    void parsesPmTimeAndHours() {
        CreateTaskCommand cmd = parse("tomorrow at 7pm project 2 hours");

        assertThat(cmd.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 19, 0).atZone(ALMATY).toInstant());
        assertThat(cmd.estimatedMinutes()).isEqualTo(120);
        assertThat(cmd.type()).isEqualTo(TaskType.PROJECT);
    }

    @Test
    void timeAlreadyPassedTodayRollsToTomorrow() {
        CreateTaskCommand cmd = parse("в 9 математика час");

        assertThat(cmd.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 9, 0).atZone(ALMATY).toInstant());
        assertThat(cmd.estimatedMinutes()).isEqualTo(60);
        assertThat(cmd.type()).isEqualTo(TaskType.MATH);
    }

    @Test
    void namedDayWithoutEveryIsOneTime() {
        CreateTaskCommand cmd = parse("в пятницу в 18 проект 40 минут");

        assertThat(cmd.isRecurring()).isFalse();
        assertThat(cmd.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 25, 18, 0).atZone(ALMATY).toInstant());
    }

    @Test
    void gymIsNotVerified() {
        CreateTaskCommand cmd = parse("завтра в 7 зал");

        assertThat(cmd.type()).isEqualTo(TaskType.OTHER);
        assertThat(cmd.verificationRequired()).isFalse();
    }

    @Test
    void deadlinePhraseSetsDeadlineWithoutStealingTheScheduledDay() {
        CreateTaskCommand cmd = parse("сделать лабу до пятницы завтра в 15 на 2 часа");

        assertThat(cmd.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 15, 0).atZone(ALMATY).toInstant());
        assertThat(cmd.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 9, 25, 23, 59).atZone(ALMATY).toInstant());
        assertThat(cmd.estimatedMinutes()).isEqualTo(120);
        assertThat(cmd.title()).doesNotContainIgnoringCase("пятниц");
    }

    @Test
    void englishDeadlineAndNumericDeadline() {
        CreateTaskCommand cmd = parse("project report by friday tomorrow at 10 1 hour");
        assertThat(cmd.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 9, 25, 23, 59).atZone(ALMATY).toInstant());
        assertThat(cmd.scheduledAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 10, 0).atZone(ALMATY).toInstant());

        CreateTaskCommand numeric = parse("курсовая до 30.09 сегодня в 20 час");
        assertThat(numeric.deadlineAt()).isEqualTo(LocalDateTime.of(2026, 9, 30, 23, 59).atZone(ALMATY).toInstant());
    }

    @Test
    void chitChatIsNotATask() {
        assertThat(NaturalLanguageTaskParser.parse("привет, как дела?", NOW, ALMATY)).isEmpty();
        assertThat(NaturalLanguageTaskParser.parse("what is a hashmap", NOW, ALMATY)).isEmpty();
    }

    private static CreateTaskCommand parse(String text) {
        Optional<CreateTaskCommand> cmd = NaturalLanguageTaskParser.parse(text, NOW, ALMATY);
        assertThat(cmd).as("parse of '%s'", text).isPresent();
        return cmd.get();
    }
}
