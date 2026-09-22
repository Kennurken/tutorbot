# AI layer

## Principles

1. **The model proposes; the backend decides.** Model output is JSON, parsed into DTOs with bean
   validation. Nothing the model says reaches the database except through service methods that
   apply their own rules (`VerificationPolicy`, `TaskService.transition`).
2. **Every call is optional.** Each job has a deterministic fallback, and callers receive
   `Optional<T>`; an outage degrades the experience, never blocks the loop.
3. **Every call is accounted for.** `ai_interactions` stores purpose, prompt version, model, tokens,
   estimated cost, latency, success/error.
4. **Every call is bounded.** Per-user sliding-window rate limit, backend min/max question counts,
   max tokens per job, timeouts, retries with backoff and jitter, circuit breaker.

## Provider abstraction

```
AiProvider                     complete(AiRequest) -> AiResponse
 ├─ OpenAiCompatibleAiProvider  POST {base}/chat/completions (Vercel AI Gateway, Grok, any OpenAI-compatible server)
 ├─ ResilientAiProvider         retries · fallback model · circuit breaker (decorator, the injected bean)
 └─ FakeAiProvider              deterministic answers for tests and AI_PROVIDER=fake
```

Vercel AI Gateway is used because it is OpenAI-compatible (one HTTP client covers every vendor),
routes to xAI Grok (`spacexai/grok-4.1-fast-non-reasoning`, $0.20/M input, $0.50/M output at the
time of writing), offers free monthly credits on the Hobby plan, and exposes zero-cost fallback
models (`poolside/laguna-s-2.1-free`). Switching provider = changing `AI_BASE_URL`, `AI_API_KEY`,
`AI_MODEL`.

## Prompt architecture

One prompt per job, no shared mega system prompt. All prompts live in `ai/prompt/Prompts.java`
with a version constant that is written to every `ai_interactions` row.

| Version | Job | Max tokens | Temp |
|---|---|---|---|
| `task_intent.v2` | free text → task JSON (v2: + deadline) | 2500 | 0.2 |
| `verification_step.v1` | one exam turn: evaluate last answer, decide, next question or verdict | 3000 | 0.3 |
| `weekly_review.v1` | stats JSON → summary, observations (hypotheses), recommendations with reasons | 3500 | 0.4 |
| `skip_analysis.v1` | free-text reason → category, insight, suggestion | 2500 | 0.2 |
| `goal_plan.v1` | goal → 6–12 ordered, examinable steps (title, subject, topic, type, minutes) | 5000 | 0.4 |

Token budgets are generous on purpose: with reasoning models (Groq `gpt-oss`) the model's
thinking counts against `max_tokens`, and a budget that is too small ends in
`400 json_validate_failed: max completion tokens reached`. `AI_REASONING_EFFORT=low` keeps the
thinking short; the audit row records the real usage.

**Versioning rule:** change the text → bump the suffix. A drop in pass rate can then be correlated
with `prompt_version` in `ai_interactions`. Old versions stay in git history; the constant only
points at the current one.

### Verification step contract

```json
{"evaluation":{"score":0.0-1.0,"feedback":"...","evidenceOfUnderstanding":true}|null,
 "decision":"CONTINUE"|"FINISH",
 "nextQuestion":"..."|null,
 "difficultyDelta":-1|0|1,
 "finalVerdict":{"verdict":"PASS"|"FAIL"|"UNCERTAIN","score":..,"confidence":..,
                 "knowledgeGaps":[],"strengths":[],"summary":"..."}|null}
```

Backend overrides (in `VerificationPolicy`):
- `answered < min_questions` ⇒ CONTINUE even if the model says FINISH (fallback question if none given).
- `answered ≥ max_questions` ⇒ FINISH even if the model says CONTINUE (verdict from average scores if none given).
- PASS with average turn score < 0.5 ⇒ UNCERTAIN; FAIL with average ≥ 0.85 ⇒ UNCERTAIN;
  any verdict with confidence < 0.55 ⇒ UNCERTAIN.
- Difficulty is clamped to 1–4.

Adaptive questioning: the prompt receives `difficulty`, the full transcript, known gaps from the
knowledge profile, and the counters; it is told to go harder after strong answers and clarify after
weak ones. The backend adjusts `difficulty` by the returned delta and passes it back next turn.

### Context / token policy

No conversation history is ever sent except the current exam's transcript (≤ 4 Q/A pairs). The
weekly review sees only a stats JSON. Task intent sees one message plus `now` and the timezone.
Structured state (knowledge gaps, counters) replaces "memory". Result: ~1–2k tokens per call.

## AI call policy

| Without AI | With AI |
|---|---|
| `/done`, `/skip`, `/today`, `/tasks`, `/status`, scheduling, transitions, consequences, statistics, regex-parseable task text | exam turns, unparseable task text, weekly narrative, free-text skip reasons |

## Failure modes

| Failure | Behaviour |
|---|---|
| 429 / 5xx / timeout | retry ×2 with backoff+jitter; last attempt on the fallback model; 4 consecutive failures open the circuit for 2 min |
| Non-JSON / schema violation | audited as failure; caller gets `Optional.empty()` |
| Exam start without AI | question from the per-type fallback bank; user is told |
| Exam answer without AI | answer not consumed; "resend in a minute"; session expiry extended |
| Per-user rate limit (12/min) | treated as unavailable |
| Empty API key | warning at boot; every call falls back |

## Testing the AI layer

- `FakeAiProvider` reads counters/markers from the prompt and produces valid JSON, so the whole
  verification loop is exercised end to end without network (`TaskLifecycleIT`, `ChatFlowIT`).
- `AiJsonExtractorTest`: fences, prose around JSON, garbage.
- `ResilientAiProviderTest`: retries, fallback model, non-retryable, circuit open/close.
- `VerificationPolicyTest`: every backend override.
- Golden prompt tests against the real model are a manual script for now (see DEVELOPMENT.md);
  they cost money and are non-deterministic, so they are not part of CI.
