package dev.kennurken.tutorbot.verification;

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

/** One oral exam for one task attempt. Turns live in {@link VerificationTurn}. */
@Entity
@Table(name = "verification_sessions")
public class VerificationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(nullable = false)
    private int difficulty;

    @Column(nullable = false)
    private int maxQuestions;

    @Column(nullable = false)
    private int questionCount;

    @Column(nullable = false)
    private int shortAnswerStrikes;

    private Double score;

    private Double confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    private String verdict;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant lastActivityAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant finishedAt;

    protected VerificationSession() {
        // JPA
    }

    public VerificationSession(Long taskId, Long userId, int attemptNo, int difficulty, int maxQuestions,
                               Instant startedAt, Instant expiresAt) {
        this.taskId = taskId;
        this.userId = userId;
        this.attemptNo = attemptNo;
        this.difficulty = difficulty;
        this.maxQuestions = maxQuestions;
        this.startedAt = startedAt;
        this.lastActivityAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public void touch(Instant now, Instant newExpiry) {
        this.lastActivityAt = now;
        this.expiresAt = newExpiry;
    }

    public void finish(SessionStatus status, Double score, Double confidence, String verdictJson, Instant now) {
        this.status = status;
        this.score = score;
        this.confidence = confidence;
        this.verdict = verdictJson;
        this.finishedAt = now;
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

    public int getAttemptNo() {
        return attemptNo;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(int difficulty) {
        this.difficulty = difficulty;
    }

    public int getMaxQuestions() {
        return maxQuestions;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public int getShortAnswerStrikes() {
        return shortAnswerStrikes;
    }

    public void setShortAnswerStrikes(int shortAnswerStrikes) {
        this.shortAnswerStrikes = shortAnswerStrikes;
    }

    public Double getScore() {
        return score;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getVerdict() {
        return verdict;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
