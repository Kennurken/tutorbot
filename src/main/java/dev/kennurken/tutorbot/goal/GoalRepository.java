package dev.kennurken.tutorbot.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdAndStatusOrderByCreatedAt(Long userId, Goal.Status status);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);
}
