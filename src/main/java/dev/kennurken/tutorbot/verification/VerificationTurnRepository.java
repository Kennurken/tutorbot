package dev.kennurken.tutorbot.verification;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationTurnRepository extends JpaRepository<VerificationTurn, Long> {

    List<VerificationTurn> findBySessionIdOrderBySeq(Long sessionId);
}
