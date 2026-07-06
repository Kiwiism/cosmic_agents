# Social Relationship Runtime Design Specification

Purpose:

```text
Give each Agent a portable, bounded memory of other agents, players, parties,
shops, and social groups so future party, trade, help, avoidance, and LLM
decisions can be influenced by history without hard-coding personality inside
capabilities.
```

This package is post-reconstruction implementation work. Before Agent
reconstruction is complete, it is documentation-only.

Recommended package:

```text
agent-social-relationship-runtime
```

## Design Position

The Social Relationship Runtime is a companion package to Profile Platform.

```text
Profile Platform owns identity, traits, hard policy, and decision APIs.
Social Relationship Runtime owns counterparty memories and social graph views.
Plan Runtime and Economy Engine consume relationship summaries.
Capabilities execute validated actions.
```

It should stay advisory. A relationship can make an Agent more likely to help,
party, trade, discount, avoid, or sidetrack, but it must not bypass hard policy,
anti-abuse gates, protected items, safety checks, or trade limits.

## Goals

- Track how each Agent understands other agents and players over time.
- Support relationship dimensions beyond friend/enemy.
- Let repeated interactions influence social, party, trade, map-sharing, and
  sidetrack decisions.
- Maintain group-level summaries for economy, party compatibility, and LLM
  context.
- Decay stale relationships toward neutral.
- Preserve privacy by storing compact dimensions and summaries, not raw chat.
- Keep data portable across Cosmic-like servers.
- Support explainable decision influence and decision journals.
- Feed Agent Console with inspectable social graph views later.

## Non-Goals

- Do not execute chat, party, trade, shop, or movement actions.
- Do not decide exact plan objectives.
- Do not replace Profile Platform hard constraints.
- Do not store raw chat logs by default.
- Do not let trusted relationships bypass anti-abuse rules.
- Do not require an LLM.
- Do not import Cosmic server classes.

## Core Concepts

### Relationship Memory

A per-Agent record about another entity.

Targets may be:

- another Agent.
- a real player character.
- a party.
- a shop owner or merchant.
- a future guild/friend group.

### Counterparty

The entity being remembered.

The runtime should support privacy-safe ids:

- stable internal character id when allowed.
- server-side hashed display name.
- hashed account/world group for anti-abuse checks, if available.
- anonymous external key when only partial data exists.

### Relationship Dimensions

Recommended bounded values, usually `0.0` to `1.0` unless noted:

- `familiarity`: how often the Agent has seen or interacted with the target.
- `trust`: whether interactions have been safe and reliable.
- `affinity`: whether the Agent likes interacting with the target.
- `helpfulnessDebt`: whether the Agent feels it owes or is owed help. This can
  be negative or positive.
- `tradeReliability`: whether trades and market interactions were fair.
- `partyCompatibility`: whether party play worked well.
- `generosityReceived`: how often the target helped or gave useful value.
- `annoyance`: repeated negative friction, crowding, spam, or failed requests.
- `avoidance`: desire to avoid future interaction.

Hard policy should treat relationship dimensions as influence, not permission.

### Social Edge

A compact graph edge from one Agent to one counterparty.

```text
agent -> counterparty -> relationship dimensions + tags + summary
```

Edges are directional. Agent A may trust Agent B more than B trusts A.

### Group Summary

Aggregated social knowledge.

Examples:

- reliable low-level party helpers.
- fair traders.
- repeated bad trade counterparties.
- town regulars.
- map-sharing friendly Agents.
- players who often request help.

Group summaries help dashboards, LLM context, population behavior, and economy
abuse checks without requiring every Agent to scan all individual memories.

### Relationship Event

An append-only event that may change relationship memory.

Examples:

- met on same map.
- helped with quest.
- helped with mob kill.
- party success.
- party abandoned.
- fair trade.
- suspicious trade.
- overpricing seen.
- repeated map crowding.
- request accepted.
- request rejected.
- conflict.

### Relationship Patch

A bounded state update derived from events.

Patches should be validated and clamped.

## Example Effects

High trust and high trade reliability:

```text
Agent is more willing to accept normal-value trades.
Agent may prefer this seller when prices are similar.
```

High affinity and positive helpfulness debt:

```text
Agent is more likely to accept a short help sidetrack.
```

High annoyance or avoidance:

```text
Agent is more likely to reject party invites, change channel, or avoid sharing
the same map.
```

High party compatibility:

```text
Agent is more likely to join PQ/training parties with this counterparty.
```

## Safety Rules

Relationship memory cannot:

- exceed max trade value.
- sell protected items.
- bypass quest or level requirements.
- force unsafe maps.
- override islander or other hard constraints.
- bypass anti-abuse checks.
- bypass LLM command validation.
- mutate another Agent's profile directly.

## Decision Influence

Relationship summaries may be supplied to:

- Profile Decision API.
- Plan Runtime sidetrack scoring.
- Economy Engine counterparty risk checks.
- LLM Gateway read-only summaries.
- Agent Console dashboards.

Recommended influence shape:

```json
{
  "source": "relationship",
  "counterpartyKey": "player:hash:abc123",
  "key": "partyCompatibility",
  "value": 0.82,
  "weight": 0.18,
  "direction": "toward-accept-party"
}
```

## Decay And Forgetting

Relationships should slowly drift toward neutral unless refreshed.

Recommended:

- familiarity decays slowly.
- trust decays slowly toward neutral.
- affinity decays moderately.
- annoyance decays faster than trust unless repeated.
- avoidance decays slowly only after no new negative events.
- major tags can expire or be compacted into summaries.

Old raw interaction events should be compacted into summaries for privacy and
storage control.

## Privacy Model

Default behavior:

- store structured relationship dimensions.
- store reason codes and summaries.
- do not store raw chat lines.
- hash display names when possible.
- allow operator reset of a relationship domain.
- compact old event history.

Optional future behavior:

- allow raw excerpts only in explicit debug mode with retention limits.

## LLM Readiness

LLM summaries should be compact and safe:

```text
Agent Mira trusts Agent Ryn for parties after repeated successful training.
She avoids Player X because of repeated failed trades and crowding.
She may accept a short help request from Ryn, but trade limits still apply.
```

LLM may propose relationship tags or summaries, but patches must pass the same
validators as event-derived patches.

## Package Relationships

### Profile Platform

Owns:

- profile identity.
- personality traits.
- hard constraints.
- profile decision API.

Consumes:

- relationship summaries and influence objects.

### Plan Runtime

Consumes:

- help request compatibility.
- sidetrack acceptance pressure.
- party preference hints.
- avoidance hints.

### Economy Engine

Consumes:

- counterparty trade reliability.
- repeated same-party/same-account/same-relationship patterns.
- discount/markup preference hints.

### LLM Gateway

Consumes:

- read-only summaries.
- bounded relationship graph views.

May submit:

- proposed tags or patches through validation.

### Observability

Consumes:

- relationship patch events.
- high-level social graph summaries.
- suspicious interaction signals.

## Success Criteria

The package is ready when:

- it can store directional relationship memories per Agent.
- it can update bounded dimensions from structured events.
- it can decay stale relationships.
- it can provide top-N summaries without scanning all memories during Agent
  ticks.
- it can explain how a relationship influenced a decision.
- it can support privacy-safe player references.
- it never bypasses hard policy or anti-abuse validation.

## Deferred Until After Reconstruction

- live party invite handling.
- live trade behavior.
- live chat/social behavior.
- live Agent sidetrack execution.
- Agent Console graph visualization.
- LLM relationship patch tools.
