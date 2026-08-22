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

Optional settings:

```text
DIRECTOR_LLM_ENABLED=true
DIRECTOR_OLLAMA_ENDPOINT=http://127.0.0.1:11434
DIRECTOR_OLLAMA_MODEL=qwen3.5:9b-q4_K_M
DIRECTOR_OLLAMA_TIMEOUT_MS=15000
DIRECTOR_OLLAMA_NUM_CTX=4096
DIRECTOR_OLLAMA_MAX_PREDICT=160
```
