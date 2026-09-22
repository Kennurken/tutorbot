package dev.kennurken.tutorbot.scheduling;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-process trigger. Spring's {@code @Scheduled} is enough for a single instance: it is
 * stateless (all state is in the DB) and the tick is idempotent. Quartz would add a job store
 * and clustering we do not need yet; if a second instance ever appears, add ShedLock or move
 * to Quartz clustered mode (see docs/ARCHITECTURE.md).
 */
@Component
@ConditionalOnProperty(prefix = "scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerTick {

    private final TickService tickService;

    public SchedulerTick(TickService tickService) {
        this.tickService = tickService;
    }

    @Scheduled(fixedDelayString = "${scheduler.tick-interval}", initialDelayString = "10s")
    public void tick() {
        tickService.runTick();
    }
}
