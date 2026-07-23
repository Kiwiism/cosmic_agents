# Agent engine configuration boundary

## Decision

`config.yaml` is reserved for Cosmic server, client, world, channel, database, and protocol
settings. Agent-owned deployment switches and runtime policy tuning live in the root
`agent-engine.yaml`.

The split is enforced in tests: an `AGENT_` key may not be added to `config.yaml` or
`ServerConfig`, and every typed Agent deployment field must have exactly one value in
`agent-engine.yaml`.

## File ownership

| Concern | Owner | Location |
|---|---|---|
| Cosmic server/client/world behavior | Cosmic server | `config.yaml` |
| Agent deployment modes and broad feature switches | Agent engine | `agent-engine.yaml` → `agent:` |
| Agent timings, weights, thresholds, bounds, capacities, and policy coefficients | Agent engine | `agent-engine.yaml` → `tuning:` |
| Plans and lifecycle inputs | progression content | `src/main/resources/agents/plans/` |
| Personality profiles | profile content | validated Agent profile resources |
| AP/SP builds, quests, NPCs, maps, venues, and geometry | content catalogs | `src/main/resources/agents/` |
| Packet offsets, opcodes, schema/cache versions, and deterministic hash domains | code invariants | declaring Java type |

Content IDs and geometry are deliberately not tuning. Moving a quest ID or a foothold coordinate
into the runtime policy map would make content validation weaker and would allow an operator to
create invalid plans without a catalog version change.

## Typed access

`AgentYamlConfig` loads and validates the dedicated file. Broad `AGENT_` deployment settings are
typed by `AgentEngineConfig`. Fine-grained controls use strict `AgentTuning` accessors:

- `intValue`
- `longValue`
- `doubleValue`
- `floatValue`
- `booleanValue`

Fine-grained keys use the fully qualified declaring class plus a descriptive field name. This
keeps the large registry sortable and makes every value searchable from source to YAML. Missing,
blank, malformed, negative, invalid percentage, and inverted min/max values fail fast.

Every value is documented immediately above the key with two complementary comments:

- `Purpose` describes the visible behavior and the practical effect of increasing, decreasing, or
  disabling the value.
- `Technical` identifies the consuming component, scalar type, unit or domain, lifecycle semantics,
  and important load, ranking, or recovery implications.

Run `tools/agent-engine-config-comments.ps1` after adding or renaming settings to regenerate the
standard documentation. The generator is idempotent: it replaces its own `Purpose` and `Technical`
comments while preserving section headings and hand-authored boundary notes. The configuration
boundary test rejects undocumented keys, so new controls cannot silently become opaque magic
settings.

There are no Java fallback values for Agent tuning. A fallback would allow source defaults and
YAML to drift. Scheduler system properties remain supported as operational overrides where they
already existed, but their fallback comes from `agent-engine.yaml`.

## Maintenance rules

When adding or changing an Agent numeric control:

1. Decide whether it is policy, content, or an invariant.
2. Put policy under `tuning:` and read it through `AgentTuning`.
3. Put a broad deployment switch under `agent:` and add the typed field to `AgentEngineConfig`.
4. Keep content in a validated catalog and version it with that catalog.
5. Keep structural arithmetic and protocol/schema constants in code with a name explaining the
   invariant.
6. Preserve the old value during extraction; behavioral tuning is a separate change.
7. Add explicit validation when a value has constraints stronger than non-negative or min/max.
8. Run `AgentConfigurationBoundaryTest`.

The boundary test verifies exact source/YAML key equality, preventing both missing values and stale
configuration. It also rejects new literal numeric policy constants and public mutable literal
defaults in Agent source.

## Runtime semantics

The current registry is startup-loaded. Changing `agent-engine.yaml` requires a server restart.
This is intentional: many values initialize immutable runtime objects and hot reload without a
transaction would create sessions using different policy versions.

A future live-tuning layer should publish an immutable, versioned snapshot and swap it atomically.
It must not mutate hundreds of static fields one by one. Durable checkpoints should record the
policy version only when resumption behavior actually depends on it.

## Audit classification

The engine-wide extraction covers deployment switches and named runtime controls across auth,
behavior, combat, dialogue, equipment, follow, inventory, movement, navigation, objectives,
reactors, recovery, shop, social, supplies, TownLife, trade, coordination, monitoring, plans,
progression, runtime, and scheduler packages.

Numbers that remain in Java fall into these categories:

- neutral arithmetic identities and collection guards (`0`, `1`, empty-state sentinels);
- percentages used only to convert units (`100`, milliseconds-to-seconds);
- protocol and packet layout;
- MapleStory content IDs and skill/item/job formulas;
- schema/cache versions;
- enum/cardinality mappings;
- deterministic mixing constants and random-domain salts;
- test fixtures.

If a remaining value starts governing cadence, likelihood, ranking, admission, timeout, tolerance,
or resource bounds, it is no longer an invariant and must move to `agent-engine.yaml`.
