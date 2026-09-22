package dev.kennurken.tutorbot.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** One row per model call; the basis for cost per user / per task / per verification. */
@Entity
@Table(name = "ai_interactions")
public class AiInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false)
    private String purpose;

    @Column(nullable = false)
    private String promptVersion;

    @Column(nullable = false)
    private String model;

    private Integer inputTokens;

    private Integer outputTokens;

    @Column(precision = 10, scale = 6)
    private BigDecimal estimatedCostUsd;

    private Integer latencyMs;

    @Column(nullable = false)
    private boolean success;

    private String error;

    @Column(nullable = false)
    private Instant createdAt;

    protected AiInteraction() {
        // JPA
    }

    public AiInteraction(Long userId, String purpose, String promptVersion, String model, Integer inputTokens,
                         Integer outputTokens, BigDecimal estimatedCostUsd, Integer latencyMs, boolean success,
                         String error, Instant createdAt) {
        this.userId = userId;
        this.purpose = purpose;
        this.promptVersion = promptVersion;
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.estimatedCostUsd = estimatedCostUsd;
        this.latencyMs = latencyMs;
        this.success = success;
        this.error = error;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getModel() {
        return model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getEstimatedCostUsd() {
        return estimatedCostUsd;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
