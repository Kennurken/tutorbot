# Development

## Prerequisites

- Java 17 (Temurin/Homebrew), Maven wrapper included (`./mvnw`)
- PostgreSQL 16 locally **or** Docker
- Optional: a bot token and an AI Gateway key (`.env`); without them use
  `TELEGRAM_MODE=none AI_PROVIDER=fake`

## Local database (Homebrew on macOS)

```bash
brew install postgresql@16
# macOS quirk: start with a valid locale or the postmaster refuses to fork
LC_ALL=en_US.UTF-8 /opt/homebrew/opt/postgresql@16/bin/pg_ctl -D /opt/homebrew/var/postgresql@16 -l /tmp/pg16.log start
psql -h localhost -d postgres -c "CREATE ROLE tutorbot LOGIN PASSWORD 'tutorbot'"
createdb -h localhost -O tutorbot tutorbot
createdb -h localhost -O tutorbot tutorbot_test
```

Or `docker compose up db -d` (same credentials).

## Run

```bash
cp .env.example .env     # edit
./mvnw spring-boot:run   # polling mode by default; talk to the bot in Telegram
```

Useful: `TELEGRAM_MODE=none AI_PROVIDER=fake ./mvnw spring-boot:run` boots without any account.
`curl -H "X-Tick-Secret: $TICK_SECRET" localhost:8080/internal/tick` runs a scheduler tick on demand.

## Tests

```
./mvnw test      # unit tests (surefire): pure logic, no Spring context
./mvnw verify    # + integration tests *IT (failsafe): Spring context + PostgreSQL tutorbot_test
```

Integration tests truncate all tables before each test, use a `MutableClock` (`clock.advance(...)`)
to move time, mock `TelegramClient` (no network), and run the `FakeAiProvider`.
Override the test DB with `TEST_DATABASE_URL` / `TEST_DATABASE_USERNAME` / `TEST_DATABASE_PASSWORD`.
Skip ITs with `-DskipITs`.

### Test strategy

| Layer | Examples |
|---|---|
| Pure logic (unit) | state machine transitions, knowledge model arithmetic, verification policy overrides, NL parser, planner ranking, AI JSON extraction, retry/circuit breaker, rate limiter |
| Module integration | task lifecycle through services with real DB and events (`TaskLifecycleIT`) |
| Transport | webhook/tick auth with MockMvc (`WebhookIT`) |
| End to end | raw Telegram updates in, chat replies out (`ChatFlowIT`); the scheduler tick (`TickIT`) |
| Model (manual) | run a real exam with `AI_PROVIDER=gateway` and inspect `ai_interactions` |

## Conventions

- Constructor injection, no field injection, no Lombok.
- Services own transactions. Anything that calls the network (AI, Telegram) runs **outside** a
  transaction; use `TransactionTemplate` when a flow mixes both.
- Cross-module communication: services + Spring events. Never another module's repository.
- Every user-visible string goes through `BotMessages` (RU/EN bundles). Messages with `{n}` are
  MessageFormat patterns: no bare apostrophes.
- Every state change writes a `task_events` row via `TaskService.transition`.
- Prompts change → version bump in `Prompts`.
- Schema change → new Flyway migration, never edit an applied one.

## Adding a command

1. Create `telegram/handler/command/XCommand.java` implementing `CommandHandler` (`command()`,
   `description()`, `handle()`); it is auto-registered and appears in the Telegram menu.
2. Put shared logic in `telegram/flow/*` if a button needs the same behaviour.
3. Add message keys to both bundles.
4. Add a `ChatFlowIT` scenario.
