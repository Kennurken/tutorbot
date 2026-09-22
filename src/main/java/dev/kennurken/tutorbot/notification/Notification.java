package dev.kennurken.tutorbot.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Outbox row. Created inside the business transaction, delivered later by the scheduler tick.
 * If Telegram is down the row stays SCHEDULED and is retried; nothing is lost with the request.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Long taskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationKind kind;

    private String dedupeKey;

    @Column(nullable = false)
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    private String replyMarkup;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.SCHEDULED;

    @Column(nullable = false)
    private int attempts;

    private Instant nextAttemptAt;

    private String lastError;

    private Instant sentAt;

    private Long telegramMessageId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Notification() {
        // JPA
    }

    public Notification(Long userId, Long taskId, NotificationKind kind, String dedupeKey, String text,
                        String replyMarkup, Instant scheduledAt) {
        this.userId = userId;
        this.taskId = taskId;
        this.kind = kind;
        this.dedupeKey = dedupeKey;
        this.text = text;
        this.replyMarkup = replyMarkup;
        this.scheduledAt = scheduledAt;
    }

    public void lease(Instant until) {
        this.attempts++;
        this.nextAttemptAt = until;
    }

    public void markSent(Instant at, Long messageId) {
        this.status = NotificationStatus.SENT;
        this.sentAt = at;
        this.telegramMessageId = messageId;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = NotificationStatus.FAILED;
        this.lastError = error;
    }

    /** Quiet hours: postpone without counting an attempt. */
    public void defer(Instant until) {
        this.nextAttemptAt = until;
    }

    public void retryLater(Instant at, String error) {
        this.nextAttemptAt = at;
        this.lastError = error;
    }

    public void cancel() {
        this.status = NotificationStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public NotificationKind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public String getReplyMarkup() {
        return replyMarkup;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public String getLastError() {
        return lastError;
    }
}
