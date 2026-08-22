# Cosmic Agent Director panel

Local responsive control surface for the Agent OS World Director. It is kept
separate from the Database Console and never connects directly to Cosmic's
database, the social database, or Ollama.

## Start

1. Start Docker Desktop, Ollama, and Cosmic.
2. Run `start-director-panel.cmd`.
3. Open `http://127.0.0.1:3010`.

The launcher reads `COSMIC_DIRECTOR_TOKEN` and `COSMIC_DIRECTOR_PORT` from the
current Windows user's environment. Cosmic must be restarted after these
variables are first configured.

The panel proxies authenticated requests server-side to the loopback Director
bridge. The browser never receives the bridge token. If Cosmic is unavailable,
the interface displays explicitly labelled preview data and disables mutations.

## Director LLM

The high-level model can select only from action IDs already validated and
exposed by Agent OS. It creates an expiring proposal and cannot submit a
directive. Approval rechecks the Agent's context revision before execution.

The chat box also supports catalog-grounded training questions, for example:

```text
For lv16, what are the top 3 maps we can consider grinding?
```

The response contains structured map cards. Selecting a card creates an
approval-gated proposal for the selected Agent; it does not travel or start
hunting immediately. This bounded query currently uses the versioned Victoria
level 15–30 training catalog and falls back to its predefined ordering when the
local model is unavailable or returns invalid IDs.

Optional settings:

```text
DIRECTOR_LLM_ENABLED=true
DIRECTOR_OLLAMA_ENDPOINT=http://127.0.0.1:11434
DIRECTOR_OLLAMA_MODEL=qwen3.5:9b-q4_K_M
DIRECTOR_OLLAMA_TIMEOUT_MS=45000
DIRECTOR_OLLAMA_NUM_CTX=4096
DIRECTOR_OLLAMA_MAX_PREDICT=384
```

## Agent clean slate

For an offline Agent, choose `Review clean slate` in the Agent detail view. The
panel first creates a two-minute preview showing what will be reset, what will be
retained, and any safety blockers. Execution requires typing the displayed
`RESET <name>` phrase; its server-issued token is single-use.

The workflow resets gameplay progression and ordinary inventory to the standard
level-1 Beginner baseline. It preserves the durable Agent identity, name,
appearance, personality, social memories and relationships, cash/cosmetic items,
and relationship rings. It never deletes the character. Cosmic records every
preview and outcome in `agent_reset_audit`. The action is blocked while the
Agent is online, runtime-active, interactive-enabled, outside its dedicated
locked account, or has unsettled merchant state.
