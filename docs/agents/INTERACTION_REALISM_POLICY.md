# Interaction Realism Policy

Interaction realism controls how human-like an Agent looks while executing an
objective. It must not make Plan Cards messy.

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
  how human-like the execution should look

Agent Profile
  how this specific Agent varies from others
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

Use a global mode first, then allow per-Agent overrides later.

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
  small micro-pauses/facing variance where supported
```

Suggested server config shape:

```yaml
agents:
  interactionRealism:
    mode: OFF # OFF, LIGHT, FULL
    enableRandomNpcApproach: false
    enableDialogueLengthDelay: false
    enableAgentPersonalityVariance: false
    defaultMinDelayMs: 0
    defaultMaxDelayMs: 0
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
    enableRandomNpcApproach: true
    enableDialogueLengthDelay: true
    enableAgentPersonalityVariance: true
```

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

Example:

```json
{
  "interactionStyle": {
    "readingStyle": "casual-reader",
    "charsPerSecondRange": [35, 70],
    "reactionDelayMsRange": [700, 2400],
    "npcApproachStyle": "random-nearby",
    "repeatDialogueDelayMultiplier": 0.35,
    "microPauseChance": 0.08
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

1. Add interaction realism config with `OFF`, `LIGHT`, and `FULL`.
2. Add `AgentInteractionRealismPolicy` model.
3. Add profile fields for reading speed, reaction delay, and NPC approach style.
4. Add `AgentNpcApproachPointChooser`.
5. Add short-lived approach point reservation memory.
6. Add `AgentDialogueDelayPolicy`.
7. Wire policy into NPC validation-only command.
8. Wire policy into quest start/complete command.
9. Log selected point and delay in `AgentNpcInteractionResult`.
10. Add tests for `OFF` mode so Maple Island sequence can run fast.
11. Add tests for `FULL` mode so two Agents choose different valid points.

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
