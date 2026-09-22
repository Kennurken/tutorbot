package dev.kennurken.tutorbot.task;

import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Turns recurrence rules into concrete tasks for the near future. Idempotent: the unique
 * index on (recurrence_rule_id, occurrence_date) makes a second run a no-op, so it is safe
 * to call from every scheduler tick.
 */
@Service
public class RecurrenceService {

    private static final Logger log = LoggerFactory.getLogger(RecurrenceService.class);

    /** Occurrences older than this at materialisation time are skipped (bot was down for days). */
    private static final Duration BACKFILL_TOLERANCE = Duration.ofMinutes(5);

    private final RecurrenceRuleRepository rules;
    private final TaskRepository tasks;
    private final TaskService taskService;
    private final UserRepository users;
    private final Clock clock;

    public RecurrenceService(RecurrenceRuleRepository rules, TaskRepository tasks, TaskService taskService,
                             UserRepository users, Clock clock) {
        this.rules = rules;
        this.tasks = tasks;
        this.taskService = taskService;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Deliberately not @Transactional: each occurrence is created in its own transaction so a
     * duplicate-key race on one rule cannot poison the whole run.
     */
    public int materialize(Duration lookahead) {
        Instant now = clock.instant();
        Instant horizon = now.plus(lookahead);
        int created = 0;
        for (RecurrenceRule rule : rules.findByActiveTrue()) {
            User user = users.findById(rule.getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            // The rule's zone is the user's zone at creation; if the user moved, prefer the current one.
            ZoneId zone = user.zone();
            LocalDate date = now.atZone(zone).toLocalDate();
            LocalDate lastDate = horizon.atZone(zone).toLocalDate();
            while (!date.isAfter(lastDate)) {
                if (rule.days().contains(date.getDayOfWeek())) {
                    ZonedDateTime at = date.atTime(rule.getTimeOfDay()).atZone(zone);
                    Instant scheduledAt = at.toInstant();
                    boolean inWindow = !scheduledAt.isBefore(now.minus(BACKFILL_TOLERANCE)) && !scheduledAt.isAfter(horizon);
                    if (inWindow && !tasks.existsByRecurrenceRuleIdAndOccurrenceDate(rule.getId(), date)) {
                        try {
                            taskService.createOccurrence(rule, date, scheduledAt);
                            created++;
                        } catch (DataIntegrityViolationException raced) {
                            log.debug("Occurrence {} of rule {} already exists", date, rule.getId());
                        }
                    }
                }
                date = date.plusDays(1);
            }
        }
        return created;
    }

    public List<RecurrenceRule> activeRules() {
        return rules.findByActiveTrue();
    }
}
