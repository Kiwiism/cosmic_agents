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

`interactive_allowed` is stored for the future player/Agent takeover policy, but
this change does not enable interactive Agent login. `RETIRED` is reserved for a
future non-destructive retirement workflow.

The Director panel exposes a separate clean-slate operation for active Agents.
It does not delete the character or its `agent_characters` row. The operation is
offline-only, requires a fresh preview plus the exact `RESET &lt;name&gt;` phrase,
and writes an `agent_reset_audit` record. Gameplay progression and ordinary
inventory return to a level-1 Beginner baseline, while Agent identity, name,
appearance, personality, social memories, durable relationships, cash/cosmetic
items, and relationship rings are retained. Retirement remains unimplemented.
