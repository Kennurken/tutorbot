# Security

## Threat model

Personal bot on the public internet. Assets: the Telegram token, the AI key, the database, the
user's study history. Attackers: anyone who finds the public URL; anyone who talks to the bot.

## Controls

| Area | Control |
|---|---|
| Secrets | Environment variables only (`.env` is git-ignored; `.env.example` has placeholders). Render stores them encrypted; `render.yaml` marks them `sync: false`. Logs never print the token (client logs method names only). |
| Webhook | Telegram echoes `TELEGRAM_WEBHOOK_SECRET` in `X-Telegram-Bot-Api-Secret-Token`; compared with `MessageDigest.isEqual` (constant time). Wrong/missing → 401. |
| Tick endpoint | `X-Tick-Secret` header, constant-time compare; never a query parameter (access logs). |
| Who may talk to the bot | `TELEGRAM_ALLOWED_USER_IDS` allowlist; everyone else gets "private bot". |
| Input validation | Task fields validated in `TaskService`; ids parsed defensively; settings bounded (`limit 1–20`, `questions 2–8`, …). |
| SQL | JPA/JPQL with bound parameters everywhere; no string-built SQL. |
| Telegram HTML | All user/model text passes through `Html.esc` before being embedded in HTML messages. |
| Authorization | Every task lookup is `findByIdAndUserId`; a user cannot act on another user's task by id. |
| LLM guardrails | Model output is data: JSON → DTO → validation. No tool calls, no code execution, no free-form actions. Allow-listed effects only (verdict, question, task draft the user must confirm). Prompt injection in an answer can at most produce an odd verdict, which the backend policy bounds and the user can retry. |
| Rate limits | Per-user AI calls per minute; Telegram delivery backoff; notification ceiling per task. |
| Idempotency | `telegram_updates` PK; optimistic locking on tasks; dedupe keys on notifications. |
| Health endpoint | `/actuator/health` is public (used by the host); `metrics`/`info` are exposed but contain no user data. Lock them down with Spring Security when a dashboard arrives. |
| Dependencies | Spring Boot BOM pins versions; CI builds the Docker image. Run `mvn versions:display-dependency-updates` periodically. |
| Container | Non-root user, JRE-only image, no build tools at runtime. |

## Not in scope (yet)

Spring Security / OAuth (no browser users), encryption at rest beyond the provider's,
GDPR tooling (single user; add `/forget_me` that deletes by `user_id` before opening the bot).
