package dev.kennurken.tutorbot.task;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, Long> {

    List<RecurrenceRule> findByActiveTrue();

    List<RecurrenceRule> findByUserIdAndActiveTrueOrderByTimeOfDay(Long userId);
}
