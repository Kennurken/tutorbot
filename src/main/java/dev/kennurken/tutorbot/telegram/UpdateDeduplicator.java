package dev.kennurken.tutorbot.telegram;

import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateDeduplicator {

    private final TelegramUpdateRepository updates;
    private final Clock clock;

    public UpdateDeduplicator(TelegramUpdateRepository updates, Clock clock) {
        this.updates = updates;
        this.clock = clock;
    }

    /** @return true the first time an update id is seen, false on any redelivery. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean firstTime(long updateId) {
        if (updates.existsById(updateId)) {
            return false;
        }
        try {
            updates.saveAndFlush(new TelegramUpdateRecord(updateId, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException raced) {
            return false;
        }
    }
}
