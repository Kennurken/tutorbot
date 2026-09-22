package dev.kennurken.tutorbot.task.event;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    List<TaskEvent> findByTaskIdOrderByOccurredAt(Long taskId);

    List<TaskEvent> findByUserIdAndOccurredAtBetween(Long userId, Instant from, Instant to);

    long countByUserIdAndEventTypeAndOccurredAtAfter(Long userId, TaskEventType type, Instant after);
}
