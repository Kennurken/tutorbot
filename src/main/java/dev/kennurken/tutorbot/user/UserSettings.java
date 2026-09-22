package dev.kennurken.tutorbot.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Per-user planning limits. Embedded in {@code users} instead of a 1:1 table: there is
 * exactly one row per user and it is always loaded together with the user.
 */
@Embeddable
public class UserSettings {

    @Column(name = "daily_task_limit", nullable = false)
    private int dailyTaskLimit = 6;

    @Column(name = "max_daily_study_minutes", nullable = false)
    private int maxDailyStudyMinutes = 180;

    @Column(name = "morning_plan_time", nullable = false)
    private LocalTime morningPlanTime = LocalTime.of(8, 0);

    @Column(name = "verification_max_questions", nullable = false)
    private int verificationMaxQuestions = 4;

    /** Nudges are held back between these local times (null = no quiet hours). */
    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart = LocalTime.of(23, 0);

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd = LocalTime.of(8, 0);

    /** Local time of the "how did today go" message (null = off). */
    @Column(name = "evening_summary_time")
    private LocalTime eveningSummaryTime = LocalTime.of(21, 30);

    /** Local time of the daily retrieval-practice quiz (null = off). */
    @Column(name = "quiz_time")
    private LocalTime quizTime = LocalTime.of(13, 0);

    public int getDailyTaskLimit() {
        return dailyTaskLimit;
    }

    public void setDailyTaskLimit(int dailyTaskLimit) {
        this.dailyTaskLimit = dailyTaskLimit;
    }

    public int getMaxDailyStudyMinutes() {
        return maxDailyStudyMinutes;
    }

    public void setMaxDailyStudyMinutes(int maxDailyStudyMinutes) {
        this.maxDailyStudyMinutes = maxDailyStudyMinutes;
    }

    public LocalTime getMorningPlanTime() {
        return morningPlanTime;
    }

    public void setMorningPlanTime(LocalTime morningPlanTime) {
        this.morningPlanTime = morningPlanTime;
    }

    public int getVerificationMaxQuestions() {
        return verificationMaxQuestions;
    }

    public void setVerificationMaxQuestions(int verificationMaxQuestions) {
        this.verificationMaxQuestions = verificationMaxQuestions;
    }

    public LocalTime getQuietHoursStart() {
        return quietHoursStart;
    }

    public LocalTime getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHours(LocalTime start, LocalTime end) {
        this.quietHoursStart = start;
        this.quietHoursEnd = end;
    }

    public boolean hasQuietHours() {
        return quietHoursStart != null && quietHoursEnd != null && !quietHoursStart.equals(quietHoursEnd);
    }

    /** True if the local wall-clock time is inside the quiet window (which may wrap midnight). */
    public boolean isQuietAt(Instant instant, ZoneId zone) {
        if (!hasQuietHours()) {
            return false;
        }
        LocalTime t = instant.atZone(zone).toLocalTime();
        if (quietHoursStart.isBefore(quietHoursEnd)) {
            return !t.isBefore(quietHoursStart) && t.isBefore(quietHoursEnd);
        }
        return !t.isBefore(quietHoursStart) || t.isBefore(quietHoursEnd);
    }

    /** The next instant at which the quiet window ends (only meaningful when {@link #isQuietAt} is true). */
    public Instant quietWindowEnd(Instant instant, ZoneId zone) {
        ZonedDateTime local = instant.atZone(zone);
        ZonedDateTime end = local.with(quietHoursEnd);
        if (!end.isAfter(local)) {
            end = end.plusDays(1);
        }
        return end.toInstant();
    }

    public LocalTime getEveningSummaryTime() {
        return eveningSummaryTime;
    }

    public void setEveningSummaryTime(LocalTime eveningSummaryTime) {
        this.eveningSummaryTime = eveningSummaryTime;
    }

    public LocalTime getQuizTime() {
        return quizTime;
    }

    public void setQuizTime(LocalTime quizTime) {
        this.quizTime = quizTime;
    }
}
