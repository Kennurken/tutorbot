package dev.kennurken.tutorbot.ai;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiInteractionRepository extends JpaRepository<AiInteraction, Long> {

    long countByUserIdAndCreatedAtAfter(Long userId, Instant after);

    @Query("select coalesce(sum(a.estimatedCostUsd), 0) from AiInteraction a where a.userId = :userId and a.createdAt >= :after")
    BigDecimal sumCostSince(@Param("userId") Long userId, @Param("after") Instant after);
}
