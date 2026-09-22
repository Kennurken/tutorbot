package dev.kennurken.tutorbot.review;

import dev.kennurken.tutorbot.task.SkipCategory;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskStatus;
import dev.kennurken.tutorbot.task.VerificationStatus;
import dev.kennurken.tutorbot.verification.SessionStatus;
import dev.kennurken.tutorbot.verification.VerificationSession;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Pure aggregation over the week's tasks and verification sessions. */
public final class WeeklyStatsCalculator {

    private static final int[][] BUCKETS = {{6, 9}, {9, 12}, {12, 15}, {15, 18}, {18, 21}, {21, 24}, {0, 6}};
    private static final int LONG_TASK_MINUTES = 60;

    private WeeklyStatsCalculator() {
    }

    public static WeeklyStats compute(LocalDate weekStart, ZoneId zone, List<Task> tasks,
                                      List<VerificationSession> sessions, double aiCostUsd) {
        int planned = tasks.size();
        int started = (int) tasks.stream().filter(t -> t.getStartedAt() != null).count();
        int completed = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        int verified = (int) tasks.stream().filter(t -> t.getVerificationStatus() == VerificationStatus.PASSED).count();
        int missed = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.MISSED).count();
        int skipped = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.SKIPPED).count();
        int rescheduled = tasks.stream().mapToInt(Task::getRescheduleCount).sum();
        int failed = (int) sessions.stream().filter(s -> s.getStatus() == SessionStatus.FAILED).count();
        int finishedSessions = (int) sessions.stream()
                .filter(s -> s.getStatus() == SessionStatus.PASSED || s.getStatus() == SessionStatus.FAILED).count();
        int passedSessions = (int) sessions.stream().filter(s -> s.getStatus() == SessionStatus.PASSED).count();

        List<WeeklyStats.HourBucket> buckets = new ArrayList<>();
        for (int[] b : BUCKETS) {
            List<Task> in = tasks.stream().filter(t -> t.getScheduledAt() != null).filter(t -> {
                int hour = t.getScheduledAt().atZone(zone).getHour();
                return hour >= b[0] && hour < b[1];
            }).toList();
            if (!in.isEmpty()) {
                buckets.add(new WeeklyStats.HourBucket("%02d:00-%02d:00".formatted(b[0], b[1]), in.size(),
                        (int) in.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count()));
            }
        }

        List<Task> longTasks = tasks.stream().filter(t -> t.effectiveMinutes() > LONG_TASK_MINUTES).toList();
        WeeklyStats.Bucket longBucket = new WeeklyStats.Bucket(longTasks.size(),
                (int) longTasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count());

        Map<Long, Double> scoreByTask = sessions.stream()
                .filter(s -> s.getScore() != null)
                .collect(Collectors.toMap(VerificationSession::getTaskId, VerificationSession::getScore, (a, b) -> b));
        Map<String, List<Task>> bySubject = new LinkedHashMap<>();
        for (Task t : tasks) {
            bySubject.computeIfAbsent(t.subjectOrTitle(), k -> new ArrayList<>()).add(t);
        }
        List<WeeklyStats.SubjectStat> subjects = bySubject.entrySet().stream().map(e -> {
            List<Task> list = e.getValue();
            double[] scores = list.stream().map(t -> scoreByTask.get(t.getId())).filter(s -> s != null)
                    .mapToDouble(Double::doubleValue).toArray();
            Double avg = scores.length == 0 ? null : Math.round(java.util.Arrays.stream(scores).average().orElse(0) * 100) / 100.0;
            return new WeeklyStats.SubjectStat(e.getKey(), list.size(),
                    (int) list.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count(),
                    (int) list.stream().filter(t -> t.getStatus() == TaskStatus.MISSED).count(), avg);
        }).sorted(Comparator.comparingInt(WeeklyStats.SubjectStat::planned).reversed()).toList();

        String topSkip = tasks.stream().map(Task::getSkipCategory).filter(c -> c != null)
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).map(SkipCategory::name)
                .orElse(null);

        return new WeeklyStats(weekStart.toString(), weekStart.plusDays(6).toString(), planned, started, completed,
                verified, missed, skipped, failed, rescheduled,
                rate(completed, planned), rate(passedSessions, finishedSessions), buckets, longBucket, subjects,
                topSkip, aiCostUsd);
    }

    private static double rate(int part, int whole) {
        return whole == 0 ? 0.0 : Math.round((double) part / whole * 1000) / 1000.0;
    }
}
