# Agent Social Dialogue Runbook

## Runtime guarantees

- Dialogue is optional presentation and never controls Agent objectives.
- Deterministic commands run before generic social handling.
- Targeted generic chat selects the addressed Agent.
- Untargeted generic chat elects at most one responder.
- Ollama receives immutable `DialogueRequest` data only.
- External model work uses the bounded `LLM_NETWORK` executor.
- Social database work uses the bounded persistence executor.
- Completion returns through the owning generation-safe mailbox.
- Queue rejection, timeout, invalid output, missing Ollama, load shedding, and
  an already-running request select a predefined catalog reply.
- Unobserved presentation makes no model call.

## Dialogue configuration

Environment variables:

```text
SOCIAL_DIALOGUE_MODE=DETERMINISTIC_ONLY|DIALOGUE_ONLY
SOCIAL_OLLAMA_ENDPOINT=http://127.0.0.1:11434
SOCIAL_OLLAMA_MODEL=qwen3.5:9b-q4_K_M
SOCIAL_OLLAMA_TIMEOUT_MS=6000
SOCIAL_DIALOGUE_MAX_CHARS=180
SOCIAL_OLLAMA_MAX_PREDICT=80
SOCIAL_OLLAMA_NUM_CTX=4096
SOCIAL_OLLAMA_CIRCUIT_MS=30000
```

`DETERMINISTIC_ONLY` performs no model call. `DIALOGUE_ONLY` attempts model
enrichment and falls back to the personality-specific catalog.

The existing `!botllm on|off` operator command remains as a compatibility alias
for switching these two modes at runtime. It no longer enables legacy code.

## Independent social database

The social database is PostgreSQL on loopback port 5434 by default. It has no
foreign keys, credentials, pool, or transactions shared with Cosmic MySQL or
the economy database.

```text
SOCIAL_DB_ENABLED=true
SOCIAL_DB_HOST=127.0.0.1
SOCIAL_DB_PORT=5434
SOCIAL_DB_NAME=cosmic_social
SOCIAL_DB_USER=cosmic_social
SOCIAL_DB_PASSWORD=<private value>
```

Start it with:

```powershell
docker compose -f social-database/compose.yaml up -d
```

If it is disabled or unavailable, relationship and recent-turn state remains
bounded in memory and Agent operation continues. Relationship summaries are
durable. Raw turns expire after seven days and are not copied into summaries.

## Personality presentation

Operational traits remain in `personality-profiles.json`. Communication styles
are separately versioned in `agents/social/dialogue-style-profiles.json`:

- `efficient-v1` -> quiet practical;
- `relaxed-v1` -> friendly casual;
- `restless-v1` -> playful social;
- `explorer-v1` -> curious conversational.

Both the predefined catalog and the model receive the same style snapshot.
Slang is probabilistic guidance, not a demographic caricature or gameplay rule.

## Verification

Required focused tests cover deterministic fallback, provider failure, output
validation, asynchronous non-blocking submission, per-Agent concurrency,
stale-session rejection, responder election, bounded memory, style loading,
Ollama HTTP isolation, and a live PostgreSQL round trip.
