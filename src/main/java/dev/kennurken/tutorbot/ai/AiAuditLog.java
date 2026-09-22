package dev.kennurken.tutorbot.ai;

import dev.kennurken.tutorbot.ai.provider.AiResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes {@code ai_interactions}. Separate bean (not a private method of AiGateway) because
 * Spring's @Transactional works through proxies: a self-invocation would ignore REQUIRES_NEW.
 */
@Component
public class AiAuditLog {

    private static final Logger log = LoggerFactory.getLogger(AiAuditLog.class);
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private final AiInteractionRepository interactions;
    private final AiProperties properties;
    private final Clock clock;

    public AiAuditLog(AiInteractionRepository interactions, AiProperties properties, Clock clock) {
        this.interactions = interactions;
        this.properties = properties;
        this.clock = clock;
    }

    /** The audit row must survive even if the caller's work later rolls back. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AiCall<?> call, AiResponse response, boolean success, String error) {
        try {
            String model = response != null ? response.model() : properties.model();
            Integer in = response != null ? response.inputTokens() : null;
            Integer out = response != null ? response.outputTokens() : null;
            Integer latency = response != null ? (int) response.latencyMs() : null;
            interactions.save(new AiInteraction(call.userId(), call.purpose(), call.promptVersion(), model, in, out,
                    estimateCost(in, out), latency, success, error == null ? null : snippet(error), clock.instant()));
        } catch (RuntimeException e) {
            log.error("Failed to write ai_interactions row", e);
        }
    }

    BigDecimal estimateCost(Integer inputTokens, Integer outputTokens) {
        if (inputTokens == null || outputTokens == null) {
            return null;
        }
        BigDecimal in = BigDecimal.valueOf(inputTokens).multiply(BigDecimal.valueOf(properties.pricing().inputPerMillion()));
        BigDecimal out = BigDecimal.valueOf(outputTokens).multiply(BigDecimal.valueOf(properties.pricing().outputPerMillion()));
        return in.add(out).divide(MILLION, 6, RoundingMode.HALF_UP);
    }

    private static String snippet(String s) {
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
