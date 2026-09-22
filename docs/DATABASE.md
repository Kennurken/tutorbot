# Database

PostgreSQL 16, schema owned by Flyway (`src/main/resources/db/migration`). Hibernate runs with
`ddl-auto: validate`: it may never change the schema, only check that entities match it.

## Conventions

- `BIGSERIAL` identity keys (`GenerationType.IDENTITY`): ids are shown to users (`#184`), so they
  must be short and monotonic. Sequences with batching are a premature optimisation here.
- Every timestamp is `TIMESTAMPTZ` and mapped to `java.time.Instant`. The database never stores a
  wall-clock time except `recurrence_rules.time_of_day` / `users.morning_plan_time`, which are
  *by definition* local times and are always paired with a zone.
- Enums are `VARCHAR` with `@Enumerated(EnumType.STRING)`: readable in SQL, safe to reorder in Java,
  no `ALTER TYPE` ceremony. Renaming a constant needs a data migration; that is the accepted cost.
- JSON payloads (`task_events.payload`, `verification_sessions.verdict`, `weekly_reviews.stats`,
  `notifications.reply_markup`, `conversation_states.context`) are `JSONB` mapped as `String` with
  `@JdbcTypeCode(SqlTypes.JSON)`: the application serialises with Jackson, the DB can still index/query.
- Cross-module references are plain id columns in entities (no `@ManyToOne` graphs across modules).
  Foreign keys still exist in SQL for integrity.
- `created_at` / `updated_at` maintained by JPA callbacks; `task_events` is append-only.

## Timezone model

```
input  "завтра в 19"  ─(user.timezone)─▶  Instant (UTC)  ──stored──▶  scheduled_at TIMESTAMPTZ
output scheduled_at   ─(user.timezone)─▶  "22.09 19:00"
"today" for a user    = now.atZone(user.zone()).toLocalDate()
```

The user can change `users.timezone` at any time (travel): stored instants do not move, the
rendering does. Recurring rules store the zone they were created in but are materialised in the
user's *current* zone, so a rule "20:00" follows the user.

## Tables

### users
Identity + settings + consequence policy + override state. One row per Telegram user.
Notable columns: `telegram_user_id` (unique), `chat_id`, `timezone`, `mode`, `daily_task_limit`,
`max_daily_study_minutes`, `verification_max_questions`, `morning_plan_time`,
`consequences_enabled`, `missed_extra_minutes`, `max_extra_minutes`, `review_task_on_fail`,
`require_skip_reason`, `paused_until`, `recovery_mode_until`, `last_morning_plan_date`.

### goals
`user_id`, `title`, `horizon` (MONTHS_1 … YEAR_1), `status` (ACTIVE/ACHIEVED/DROPPED).
Tasks optionally point at a goal (`tasks.goal_id`). Milestones/projects from the brief are
deferred: a goal → task link is enough until decomposition exists.

### recurrence_rules
`days_of_week` ("MONDAY,WEDNESDAY"), `time_of_day`, `timezone`, template fields for the task.
Materialised 36 h ahead by the tick; `active = false` stops future occurrences only.

### tasks
The commitment. `status` (see state machine), `verification_required`, `verification_status`,
`scheduled_at`, `estimated_minutes` + `extra_minutes` (consequence), `deadline_at`,
per-phase timestamps (`notified_at`, `started_at`, `reported_done_at`, `completed_at`, `missed_at`),
`reschedule_count`, `verification_attempts`, `skip_category`/`skip_reason`, `system_fault`
(missed because the bot was down/paused), `version` (optimistic lock), `recurrence_rule_id` +
`occurrence_date`.

Indexes: `(user_id, status)`, `(user_id, scheduled_at)`, partial `(scheduled_at) WHERE status IN
('SCHEDULED','NOTIFIED','STARTED')` for the tick, unique partial `(recurrence_rule_id,
occurrence_date)` for idempotent materialisation.

### task_events
`event_type`, `actor` (USER/SYSTEM/AI), `payload` JSONB, `occurred_at`. Answers "why did the bot
do X" and feeds analytics. Indexed by `(task_id, occurred_at)` and `(user_id, occurred_at)`.

### notifications (outbox)
`kind`, `dedupe_key` (unique; e.g. `task:184:REMINDER:0`), `text`, `reply_markup`, `scheduled_at`,
`status` (SCHEDULED/SENT/FAILED/CANCELLED), `attempts`, `next_attempt_at` (lease/backoff),
`last_error`, `sent_at`, `telegram_message_id`. Partial index on `(scheduled_at) WHERE status='SCHEDULED'`.

### verification_sessions / verification_turns
Session: `attempt_no`, `status`, `difficulty` (1–4), `max_questions`, `question_count`,
`short_answer_strikes`, `score`, `confidence`, `verdict` JSONB, `expires_at`. Unique partial index
`(user_id) WHERE status='IN_PROGRESS'`: one exam at a time. Turns: `seq`, `question`, `answer`,
`score`, `feedback`, timestamps; unique `(session_id, seq)`.

### knowledge_topics
Unique `(user_id, subject, topic)`. `estimated_mastery`, `confidence`, `sample_count`,
`stability_days` (forgetting-curve constant), `last_verified_at`. Retention is computed, not stored.

### consequences
`type` (EXTRA_MINUTES / REVIEW_TASK / OVERLOAD_DETECTED), `source_task_id`, `target_task_id`,
`minutes`, `reason` (human-readable explanation), `reverted`.

### weekly_reviews
Unique `(user_id, week_start)`; `stats` JSONB (the exact input the model saw), `narrative`,
`ai_generated`.

### ai_interactions
`purpose`, `prompt_version`, `model`, `input_tokens`, `output_tokens`, `estimated_cost_usd`,
`latency_ms`, `success`, `error`. Cost per user/task/verification is a `GROUP BY` away.

### telegram_updates
`update_id` primary key. Insert-first idempotency for redelivered webhooks.

### conversation_states
`user_id` PK, `state`, `context` JSONB (task id, skip category, task draft). Persisted so a restart
mid-exam loses nothing.

### V2 additions
`users`: `quiet_hours_start/end`, `evening_summary_time`, `last_evening_summary_date`, `quiz_time`,
`last_quiz_date`. `tasks.kind` (REGULAR / REVIEW / QUIZ). `knowledge_topics.task_type`,
`knowledge_topics.last_quiz_at`.

## Migrations policy

- Never edit an applied migration; add `V2__...sql`.
- Additive changes first (new nullable column, backfill, then constraint) so a rollback is a redeploy.
- Enum value renames: `UPDATE ... SET col = 'NEW' WHERE col = 'OLD'` in the same migration as the code change.
