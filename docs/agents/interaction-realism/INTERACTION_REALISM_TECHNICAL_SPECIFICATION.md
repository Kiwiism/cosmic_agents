# Interaction Realism Technical Specification

Purpose:

```text
Define interfaces, DTOs, formulas, state, audit, and tests for the future
Interaction Realism package.
```

Package name:

```text
agent-interaction-realism
```

This spec is documentation only until Agent reconstruction is stable.

## Package Boundary

Interaction Realism owns:

- realism mode.
- approach point selection policy.
- dialogue delay calculation.
- point reservations.
- repeat-dialogue memory.
- profile timing sampling.
- audit payloads for selected realism choices.

It does not own:

- navigation pathfinding.
- NPC/quest/shop execution.
- plan objective state.
- server validation.
- profile storage.

## Suggested Layout

```text
agent-interaction-realism/
  api/
    InteractionRealismService.java
    ApproachPointSelector.java
    DialogueDelayCalculator.java
    InteractionRealismConfigProvider.java
  model/
    InteractionRealismMode.java
    InteractionRealismRequest.java
    InteractionRealismDecision.java
    ApproachPointCandidate.java
    ApproachPointSelection.java
    DialogueTimingInput.java
    DialogueDelayDecision.java
    InteractionRealismProfile.java
  runtime/
    ApproachPointReservationStore.java
    RepeatDialogueMemoryStore.java
    SeededInteractionRandom.java
  audit/
    InteractionRealismAuditEvent.java
```

## Core Interface

```java
public interface InteractionRealismService {
    InteractionRealismDecision decide(InteractionRealismRequest request);
}
```

## Request DTO

```java
record InteractionRealismRequest(
    String agentId,
    long agentSeed,
    String planId,
    String objectiveId,
    String actionType,
    Integer mapId,
    Integer npcId,
    Integer questId,
    Integer shopId,
    String phase,
    int attempt,
    Point currentPosition,
    List<ApproachPointCandidate> candidates,
    DialogueTimingInput dialogueTiming,
    InteractionRealismProfile profile,
    InteractionRealismMode mode
) {}
```

## Decision DTO

```java
record InteractionRealismDecision(
    InteractionRealismMode mode,
    ApproachPointSelection approach,
    DialogueDelayDecision delay,
    long seedUsed,
    List<String> warnings
) {}
```

## Mode Enum

```java
enum InteractionRealismMode {
    OFF,
    LIGHT,
    FULL
}
```

## Approach Candidate

```java
record ApproachPointCandidate(
    String pointKey,
    int x,
    int y,
    Integer footholdId,
    boolean insideInteractionBox,
    boolean reachable,
    double distanceScore,
    double crowdingScore,
    List<String> flags
) {}
```

## Approach Selection

```java
record ApproachPointSelection(
    String pointKey,
    int x,
    int y,
    Integer footholdId,
    boolean reserved,
    String reasonCode,
    double score
) {}
```

Reason codes:

- `DETERMINISTIC_NEAREST`
- `SEEDED_RANDOM`
- `PROFILE_PREFERRED`
- `CROWD_AVOIDED`
- `FAILED_POINTS_FILTERED`
- `FALLBACK_NEAREST`
- `NO_VALID_POINT`

## Dialogue Timing Input

```java
record DialogueTimingInput(
    int visibleChars,
    int stringCount,
    int optionCount,
    long minDelayMs,
    long maxDelayMs,
    boolean repeatedDialogue
) {}
```

## Dialogue Delay Decision

```java
record DialogueDelayDecision(
    long delayMs,
    long baseMs,
    long reactionMs,
    long optionMs,
    long jitterMs,
    String reasonCode
) {}
```

Reason codes:

- `REALISM_OFF`
- `LIGHT_CAPPED`
- `FULL_DIALOGUE_LENGTH`
- `REPEAT_REDUCED`
- `NO_DIALOGUE_TIMING`

## Profile Input

```java
record InteractionRealismProfile(
    int minCharsPerSecond,
    int maxCharsPerSecond,
    long minReactionDelayMs,
    long maxReactionDelayMs,
    double repeatDialogueMultiplier,
    double crowdAvoidanceWeight,
    double distanceWeight,
    double microPauseChance,
    String approachStyle
) {}
```

## Seed Formula

```text
seed = hash(agentSeed, mapId, npcId, actionType, questId, shopId, phase, attempt)
```

All random samples for one decision should derive from this seed.

## Approach Algorithm

```text
if mode == OFF:
    choose nearest reachable point
else:
    candidates = reachable and not recently failed
    score = distanceWeight * distanceScore
          + crowdAvoidanceWeight * crowdingScore
          + interactionBoxBonus
          + profileStyleBonus
          + reservationPenalty
    choose weighted seeded sample from top candidates
    reserve selected point if enabled
```

If no reachable point exists, return `NO_VALID_POINT` and let the capability
return `UNREACHABLE`.

## Delay Algorithm

```text
if mode == OFF:
    delay = 0
else:
    charsPerSecond = seeded sample from profile range
    baseMs = visibleChars / charsPerSecond * 1000
    optionMs = optionCount * sampledOptionDecisionMs
    reactionMs = seeded sample from profile reaction range
    jitterMs = seeded sample from bounded jitter range
    delay = baseMs + optionMs + reactionMs + jitterMs
    if repeated: delay *= repeatDialogueMultiplier
    if mode == LIGHT: delay = clamp(delay, 300, lightMaxDelayMs)
    if mode == FULL: delay = clamp(delay, minDelayMs, fullMaxDelayMs)
```

If no timing row exists:

- `OFF`: 0 ms.
- `LIGHT`: small default delay.
- `FULL`: default bounded delay from config/profile.

## Reservation Store

```java
public interface ApproachPointReservationStore {
    boolean tryReserve(String pointKey, String agentId, long ttlMs);
    void release(String pointKey, String agentId);
    boolean isReserved(String pointKey);
}
```

Rules:

- TTL must be short.
- expired reservations are cleaned opportunistically.
- reservation failure should reduce score, not hard-fail selection.

## Repeat Dialogue Memory

```java
public interface RepeatDialogueMemoryStore {
    boolean hasSeen(String agentId, String dialogueKey);
    void markSeen(String agentId, String dialogueKey, long nowMs);
}
```

Dialogue key:

```text
npcId + actionType + questId/shopId + phase
```

Memory should be bounded per Agent.

## Audit Event

Emit:

- agent id.
- plan id.
- objective id.
- mode.
- selected point key.
- delay ms.
- seed.
- reason codes.
- warnings.

## Tests

Unit tests:

- `OFF` returns zero delay.
- `OFF` selects deterministic nearest reachable point.
- same seed returns same point and delay.
- different agent seed can produce different point.
- unreachable candidates are ignored.
- recently failed points are ignored when alternatives exist.
- LIGHT caps delay.
- FULL uses dialogue length.
- repeated dialogue reduces delay.
- reservation affects scoring but does not deadlock.

Integration tests later:

- Maple Island MVP deterministic mode produces no delay.
- NPC Quest Capability receives selected point and delay.
- multiple Agents targeting same NPC choose varied points in LIGHT/FULL.
- observability records selected realism choices.

## Implementation Gates

Do not implement live integration until:

- NPC catalog approach points and dialogue timing are available.
- Profile runtime can provide interaction style.
- Capability Runtime can pass plan/objective context.
- Observability can record chosen point/delay.
