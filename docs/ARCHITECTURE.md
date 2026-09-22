# Architecture & product analysis

This is the senior-architect pass the project brief asked for *before* coding, kept as the
living design document. Sections A–N follow the brief; the "Decisions" section records what was
actually built and why, including what was deliberately left out.

---

## A. Product concept

An **accountability tutor**: a Telegram bot that (1) turns intentions into scheduled, sized
commitments, (2) pushes at the right moment with a bounded escalation ladder, (3) refuses to
accept "done" without evidence — a short adaptive exam — and (4) learns from misses and failed
exams to adapt the schedule instead of adding pressure.

It is not a reminder bot (reminders are a means), not a chatbot (the model is a component, not
the product), and not a gamified habit app (the only metric that matters is *promised → started →
verified*).

## B. Core problems it solves

1. **Activation energy.** The gap between wanting and starting. Solved by scheduled commands with
   explicit start times, a `[Start]` button, a 10/20/30-minute ladder, and "5 minutes is enough".
2. **Completion ≠ understanding.** Solved by verification: `/done` moves a task to
   `PENDING_VERIFICATION`; only a passed exam completes it.
3. **Pressure without adaptation.** Solved by bounded consequences, overload detection, recovery
   mode, and a weekly review that recommends *smaller / earlier / fewer*, never *harder*.
4. **Forgetting.** Solved by a knowledge profile with a forgetting curve and review recommendations.

### Honest weaknesses of the concept (not flattery)

- **The examiner is an LLM.** It can be gamed by confident nonsense and can be unfair to terse but
  correct answers. Mitigations: skeptical prompt, backend sanity checks on verdicts, min word count,
  UNCERTAIN as a first-class outcome, and the user's ability to retry. It is still not an
  objective exam; the product must say so (`/knowledge` legend does).
- **Self-reported start/finish.** Nothing stops the user from pressing Start and watching videos.
  The exam is the only real check. That is by design: the system polices outcomes, not screens.
- **Notification fatigue is the main churn risk.** Four messages per missed task is the absolute
  ceiling; escalation is cancelled the moment the user acts, and paused users get nothing.
- **Single-user economics.** At ~$0.001 per verification the cost is irrelevant; the design cost
  is engineering time. Most of the brief's SaaS/multi-tenant ideas are correctly deferred.
- **Behavioural analytics need volume.** "You complete 91% between 17:00 and 20:00" needs weeks of
  data. The weekly review only reports buckets with ≥2 samples and phrases everything as a hypothesis.

### What is technically difficult

- Reliable time-driven behaviour on a free host that sleeps (solved: DB-driven idempotent tick +
  external ping, see I).
- Keeping model calls out of database transactions (solved with explicit `TransactionTemplate`
  boundaries in the verification and review services).
- Adaptive questioning that the backend can still constrain (solved: the model returns one turn at
  a time; the backend enforces min/max questions and overrides inconsistent verdicts).

### What is unnecessary now (and was not built)

Microservices, Redis, Quartz, Kafka, a REST API for a dashboard that does not exist, entertainment
blocking (no reversible platform hook exists on Telegram), voice, calendar sync, multi-tenancy
billing, vector memory. Each is a configuration point or a module boundary, not code.

### What is dangerous to overengineer

The knowledge model (a "true" Bayesian knowledge tracing model would be unfalsifiable at n=4),
the consequence engine (anything cleverer than *bounded, reversible, non-stacking* becomes
punishment design), and the planner (an optimiser over a day with six tasks is a sort).

## C. High-level architecture

Modular monolith, one deployable, one database:

```
              ┌────────────── Telegram Bot API ──────────────┐
              │ webhook (prod)          long polling (dev)    │
              └───────────────┬──────────────────────────────┘
                              ▼
   ┌─────────────────────── telegram ───────────────────────┐
   │ UpdateProcessor (dedupe, allowlist, errors) → Dispatcher │
   │ commands/*  callbacks/*  flows/* (shared chat logic)     │
   └───┬──────────┬──────────┬──────────┬──────────┬─────────┘
       ▼          ▼          ▼          ▼          ▼
     task    verification  planning   review    user/goal/conversation
       │          │                       │
       │   ┌──────┴───────┐               │
       │   ▼              ▼               ▼
       │ knowledge       ai (AiGateway → AiProvider → gateway | fake)
       ▼
   Spring ApplicationEvents (TaskStatusChanged, VerificationFinished, VerificationExpired)
       │
       ├──▶ notification (outbox rows)      ├──▶ consequence (bounded policy)
       ▼
   scheduling: TickService  ◀── @Scheduled every 30 s  ◀── POST /internal/tick (external cron)
       │  recurrence → notifyDue → missed → expire sessions → daily routine → dispatch outbox
       ▼
   PostgreSQL (Flyway-managed) — the only source of truth
```

Rules of the monolith:

- Modules reference each other through **services** and **events**, never through repositories.
- Foreign keys are plain ids in entities (no object graphs across modules) so each module can be
  tested and reasoned about alone.
- **The LLM never touches state.** `AiGateway` returns validated DTOs; only services with explicit
  rules (`TaskService.transition`, `VerificationPolicy.decide`) change rows.

## D. Modules and responsibilities

| Package | Responsibility | Key classes |
|---|---|---|
| `user` | Identity, timezone, language, mode, settings, consequence policy, pause/recovery | `User`, `UserSettings`, `ConsequencePolicy`, `UserService` |
| `task` | Task entity, state machine, event log, recurrence, time-driven lifecycle | `TaskService`, `TaskStateMachine`, `TaskLifecycleService`, `RecurrenceService`, `TaskEventRecorder` |
| `verification` | Exam sessions and turns, adaptive policy, fallbacks, expiry | `VerificationService`, `VerificationPolicy`, `FallbackQuestions` |
| `knowledge` | Mastery / confidence / forgetting model per topic | `KnowledgeModel`, `KnowledgeService` |
| `consequence` | Bounded, reversible consequences; overload detection | `ConsequenceEngine` |
| `planning` | NL parser, model intent fallback, day planner, plan rendering | `NaturalLanguageTaskParser`, `TaskIntentService`, `DailyPlanner` |
| `review` | Weekly statistics and narrative | `WeeklyStatsCalculator`, `WeeklyReviewService` |
| `notification` | Outbox with retries, escalation ladder, cancellation | `NotificationService`, `TaskNotificationListener` |
| `scheduling` | The idempotent tick, external trigger, daily routines | `TickService`, `TickController`, `DailyRoutineService` |
| `ai` | Provider abstraction, resilience, JSON validation, audit, rate limit, prompts | `AiProvider`, `ResilientAiProvider`, `AiGateway`, `AiTutorService`, `Prompts` |
| `telegram` | Transport, dedupe, dispatch, handlers, flows | `UpdateProcessor`, `UpdateDispatcher`, `handler/*`, `flow/*` |
| `messaging` | i18n (RU/EN), tone by mode, keyboards, formatting | `BotMessages`, `Keyboards`, `TaskFormatter` |
| `conversation` | What the bot is waiting for from the user | `ConversationService` |
| `goal` | Long-term goals (minimal) | `GoalService` |
| `common` | Clock, properties, exceptions, time formatting | `ClockConfig`, `AccountabilityProperties` |

## E. Database

See [DATABASE.md](DATABASE.md) for the full schema. Shape:

```
users 1──∞ goals
users 1──∞ recurrence_rules 1──∞ tasks (occurrence_date unique per rule)
users 1──∞ tasks 1──∞ task_events               (append-only)
                 tasks 1──∞ notifications        (outbox)
                 tasks 1──∞ verification_sessions 1──∞ verification_turns
users 1──∞ knowledge_topics (unique user+subject+topic)
users 1──∞ consequences (source_task, target_task)
users 1──∞ weekly_reviews (unique user+week_start)
users 1──∞ ai_interactions
users 1──1 conversation_states
telegram_updates (update_id PK: idempotency)
```

## F. State machines

### Task

```
CREATED ─▶ SCHEDULED ─▶ NOTIFIED ─▶ STARTED ─▶ REPORTED_DONE ─▶ PENDING_VERIFICATION
                │           │          │              │                    │
                │           │          │              └─(no verification)─▶ COMPLETED
                │           │          │                                   │
                │           ▼          ▼                    VERIFICATION_IN_PROGRESS
                │        MISSED ◀──────┘                      │        │        │
                │           │                              COMPLETED  FAILED  PENDING_VERIFICATION
                ▼           ▼                                            │      (uncertain/expired)
             SKIPPED ─▶ SCHEDULED (reschedule = event, not a state)     └──▶ PENDING_VERIFICATION (retry)
             CANCELLED / EXPIRED (terminal-ish)
```

Who initiates what:

| Transition | Actor |
|---|---|
| CREATED→SCHEDULED, START, REPORTED_DONE, SKIPPED, reschedule, CANCELLED | USER |
| SCHEDULED→NOTIFIED, →MISSED (grace / downtime / day-end sweep), verification EXPIRED | SYSTEM (tick) |
| VERIFICATION_IN_PROGRESS→COMPLETED / FAILED / PENDING (uncertain) | AI verdict, **decided by `VerificationPolicy`** |

Every transition is one transaction: state change + event row + in-process event; listeners
(notification, consequence, knowledge) run inside it, so a task is never NOTIFIED without its
reminders queued. Optimistic locking (`tasks.version`) makes two concurrent `/done` presses safe.

### Verification session

```
IN_PROGRESS ─▶ PASSED | FAILED | UNCERTAIN     (verdict after min..max questions)
            ─▶ EXPIRED   (no activity for accountability.verification.session-timeout)
            ─▶ ABANDONED (/abandon)
```
One active session per user (partial unique index). Starting another task's exam is refused
with a message naming the active one; the trade-off (no parallel exams) keeps the chat unambiguous.

## G. AI architecture

Full detail in [AI.md](AI.md). Summary of every interaction:

| Job | When | Prompt | Output DTO | Fallback without AI |
|---|---|---|---|---|
| Task intent | free text the regex parser could not read | `task_intent.v1` | `TaskIntentResult` | "I could not read that" |
| Verification step | every answer (and the opening question) | `verification_step.v1` | `VerificationStep` | type-specific question bank; answer kept, retried later |
| Weekly narrative | Sunday 20:00 and `/review` | `weekly_review.v1` | `WeeklyNarrative` | numbers-only review |
| Skip analysis | free-text skip reason | `skip_analysis.v1` | `SkipAnalysis` | reason stored, no insight |

Pipeline: `AiTutorService` builds the prompt → `AiGateway` (per-user rate limit → provider →
extract JSON → parse → bean validation → audit row) → typed `Optional<T>`; callers must handle
`empty`. The provider chain is `ResilientAiProvider(OpenAiCompatibleAiProvider)`: retry with
backoff+jitter, fallback model on the last attempt, circuit breaker.

Where AI is **not** used: `/done`, `/skip`, `/today`, `/tasks`, scheduling, state transitions,
consequences, statistics. Cost per verification ≈ 3–4 calls × ~1.5k tokens ≈ $0.001 on
`grok-4.1-fast`.

## H. Accountability system

Commitment = task with time + duration. Verification = exam. Consequence = what the user's
`ConsequencePolicy` allows:

- `+missedExtraMinutes` to the next scheduled task of the same subject, capped by
  `maxExtraMinutes`, at most once per target task, never while paused / in recovery.
- A 20-minute review task tomorrow after a failed exam, once per source task.
- **Overload detection**: ≥3 misses in 24 h or ≥6 in 7 days ⇒ no consequence, `OVERLOAD_DETECTED`,
  24 h recovery mode (planner shows one ≤30-minute task), once per day.

Safety boundaries: everything is reversible (reschedule resets extra minutes; review tasks can be
cancelled), everything is explainable (`consequences.reason`, `task_events`), and everything is
switchable (`/settings consequences off`, `/pause`). No irreversible action exists in the codebase.
Entertainment blocking is intentionally absent: Telegram offers no reversible hook, and a bot
that deletes accounts is a bug, not a feature.

## I. Scheduling

Design goal: correctness after restarts and on a host that sleeps.

- **All state in PostgreSQL, nothing in memory.** The tick asks "what is due now?" and acts;
  running it twice, late, or after a crash converges to the same state (idempotent by
  construction: unique dedupe keys for notifications, unique occurrence per recurrence rule,
  state-machine guards on tasks).
- **Two triggers, one lock.** `@Scheduled` every 30 s in-process, plus `POST /internal/tick` for an
  external cron pinger. A `ReentrantLock.tryLock` skips overlapping runs.
- **Downtime semantics.** Restart at 18:59 → the 19:00 task is picked up by the first tick after
  boot. Down 18:50–19:20 → notified at 19:20 (20 min late, still inside the 30-min grace).
  Down for hours → the task is MISSED with `system_fault = true`: no consequence, a reschedule offer.
- **Outbox delivery** is at-least-once with a 2-minute lease and exponential backoff (max 5
  attempts). Duplicate sends after a crash between "sent" and "marked sent" are accepted over lost
  reminders. Nudges older than 6 hours are dropped rather than delivered stale.
- **Spring `@Scheduled` vs Quartz.** For one instance and an idempotent DB-driven tick, Quartz adds
  a job store and clustering we do not need. When a second instance appears, add ShedLock on the
  tick (one row, one dependency) or move to Quartz clustered mode. Documented, not built.

## J. MVP scope (what is built)

MVP 1: Telegram (webhook + polling), tasks, scheduler, escalation ladder, done/skip with reasons,
reschedule, pause, timezone, RU/EN.
MVP 2: recurring tasks, verification engine with adaptive questions, knowledge profile with decay.
MVP 3 (core): consequences with overload protection, daily planner + morning plan, weekly review
with statistics and narrative, event log, AI audit and cost, streak.

## K. Roadmap

- **V2:** voice answers (Telegram voice → STT → same verification path), goal → milestone →
  task decomposition prompt, deadline-aware planner, English-only mode, calendar import.
- **V3:** REST API + web dashboard (tasks, knowledge, analytics), multi-instance scheduling
  (ShedLock), Redis only if rate limiting/conversation state must be shared across instances.
- **SaaS:** plans and limits are already representable per user (`UserSettings`); add tenancy
  fields on `users`, per-plan AI budgets via `ai_interactions`, and billing.

## L. Risks

| Risk | Mitigation |
|---|---|
| Model outages / rate limits | fallback questions, fallback model, circuit breaker, "resend in a minute" |
| Model verdict quality | backend policy overrides, UNCERTAIN, retries, prompt versioning for regression tracking |
| Free host sleeps | external cron ping every minute keeps it awake; DB-driven tick tolerates gaps |
| Free Postgres limits (few connections) | Hikari pool of 5, session pooler URL |
| Notification spam → churn | hard ceiling of 4 messages per task, cancel-on-action, pause |
| Timezone bugs | all instants UTC, zone applied only at the edges, tests pin Asia/Almaty |
| Telegram redelivery / double taps | `telegram_updates` PK, optimistic locking, state machine |

## M. Portfolio value

What a Java backend reviewer sees: a modular monolith with explicit boundaries; a hand-written
state machine with tests; transactional event-driven decoupling; an outbox with leases and
retries; programmatic transaction boundaries around slow I/O; provider-agnostic LLM integration
with schema validation, resilience and cost accounting; Flyway-owned schema with partial unique
indexes; timezone-correct scheduling; a deterministic test double for the model; 67 tests
including chat-level end-to-end tests over raw Telegram updates; Docker + CI + free-tier deployment.

## N. Development plan (as executed)

1. Skeleton, configuration, Flyway V1, local Postgres.
2. `user`, `task` (entity, state machine, events, service), compile.
3. `ai` (provider, resilience, gateway, prompts, DTOs, fake).
4. `knowledge`, `conversation`, `verification`.
5. `messaging`, `telegram` client, `notification` outbox + listener.
6. `planning` (parser with tests first), `goal`, `consequence`, `review`, `scheduling`.
7. Telegram dispatcher, handlers, flows, i18n bundles.
8. Integration tests (lifecycle, chat flow, webhook, tick), Docker, CI, docs, deployment.

---

## Decisions (ADR-style, short)

- **Spring Boot 4.1 / Jackson 3 / Hibernate 7** — current supported line; Jackson 3 records + ISO
  dates by default removed a class of config.
- **No Lombok** — entities are explicit; the project is also a teaching artefact.
- **No Spring Security (yet)** — no browser users, no sessions. The two inbound endpoints are
  protected by constant-time secret comparison. Add Security with the dashboard.
- **Settings embedded in `users`** — one row per user, always loaded with the user; a 1:1 table adds
  a join and nothing else at this size.
- **RESCHEDULED is an event, not a status** — a rescheduled task is SCHEDULED again with a counter;
  otherwise every query for "active tasks" grows a special case.
- **Own Telegram client over a library** — five methods, explicit timeouts, no framework opinions
  about the update loop.
- **Hand-written retry/circuit breaker over Resilience4j** — one external service, one policy,
  fully unit-tested; revisit when a second policy appears.
- **In-memory per-user AI rate limit** — correct for one instance; move to the DB when scaling.
- **Regex parser before the model** — zero tokens for the 90% case, and the confirmation step makes
  a mis-parse cost one tap.
