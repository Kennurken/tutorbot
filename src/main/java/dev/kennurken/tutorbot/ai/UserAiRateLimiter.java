package dev.kennurken.tutorbot.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sliding-window limit of model calls per user. In-memory is correct for a single instance;
 * with several instances this moves to the database or Redis (documented trade-off).
 */
@Component
public class UserAiRateLimiter {

    private final Map<Long, Deque<Instant>> windows = new ConcurrentHashMap<>();
    private final int perMinute;
    private final Clock clock;

    public UserAiRateLimiter(AiProperties properties, Clock clock) {
        this.perMinute = properties.rateLimit().perUserPerMinute();
        this.clock = clock;
    }

    /** @return true if the call may proceed (and is counted), false if the user is over the limit. */
    public boolean tryAcquire(Long userId) {
        if (userId == null) {
            return true;
        }
        Instant now = clock.instant();
        Deque<Instant> window = windows.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            Instant cutoff = now.minus(Duration.ofMinutes(1));
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() >= perMinute) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
