package dev.kennurken.tutorbot.verification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "verification_turns")
public class VerificationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private String question;

    private String answer;

    private Double score;

    private String feedback;

    @Column(nullable = false)
    private Instant askedAt;

    private Instant answeredAt;

    protected VerificationTurn() {
        // JPA
    }

    public VerificationTurn(Long sessionId, int seq, String question, Instant askedAt) {
        this.sessionId = sessionId;
        this.seq = seq;
        this.question = question;
        this.askedAt = askedAt;
    }

    public void answer(String answer, Double score, String feedback, Instant at) {
        this.answer = answer;
        this.score = score;
        this.feedback = feedback;
        this.answeredAt = at;
    }

    public boolean isAnswered() {
        return answeredAt != null;
    }

    public Long getId() {
        return id;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public int getSeq() {
        return seq;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public Double getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }
}
