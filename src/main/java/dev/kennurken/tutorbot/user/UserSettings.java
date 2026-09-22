package dev.kennurken.tutorbot.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;

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
}
