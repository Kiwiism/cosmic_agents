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

## Catalog-grounded chat

Director chat recognizes bounded training-map questions such as `For lv16,
what top 3 maps should we consider grinding?`. The bridge projects only the
relevant level slice from the versioned Victoria level 15–30 training catalog.
It includes map level bands, curated rank/weight, terrain, capacity, tags,
hazards, conditions, and mob spawn composition. It never reads WZ, XML, or a
database during the request.

Ollama may independently reorder those candidates, but it may return only the
supplied `hunting-map:<mapId>` action IDs. Invalid IDs are discarded and missing
choices are filled from the predefined catalog order. The UI joins every model
choice back to authoritative catalog facts. Selecting a recommendation creates
an expiring proposal; it still requires explicit approval and current-context
revalidation before Agent OS receives a directive.

The current grounded training-map slice covers Victoria levels 15–30. Questions
outside that range return an explicit catalog-boundary response instead of
guessing from model pretraining.

## Energy

Energy is bounded from 0 to 100. Hunting, questing, and party quests drain it;
town life and commerce recover it; idle time recovers faster; offline time
recovers fastest. Combat events continue to contribute immediate drain and
rest debt. Checkpoints are atomic local files under
`.runtime/agents/behavior-energy`, outside Cosmic's database.

## Clean-slate reset

The offline Agent detail view includes `Review clean slate`. This is a guarded
two-stage operation, not a delete:

1. enter an operator reason and request a preview;
2. review the authoritative current state, reset scope, retained scope, and any
   safety blockers;
3. type the exact phrase shown by the panel; and
4. execute within the two-minute confirmation window.

Execution is accepted only for an active durable Agent on its dedicated locked
account, with interactive login disabled, no online/runtime session, and no
merchant listings, proceeds, or escrow. A state fingerprint invalidates the
preview if the character changes before confirmation, and the token is
single-use. The Cosmic gameplay reset and `agent_reset_audit` success record
commit atomically. Agent OS checkpoints are then cleared; personality, social
memory, relationship data, identity, cash/cosmetic items, and relationship rings
are retained. Any post-commit checkpoint-cleanup failure is shown as a warning
and recorded in the audit row.

Do not use the legacy Amherst live-test reset as an operator reset. It remains a
test-fixture capability with different semantics.

## Responsive layouts

- Wide: Agent roster, command deck, and proposal/chat rail.
- Short landscape: roster at left, command deck above an underbar containing
  proposals and chat.
- Portrait/narrow: bottom navigation for Agents, Overview, Proposals, and Chat.

When the bridge is unavailable the UI renders explicitly labelled preview data
and disables all mutations.
