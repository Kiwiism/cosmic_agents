# Interaction And Movement Realism Policy

Interaction realism controls how human-like an Agent looks while executing an
objective. This includes pacing, NPC approach, bounded fidgets, valid route
variation, incidental mob choices, and terminal resting behavior. It must not
make Plan Cards messy or weaken capability validation.

The staged Maple Island implementation and 100-Agent acceptance plan is in
`docs/agents/MAPLE_ISLAND_REALISM_AND_100_AGENT_VALIDATION_PLAN.md`.

## Meaning Of MVP

`MVP` means `minimum viable product`.

For this project, the Maple Island MVP is the smallest useful milestone that
proves the reconstructed Agent can:

- follow the Maple Island quest sequence.
- use navigation, NPC quest interaction, combat, loot, inventory, and recovery.
- finish selected Maple Island objectives.
- stop at Southperry without leaving through Shanks.

It intentionally leaves richer behavior, economy, LLM autonomy, and full
human-like variance for later layers.

## Design Rule

Keep these responsibilities separate:

```text
Plan Card
  what to do

Capability
  how to do it

Interaction Realism Policy
  samples bounded human-like presentation preferences

Agent Profile
  owns how this specific Agent varies from others

Capability Runtime
  accepts or suppresses the presentation choice and executes normal behavior
```

A Plan Card objective should stay simple:

```json
{
  "kind": "quest-start",
  "questId": 1021,
  "npcId": 2000,
  "mapId": 20000
}
```

It should not encode low-level presentation details such as exact wait times,
exact stop points, facing direction, or fake dialogue clicks.

## Runtime Modes

Use the server mode as a safety envelope. Normal timing ranges and presentation
preferences come from the assigned Agent profile.

```text
OFF
  no random NPC approach
  no dialogue-length delay
  fastest mode for sequence debugging and tests

LIGHT
  random valid NPC approach points
  short bounded interaction delay
  useful for normal load tests without very slow questing

FULL
  random valid NPC approach points
  dialogue-length delay
  profile-specific reading speed and jitter
  bounded fidgets, route variation, encounter style, and rest variation
```

Suggested server config shape after profile migration:

```yaml
agents:
  interactionRealism:
    mode: OFF # OFF, LIGHT, FULL
    deterministicTestOverride: true
    defaultProfileId: deterministic-test
    maxNpcDelayMs: 12000
    maxObjectiveDelayMs: 12000
    maxFidgetDurationMs: 2500
```

For quick Maple Island testing, use:

```yaml
agents:
  interactionRealism:
    mode: OFF
```

For production-like behavior:

```yaml
agents:
  interactionRealism:
    mode: FULL
    deterministicTestOverride: false
    defaultProfileId: casual-quester
```

The old `AGENT_AMHERST_NPC_INTERACTION_DELAY_*` and
`AGENT_AMHERST_NEXT_OBJECTIVE_DELAY_*` settings have been removed. Amherst and
Southperry runs assign the executable `maple-island-quester` profile, and both
NPC and between-objective pacing resolve from its `presentation.timing` block.
Its profile-aware navigation policy also reuses the existing grounded `WAIT`,
`PRONE`, and `SPAM_PRONE` fidgets. Global OFF/FULL modes, deterministic
overrides, server caps, varied NPC approaches, encounter behavior, and
profile-ranked rest selection in this document remain follow-up work.

## NPC Approach Variation

The catalog should provide valid interaction geometry. The plan only names the
NPC and objective.

Example catalog row:

```json
{
  "npcId": 2000,
  "mapId": 20000,
  "placementKey": "20000|0|2000",
  "x": 233,
  "y": 58,
  "interactionBox": {
    "left": 180,
    "right": 180,
    "up": 120,
    "down": 220,
    "allowBelowPlatformClick": true
  },
  "approachPoints": [
    { "x": 120, "y": 58, "footholdId": 7 },
    { "x": 180, "y": 58, "footholdId": 7 },
    { "x": 310, "y": 58, "footholdId": 7 }
  ]
}
```

The `NpcQuestInteractionCapability` chooses one approach point at execution
time.

Selection inputs:

- interaction realism mode.
- Agent identity seed.
- Agent profile.
- current side/path to NPC.
- recently failed approach points.
- short-lived reservations from nearby Agents.
- live reachability validation.

Selection rule:

```text
candidate points from catalog
  -> remove unreachable points
  -> remove recently failed points
  -> prefer points inside interaction box
  -> score by distance, profile, crowding, and reservation state
  -> choose with stable seeded randomness
```

Stable seeded randomness prevents every Agent from choosing the same spot while
still making behavior reproducible enough to debug.

Suggested seed:

```text
agentIdentitySeed + mapId + npcId + questId + phase + attemptNumber
```

## Dialogue-Length Delay

Agents do not need to click through dialogue for quest start/complete when a
safe direct quest API is available. But they may apply a simulated delay before
or after the interaction.

Catalog input:

```json
{
  "questId": 1021,
  "phase": "start",
  "visibleChars": 420,
  "stringCount": 3,
  "optionCount": 0,
  "firstReadDelayMsRange": [5800, 8800],
  "repeatReadDelayMsRange": [1200, 2600]
}
```

Runtime formula:

```text
baseDelayMs = visibleChars / charsPerSecond * 1000
reactionDelayMs = profile reaction delay sample
jitterMs = bounded random variance
finalDelayMs = clamp(baseDelayMs + reactionDelayMs + jitterMs, min, max)
```

Example:

```text
visibleChars: 420
reading speed: 60 chars/sec
base delay: 7000 ms
reaction delay: 800 ms
jitter: -600 ms
final delay: 7200 ms
```

In `LIGHT` mode, cap the delay aggressively:

```text
finalDelayMs = clamp(calculatedDelayMs, 300, 1500)
```

In `OFF` mode:

```text
finalDelayMs = 0
```

## Profile Variation

Agent profiles should provide ranges and preferences, not exact scripted
behavior.

Example profile slice:

```json
{
  "presentation": {
    "mode": "LIGHT",
    "timing": {
      "beforeNpcInteractionMs": [600, 1400],
      "betweenObjectivesMs": [900, 1800],
      "charsPerSecond": [35, 70],
      "reactionDelayMs": [700, 2400],
      "repeatDialogueDelayMultiplier": 0.35,
      "microPauseChancePerWindow": 0.08
    },
    "npc": {
      "approachStyle": "crowd-aware-random",
      "faceNpcBeforeInteraction": true
    },
    "movement": {
      "style": "opportunistic",
      "fidgetCooldownMs": [12000, 45000],
      "maxFidgetDurationMs": 1500,
      "alternateDropEdgeWeight": 0.35
    },
    "encounter": {
      "style": "attack-if-cheap",
      "maxEstimatedHits": 3
    }
  }
}
```

Profile examples:

```text
fast-clicker
  high chars/sec
  low reaction delay
  low patience

casual-reader
  medium chars/sec
  medium reaction delay
  occasional micro-pauses

careful-quester
  lower chars/sec
  longer first-time dialogue delay
  prefers closer/safe approach points
```

Profile resolution order:

```text
deterministic test override
  -> assigned profile
  -> archetype/template default
  -> server fallback
  -> deterministic fail-safe
```

The server clamps profile ranges. Mode `OFF` forces deterministic points, zero
presentation delay, and no cosmetic fidgets regardless of profile values.

## Movement And Encounter Variation

The current movement package already supports wait, jump, diagonal jump,
prone, repeated prone, and short sideways fidget actions. Plan execution may
reuse those physical actions only through an objective-aware eligibility gate.

Safe presentation windows require the Agent to be grounded with no committed
NPC, quest, portal, reactor, loot, combat, chair, rope, drop, fall, or recovery
action. Fidgets use cooldowns and per-objective time/distance budgets, never a
chance on every movement tick.

Navigation variation chooses among valid graph edges. It does not randomize
physics or invent coordinates. Alternative down-jump points must still be
reachable, progress toward the objective, and use normal airborne and landing
behavior.

Incidental mob preferences are profile choices:

- `evasive`: take a bounded safe detour or jump when one exists;
- `attack-if-cheap`: interrupt travel only when normal estimated hits-to-kill
  are within the profile threshold;
- `direct`: continue walking while health and safety policy allow it.

Quest-required combat overrides all incidental preferences. No presentation
policy may extend attack range, ignore collision, teleport, force loot, or
bypass damage and cooldown rules.

Random Southperry rest positions must come from cataloged, grounded, reachable
points with short-lived crowd reservations. Terminal success requires a
client-visible chair state, not merely a stored chair item ID.

## Execution Flow

```text
Objective: start quest 1021 at Roger
  -> NPC capability receives command
  -> load NPC placement and approach points
  -> read interaction realism mode
  -> consult Agent profile if enabled
  -> choose approach point
  -> navigate to selected point
  -> validate live NPC/range/quest requirements
  -> apply pre-action delay if enabled
  -> execute quest start through QuestCapability
  -> apply post-action dialogue delay if enabled
  -> return structured result
```

The objective result should include:

```json
{
  "selectedPlacementKey": "20000|0|2000",
  "selectedApproachPoint": {
    "x": 180,
    "y": 58,
    "footholdId": 7
  },
  "realismMode": "FULL",
  "delayAppliedMs": 7200,
  "delayReason": "dialogue-length-profile-jitter",
  "seed": "stable-debug-seed-or-hash"
}
```

## Plan Cleanliness

Do not create different Plan Cards for:

- fast reader vs slow reader.
- left-side NPC approach vs right-side NPC approach.
- short delay vs long delay.
- random stop point variants.

Use the same Plan Card and change only:

- runtime config.
- Agent profile.
- catalog approach point data.

## Implementation Order

1. Keep current Southperry and full Maple Island runs deterministic.
2. Add profile fields for interaction, objective, movement, encounter, and rest
   presentation.
3. Resolve profile ranges with server fallbacks and hard caps.
4. Add stable domain-separated seeds and replay keys.
5. Add `AgentNpcApproachPointChooser` and short-lived reservations.
6. Add NPC and between-objective delay decisions.
7. Log selected point, delay, seed, and suppression reasons.
8. Add grounded expression and crouch windows.
9. Add bounded stationary jump and sideways-origin-return windows.
10. Add valid route-edge variation with recent-edge memory.
11. Add incidental mob encounter policies.
12. Add Southperry rest-point catalog, reservation, and chair verification.
13. Prove `OFF` mode remains identical to deterministic Maple Island behavior.
14. Prove same-seed replay and different-seed bounded variation.
15. Run staged `5`, `10`, `25`, `50`, and `100` Agent cohorts.

## Acceptance Criteria

For quick testing:

- `OFF` mode produces no realism delay.
- the Maple Island plan executes the same sequence without extra presentation
  behavior.

For realistic behavior:

- two Agents doing the same NPC objective can choose different valid approach
  points.
- dialogue delay scales with catalog dialogue length.
- repeated dialogue is faster than first-time dialogue.
- all random choices are bounded and logged.
- live validation still blocks invalid NPC range, missing NPC, missing quest
  requirement, or forbidden Shanks interaction.
- fidgets never interrupt committed capability actions or occur in mid-air.
- route variation uses only valid navigation edges and normal physics.
- incidental mob choices obey normal combat, damage, and loot behavior.
- 100 Agents can finish the selected Maple Island route and visibly rest at
  varied valid Southperry points without synchronized movement.
