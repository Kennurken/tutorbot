package dev.kennurken.tutorbot.task;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdAndUserId(Long id, Long userId);

    List<Task> findByUserIdAndScheduledAtBetweenOrderByScheduledAt(Long userId, Instant from, Instant to);

    List<Task> findByUserIdAndStatusInOrderByScheduledAt(Long userId, Collection<TaskStatus> statuses);

    List<Task> findByUserIdAndStatusOrderByScheduledAt(Long userId, TaskStatus status);

    /** Tasks whose start time has come and that nobody has been told about yet. */
    @Query("select t from Task t where t.status = 'SCHEDULED' and t.scheduledAt <= :now")
    List<Task> findDueForNotification(@Param("now") Instant now);

    /** Notified but not started; the caller compares scheduledAt + grace against now. */
    @Query("select t from Task t where t.status = 'NOTIFIED' and t.scheduledAt <= :threshold")
    List<Task> findNotifiedNotStartedBefore(@Param("threshold") Instant threshold);

    /** Started/notified yesterday or earlier and never resolved. */
    @Query("select t from Task t where t.userId = :userId and t.status in ('NOTIFIED', 'STARTED') and t.scheduledAt < :before")
    List<Task> findUnresolvedBefore(@Param("userId") Long userId, @Param("before") Instant before);

    long countByUserIdAndStatusAndMissedAtAfterAndSystemFaultFalse(Long userId, TaskStatus status, Instant after);

    Optional<Task> findFirstByUserIdAndSubjectIgnoreCaseAndStatusAndScheduledAtAfterOrderByScheduledAt(
            Long userId, String subject, TaskStatus status, Instant after);

    boolean existsByRecurrenceRuleIdAndOccurrenceDate(Long recurrenceRuleId, LocalDate occurrenceDate);

    long countByUserIdAndStatusInAndScheduledAtBetween(Long userId, Collection<TaskStatus> statuses, Instant from, Instant to);
}
