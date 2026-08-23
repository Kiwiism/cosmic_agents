# Agent Character Identity

`agent_characters` is the authoritative Cosmic registry for durable Agent
identity. A character remains an Agent whether it is offline, headless-controlled,
or later connected through an explicitly permitted interactive session.

This is separate from live control mode:

- `AgentIdentityGateway.isActiveAgent(characterId)` reads durable identity.
- `CharacterGateway.isHeadlessControlled(character)` reports whether the current
  live character uses the headless `BotClient` controller.

Director rosters, Director session admission, population admission, and operator
control authorization use durable identity. Runtime packet, physics, and capability
checks use headless control mode and do not query the database from scheduler ticks.

Liquibase changesets 37 and 38 create the registry and backfill characters on the
former `Agent-only backing account` sentinel. New command, cohort, and live-test
provisioning paths register identity immediately after character creation. The
account sentinel remains as a login lock and destructive-test safety layer; it is
no longer the Agent roster.

`interactive_allowed` normally remains false. A GM6 Agent operator may explicitly
adopt an offline character from the operator's own account with
`!adopttestagent <name> confirm`. That compatibility path records
`LEGACY_TEST_FIXTURE` with `interactive_allowed=true`; it is intended only for
older named test fixtures that predate dedicated Agent accounts. It does not
permit simultaneous interactive and headless control: spawning still fails while
the character is being played by a real client. Other existing player characters
remain ineligible, and normal Agent provisioning continues to use dedicated,
login-locked accounts. `RETIRED` is reserved for a future non-destructive
retirement workflow and cannot be reactivated through test-fixture adoption.

The Director panel exposes a separate clean-slate operation for active Agents.
It does not delete the character or its `agent_characters` row. The operation is
offline-only, requires a fresh preview plus the exact `RESET &lt;name&gt;` phrase,
and writes an `agent_reset_audit` record. Gameplay progression and ordinary
inventory return to a level-1 Beginner baseline, while Agent identity, name,
appearance, personality, social memories, durable relationships, cash/cosmetic
items, and relationship rings are retained. Retirement remains unimplemented.
