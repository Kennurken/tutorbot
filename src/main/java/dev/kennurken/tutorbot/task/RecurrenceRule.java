package dev.kennurken.tutorbot.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Every Mon/Wed/Fri at 20:00, Java, 45 min". The scheduler materialises concrete
 * {@link Task} rows from it a day ahead, so every occurrence has its own history.
 */
@Entity
@Table(name = "recurrence_rules")
public class RecurrenceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Long goalId;

    @Column(nullable = false)
    private String title;

    private String subject;

    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    /** Comma separated {@link DayOfWeek} names; a tiny bitmask would be less readable in SQL. */
    @Column(nullable = false)
    private String daysOfWeek;

    @Column(nullable = false)
    private LocalTime timeOfDay;

    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private int estimatedMinutes;

    @Column(nullable = false)
    private boolean verificationRequired;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected RecurrenceRule() {
        // JPA
    }

    public RecurrenceRule(Long userId, String title, TaskType type, Priority priority, Set<DayOfWeek> days,
                          LocalTime timeOfDay, String timezone, int estimatedMinutes, boolean verificationRequired) {
        this.userId = userId;
        this.title = title;
        this.type = type;
        this.priority = priority;
        this.daysOfWeek = days.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
        this.timeOfDay = timeOfDay;
        this.timezone = timezone;
        this.estimatedMinutes = estimatedMinutes;
        this.verificationRequired = verificationRequired;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Set<DayOfWeek> days() {
        return Arrays.stream(daysOfWeek.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getGoalId() {
        return goalId;
    }

    public void setGoalId(Long goalId) {
        this.goalId = goalId;
    }

    public String getTitle() {
        return title;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public TaskType getType() {
        return type;
    }

    public Priority getPriority() {
        return priority;
    }

    public LocalTime getTimeOfDay() {
        return timeOfDay;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDaysOfWeek() {
        return daysOfWeek;
    }
}
