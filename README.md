# tutorbot — AI accountability tutor for Telegram

> Turn intentions into verified actions.

A Telegram bot that is a planner, an external accountability loop and an AI examiner at once.
It does **not** trust `/done`: every study task ends with a short adaptive oral exam, and only a
passed exam completes the task. Misses have bounded, pre-agreed consequences; repeated misses
trigger a recovery protocol instead of more pressure.

```
19:00  🕒 Time to start.  #184 English — Present Perfect · 20m        [Start] [Done] [Skip]
19:24  /done
       ☑️ Reported done. Verification required.
       🧪 Question 1/4 — Explain in your own words when Present Perfect is used, not Past Simple.
       …
       ✅ PASSED — score 84% · confidence 88%
       Strengths: result-in-present idea · Gaps: "since/for" contrast
```

Built as a **modular monolith in Java 17 / Spring Boot 4** with PostgreSQL, Flyway, an
OpenAI-compatible AI layer (Vercel AI Gateway → Grok), and a fully DB-driven scheduler that
survives restarts and free-tier sleeps.

## Why it exists

The hard part of studying is not the plan; it is the transition from *"I want to"* to *"I started"*.
An external voice that says *"19:00 — English. Just begin."* raises the odds dramatically.
This bot is that voice, plus the one thing a human tutor adds that a todo app cannot:
**it checks whether you actually understood**.

Principle: **strict with behaviour, respectful toward the person.** The system may say
*TASK NOT COMPLETED* or *VERIFICATION FAILED*. It never judges the human.

## What it does

| Area | Behaviour |
|---|---|
| Tasks | Natural-language creation (RU/EN): *"завтра в 19 английский на 30 минут"*, *"every mon wed fri Java at 20:00 45 min"*. One-off and recurring. Backlog without time. |
| Reminders | Start → +10 min *not started* → +20 min *overdue* → +30 min **MISSED**. Escalation is cancelled the moment you act. |
| Verification | Adaptive 2–4 question exam per task type (theory / programming / language / math / reading / project). Harder after a good answer, simpler after a weak one. PASS / FAIL / UNCERTAIN with score, confidence, strengths, gaps. |
| Anti-gaming | Too-short answers are bounced before a model call; the examiner is prompted to be skeptical; inconsistent verdicts (PASS on low-scored answers) are downgraded by the backend. |
| Knowledge profile | Per subject/topic: mastery estimate, confidence, sample count, forgetting-curve retention, *review recommended* flags. |
| Consequences | User-defined, bounded: +N min to the next session of the same subject (capped), a 20-min review task after a failed exam. Never stacked: repeated misses trip **overload detection** → 24 h recovery mode. |
| Planner | `/today` ranks the day by priority, deadline, size; defers what does not fit your limits; one small task in recovery mode. Morning plan message. |
| Weekly review | Sunday evening (and `/review`): commitments vs started vs verified, best/worst time windows, long-task completion, per-subject scores, most common skip reason, AI cost — plus a model-written narrative with reasons for each recommendation. |
| Human override | `/pause 1h|today|24h|off`, `/settings consequences off`, three tones (NORMAL / STRICT / HARDCORE) that change wording only. |
| Audit | Every transition in `task_events`; every model call in `ai_interactions` with prompt version, model, tokens, cost. |

## Architecture in one picture

```
Telegram ──webhook/polling──▶ telegram (dispatcher, commands, buttons)
                                   │
        ┌──────────┬───────────────┼────────────────┬──────────────┐
        ▼          ▼               ▼                ▼              ▼
      task    verification     planning         review        settings/user
   (state     (session +       (NL parser,     (weekly       (pause, mode,
    machine,   adaptive         planner)        stats +        policy)
    events)    questions)                       narrative)
        │          │                                │
        │          ▼                                ▼
        │       ai  ─── AiProvider ── OpenAI-compatible gateway (Vercel AI Gateway → Grok)
        │                          └─ FakeAiProvider (tests / offline)
        ▼
   notification (outbox) ◀── consequence ◀── knowledge ◀── Spring application events
        │
   scheduling tick (every 30 s + external cron ping) ──▶ PostgreSQL (source of truth)
```

The LLM proposes; the backend decides. Model output is JSON, schema-validated, and can only
influence state through allow-listed service methods. See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Stack

Java 17 · Spring Boot 4.1 (Web MVC, Data JPA, Validation, Actuator, RestClient) · Hibernate 7 ·
PostgreSQL 16 · Flyway · Jackson 3 · JUnit 5 / Mockito / AssertJ · Docker · Render (host) ·
Supabase (Postgres) · Vercel AI Gateway (model routing, OpenAI-compatible API).

## Quick start (local)

Prerequisites: Java 17, PostgreSQL (local or Docker), a bot token from [@BotFather](https://t.me/BotFather),
an [AI Gateway](https://vercel.com/ai-gateway) API key (free credits on the Hobby plan).

```bash
git clone <this repo> && cd tutorbot
cp .env.example .env            # fill TELEGRAM_BOT_TOKEN, AI_API_KEY, DATABASE_*
createdb tutorbot               # or: docker compose up db -d
./mvnw spring-boot:run          # long polling: no public URL needed
```

Or everything in containers: `docker compose up --build`.

Without any keys the bot still boots: `AI_PROVIDER=fake` gives a deterministic examiner,
`TELEGRAM_MODE=none` disables Telegram, `/actuator/health` answers.

## Commands

```
/add        add a task in plain words           /status     promised vs. verified
/today      today's plan                        /review     weekly review
/tasks      upcoming + pending verification     /knowledge  knowledge profile
/start_task [id]   start                        /goals      long-term goals
/done [id]  report done → verification          /settings   mode, timezone, limits, consequences
/skip [id]  skip with a reason                  /pause      pause accountability
/reschedule id when · /cancel id · /recurring · /abandon (stop a verification)
```

Free text without a command is treated as a task description and confirmed with buttons.

## Configuration

All configuration is environment variables (see `.env.example`); nothing secret lives in the repo.

| Variable | Purpose |
|---|---|
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_MODE` (`polling`/`webhook`), `TELEGRAM_WEBHOOK_URL`, `TELEGRAM_WEBHOOK_SECRET` | Telegram transport |
| `TELEGRAM_ALLOWED_USER_IDS` | Allowlist (empty = public). Set it for a personal bot. |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | PostgreSQL |
| `AI_PROVIDER` (`gateway`/`fake`), `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`, `AI_FALLBACK_MODEL` | Model access |
| `TICK_SECRET` | Header secret for `/internal/tick` (external cron) |
| `APP_DEFAULT_TIMEZONE`, `APP_DEFAULT_LANGUAGE` | Defaults for new users |

Tuning knobs (reminder ladder, grace period, question counts, overload thresholds) live in
`application.yml` under `accountability.*`.

## Tests

```bash
./mvnw test      # 52 unit tests, no database
./mvnw verify    # + 15 integration tests against PostgreSQL (profile "test", db tutorbot_test)
```

Integration tests cover the full loop (create → notify → start → done → exam → pass → knowledge),
failure and consequence paths, downtime handling, the Telegram chat flow through raw updates,
webhook/tick authentication, recurrence materialisation, retry on Telegram failure, session expiry
and the morning routine. See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Documentation

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — the full product/technical analysis, modules, state machines, decisions, roadmap
- [docs/DATABASE.md](docs/DATABASE.md) — schema, relationships, indexes, timezone model
- [docs/AI.md](docs/AI.md) — prompts, schemas, versioning, call policy, cost control, failure modes
- [docs/SECURITY.md](docs/SECURITY.md) — threat model and controls
- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) — local setup, conventions, testing strategy
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) — Render + Supabase + Vercel AI Gateway + cron ping, step by step

## Status

MVP 1 + MVP 2 + the core of MVP 3 are implemented (tasks, scheduler, notifications, recurring
tasks, verification, knowledge tracking, consequences with overload protection, planner, weekly
review, analytics). Not yet: entertainment blocking integrations, voice answers, calendar sync,
web dashboard, multi-instance scheduling. Roadmap in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md#k-roadmap).

## License

MIT
