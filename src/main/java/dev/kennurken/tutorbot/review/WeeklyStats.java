package dev.kennurken.tutorbot.review;

import java.util.List;

/**
 * The numbers behind a weekly review. Serialised as JSON both for storage and as the only
 * input the model sees (it must not invent data, so it gets exactly this).
 */
public record WeeklyStats(
        String weekStart,
        String weekEnd,
        int planned,
        int started,
        int completed,
        int verified,
        int missed,
        int skipped,
        int failedVerifications,
        int rescheduled,
        double executionRate,
        double verificationPassRate,
        List<HourBucket> byTimeOfDay,
        Bucket longTasks,
        List<SubjectStat> subjects,
        String mostCommonSkipCategory,
        double aiCostUsd) {

    public record HourBucket(String range, int planned, int completed) {
        public double rate() {
            return planned == 0 ? 0 : (double) completed / planned;
        }
    }

    public record Bucket(int planned, int completed) {
    }

    public record SubjectStat(String subject, int planned, int completed, int missed, Double avgScore) {
    }
}
