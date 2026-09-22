package dev.kennurken.tutorbot.goal;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.common.NotFoundException;
import dev.kennurken.tutorbot.user.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalRepository goals;

    public GoalService(GoalRepository goals) {
        this.goals = goals;
    }

    @Transactional
    public Goal add(User user, String title, Goal.Horizon horizon) {
        if (title == null || title.isBlank()) {
            throw new DomainException("Goal title is required");
        }
        return goals.save(new Goal(user.getId(), title.trim(), horizon));
    }

    @Transactional(readOnly = true)
    public List<Goal> active(User user) {
        return goals.findByUserIdAndStatusOrderByCreatedAt(user.getId(), Goal.Status.ACTIVE);
    }

    @Transactional
    public Goal close(User user, Long goalId, Goal.Status status) {
        Goal goal = goals.findByIdAndUserId(goalId, user.getId()).orElseThrow(() -> new NotFoundException("Goal", goalId));
        goal.setStatus(status);
        return goals.save(goal);
    }
}
