# Interaction Realism Design Specification

Purpose:

```text
Define the future package that makes Agent interactions look less identical
without making Plan Cards messy or changing gameplay requirements.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
Plan Cards say what to do.
Capabilities say how to do it.
Interaction Realism says how human-like it should look.
```

Realism is presentation policy. It must never bypass server validation, quest
requirements, plan forbidden actions, or capability safety rules.

## Goals

- Keep deterministic fast test mode for Maple Island MVP.
- Add randomized but valid NPC approach points.
- Add dialogue-length delays based on cataloged text length.
- Add profile-based reading speed, reaction delay, and movement variation.
- Reduce identical timing and clustering among Agents.
- Preserve reproducibility through stable seeded randomness.
- Keep all realism behavior optional and mode-gated.

## Non-Goals

- Do not encode exact delays or exact stop points inside Plan Cards.
- Do not replay arbitrary NPC scripts.
- Do not change quest/shop/server validation.
- Do not force delays when running deterministic tests.
- Do not require LLM involvement.
- Do not create per-tick random behavior that is impossible to debug.

## Modes

### OFF

Use for tests and first Maple Island MVP implementation.

Behavior:

- no dialogue delay.
- no random approach point.
- deterministic nearest valid point.
- no micro-pauses.
- no anti-clustering reservation.

### LIGHT

Use for normal load tests and light realism.

Behavior:

- seeded random valid approach point.
- short bounded delay.
- simple anti-clustering.
- no long dialogue-length delay.
- profile variance capped tightly.

### FULL

Use for production-like presentation.

Behavior:

- seeded random approach point.
- dialogue-length delay.
- profile reading speed.
- repeat-dialogue reduction.
- reaction delay and jitter.
- short-lived approach point reservations.
- optional facing/micro-pause hooks when supported by capability.

## Configuration Shape

Future config/provider shape:

```yaml
agents:
  interactionRealism:
    mode: OFF
    enableRandomNpcApproach: false
    enableDialogueLengthDelay: false
    enableProfileVariance: false
    enablePointReservations: false
    lightMaxDelayMs: 1500
    fullMaxDelayMs: 12000
```

These settings should live in an Agent package config/provider later, not in
current `config.yaml` unless explicitly approved.

## Approach Point Selection

Inputs:

- agent id and identity seed.
- map id.
- NPC id.
- quest/shop/action id.
- phase: start, complete, buy, sell, talk.
- attempt number.
- catalog approach points.
- current position.
- failed approach point memory.
- active point reservations.
- navigation reachability result.
- profile approach preference.

Selection flow:

```text
catalog candidates
  -> filter live-reachable points
  -> remove recently failed points
  -> prefer points inside interaction box
  -> score by distance, crowding, profile, and reservation
  -> choose with stable seeded randomness
  -> reserve briefly if enabled
```

Stable seed:

```text
agentSeed + mapId + npcId + actionType + questId/shopId + attempt
```

## Dialogue Delay

Inputs:

- catalog visible character count.
- dialogue string count.
- option count.
- phase: start/complete/menu.
- first/repeat memory.
- profile reading speed.
- profile reaction delay.
- mode cap.
- jitter seed.

Formula:

```text
baseMs = visibleChars / charsPerSecond * 1000
optionMs = optionCount * optionDecisionMs
reactionMs = profile reaction delay sample
jitterMs = bounded seeded jitter
delayMs = clamp(baseMs + optionMs + reactionMs + jitterMs, minMs, maxMs)
```

Mode behavior:

```text
OFF:   delayMs = 0
LIGHT: delayMs = clamp(delayMs, 300, lightMaxDelayMs)
FULL:  delayMs = clamp(delayMs, minDelayMs, fullMaxDelayMs)
```

Repeat dialogue:

```text
delayMs *= repeatDialogueMultiplier
```

## Profile Variation

Profile fields:

- reading speed range.
- reaction delay range.
- repeat dialogue multiplier.
- approach style.
- impatience.
- micro-pause chance.
- crowd avoidance.
- preferred side/distance.

Examples:

- fast clicker.
- casual reader.
- careful quester.
- social lingerer.
- impatient grinder.

Profile can vary timing and approach. It cannot override safety, range,
requirements, forbidden actions, or deterministic test mode.

## Anti-Clustering

Short-lived reservations prevent many Agents standing on the same point.

Rules:

- reservations expire quickly.
- reservations are advisory, not hard locks.
- blocked/failed approach points are remembered briefly.
- if all points are occupied, use nearest safe candidate.

## Determinism

For debugging:

- seeded random must be reproducible.
- every selected point and delay should be logged/audited.
- mode `OFF` must produce zero delay.
- tests should be able to force seed values.

## Relationship To Packages

NPC Quest Capability:

- asks for approach point and delay before quest start/complete.

Shop Capability:

- asks for approach point and delay before buy/sell/open shop.

Plan Runtime:

- does not store realism details.
- only passes objective context.

Profile Platform:

- supplies personality timing and approach preferences.

Catalog Platform:

- supplies approach point candidates and dialogue timing.

Observability:

- records selected approach point, delay, and mode.

## Success Criteria

The package is ready when:

- `OFF`, `LIGHT`, and `FULL` modes are defined and testable.
- Plan Cards remain free of exact timing/position details.
- approach point selection is seeded and reproducible.
- dialogue delay formula is bounded.
- profile variance is optional and safe.
- anti-clustering is advisory and bounded.
- Maple Island MVP can run with realism off first, then enable light/full later.
