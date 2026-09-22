package dev.kennurken.tutorbot.planning;

import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.Priority;
import dev.kennurken.tutorbot.task.TaskType;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic parser for the common shapes of "add a task" messages in Russian and English:
 * "завтра в 19 английский на 30 минут", "every mon wed fri java at 20:00", "сегодня в 20:30 java 45 мин".
 * It runs before the model (AI call policy: no tokens for what a regex can do) and its output is
 * shown to the user for confirmation, so a mis-parse costs one tap, not a wrong commitment.
 */
public final class NaturalLanguageTaskParser {

    private static final int DEFAULT_MINUTES = 30;

    private static final Pattern MINUTES = p("\\b(?:на\\s+|for\\s+)?(\\d{1,3})\\s*(?:мин(?:ут[аы]?)?|m|min|mins|minutes?)\\b");
    private static final Pattern HOURS = p("\\b(?:на\\s+|for\\s+)?(\\d(?:[.,]5)?)\\s*(?:ч|час(?:а|ов)?|h|hr|hrs|hours?)\\b");
    private static final Pattern HALF_HOUR = p("\\b(?:на\\s+)?(?:полчаса|half an hour)\\b");
    private static final Pattern ONE_HOUR = p("\\b(?:на\\s+час|час|for an hour|an hour|one hour)\\b");

    private static final Pattern TIME_RU = p("\\bв\\s*(\\d{1,2})(?::(\\d{2}))?\\b");
    private static final Pattern TIME_EN = p("\\bat\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b");
    private static final Pattern TIME_BARE = p("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern DATE_NUMERIC = p("\\b(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?\\b");

    private static final Map<Pattern, LocalTime> WORD_TIMES = new LinkedHashMap<>();
    private static final Map<Pattern, DayOfWeek> DAY_WORDS = new LinkedHashMap<>();
    private static final Map<Pattern, SubjectHint> SUBJECTS = new LinkedHashMap<>();

    private static final Pattern EVERY = p("\\b(каждый|каждую|каждое|каждые|ежедневно|every|daily|по будням|weekdays|по выходным|weekends)\\b");
    private static final Pattern EVERY_DAY = p("\\b(каждый день|ежедневно|every day|daily)\\b");
    private static final Pattern WEEKDAYS = p("\\b(по будням|каждый будний день|weekdays|every weekday)\\b");
    private static final Pattern WEEKENDS = p("\\b(по выходным|weekends|every weekend)\\b");
    private static final Pattern TODAY = p("\\b(сегодня|today)\\b");
    private static final Pattern TOMORROW = p("\\b(завтра|tomorrow|tmrw)\\b");
    private static final Pattern AFTER_TOMORROW = p("\\b(послезавтра|day after tomorrow)\\b");
    /** "до пятницы", "к среде", "до конца недели", "by friday", "before 30.09", "до 30.09". */
    private static final Pattern DEADLINE = p("\\b(?:до|к|by|before|due)\\s+(завтра|послезавтра|tomorrow|"
            + "конца недели|end of (?:the )?week|понедельника|вторника|среды|четверга|пятницы|субботы|воскресенья|"
            + "понедельнику|вторнику|среде|четвергу|пятнице|субботе|воскресенью|"
            + "monday|tuesday|wednesday|thursday|friday|saturday|sunday|\\d{1,2}[./]\\d{1,2}(?:[./]\\d{2,4})?)\\b");

    private static final Pattern FILLER = p("(?i)\\b(на|в|во|и|at|for|and|the|a|an|с|до|по|каждый|каждую|каждое|every|минут|мин|min|minutes)\\b");

    static {
        WORD_TIMES.put(p("\\b(утром|morning)\\b"), LocalTime.of(9, 0));
        WORD_TIMES.put(p("\\b(днём|днем|после обеда|afternoon)\\b"), LocalTime.of(14, 0));
        WORD_TIMES.put(p("\\b(вечером|evening|tonight)\\b"), LocalTime.of(19, 0));
        WORD_TIMES.put(p("\\b(ночью|night)\\b"), LocalTime.of(22, 0));

        DAY_WORDS.put(p("\\b(понедельник\\w*|пн|monday|mon)\\b"), DayOfWeek.MONDAY);
        DAY_WORDS.put(p("\\b(вторник\\w*|вт|tuesday|tue|tues)\\b"), DayOfWeek.TUESDAY);
        DAY_WORDS.put(p("\\b(сред\\w*|ср|wednesday|wed)\\b"), DayOfWeek.WEDNESDAY);
        DAY_WORDS.put(p("\\b(четверг\\w*|чт|thursday|thu|thur|thurs)\\b"), DayOfWeek.THURSDAY);
        DAY_WORDS.put(p("\\b(пятниц\\w*|пт|friday|fri)\\b"), DayOfWeek.FRIDAY);
        DAY_WORDS.put(p("\\b(суббот\\w*|сб|saturday|sat)\\b"), DayOfWeek.SATURDAY);
        DAY_WORDS.put(p("\\b(воскресень\\w*|вс|sunday|sun)\\b"), DayOfWeek.SUNDAY);

        SUBJECTS.put(p("\\b(английск\\w*|english|англ)\\b"), new SubjectHint("English", TaskType.LANGUAGE));
        SUBJECTS.put(p("\\b(spring|hibernate|backend|бэкенд|бекенд)\\b"), new SubjectHint("Backend", TaskType.PROGRAMMING));
        SUBJECTS.put(p("\\b(java|джава|ява)\\b"), new SubjectHint("Java", TaskType.PROGRAMMING));
        SUBJECTS.put(p("\\b(python|питон|kotlin|sql|algorithms?|алгоритм\\w*|leetcode|код\\w*|coding|programming|программирован\\w*)\\b"), new SubjectHint("Programming", TaskType.PROGRAMMING));
        SUBJECTS.put(p("\\b(матем\\w*|math\\w*|матан|алгебр\\w*|calculus|линал)\\b"), new SubjectHint("Math", TaskType.MATH));
        SUBJECTS.put(p("\\b(чтение|читать|почитать|книг\\w*|reading|read|book)\\b"), new SubjectHint("Reading", TaskType.READING));
        SUBJECTS.put(p("\\b(проект\\w*|project)\\b"), new SubjectHint("Project", TaskType.PROJECT));
        SUBJECTS.put(p("\\b(универ\\w*|university|уник|лаба|лабу|лабораторн\\w*|курсов\\w*|homework|домашк\\w*|дз)\\b"), new SubjectHint("University", TaskType.THEORY));
        SUBJECTS.put(p("\\b(ai|ml|machine learning|нейросет\\w*|llm)\\b"), new SubjectHint("AI", TaskType.THEORY));
        SUBJECTS.put(p("\\b(зал|спортзал|gym|тренировк\\w*|workout|бег|run)\\b"), new SubjectHint("Gym", TaskType.OTHER));
    }

    private record SubjectHint(String subject, TaskType type) {
    }

    /** Word boundaries and \w must understand Cyrillic: hence UNICODE_CHARACTER_CLASS. */
    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS);
    }

    private NaturalLanguageTaskParser() {
    }

    public static Optional<CreateTaskCommand> parse(String input, Instant now, ZoneId zone) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        String text = " " + input.trim().replaceAll("\\s+", " ") + " ";
        StringBuilder residual = new StringBuilder(text);

        Integer minutes = extractMinutes(residual);
        ZonedDateTime localNow = now.atZone(zone);
        LocalDate deadlineDate = extractDeadline(residual, localNow.toLocalDate());

        Set<DayOfWeek> everyDays = EnumSet.noneOf(DayOfWeek.class);
        boolean recurring = false;
        if (find(EVERY_DAY, residual)) {
            everyDays.addAll(EnumSet.allOf(DayOfWeek.class));
            recurring = true;
        } else if (find(WEEKDAYS, residual)) {
            everyDays.addAll(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY));
            recurring = true;
        } else if (find(WEEKENDS, residual)) {
            everyDays.addAll(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));
            recurring = true;
        } else if (find(EVERY, residual)) {
            recurring = true;
        }
        Set<DayOfWeek> namedDays = EnumSet.noneOf(DayOfWeek.class);
        for (Map.Entry<Pattern, DayOfWeek> e : DAY_WORDS.entrySet()) {
            if (find(e.getKey(), residual)) {
                namedDays.add(e.getValue());
            }
        }
        if (recurring && everyDays.isEmpty()) {
            everyDays.addAll(namedDays);
        }

        LocalTime time = extractTime(residual);

        ZonedDateTime local = now.atZone(zone);
        LocalDate date = null;
        if (find(AFTER_TOMORROW, residual)) {
            date = local.toLocalDate().plusDays(2);
        } else if (find(TOMORROW, residual)) {
            date = local.toLocalDate().plusDays(1);
        } else if (find(TODAY, residual)) {
            date = local.toLocalDate();
        } else {
            Matcher dm = DATE_NUMERIC.matcher(residual);
            if (dm.find()) {
                int day = Integer.parseInt(dm.group(1));
                int month = Integer.parseInt(dm.group(2));
                int year = dm.group(3) == null ? local.getYear() : normalizeYear(Integer.parseInt(dm.group(3)));
                try {
                    date = LocalDate.of(year, month, day);
                    residual.replace(dm.start(), dm.end(), " ");
                } catch (java.time.DateTimeException ignored) {
                    // not a date, leave it in the title
                }
            }
        }

        SubjectHint hint = null;
        for (Map.Entry<Pattern, SubjectHint> e : SUBJECTS.entrySet()) {
            if (e.getKey().matcher(residual).find()) {
                hint = e.getValue();
                break;
            }
        }

        if (recurring) {
            if (everyDays.isEmpty() || time == null) {
                return Optional.empty();
            }
        } else {
            if (!namedDays.isEmpty() && date == null) {
                date = nextOccurrence(local.toLocalDate(), namedDays.iterator().next(), time, local.toLocalTime());
            }
            if (date == null && time != null) {
                date = time.isAfter(local.toLocalTime().plusMinutes(1)) ? local.toLocalDate() : local.toLocalDate().plusDays(1);
            }
            if (time == null && date != null) {
                return Optional.empty();
            }
        }

        String title = cleanTitle(residual.toString());
        String subject = hint == null ? null : hint.subject();
        TaskType type = hint == null ? TaskType.THEORY : hint.type();
        if (title.isEmpty()) {
            if (subject == null) {
                return Optional.empty();
            }
            title = subject;
        }
        String topic = topicFrom(title, hint);
        int duration = minutes == null ? DEFAULT_MINUTES : minutes;
        boolean verification = type != TaskType.OTHER;
        Instant deadlineAt = deadlineDate == null ? null : deadlineDate.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(60);

        if (recurring) {
            return Optional.of(new CreateTaskCommand(title, null, subject, topic, type, Priority.MEDIUM, null, duration,
                    null, verification, null, new CreateTaskCommand.Recurrence(everyDays, time)));
        }
        Instant scheduledAt = date == null ? null : date.atTime(time).atZone(zone).toInstant();
        if (scheduledAt == null) {
            return Optional.empty();
        }
        return Optional.of(new CreateTaskCommand(title, null, subject, topic, type, Priority.MEDIUM, scheduledAt,
                duration, deadlineAt, verification, null, null));
    }

    /** Removes a "by <day>" phrase and returns the deadline date (end of that day), or null. */
    static LocalDate extractDeadline(StringBuilder residual, LocalDate today) {
        Matcher m = DEADLINE.matcher(residual);
        if (!m.find()) {
            return null;
        }
        String word = m.group(1).toLowerCase(Locale.ROOT);
        residual.replace(m.start(), m.end(), " ");
        if (word.equals("завтра") || word.equals("tomorrow")) {
            return today.plusDays(1);
        }
        if (word.equals("послезавтра")) {
            return today.plusDays(2);
        }
        if (word.startsWith("конца недели") || word.startsWith("end of")) {
            return today.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        }
        Matcher dm = DATE_NUMERIC.matcher(word);
        if (dm.matches()) {
            int year = dm.group(3) == null ? today.getYear() : normalizeYear(Integer.parseInt(dm.group(3)));
            try {
                LocalDate d = LocalDate.of(year, Integer.parseInt(dm.group(2)), Integer.parseInt(dm.group(1)));
                return d.isBefore(today) && dm.group(3) == null ? d.plusYears(1) : d;
            } catch (java.time.DateTimeException e) {
                return null;
            }
        }
        for (Map.Entry<Pattern, DayOfWeek> e : DAY_WORDS.entrySet()) {
            if (e.getKey().matcher(word).find()) {
                LocalDate d = today.with(java.time.temporal.TemporalAdjusters.next(e.getValue()));
                return d;
            }
        }
        return null;
    }

    private static Integer extractMinutes(StringBuilder residual) {
        Matcher m = MINUTES.matcher(residual);
        if (m.find()) {
            int v = Integer.parseInt(m.group(1));
            residual.replace(m.start(), m.end(), " ");
            return v;
        }
        m = HOURS.matcher(residual);
        if (m.find()) {
            double h = Double.parseDouble(m.group(1).replace(',', '.'));
            residual.replace(m.start(), m.end(), " ");
            return (int) Math.round(h * 60);
        }
        if (find(HALF_HOUR, residual)) {
            return 30;
        }
        if (find(ONE_HOUR, residual)) {
            return 60;
        }
        return null;
    }

    private static LocalTime extractTime(StringBuilder residual) {
        Matcher m = TIME_RU.matcher(residual);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            if (hour <= 23 && minute <= 59) {
                residual.replace(m.start(), m.end(), " ");
                return LocalTime.of(hour, minute);
            }
        }
        m = TIME_EN.matcher(residual);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            String ampm = m.group(3);
            if (ampm != null) {
                if (ampm.equalsIgnoreCase("pm") && hour < 12) {
                    hour += 12;
                } else if (ampm.equalsIgnoreCase("am") && hour == 12) {
                    hour = 0;
                }
            }
            if (hour <= 23 && minute <= 59) {
                residual.replace(m.start(), m.end(), " ");
                return LocalTime.of(hour, minute);
            }
        }
        m = TIME_BARE.matcher(residual);
        if (m.find()) {
            int hour = Integer.parseInt(m.group(1));
            int minute = Integer.parseInt(m.group(2));
            if (hour <= 23 && minute <= 59) {
                residual.replace(m.start(), m.end(), " ");
                return LocalTime.of(hour, minute);
            }
        }
        for (Map.Entry<Pattern, LocalTime> e : WORD_TIMES.entrySet()) {
            if (find(e.getKey(), residual)) {
                return e.getValue();
            }
        }
        return null;
    }

    private static LocalDate nextOccurrence(LocalDate today, DayOfWeek day, LocalTime time, LocalTime nowTime) {
        LocalDate date = today;
        while (date.getDayOfWeek() != day) {
            date = date.plusDays(1);
        }
        if (date.equals(today) && time != null && !time.isAfter(nowTime)) {
            date = date.plusWeeks(1);
        }
        return date;
    }

    /** Removes the token if present and reports whether it was there. */
    private static boolean find(Pattern p, StringBuilder residual) {
        Matcher m = p.matcher(residual);
        if (m.find()) {
            residual.replace(m.start(), m.end(), " ");
            return true;
        }
        return false;
    }

    private static String cleanTitle(String residual) {
        String cleaned = FILLER.matcher(residual).replaceAll(" ")
                .replaceAll("[\\s,.;:—-]+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private static String topicFrom(String title, SubjectHint hint) {
        if (hint == null) {
            return null;
        }
        String lowered = title.toLowerCase(Locale.ROOT);
        for (Map.Entry<Pattern, SubjectHint> e : SUBJECTS.entrySet()) {
            if (e.getValue() == hint) {
                String rest = e.getKey().matcher(lowered).replaceAll(" ").replaceAll("\\s+", " ").trim();
                return rest.isEmpty() || rest.equals(lowered) ? null : rest;
            }
        }
        return null;
    }

    private static int normalizeYear(int y) {
        return y < 100 ? 2000 + y : y;
    }

    /** Exposed for tests and the /help text: subjects the parser recognises. */
    public static List<String> knownSubjects() {
        return SUBJECTS.values().stream().map(SubjectHint::subject).distinct().toList();
    }
}
