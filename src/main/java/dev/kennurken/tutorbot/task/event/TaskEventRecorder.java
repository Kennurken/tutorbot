package dev.kennurken.tutorbot.task.event;

import dev.kennurken.tutorbot.task.Task;
import java.time.Clock;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Thin writer for the event log. Called inside the transaction that changes the task. */
@Component
public class TaskEventRecorder {

    private final TaskEventRepository events;
    private final JsonMapper json;
    private final Clock clock;

    public TaskEventRecorder(TaskEventRepository events, JsonMapper json, Clock clock) {
        this.events = events;
        this.json = json;
        this.clock = clock;
    }

    public void record(Task task, TaskEventType type, Actor actor) {
        record(task, type, actor, Map.of());
    }

    public void record(Task task, TaskEventType type, Actor actor, Map<String, ?> payload) {
        String serialized = payload.isEmpty() ? null : json.writeValueAsString(payload);
        events.save(new TaskEvent(task.getId(), task.getUserId(), type, actor, serialized, clock.instant()));
    }
}
