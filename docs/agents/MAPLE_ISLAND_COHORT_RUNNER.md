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

`total` is 1 through `AGENT_MAPLE_ISLAND_COHORT_MAX_TOTAL` (500 by default and
hard-capped at 2,000), `batch` is 1-10 and cannot exceed `total`, and the
interval is 5-3,600 seconds. Omitting `seed` creates one and reports it in
status so a run can be replayed. The legacy `!mapleisland run <AgentIGN>`
showcase remains available.

Agents within a batch are spread across that batch's interval using seeded
stratified jitter. For example, `100 5 10` launches 100 Agents total and places
one of each five-Agent batch at a random point inside each successive two-second
part of its ten-second wave. This avoids synchronized bursts while keeping runs
replayable from their reported seed.

The realism preset defaults to `light`. You may also give a preset without a
seed, for example `!mapleisland run 25 5 10 light`.

- `off`: deterministic control group; no added objective/NPC delay, nearest
  legacy navigation, deterministic rest-catalog order, and no optional travel
  hops.
- `light` (recommended for demos and normal runs): seeded profile pacing,
  varied valid NPC approach points, routes up to 8% longer than optimal, and
  seeded Southperry rest placement. Optional travel hops remain off.
- `full`: wider seeded pacing (600-2,200 ms before NPC interaction and
  900-3,000 ms between objectives), varied valid NPC approach points, routes
  up to 15% longer than optimal, seeded Southperry rest placement/facing, and
  observer-gated personality presentation. Safe travel-hop probability varies
  by the Agent's durable archetype (bounded to 1-12%); checks occur every
  2.5-4.5 seconds with an 8-15 second cooldown after a hop. Presentation may
  also produce bounded pauses, turns, prone taps, shuffles, and combat-idle
  variation, but never while required navigation is unsafe.

Recommended runs:

```text
# Fast correctness or performance baseline
!mapleisland run 25 5 10 off

# Normal visible demo/load run (also the default when mode is omitted)
!mapleisland run 25 5 10 light

# Maximum bounded presentation variation / soak run
!mapleisland run 25 5 10 full

# Replay any run exactly by inserting the reported seed before the mode
!mapleisland run 25 5 10 123456 light
```

Every mode keeps required graph jumps, rope/ladder movement, portal behavior,
quest/combat correctness, and the cash-shop return grounding guard unchanged.
Set `AGENT_PERSONALITY_PRESENTATION_ENABLED: false` to disable only the new
personality layer. See `docs/agents/AGENT_PERSONALITY_PRESENTATION.md` for its
catalog, persistence, observer gate, and safety model.

`cancel` cancels only future waves; Agents already released continue their
runs. `stop` cancels future waves, disconnects this channel's cohort Agents,
and releases their pool leases. Pool stop/release is performed on the serialized
cohort worker, so `STOPPING` can be visible briefly.

`pool [page]` is a bounded eight-entry roster showing each IGN, backing account
name/id, character world, lease state, and session. `status` reports wave
progress and global pool counts; availability is selected strictly within the
world where the command is run.

`stats` reports cohort milestones/recovery plus bounded presentation totals.
The presentation totals are server-lifetime aggregate counters rather than
per-run or per-Agent labels.

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
existing IGN. The deterministic candidate catalog contains 30,000 mixed
old-school Maple-style names that satisfy the server's 3-12-character and
blocked-substring policy. It interleaves nickname-like, fantasy, Maple-themed,
prefixed, suffixed, framed, and lightly numbered styles so even a small cohort
has visible variety.

Each pooled character also owns one non-repeating ordinal from the 24,192 valid
v83 Beginner appearance/equipment combinations. The ordinal is durable pool
metadata, and its gender, skin, face, colored hair, top, bottom, shoes, and
weapon are reapplied before every clean run reset. Equipment acquired during a
previous run therefore cannot replace the character's persistent identity.

Before the destructive clean-level-1 reset, the runtime verifies that all of
the following match: persisted pool character id, IGN, account id/name, current
world, session lease, the current database character/account mapping, and the
Agent-only account lock. A character outside that exact boundary is rejected.

Pool schema version 2 records the character world. A failed character is kept
in `BROKEN` quarantine instead of being silently recycled; healthy characters
are released by `stop` and reused on later sessions.

Database provisioning, password hashing, offline loading, reset, and plan start
all run on the dedicated serialized `MAPLE_ISLAND_COHORT` worker. Timer callbacks
only enqueue the next wave. New waves pause automatically while the persistence
queue is at least 75% full or the central scheduler ready queue is unusually
deep. A run becomes `STALLED` after the configured no-progress timeout instead
of remaining silently active forever.

## Deterministic behavior hook

`MapleIslandCohortEntrySetup.apply(entry, context)` is the per-Agent setup hook.
Its context supplies the session id, run seed, ordinal, owner id, world,
channel, and realism mode. The run seed is mixed with stable Agent identity so
the same command can be replayed without making every Agent choose identical
delays, NPC positions, routes, optional hops, or rest positions.
