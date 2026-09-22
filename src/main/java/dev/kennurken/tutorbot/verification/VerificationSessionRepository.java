package dev.kennurken.tutorbot.verification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationSessionRepository extends JpaRepository<VerificationSession, Long> {

    Optional<VerificationSession> findByUserIdAndStatus(Long userId, SessionStatus status);

    List<VerificationSession> findByStatusAndExpiresAtBefore(SessionStatus status, Instant before);

    List<VerificationSession> findByTaskIdOrderByAttemptNo(Long taskId);
}
