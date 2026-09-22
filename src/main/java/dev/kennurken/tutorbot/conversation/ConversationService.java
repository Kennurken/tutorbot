package dev.kennurken.tutorbot.conversation;

import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ConversationService {

    private final ConversationStateRepository states;
    private final JsonMapper json;

    public ConversationService(ConversationStateRepository states, JsonMapper json) {
        this.states = states;
        this.json = json;
    }

    public record Snapshot(ConversationState state, Map<String, Object> context) {

        public Long taskId() {
            Object v = context.get("taskId");
            return v == null ? null : ((Number) v).longValue();
        }

        public String text(String key) {
            Object v = context.get(key);
            return v == null ? null : v.toString();
        }
    }

    @Transactional(readOnly = true)
    public Snapshot current(Long userId) {
        Optional<ConversationStateEntity> entity = states.findById(userId);
        if (entity.isEmpty() || entity.get().getContext() == null) {
            return new Snapshot(entity.map(ConversationStateEntity::getState).orElse(ConversationState.IDLE), Map.of());
        }
        Map<String, Object> ctx = json.readValue(entity.get().getContext(), new TypeReference<Map<String, Object>>() { });
        return new Snapshot(entity.get().getState(), ctx);
    }

    @Transactional
    public void set(Long userId, ConversationState state, Map<String, Object> context) {
        ConversationStateEntity entity = states.findById(userId).orElseGet(() -> new ConversationStateEntity(userId));
        entity.set(state, context == null || context.isEmpty() ? null : json.writeValueAsString(context));
        states.save(entity);
    }

    @Transactional
    public void clear(Long userId) {
        set(userId, ConversationState.IDLE, null);
    }
}
