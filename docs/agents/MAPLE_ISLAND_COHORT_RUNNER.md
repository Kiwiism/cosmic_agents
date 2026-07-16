# Maple Island cohort runner

The GM6 cohort runner releases reusable test Agents in waves for the full
Mushroom Town-to-Southperry plan.

## Commands

```text
!mapleisland run <total> <batch> <intervalSeconds> [seed] [off|light|full]
!mapleisland status
!mapleisland pool [page]
!mapleisland cancel
!mapleisland stop
```

`total` is 1-100, `batch` is 1-10 and cannot exceed `total`, and the interval is
5-3,600 seconds. Omitting `seed` creates one and reports it in status so a run
can be replayed. The legacy `!mapleisland run <AgentIGN>` showcase remains
available.

The realism preset defaults to `full`. You may also give a preset without a
seed, for example `!mapleisland run 25 5 10 light`.

- `off`: deterministic control group; no added objective/NPC delay, nearest
  legacy navigation, deterministic rest-catalog order, and no optional travel
  hops.
- `light`: seeded profile pacing, varied valid NPC approach points, and bounded
  alternate routes plus seeded Southperry rest placement; optional travel hops
  remain off.
- `full`: wider seeded pacing (600-2,200 ms before NPC interaction and
  900-3,000 ms between objectives), varied valid NPC approach points, bounded
  alternate routes, seeded Southperry rest placement/facing, and safe forward
  travel/combat hops at 0.10 probability.

Every mode keeps required graph jumps, rope/ladder movement, portal behavior,
quest/combat correctness, and the cash-shop return grounding guard unchanged.

`cancel` cancels only future waves; Agents already released continue their
runs. `stop` cancels future waves, disconnects this channel's cohort Agents,
and releases their pool leases. Pool stop/release is performed on the serialized
cohort worker, so `STOPPING` can be visible briefly.

`pool [page]` is a bounded eight-entry roster showing each IGN, backing account
name/id, character world, lease state, and session. `status` reports wave
progress and global pool counts; availability is selected strictly within the
world where the command is run.

## Safety and persistence

Mass provisioning requires both `AGENT_MAPLE_ISLAND_COHORT_ENABLED` and
`AGENT_MAPLE_ISLAND_SHOWCASE_ENABLED`. Cohort identities and leases persist in:

```text
.runtime/agents/maple-island-cohort-pool.json
```

Set `COSMIC_MAPLE_ISLAND_COHORT_POOL_FILE` to move that file. Startup recovers
stale leases when the corresponding character is not live.

The provisioner creates only dedicated, interactively locked Agent accounts.
Only accounts already recorded in the cohort pool are expanded to 15 character
slots, and 15 remains a hard usable cap even if a database row reports more.
It never expands ordinary accounts, deletes characters, or overwrites an
existing IGN. The deterministic candidate catalog contains 5,000 unique names
that satisfy the server's 3-12-character and blocked-substring policy.

Before the destructive clean-level-1 reset, the runtime verifies that all of
the following match: persisted pool character id, IGN, account id/name, current
world, session lease, the current database character/account mapping, and the
Agent-only account lock. A character outside that exact boundary is rejected.

Pool schema version 2 records the character world. A failed character is kept
in `BROKEN` quarantine instead of being silently recycled; healthy characters
are released by `stop` and reused on later sessions.

Database provisioning, password hashing, offline loading, reset, and plan start
all run on the dedicated serialized `MAPLE_ISLAND_COHORT` worker. Timer callbacks
only enqueue the next wave.

## Deterministic behavior hook

`MapleIslandCohortEntrySetup.apply(entry, context)` is the per-Agent setup hook.
Its context supplies the session id, run seed, ordinal, owner id, world,
channel, and realism mode. The run seed is mixed with stable Agent identity so
the same command can be replayed without making every Agent choose identical
delays, NPC positions, routes, optional hops, or rest positions.
