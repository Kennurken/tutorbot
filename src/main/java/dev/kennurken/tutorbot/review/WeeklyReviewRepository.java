package dev.kennurken.tutorbot.review;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyReviewRepository extends JpaRepository<WeeklyReview, Long> {

    Optional<WeeklyReview> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);
}
