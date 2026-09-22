package dev.kennurken.tutorbot.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * What the user agreed to in advance. The consequence engine never goes beyond these
 * bounds, and the user can switch everything off with one command (human override).
 */
@Embeddable
public class ConsequencePolicy {

    @Column(name = "consequences_enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "missed_extra_minutes", nullable = false)
    private int missedExtraMinutes = 10;

    @Column(name = "max_extra_minutes", nullable = false)
    private int maxExtraMinutes = 30;

    @Column(name = "review_task_on_fail", nullable = false)
    private boolean reviewTaskOnFail = true;

    @Column(name = "require_skip_reason", nullable = false)
    private boolean requireSkipReason = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMissedExtraMinutes() {
        return missedExtraMinutes;
    }

    public void setMissedExtraMinutes(int missedExtraMinutes) {
        this.missedExtraMinutes = missedExtraMinutes;
    }

    public int getMaxExtraMinutes() {
        return maxExtraMinutes;
    }

    public void setMaxExtraMinutes(int maxExtraMinutes) {
        this.maxExtraMinutes = maxExtraMinutes;
    }

    public boolean isReviewTaskOnFail() {
        return reviewTaskOnFail;
    }

    public void setReviewTaskOnFail(boolean reviewTaskOnFail) {
        this.reviewTaskOnFail = reviewTaskOnFail;
    }

    public boolean isRequireSkipReason() {
        return requireSkipReason;
    }

    public void setRequireSkipReason(boolean requireSkipReason) {
        this.requireSkipReason = requireSkipReason;
    }
}
