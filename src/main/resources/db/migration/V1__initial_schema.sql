-- =============================================================================
-- V1: initial schema. All timestamps are TIMESTAMPTZ (stored as UTC); the user's
-- IANA timezone lives in users.timezone and is applied only at the edges
-- (parsing input, rendering output, computing "today").
-- =============================================================================

CREATE TABLE users (
    id                      BIGSERIAL PRIMARY KEY,
    telegram_user_id        BIGINT       NOT NULL UNIQUE,
    chat_id                 BIGINT       NOT NULL,
    first_name              VARCHAR(128),
    username                VARCHAR(64),
    language                VARCHAR(8)   NOT NULL DEFAULT 'ru',
    timezone                VARCHAR(64)  NOT NULL,
    mode                    VARCHAR(16)  NOT NULL DEFAULT 'NORMAL',
    -- settings (embedded; 1:1 tables add joins without adding value at this size)
    daily_task_limit        INT          NOT NULL DEFAULT 6,
    max_daily_study_minutes INT          NOT NULL DEFAULT 180,
    morning_plan_time       TIME         NOT NULL DEFAULT '08:00',
    verification_max_questions INT       NOT NULL DEFAULT 4,
    -- consequence policy (user-defined, bounded)
    consequences_enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    missed_extra_minutes    INT          NOT NULL DEFAULT 10,
    max_extra_minutes       INT          NOT NULL DEFAULT 30,
    review_task_on_fail     BOOLEAN      NOT NULL DEFAULT TRUE,
    require_skip_reason     BOOLEAN      NOT NULL DEFAULT TRUE,
    -- human override
    paused_until            TIMESTAMPTZ,
    recovery_mode_until     TIMESTAMPTZ,
    last_morning_plan_date  DATE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE goals (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    horizon     VARCHAR(16)  NOT NULL DEFAULT 'MONTHS_3',
    target_date DATE,
    status      VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_goals_user_status ON goals (user_id, status);

CREATE TABLE recurrence_rules (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users (id),
    goal_id               BIGINT       REFERENCES goals (id),
    title                 VARCHAR(200) NOT NULL,
    subject               VARCHAR(64),
    topic                 VARCHAR(120),
    task_type             VARCHAR(16)  NOT NULL,
    priority              VARCHAR(16)  NOT NULL,
    days_of_week          VARCHAR(80)  NOT NULL,  -- "MONDAY,WEDNESDAY,FRIDAY"
    time_of_day           TIME         NOT NULL,  -- wall-clock time in `timezone`
    timezone              VARCHAR(64)  NOT NULL,
    estimated_minutes     INT          NOT NULL,
    verification_required BOOLEAN      NOT NULL DEFAULT TRUE,
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_recurrence_rules_user_active ON recurrence_rules (user_id, active);

CREATE TABLE tasks (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT       NOT NULL REFERENCES users (id),
    goal_id               BIGINT       REFERENCES goals (id),
    recurrence_rule_id    BIGINT       REFERENCES recurrence_rules (id),
    occurrence_date       DATE,                   -- local date of a recurring occurrence
    title                 VARCHAR(200) NOT NULL,
    description           TEXT,
    subject               VARCHAR(64),
    topic                 VARCHAR(120),
    task_type             VARCHAR(16)  NOT NULL,
    priority              VARCHAR(16)  NOT NULL,
    status                VARCHAR(32)  NOT NULL,
    verification_required BOOLEAN      NOT NULL DEFAULT TRUE,
    verification_status   VARCHAR(16)  NOT NULL DEFAULT 'NOT_REQUIRED',
    scheduled_at          TIMESTAMPTZ,
    estimated_minutes     INT          NOT NULL,
    extra_minutes         INT          NOT NULL DEFAULT 0,   -- added by the consequence engine
    deadline_at           TIMESTAMPTZ,
    notified_at           TIMESTAMPTZ,
    started_at            TIMESTAMPTZ,
    reported_done_at      TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    missed_at             TIMESTAMPTZ,
    reschedule_count      INT          NOT NULL DEFAULT 0,
    verification_attempts INT          NOT NULL DEFAULT 0,
    skip_category         VARCHAR(32),
    skip_reason           TEXT,
    system_fault          BOOLEAN      NOT NULL DEFAULT FALSE, -- missed because the bot was down
    version               BIGINT       NOT NULL DEFAULT 0,     -- optimistic locking
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_user_status ON tasks (user_id, status);
CREATE INDEX idx_tasks_user_scheduled ON tasks (user_id, scheduled_at);
CREATE INDEX idx_tasks_due ON tasks (scheduled_at)
    WHERE status IN ('SCHEDULED', 'NOTIFIED', 'STARTED');
CREATE UNIQUE INDEX uq_tasks_recurrence_occurrence ON tasks (recurrence_rule_id, occurrence_date)
    WHERE recurrence_rule_id IS NOT NULL;

-- Append-only behaviour log. Analytics and "why did the bot do X" are answered from here.
CREATE TABLE task_events (
    id          BIGSERIAL PRIMARY KEY,
    task_id     BIGINT      NOT NULL REFERENCES tasks (id),
    user_id     BIGINT      NOT NULL REFERENCES users (id),
    event_type  VARCHAR(48) NOT NULL,
    actor       VARCHAR(16) NOT NULL,   -- USER | SYSTEM | AI
    payload     JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_task_events_task ON task_events (task_id, occurred_at);
CREATE INDEX idx_task_events_user_time ON task_events (user_id, occurred_at);

-- Outbox: a row is created first, delivery is a separate step with retries.
CREATE TABLE notifications (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL REFERENCES users (id),
    task_id             BIGINT      REFERENCES tasks (id),
    kind                VARCHAR(32) NOT NULL,
    dedupe_key          VARCHAR(96) UNIQUE,
    text                TEXT        NOT NULL,
    reply_markup        JSONB,
    scheduled_at        TIMESTAMPTZ NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',
    attempts            INT         NOT NULL DEFAULT 0,
    next_attempt_at     TIMESTAMPTZ,
    last_error          VARCHAR(500),
    sent_at             TIMESTAMPTZ,
    telegram_message_id BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_due ON notifications (scheduled_at) WHERE status = 'SCHEDULED';
CREATE INDEX idx_notifications_task ON notifications (task_id);

CREATE TABLE verification_sessions (
    id               BIGSERIAL PRIMARY KEY,
    task_id          BIGINT           NOT NULL REFERENCES tasks (id),
    user_id          BIGINT           NOT NULL REFERENCES users (id),
    attempt_no       INT              NOT NULL DEFAULT 1,
    status           VARCHAR(16)      NOT NULL,   -- IN_PROGRESS PASSED FAILED UNCERTAIN EXPIRED ABANDONED
    difficulty       INT              NOT NULL DEFAULT 2,  -- 1..4, adapted per answer
    max_questions    INT              NOT NULL,
    question_count   INT              NOT NULL DEFAULT 0,
    short_answer_strikes INT          NOT NULL DEFAULT 0,  -- anti-gaming nudges given
    score            DOUBLE PRECISION,
    confidence       DOUBLE PRECISION,
    verdict          JSONB,
    started_at       TIMESTAMPTZ      NOT NULL DEFAULT now(),
    last_activity_at TIMESTAMPTZ      NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ      NOT NULL,
    finished_at      TIMESTAMPTZ
);
-- One active verification per user: keeps the conversation unambiguous.
CREATE UNIQUE INDEX uq_verification_active_per_user ON verification_sessions (user_id)
    WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_verification_sessions_task ON verification_sessions (task_id);

CREATE TABLE verification_turns (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT           NOT NULL REFERENCES verification_sessions (id),
    seq         INT              NOT NULL,
    question    TEXT             NOT NULL,
    answer      TEXT,
    score       DOUBLE PRECISION,
    feedback    TEXT,
    asked_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    answered_at TIMESTAMPTZ,
    UNIQUE (session_id, seq)
);

CREATE TABLE knowledge_topics (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT           NOT NULL REFERENCES users (id),
    subject           VARCHAR(64)      NOT NULL,
    topic             VARCHAR(120)     NOT NULL,
    estimated_mastery DOUBLE PRECISION NOT NULL,
    confidence        DOUBLE PRECISION NOT NULL,
    sample_count      INT              NOT NULL DEFAULT 0,
    stability_days    DOUBLE PRECISION NOT NULL DEFAULT 7,  -- forgetting-curve time constant
    last_verified_at  TIMESTAMPTZ,
    created_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ      NOT NULL DEFAULT now(),
    UNIQUE (user_id, subject, topic)
);

CREATE TABLE consequences (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT      NOT NULL REFERENCES users (id),
    source_task_id BIGINT      REFERENCES tasks (id),
    target_task_id BIGINT      REFERENCES tasks (id),
    type           VARCHAR(32) NOT NULL,   -- EXTRA_MINUTES REVIEW_TASK OVERLOAD_DETECTED
    minutes        INT,
    reason         TEXT        NOT NULL,
    applied_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    reverted       BOOLEAN     NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_consequences_user_time ON consequences (user_id, applied_at);

CREATE TABLE weekly_reviews (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    week_start   DATE        NOT NULL,
    stats        JSONB       NOT NULL,
    narrative    TEXT,
    ai_generated BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, week_start)
);

-- Every model call: what prompt version, which model, how much it cost.
CREATE TABLE ai_interactions (
    id                 BIGSERIAL PRIMARY KEY,
    user_id            BIGINT         REFERENCES users (id),
    purpose            VARCHAR(32)    NOT NULL,
    prompt_version     VARCHAR(32)    NOT NULL,
    model              VARCHAR(96)    NOT NULL,
    input_tokens       INT,
    output_tokens      INT,
    estimated_cost_usd NUMERIC(10, 6),
    latency_ms         INT,
    success            BOOLEAN        NOT NULL,
    error              VARCHAR(500),
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_interactions_user_time ON ai_interactions (user_id, created_at);

-- Idempotency for Telegram updates (Telegram retries webhooks that were not acknowledged).
CREATE TABLE telegram_updates (
    update_id   BIGINT PRIMARY KEY,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- What the bot is currently waiting for from this user (a skip reason, a verification answer...).
CREATE TABLE conversation_states (
    user_id    BIGINT PRIMARY KEY REFERENCES users (id),
    state      VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    context    JSONB,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
