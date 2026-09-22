package dev.kennurken.tutorbot.task.event;

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

/** Append-only log row. Never updated, never deleted. */
@Entity
@Table(name = "task_events")
public class TaskEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private TaskEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Actor actor;

    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    protected TaskEvent() {
        // JPA
    }

    public TaskEvent(Long taskId, Long userId, TaskEventType eventType, Actor actor, String payload, Instant occurredAt) {
        this.taskId = taskId;
        this.userId = userId;
        this.eventType = eventType;
        this.actor = actor;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public Long getUserId() {
        return userId;
    }

    public TaskEventType getEventType() {
        return eventType;
    }

    public Actor getActor() {
        return actor;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
