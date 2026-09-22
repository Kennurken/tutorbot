package dev.kennurken.tutorbot.task;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.common.NotFoundException;
import dev.kennurken.tutorbot.task.event.Actor;
import dev.kennurken.tutorbot.task.event.TaskEventRecorder;
import dev.kennurken.tutorbot.task.event.TaskEventType;
import dev.kennurken.tutorbot.task.event.TaskStatusChanged;
import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the task lifecycle. Every status change goes through
 * {@link #transition}, which (1) validates against the state machine, (2) writes the event
 * log, (3) publishes an in-process event. That is the whole "source of truth" story:
 * the database plus these rules decide; the LLM only suggests.
 */
@Service
public class TaskService {

    private static final int MIN_MINUTES = 5;
    private static final int MAX_MINUTES = 240;

    private final TaskRepository tasks;
    private final RecurrenceRuleRepository recurrenceRules;
    private final TaskEventRecorder eventRecorder;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public TaskService(TaskRepository tasks, RecurrenceRuleRepository recurrenceRules,
                       TaskEventRecorder eventRecorder, ApplicationEventPublisher publisher, Clock clock) {
        this.tasks = tasks;
        this.recurrenceRules = recurrenceRules;
        this.eventRecorder = eventRecorder;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- creation

    @Transactional
    public Task create(User user, CreateTaskCommand cmd) {
        validate(cmd);
        Task task = new Task(user.getId(), cmd.title().trim(), cmd.type(), cmd.priority(),
                cmd.estimatedMinutes(), cmd.verificationRequired());
        task.setDescription(cmd.description());
        task.setSubject(cmd.subject());
        task.setTopic(cmd.topic());
        task.setDeadlineAt(cmd.deadlineAt());
        task.setGoalId(cmd.goalId());
        task = tasks.save(task);
        eventRecorder.record(task, TaskEventType.TASK_CREATED, Actor.USER, Map.of("title", task.getTitle()));
        if (cmd.scheduledAt() != null) {
            schedule(task, cmd.scheduledAt(), Actor.USER);
        }
        return task;
    }

    /** Creates the rule; concrete occurrences are materialised by {@link RecurrenceService}. */
    @Transactional
    public RecurrenceRule createRecurring(User user, CreateTaskCommand cmd) {
        validate(cmd);
        if (!cmd.isRecurring()) {
            throw new DomainException("Recurrence days are required");
        }
        RecurrenceRule rule = new RecurrenceRule(user.getId(), cmd.title().trim(), cmd.type(), cmd.priority(),
                cmd.recurrence().days(), cmd.recurrence().timeOfDay(), user.getTimezone(),
                cmd.estimatedMinutes(), cmd.verificationRequired());
        rule.setSubject(cmd.subject());
        rule.setTopic(cmd.topic());
        rule.setGoalId(cmd.goalId());
        return recurrenceRules.save(rule);
    }

    /** Used by the recurrence materialiser; occurrence uniqueness is enforced by the DB index. */
    @Transactional
    public Task createOccurrence(RecurrenceRule rule, LocalDate date, Instant scheduledAt) {
        Task task = new Task(rule.getUserId(), rule.getTitle(), rule.getType(), rule.getPriority(),
                rule.getEstimatedMinutes(), rule.isVerificationRequired());
        task.setSubject(rule.getSubject());
        task.setTopic(rule.getTopic());
        task.setGoalId(rule.getGoalId());
        task.setRecurrenceRuleId(rule.getId());
        task.setOccurrenceDate(date);
        task = tasks.save(task);
        eventRecorder.record(task, TaskEventType.TASK_CREATED, Actor.SYSTEM, Map.of("recurrenceRuleId", rule.getId()));
        schedule(task, scheduledAt, Actor.SYSTEM);
        return task;
    }

    private void validate(CreateTaskCommand cmd) {
        if (cmd.title() == null || cmd.title().isBlank()) {
            throw new DomainException("Title is required");
        }
        if (cmd.estimatedMinutes() < MIN_MINUTES || cmd.estimatedMinutes() > MAX_MINUTES) {
            throw new DomainException("Duration must be between " + MIN_MINUTES + " and " + MAX_MINUTES + " minutes");
        }
        if (cmd.type() == null || cmd.priority() == null) {
            throw new DomainException("Type and priority are required");
        }
    }

    // ------------------------------------------------------------- transitions

    @Transactional
    public Task schedule(Task task, Instant at, Actor actor) {
        task.setScheduledAt(at);
        transition(task, TaskStatus.SCHEDULED, actor, TaskEventType.TASK_SCHEDULED, Map.of("scheduledAt", at.toString()));
        return task;
    }

    @Transactional
    public Task markNotified(Task task) {
        task.setNotifiedAt(clock.instant());
        transition(task, TaskStatus.NOTIFIED, Actor.SYSTEM, TaskEventType.TASK_NOTIFIED, Map.of());
        return task;
    }

    @Transactional
    public Task start(User user, Long taskId) {
        Task task = requireOwned(user, taskId);
        if (task.getStatus() == TaskStatus.STARTED) {
            throw new DomainException("Task is already started");
        }
        task.setStartedAt(clock.instant());
        transition(task, TaskStatus.STARTED, Actor.USER, TaskEventType.TASK_STARTED, Map.of());
        return task;
    }

    /**
     * /done. The task is never completed here directly: if verification is required it goes to
     * PENDING_VERIFICATION and the verification module takes over. Pressing /done twice hits the
     * state machine (REPORTED_DONE is not reportable) and is answered with a clear message.
     */
    @Transactional
    public Task reportDone(User user, Long taskId) {
        Task task = requireOwned(user, taskId);
        if (!TaskStatus.REPORTABLE.contains(task.getStatus())) {
            throw new DomainException("Task #" + taskId + " is " + task.getStatus() + " and cannot be reported done");
        }
        Instant now = clock.instant();
        task.setReportedDoneAt(now);
        if (task.getStartedAt() == null) {
            task.setStartedAt(now);
        }
        transition(task, TaskStatus.REPORTED_DONE, Actor.USER, TaskEventType.TASK_COMPLETION_REPORTED, Map.of());
        if (task.isVerificationRequired()) {
            task.setVerificationStatus(VerificationStatus.PENDING);
            transition(task, TaskStatus.PENDING_VERIFICATION, Actor.SYSTEM, TaskEventType.TASK_COMPLETION_REPORTED,
                    Map.of("verification", "required"));
        } else {
            complete(task, Actor.USER);
        }
        return task;
    }

    private void complete(Task task, Actor actor) {
        task.setCompletedAt(clock.instant());
        transition(task, TaskStatus.COMPLETED, actor, TaskEventType.TASK_COMPLETED, Map.of());
    }

    @Transactional
    public Task skip(User user, Long taskId, SkipCategory category, String reason) {
        Task task = requireOwned(user, taskId);
        if (!TaskStatus.ACTIVE.contains(task.getStatus())) {
            throw new DomainException("Task #" + taskId + " is " + task.getStatus() + " and cannot be skipped");
        }
        task.setSkipCategory(category);
        task.setSkipReason(reason);
        transition(task, TaskStatus.SKIPPED, Actor.USER, TaskEventType.TASK_SKIPPED,
                Map.of("category", category.name(), "reason", reason == null ? "" : reason));
        return task;
    }

    @Transactional
    public Task reschedule(User user, Long taskId, Instant newTime) {
        Task task = requireOwned(user, taskId);
        return reschedule(task, newTime, Actor.USER);
    }

    @Transactional
    public Task reschedule(Task task, Instant newTime, Actor actor) {
        if (newTime.isBefore(clock.instant().minusSeconds(60))) {
            throw new DomainException("Cannot reschedule into the past");
        }
        Instant previous = task.getScheduledAt();
        task.setScheduledAt(newTime);
        task.setNotifiedAt(null);
        task.setStartedAt(null);
        task.setMissedAt(null);
        task.setReportedDoneAt(null);
        task.setSkipCategory(null);
        task.setSkipReason(null);
        task.setSystemFault(false);
        task.setRescheduleCount(task.getRescheduleCount() + 1);
        if (task.isVerificationRequired()) {
            task.setVerificationStatus(VerificationStatus.PENDING);
        }
        transition(task, TaskStatus.SCHEDULED, actor, TaskEventType.TASK_RESCHEDULED,
                Map.of("from", previous == null ? "" : previous.toString(), "to", newTime.toString()));
        return task;
    }

    @Transactional
    public Task cancel(User user, Long taskId) {
        Task task = requireOwned(user, taskId);
        transition(task, TaskStatus.CANCELLED, Actor.USER, TaskEventType.TASK_CANCELLED, Map.of());
        return task;
    }

    @Transactional
    public Task cancelBySystem(Task task, String reason) {
        transition(task, TaskStatus.CANCELLED, Actor.SYSTEM, TaskEventType.TASK_CANCELLED, Map.of("reason", reason));
        return task;
    }

    /** Same as {@link #create} but tagged with a non-user kind (review, quiz). */
    @Transactional
    public Task create(User user, CreateTaskCommand cmd, TaskKind kind) {
        Task task = create(user, cmd);
        task.setKind(kind);
        return tasks.save(task);
    }

    /** System decision: the grace period passed. {@code systemFault} = the bot itself was down. */
    @Transactional
    public Task markMissed(Task task, boolean systemFault, String reason) {
        task.setMissedAt(clock.instant());
        task.setSystemFault(systemFault);
        transition(task, TaskStatus.MISSED, Actor.SYSTEM, TaskEventType.TASK_MISSED,
                Map.of("systemFault", systemFault, "reason", reason));
        return task;
    }

    // ------------------------------------------------- verification hand-offs

    @Transactional
    public Task beginVerification(Task task) {
        task.setVerificationAttempts(task.getVerificationAttempts() + 1);
        task.setVerificationStatus(VerificationStatus.IN_PROGRESS);
        transition(task, TaskStatus.VERIFICATION_IN_PROGRESS, Actor.USER, TaskEventType.VERIFICATION_STARTED,
                Map.of("attempt", task.getVerificationAttempts()));
        return task;
    }

    @Transactional
    public Task passVerification(Task task, double score, double confidence) {
        task.setVerificationStatus(VerificationStatus.PASSED);
        eventRecorder.record(task, TaskEventType.VERIFICATION_PASSED, Actor.AI,
                Map.of("score", score, "confidence", confidence));
        complete(task, Actor.AI);
        return task;
    }

    @Transactional
    public Task failVerification(Task task, double score, double confidence) {
        task.setVerificationStatus(VerificationStatus.FAILED);
        transition(task, TaskStatus.FAILED, Actor.AI, TaskEventType.VERIFICATION_FAILED,
                Map.of("score", score, "confidence", confidence));
        return task;
    }

    @Transactional
    public Task uncertainVerification(Task task, double score, double confidence) {
        task.setVerificationStatus(VerificationStatus.UNCERTAIN);
        transition(task, TaskStatus.PENDING_VERIFICATION, Actor.AI, TaskEventType.VERIFICATION_UNCERTAIN,
                Map.of("score", score, "confidence", confidence));
        return task;
    }

    @Transactional
    public Task expireVerification(Task task) {
        task.setVerificationStatus(VerificationStatus.EXPIRED);
        transition(task, TaskStatus.PENDING_VERIFICATION, Actor.SYSTEM, TaskEventType.VERIFICATION_EXPIRED, Map.of());
        return task;
    }

    @Transactional
    public Task abandonVerification(Task task) {
        task.setVerificationStatus(VerificationStatus.PENDING);
        transition(task, TaskStatus.PENDING_VERIFICATION, Actor.USER, TaskEventType.VERIFICATION_EXPIRED,
                Map.of("reason", "abandoned"));
        return task;
    }

    @Transactional
    public Task retryVerification(User user, Long taskId) {
        Task task = requireOwned(user, taskId);
        if (task.getStatus() == TaskStatus.PENDING_VERIFICATION) {
            return task;
        }
        task.setVerificationStatus(VerificationStatus.PENDING);
        transition(task, TaskStatus.PENDING_VERIFICATION, Actor.USER, TaskEventType.VERIFICATION_STARTED,
                Map.of("retry", true));
        return task;
    }

    // ----------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Task requireOwned(User user, Long taskId) {
        return tasks.findByIdAndUserId(taskId, user.getId())
                .orElseThrow(() -> new NotFoundException("Task", taskId));
    }

    /** System-side lookup (scheduler, maintenance). User-facing code must use {@link #requireOwned}. */
    @Transactional(readOnly = true)
    public Task getById(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Task", taskId));
    }

    @Transactional(readOnly = true)
    public Optional<Task> findOwned(User user, Long taskId) {
        return tasks.findByIdAndUserId(taskId, user.getId());
    }

    @Transactional(readOnly = true)
    public List<Task> findForLocalDate(User user, LocalDate date) {
        ZoneId zone = user.zone();
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        return tasks.findByUserIdAndScheduledAtBetweenOrderByScheduledAt(user.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public List<Task> findByStatuses(User user, List<TaskStatus> statuses) {
        return tasks.findByUserIdAndStatusInOrderByScheduledAt(user.getId(), statuses);
    }

    /** Candidates for a bare /done or /start_task: tasks the user is expected to act on now. */
    @Transactional(readOnly = true)
    public List<Task> findActionable(User user) {
        return tasks.findByUserIdAndStatusInOrderByScheduledAt(user.getId(), List.of(TaskStatus.STARTED,
                TaskStatus.NOTIFIED, TaskStatus.SCHEDULED));
    }

    @Transactional(readOnly = true)
    public List<Task> findPendingVerification(User user) {
        return tasks.findByUserIdAndStatusInOrderByScheduledAt(user.getId(),
                List.of(TaskStatus.PENDING_VERIFICATION, TaskStatus.FAILED));
    }

    @Transactional(readOnly = true)
    public List<Task> findInWindow(User user, Instant from, Instant to) {
        return tasks.findByUserIdAndScheduledAtBetweenOrderByScheduledAt(user.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public Optional<Task> findNextScheduledForSubject(User user, String subject) {
        return tasks.findFirstByUserIdAndSubjectIgnoreCaseAndStatusAndScheduledAtAfterOrderByScheduledAt(
                user.getId(), subject, TaskStatus.SCHEDULED, clock.instant());
    }

    @Transactional(readOnly = true)
    public long countMissedSince(User user, Instant since) {
        return tasks.countByUserIdAndStatusAndMissedAtAfterAndSystemFaultFalse(user.getId(), TaskStatus.MISSED, since);
    }

    /** (total, completed) tasks linked to a goal. */
    @Transactional(readOnly = true)
    public long[] goalProgress(Long goalId) {
        return new long[] {tasks.countByGoalId(goalId), tasks.countByGoalIdAndStatus(goalId, TaskStatus.COMPLETED)};
    }

    @Transactional(readOnly = true)
    public List<RecurrenceRule> findRecurrenceRules(User user) {
        return recurrenceRules.findByUserIdAndActiveTrueOrderByTimeOfDay(user.getId());
    }

    @Transactional
    public void deactivateRecurrence(User user, Long ruleId) {
        RecurrenceRule rule = recurrenceRules.findById(ruleId)
                .filter(r -> r.getUserId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Recurrence", ruleId));
        rule.setActive(false);
        recurrenceRules.save(rule);
    }

    @Transactional
    public Task save(Task task) {
        return tasks.save(task);
    }

    // ------------------------------------------------------------------ core

    private void transition(Task task, TaskStatus to, Actor actor, TaskEventType eventType, Map<String, ?> payload) {
        TaskStatus from = task.getStatus();
        TaskStateMachine.assertAllowed(from, to);
        task.setStatus(to);
        tasks.save(task);
        eventRecorder.record(task, eventType, actor, payload);
        publisher.publishEvent(new TaskStatusChanged(task, from, to, actor));
    }
}
