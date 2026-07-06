# Social Relationship Runtime Technical Specification

Purpose:

```text
Define the portable data models, APIs, storage, decay rules, validation, and
tests for Agent relationship memory and social graph summaries.
```

This is implementation guidance for after Agent reconstruction. It must not be
implemented against live Agent runtime files before stable reconstructed entry
points exist.

## Suggested Package Layout

```text
agent-social-relationship-runtime/
  api/
    RelationshipMemoryService
    RelationshipEventSink
    RelationshipQueryService
    RelationshipPatchValidator
    SocialGraphSummaryService
  model/
    RelationshipTarget
    RelationshipMemory
    RelationshipDimensions
    RelationshipEvent
    RelationshipPatch
    RelationshipSummary
    CounterpartyRiskSummary
    SocialGraphEdge
    SocialGraphQuery
    RelationshipReasonCode
  runtime/
    DefaultRelationshipMemoryService
    RelationshipEventReducer
    RelationshipDecayEngine
    RelationshipTopNCache
    SocialGraphSummaryBuilder
  store/
    RelationshipMemoryStore
    RelationshipEventStore
    RelationshipPatchStore
    RelationshipSnapshotStore
  audit/
    RelationshipAuditEvent
```

## Service Contracts

### RelationshipMemoryService

Primary read/write service.

```text
RelationshipMemory getMemory(long agentId, RelationshipTarget target)
RelationshipSummary getSummary(long agentId, RelationshipTarget target)
RelationshipPatchResult applyEvent(RelationshipEvent event)
RelationshipPatchResult applyPatch(RelationshipPatch patch)
```

Rules:

- all updates go through validators.
- values are clamped.
- hard policy cannot be changed here.
- event-derived patches are journaled.

### RelationshipQueryService

Bounded read service for decision paths.

```text
List<RelationshipSummary> getTopRelationships(long agentId, RelationshipQuery query)
CounterpartyRiskSummary getCounterpartyRisk(long agentId, RelationshipTarget target)
```

Agent tick paths must use bounded top-N caches or direct key lookups, not full
relationship scans.

### RelationshipEventSink

Append-only event intake.

```text
void record(RelationshipEvent event)
```

Event sink may be connected to Event Bus after reconstruction.

### SocialGraphSummaryService

Builds aggregate views for Agent Console, LLM, Economy Engine, and diagnostics.

```text
SocialGraphSummary buildSummary(SocialGraphQuery query)
```

Aggregate views should run on scheduled/background paths, not hot Agent ticks.

## Relationship Target

```json
{
  "schemaVersion": 1,
  "type": "PLAYER",
  "stableKey": "player:hash:abc123",
  "worldId": 0,
  "displayNameHash": "abc123",
  "characterId": null,
  "privacyLevel": "HASHED"
}
```

Target types:

- `AGENT`
- `PLAYER`
- `PARTY`
- `SHOP_OWNER`
- `GUILD`
- `GROUP`

## Relationship Memory

```json
{
  "schemaVersion": 1,
  "agentId": 123,
  "target": {
    "type": "PLAYER",
    "stableKey": "player:hash:abc123"
  },
  "dimensions": {
    "familiarity": 0.72,
    "trust": 0.81,
    "affinity": 0.64,
    "helpfulnessDebt": 0.30,
    "tradeReliability": 0.90,
    "partyCompatibility": 0.75,
    "generosityReceived": 0.20,
    "annoyance": 0.10,
    "avoidance": 0.05
  },
  "tags": ["fair-trader", "safe-party-member"],
  "summary": {
    "shortText": "Reliable low-level party and trade counterparty.",
    "lastOutcome": "successful-trade",
    "interactionCount": 12,
    "positiveInteractions": 9,
    "negativeInteractions": 1
  },
  "timestamps": {
    "createdAtMs": 0,
    "lastInteractionAtMs": 0,
    "lastDecayAtMs": 0
  }
}
```

## Relationship Event

```json
{
  "schemaVersion": 1,
  "eventId": "rel-evt-123-0001",
  "agentId": 123,
  "target": {
    "type": "PLAYER",
    "stableKey": "player:hash:abc123"
  },
  "eventType": "TRADE_GOOD",
  "occurredAtMs": 0,
  "context": {
    "mapId": 100000000,
    "planId": "sell-trash",
    "tradeValueBucket": "LOW"
  },
  "reasonCodes": ["FAIR_PRICE", "TRADE_COMPLETED"]
}
```

Recommended event types:

- `MET`
- `REPEAT_MAP_SHARING`
- `HELP_GIVEN`
- `HELP_RECEIVED`
- `PARTY_SUCCESS`
- `PARTY_FAILED`
- `TRADE_GOOD`
- `TRADE_BAD`
- `SHOP_FAIR_PRICE`
- `SHOP_OVERPRICED`
- `REQUEST_ACCEPTED`
- `REQUEST_REJECTED`
- `CONFLICT`
- `CROWDING_FRICTION`
- `AVOIDANCE_TRIGGERED`

## Relationship Patch

```json
{
  "schemaVersion": 1,
  "patchId": "rel-patch-123-0001",
  "agentId": 123,
  "target": {
    "type": "PLAYER",
    "stableKey": "player:hash:abc123"
  },
  "sourceEventId": "rel-evt-123-0001",
  "dimensionDeltas": {
    "familiarity": 0.02,
    "trust": 0.03,
    "tradeReliability": 0.05
  },
  "addTags": ["fair-trader"],
  "removeTags": [],
  "reasonCodes": ["TRADE_GOOD"]
}
```

Validation:

- clamp all dimensions.
- reject unknown target type.
- reject patches that mutate hard policy.
- reject raw chat payloads unless debug retention is explicitly enabled.
- reject unbounded deltas.

## Event Reducer

The reducer maps events into bounded patches.

Example:

```text
TRADE_GOOD:
  familiarity +0.02
  trust +0.03
  tradeReliability +0.05
  annoyance -0.01

TRADE_BAD:
  familiarity +0.01
  trust -0.08
  tradeReliability -0.10
  annoyance +0.04
  avoidance +0.03

HELP_RECEIVED:
  familiarity +0.03
  affinity +0.05
  helpfulnessDebt +0.10
  trust +0.02
```

Exact weights should be tunable and tested through replay.

## Decay

Decay should run on background/scheduled paths.

Suggested neutral values:

```text
trust: 0.50
affinity: 0.50
tradeReliability: 0.50
partyCompatibility: 0.50
annoyance: 0.00
avoidance: 0.00
helpfulnessDebt: 0.00
```

Suggested rule:

```text
newValue = neutral + (oldValue - neutral) * decayFactor
```

Decay factor may differ per dimension.

## Storage

Portable file-backed MVP:

```text
profiles/
  agents/
    123/
      relationships/
        memory.json
        events.ndjson
        patches.ndjson
        top-cache.json
```

Production options:

- SQLite.
- dedicated Agent database.
- embedded key-value store.

Storage must keep the same API and stable JSON schema.

## Fast Lookup Indexes

Required:

- `agentId + targetStableKey -> RelationshipMemory`.
- `agentId + tag -> top relationship summaries`.
- `agentId + targetType -> top relationship summaries`.
- `agentId -> top trusted`.
- `agentId -> top avoided`.
- `agentId -> top trade reliable`.
- `agentId -> top party compatible`.

Hot paths must use direct lookup or bounded top-N indexes.

## Decision Summary API

```json
{
  "agentId": 123,
  "target": {
    "type": "PLAYER",
    "stableKey": "player:hash:abc123"
  },
  "summary": {
    "trust": 0.81,
    "affinity": 0.64,
    "tradeReliability": 0.90,
    "partyCompatibility": 0.75,
    "annoyance": 0.10,
    "avoidance": 0.05,
    "tags": ["fair-trader"],
    "recommendedInfluences": [
      {
        "decisionKind": "trade-counterparty",
        "direction": "toward-accept",
        "weight": 0.18,
        "reasonCodes": ["HIGH_TRADE_RELIABILITY"]
      }
    ]
  }
}
```

## Reason Codes

Recommended enum:

```text
MET_BEFORE
REPEAT_MAP_SHARING
HELP_GIVEN
HELP_RECEIVED
PARTY_SUCCESS
PARTY_FAILED
TRADE_GOOD
TRADE_BAD
FAIR_PRICE
OVERPRICED
REQUEST_ACCEPTED
REQUEST_REJECTED
CONFLICT
CROWDING_FRICTION
HIGH_TRUST
LOW_TRUST
HIGH_AFFINITY
HIGH_ANNOYANCE
HIGH_AVOIDANCE
HIGH_TRADE_RELIABILITY
LOW_TRADE_RELIABILITY
HIGH_PARTY_COMPATIBILITY
DECAY_APPLIED
PATCH_REJECTED_POLICY
PATCH_REJECTED_PRIVACY
```

## Observability

Metrics:

- relationship events recorded.
- patches applied.
- patches rejected.
- decay runs.
- memories by Agent.
- top-N cache size.
- relationship lookup latency.
- privacy rejection count.

Reports:

- top trusted counterparties.
- top avoided counterparties.
- trade reliability outliers.
- party compatibility clusters.
- relationship event volume by type.
- suspicious same-group trade patterns for Economy Engine.

## Tests

### Unit Tests

- creates memory for new target.
- repeated `MET` increases familiarity.
- good trade increases trust and trade reliability.
- bad trade decreases trust and increases avoidance.
- help received increases affinity and helpfulness debt.
- values clamp within valid bounds.
- decay moves values toward neutral.
- patch validator rejects hard policy mutation.
- patch validator rejects raw chat payload by default.
- direct lookup returns expected memory.
- top-N cache returns bounded summaries.

### Integration Tests

- event stream rebuilds same relationship state.
- Profile Decision API receives relationship influence object.
- Economy Engine receives counterparty risk summary.
- LLM summary omits raw private chat.
- old memories compact into summaries.

### Replay Tests

- same events in same order produce same memory.
- compacted summary plus later events remains stable.
- rejected patches are auditable.

## Implementation Gates

Requires:

- Profile Platform profile ids and store interface.
- Event Bus or append-only event sink.
- Observability audit sink.
- Privacy/keying policy for player references.
- Economy Engine counterparty-risk consumer, for trade use cases.
- reconstructed Agent runtime entry points, for live social/party/trade use.

## Safe Pre-Reconstruction Work

Allowed now:

- maintain this specification.
- maintain schema drafts.
- maintain docs for privacy, decay, and summaries.
- add offline replay fixtures later.

Not allowed now:

- live Agent party/trade/chat behavior.
- live Agent sidetrack behavior.
- edits to `src/main/java/server/agents`.
- edits to `src/main/java/server/bots`.
- BotClient behavior changes.
