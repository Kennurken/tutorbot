package dev.kennurken.tutorbot.consequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** A consequence that was actually applied, with the reason, so it can be explained and reverted. */
@Entity
@Table(name = "consequences")
public class Consequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    private Long sourceTaskId;

    private Long targetTaskId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsequenceType type;

    private Integer minutes;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant appliedAt;

    @Column(nullable = false)
    private boolean reverted;

    protected Consequence() {
        // JPA
    }

    public Consequence(Long userId, Long sourceTaskId, Long targetTaskId, ConsequenceType type, Integer minutes,
                       String reason, Instant appliedAt) {
        this.userId = userId;
        this.sourceTaskId = sourceTaskId;
        this.targetTaskId = targetTaskId;
        this.type = type;
        this.minutes = minutes;
        this.reason = reason;
        this.appliedAt = appliedAt;
    }

    public Long getId() {
        return id;
    }

    public ConsequenceType getType() {
        return type;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public String getReason() {
        return reason;
    }

    public Long getTargetTaskId() {
        return targetTaskId;
    }
}
