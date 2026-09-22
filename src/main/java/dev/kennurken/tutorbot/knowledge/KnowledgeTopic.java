package dev.kennurken.tutorbot.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * What we believe about one topic for one user. Deliberately not a single percentage:
 * mastery is an estimate, confidence says how much evidence backs it, and stability drives
 * the forgetting curve (see {@link KnowledgeModel}).
 */
@Entity
@Table(name = "knowledge_topics")
public class KnowledgeTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private double estimatedMastery;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private int sampleCount;

    @Column(nullable = false)
    private double stabilityDays;

    private Instant lastVerifiedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected KnowledgeTopic() {
        // JPA
    }

    public KnowledgeTopic(Long userId, String subject, String topic) {
        this.userId = userId;
        this.subject = subject;
        this.topic = topic;
        this.estimatedMastery = 0.0;
        this.confidence = 0.0;
        this.sampleCount = 0;
        this.stabilityDays = KnowledgeModel.INITIAL_STABILITY_DAYS;
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

    public void apply(KnowledgeModel.State next, Instant at) {
        this.estimatedMastery = next.mastery();
        this.confidence = next.confidence();
        this.sampleCount = next.sampleCount();
        this.stabilityDays = next.stabilityDays();
        this.lastVerifiedAt = at;
    }

    public KnowledgeModel.State state() {
        return new KnowledgeModel.State(estimatedMastery, confidence, sampleCount, stabilityDays);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSubject() {
        return subject;
    }

    public String getTopic() {
        return topic;
    }

    public double getEstimatedMastery() {
        return estimatedMastery;
    }

    public double getConfidence() {
        return confidence;
    }

    public int getSampleCount() {
        return sampleCount;
    }

    public double getStabilityDays() {
        return stabilityDays;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }
}
