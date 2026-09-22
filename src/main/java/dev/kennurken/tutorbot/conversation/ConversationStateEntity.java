package dev.kennurken.tutorbot.conversation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Persisted (not in-memory) so a restart in the middle of a verification loses nothing. */
@Entity
@Table(name = "conversation_states")
public class ConversationStateEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationState state = ConversationState.IDLE;

    @JdbcTypeCode(SqlTypes.JSON)
    private String context;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected ConversationStateEntity() {
        // JPA
    }

    public ConversationStateEntity(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }

    public ConversationState getState() {
        return state;
    }

    public String getContext() {
        return context;
    }

    public void set(ConversationState state, String context) {
        this.state = state;
        this.context = context;
        this.updatedAt = Instant.now();
    }
}
