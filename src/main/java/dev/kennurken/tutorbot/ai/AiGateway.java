package dev.kennurken.tutorbot.ai;

import dev.kennurken.tutorbot.ai.provider.AiException;
import dev.kennurken.tutorbot.ai.provider.AiProvider;
import dev.kennurken.tutorbot.ai.provider.AiRequest;
import dev.kennurken.tutorbot.ai.provider.AiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one door through which the application talks to a model:
 * rate limit -> provider -> JSON extraction -> parse -> bean validation -> audit row.
 *
 * <p>Must be called outside database transactions: a model call can take 20+ seconds and
 * must not hold a pooled connection for that long.
 */
@Service
public class AiGateway {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);

    private final AiProvider provider;
    private final UserAiRateLimiter rateLimiter;
    private final AiAuditLog audit;
    private final JsonMapper json;
    private final Validator validator;

    public AiGateway(AiProvider provider, UserAiRateLimiter rateLimiter, AiAuditLog audit, JsonMapper json,
                     Validator validator) {
        this.provider = provider;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.json = json;
        this.validator = validator;
    }

    public <T> T callJson(AiCall<T> call) {
        if (!rateLimiter.tryAcquire(call.userId())) {
            throw new AiUnavailableException("AI rate limit reached for user " + call.userId());
        }
        AiRequest request = new AiRequest(call.purpose(), call.systemPrompt(), call.userPrompt(),
                call.maxTokens(), call.temperature(), true, null);
        AiResponse response;
        try {
            response = provider.complete(request);
        } catch (AiException e) {
            audit.record(call, null, false, e.getMessage());
            throw new AiUnavailableException("AI call failed: " + e.getMessage(), e);
        }
        try {
            T parsed = json.readValue(AiJsonExtractor.extractObject(response.content()), call.responseType());
            Set<ConstraintViolation<T>> violations = validator.validate(parsed);
            if (!violations.isEmpty()) {
                String detail = violations.stream()
                        .map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining(", "));
                audit.record(call, response, false, "schema: " + detail);
                throw new AiUnavailableException("Model output violates schema: " + detail);
            }
            audit.record(call, response, true, null);
            return parsed;
        } catch (JacksonException e) {
            audit.record(call, response, false, "json: " + e.getOriginalMessage());
            log.warn("Unparseable model output for {}: {}", call.purpose(), snippet(response.content()));
            throw new AiUnavailableException("Model output is not valid JSON", e);
        } catch (AiUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            audit.record(call, response, false, "extract: " + e.getMessage());
            throw new AiUnavailableException("Could not use model output: " + e.getMessage(), e);
        }
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
