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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A commitment: something the user promised to do at a time, with a way to prove it.
 *
 * <p>Status changes go through {@link TaskService}, which consults {@link TaskStateMachine};
 * the entity itself only stores state. Foreign keys are plain ids on purpose: modules
 * reference each other by id, not by object graph, which keeps them independently testable.
 */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Long goalId;

    private Long recurrenceRuleId;

    private LocalDate occurrenceDate;

    @Column(nullable = false)
    private String title;

    private String description;

    private String subject;

    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;

    @Column(nullable = false)
    private boolean verificationRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus;

    private Instant scheduledAt;

    @Column(nullable = false)
    private int estimatedMinutes;

    @Column(nullable = false)
    private int extraMinutes;

    private Instant deadlineAt;

    private Instant notifiedAt;

    private Instant startedAt;

    private Instant reportedDoneAt;

    private Instant completedAt;

    private Instant missedAt;

    @Column(nullable = false)
    private int rescheduleCount;

    @Column(nullable = false)
    private int verificationAttempts;

    @Enumerated(EnumType.STRING)
    private SkipCategory skipCategory;

    private String skipReason;

    @Column(nullable = false)
    private boolean systemFault;

    /** Optimistic locking: two concurrent /done presses cannot both win. */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Task() {
        // JPA
    }

    public Task(Long userId, String title, TaskType type, Priority priority, int estimatedMinutes,
                boolean verificationRequired) {
        this.userId = userId;
        this.title = title;
        this.type = type;
        this.priority = priority;
        this.estimatedMinutes = estimatedMinutes;
        this.verificationRequired = verificationRequired;
        this.status = TaskStatus.CREATED;
        this.verificationStatus = verificationRequired ? VerificationStatus.PENDING : VerificationStatus.NOT_REQUIRED;
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

    /** Planned duration including anything the consequence engine added. */
    public int effectiveMinutes() {
        return estimatedMinutes + extraMinutes;
    }

    public String subjectOrTitle() {
        return subject != null && !subject.isBlank() ? subject : title;
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

    public Long getRecurrenceRuleId() {
        return recurrenceRuleId;
    }

    public void setRecurrenceRuleId(Long recurrenceRuleId) {
        this.recurrenceRuleId = recurrenceRuleId;
    }

    public LocalDate getOccurrenceDate() {
        return occurrenceDate;
    }

    public void setOccurrenceDate(LocalDate occurrenceDate) {
        this.occurrenceDate = occurrenceDate;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public void setType(TaskType type) {
        this.type = type;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    void setStatus(TaskStatus status) {
        this.status = status;
    }

    public boolean isVerificationRequired() {
        return verificationRequired;
    }

    public void setVerificationRequired(boolean verificationRequired) {
        this.verificationRequired = verificationRequired;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    void setScheduledAt(Instant scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public void setEstimatedMinutes(int estimatedMinutes) {
        this.estimatedMinutes = estimatedMinutes;
    }

    public int getExtraMinutes() {
        return extraMinutes;
    }

    public void setExtraMinutes(int extraMinutes) {
        this.extraMinutes = extraMinutes;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public void setDeadlineAt(Instant deadlineAt) {
        this.deadlineAt = deadlineAt;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    void setNotifiedAt(Instant notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getReportedDoneAt() {
        return reportedDoneAt;
    }

    void setReportedDoneAt(Instant reportedDoneAt) {
        this.reportedDoneAt = reportedDoneAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getMissedAt() {
        return missedAt;
    }

    void setMissedAt(Instant missedAt) {
        this.missedAt = missedAt;
    }

    public int getRescheduleCount() {
        return rescheduleCount;
    }

    void setRescheduleCount(int rescheduleCount) {
        this.rescheduleCount = rescheduleCount;
    }

    public int getVerificationAttempts() {
        return verificationAttempts;
    }

    void setVerificationAttempts(int verificationAttempts) {
        this.verificationAttempts = verificationAttempts;
    }

    public SkipCategory getSkipCategory() {
        return skipCategory;
    }

    void setSkipCategory(SkipCategory skipCategory) {
        this.skipCategory = skipCategory;
    }

    public String getSkipReason() {
        return skipReason;
    }

    void setSkipReason(String skipReason) {
        this.skipReason = skipReason;
    }

    public boolean isSystemFault() {
        return systemFault;
    }

    void setSystemFault(boolean systemFault) {
        this.systemFault = systemFault;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
