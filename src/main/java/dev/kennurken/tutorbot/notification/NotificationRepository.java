package dev.kennurken.tutorbot.notification;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Due rows whose lease (nextAttemptAt) has expired or was never set. */
    @Query("""
            select n from Notification n
            where n.status = 'SCHEDULED' and n.scheduledAt <= :now
              and (n.nextAttemptAt is null or n.nextAttemptAt <= :now)
            order by n.scheduledAt
            """)
    List<Notification> findDue(@Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.status = 'CANCELLED' where n.taskId = :taskId and n.status = 'SCHEDULED'")
    int cancelPendingForTask(@Param("taskId") Long taskId);

    boolean existsByDedupeKey(String dedupeKey);

    long countByUserIdAndStatusAndSentAtAfter(Long userId, NotificationStatus status, Instant after);
}
