# Agent personality presentation

This first implementation slice makes visible Agent behavior less synchronized
without moving correctness decisions into personality code. Durable personality
identity is assigned during normal Agent registration; visible presentation is
deliberately enabled only for Maple Island cohort runs using `full` realism.

## Boundaries

Personality is a durable semantic identity. It does not choose quests, bypass
navigation, alter damage, change rewards, or own career progression. The
presentation resolver may only request bounded cosmetic actions such as a
pause, turn, prone tap, short shuffle, hop, combat pause, or linger.

The execution safety gate rejects a presentation intent when the Agent is dead,
airborne, climbing, down-jumping, on an active navigation edge, already
fidgeting, or lacks safe ground. Movement intents are also rejected near a
destination. Required plan and movement work therefore keeps priority.

Cosmetic intents execute only while at least one real player observes the map.
Agent-to-Agent simulation does not spend movement work on presentation. The
resolver is deterministic from the durable behavior seed, event sequence, and
trigger, so a problem can be replayed without making every Agent act alike.

## Profiles and persistence

The versioned catalog is:

```text
src/main/resources/agents/profiles/personality-profiles.json
```

It currently contains four archetypes:

- `efficient-v1`: active but routine-oriented and minimally expressive;
- `relaxed-v1`: patient, slower, and more likely to linger;
- `restless-v1`: highly active and expressive with more movement variation;
- `explorer-v1`: curiosity-led with more route and reposition variation.

After Pio's Collecting Recycled Goods is completed and the Relaxer is awarded,
the visible `full`-realism run also has one bounded personality-specific
interlude:

- `relaxed-v1` reserves a free ground spot near Pio and sits for 15-60 seconds;
- `restless-v1` reserves a spot, shows F2, and alternates sitting/standing for
  10-15 seconds;
- `efficient-v1` and `explorer-v1` continue directly to their next objective.

The positions come from Amherst's WZ foothold at `y=274`, exclude Pio's center
at `x=547`, reserve a two-slot footprint, and reject a position occupied by a
live character. The plan clock and current objective are paused without being
replaced. Reservations are released on completion, cancellation, reset, map
change, death, or missing chair; if no safe position opens within 15 seconds,
the Agent resumes instead of stacking or waiting indefinitely.

Each registered Agent receives a deterministic profile. Its independent behavior
seed and exact profile version are then stored under:

```text
.runtime/agents/personality/assignments/<characterId>.json
```

Assignments survive relogs and cohort reuse. A catalog profile version cannot
silently change beneath an assignment; a future schema change must include an
explicit migration.

## Event and execution flow

Meaningful existing Agent events feed the presentation resolver:

- session start;
- map arrival;
- objective or quest completion;
- combat target engaged or cleared;
- mob killed;
- transition from no real observer to an observed map.

The resolver holds at most one pending intent per Agent. Additional eligible
events coalesce instead of creating an unbounded action queue. The serialized
Agent live-mode tick consumes due intents after higher-priority capability and
shop work, but before follow, combat, and ordinary movement modes. Execution
uses the existing movement/fidget services without belonging to navigation.

This is intentionally a presentation adapter over the event system. Future
personality-driven decisions can consume the same semantic profile through a
separate policy without coupling the durable identity to packets or movement.

## Configuration and testing

The server flag is:

```yaml
AGENT_PERSONALITY_PRESENTATION_ENABLED: true
```

Both the flag and cohort realism mode `full` are required. `off` and `light`
remain unchanged control modes. Disable the flag to retain the prior `full`
cohort behavior without deleting durable assignments.

Recommended comparison:

```text
!mapleisland run 25 5 10 light
!mapleisland run 25 5 10 123456 full
!mapleisland stats
```

`!mapleisland stats` includes fixed-cardinality, server-lifetime presentation
counters: triggers, scheduled, executed, observer-suppressed, unsafe-blocked,
coalesced, and executed totals per intent kind. It retains no character-name or
per-Agent telemetry labels, so cardinality remains bounded during soak tests.

## Next extension seam

Personality profiles should later be assigned by a richer profile bundle rather
than the current deterministic cohort distribution. Keep the current three
layers separate during that migration:

1. durable semantic traits;
2. a context-specific resolver producing an intent;
3. a safety policy deciding whether that intent may execute now.

This allows future personality, goal selection, dialogue, and LLM presentation
to share stable identity while retaining independent constraints and rollout
flags.
