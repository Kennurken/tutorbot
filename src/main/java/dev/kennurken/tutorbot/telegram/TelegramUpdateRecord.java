package dev.kennurken.tutorbot.telegram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Idempotency key for Telegram updates: the primary key rejects a redelivered update. */
@Entity
@Table(name = "telegram_updates")
public class TelegramUpdateRecord {

    @Id
    @Column(name = "update_id")
    private Long updateId;

    @Column(nullable = false)
    private Instant receivedAt;

    protected TelegramUpdateRecord() {
        // JPA
    }

    public TelegramUpdateRecord(Long updateId, Instant receivedAt) {
        this.updateId = updateId;
        this.receivedAt = receivedAt;
    }

    public Long getUpdateId() {
        return updateId;
    }
}
