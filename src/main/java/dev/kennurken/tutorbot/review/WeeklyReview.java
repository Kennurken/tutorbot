package dev.kennurken.tutorbot.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "weekly_reviews")
public class WeeklyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate weekStart;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String stats;

    private String narrative;

    @Column(nullable = false)
    private boolean aiGenerated;

    @Column(nullable = false)
    private Instant createdAt;

    protected WeeklyReview() {
        // JPA
    }

    public WeeklyReview(Long userId, LocalDate weekStart, String stats, String narrative, boolean aiGenerated,
                        Instant createdAt) {
        this.userId = userId;
        this.weekStart = weekStart;
        this.stats = stats;
        this.narrative = narrative;
        this.aiGenerated = aiGenerated;
        this.createdAt = createdAt;
    }

    public void update(String stats, String narrative, boolean aiGenerated, Instant at) {
        this.stats = stats;
        this.narrative = narrative;
        this.aiGenerated = aiGenerated;
        this.createdAt = at;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public String getStats() {
        return stats;
    }

    public String getNarrative() {
        return narrative;
    }

    public boolean isAiGenerated() {
        return aiGenerated;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
