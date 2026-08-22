# Agent Director Panel Runbook

## Ownership

The panel is a local presentation client. `AgentWorldDirectorApplication`
remains the only application boundary and the Agent scheduler remains the only
gameplay executor. The browser never connects directly to Cosmic's database,
the social database, or Ollama.

```text
Browser :3010
  -> Next.js same-origin proxy
  -> authenticated loopback bridge :8790
  -> AgentWorldDirectorApplication
  -> durable proposal/directive stores
  -> Agent scheduler and activity owners
```

## Start

Restart Cosmic after the Director environment variables are configured. Then
run `director-panel/start-director-panel.cmd` and open
`http://127.0.0.1:3010`.

Required Windows user variables:

```text
COSMIC_DIRECTOR_PANEL_ENABLED=true
COSMIC_DIRECTOR_PORT=8790
COSMIC_DIRECTOR_TOKEN=<private random value of at least 32 characters>
```

The token stays in the Next.js server process. It is never returned to browser
JavaScript or stored in repository configuration.

## Modes

- `OBSERVE`: read-only inspection.
- `MANUAL`: the operator selects validated actions directly.
- `ASSISTED`: policy or Ollama can create proposals; operator approval is
  required.
- `AUTONOMOUS`: present in Agent OS but hidden behind rollout gates and not
  enabled as an ordinary panel selection.
- `EMERGENCY_HOLD`: runtime safety state, not a normal selection.

## Proposal safety

Every proposal stores its source, action ID, rationale, evidence, alternatives,
expected energy effect, context revision, and five-minute expiry. Approval:

1. reloads the current Agent view;
2. requires the exact stored context revision;
3. resolves the action through the current Agent OS action catalog;
4. requires a second confirmation for destructive actions; and
5. submits an idempotent directive for scheduler execution.

The Director LLM receives only immutable Agent facts and currently executable
action IDs. Invalid or unavailable model selections are discarded and replaced
by predefined policy selection. The model cannot create directives.

## Energy

Energy is bounded from 0 to 100. Hunting, questing, and party quests drain it;
town life and commerce recover it; idle time recovers faster; offline time
recovers fastest. Combat events continue to contribute immediate drain and
rest debt. Checkpoints are atomic local files under
`.runtime/agents/behavior-energy`, outside Cosmic's database.

## Responsive layouts

- Wide: Agent roster, command deck, and proposal/chat rail.
- Short landscape: roster at left, command deck above an underbar containing
  proposals and chat.
- Portrait/narrow: bottom navigation for Agents, Overview, Proposals, and Chat.

When the bridge is unavailable the UI renders explicitly labelled preview data
and disables all mutations.
