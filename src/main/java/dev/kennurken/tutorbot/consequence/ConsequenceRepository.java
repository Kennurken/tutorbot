package dev.kennurken.tutorbot.consequence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsequenceRepository extends JpaRepository<Consequence, Long> {

    boolean existsByUserIdAndTypeAndAppliedAtAfter(Long userId, ConsequenceType type, Instant after);

    boolean existsBySourceTaskIdAndType(Long sourceTaskId, ConsequenceType type);

    boolean existsByTargetTaskIdAndType(Long targetTaskId, ConsequenceType type);
}
