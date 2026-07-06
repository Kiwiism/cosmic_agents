# Perception Runtime Technical Specification

Purpose:

```text
Define interfaces, DTOs, refresh policies, relevance scoring, caching,
LLM-safe summaries, memory boundaries, and tests for the future Perception
Runtime package.
```

Package name:

```text
agent-perception-runtime
```

This specification is documentation only until Agent reconstruction is stable.

## Package Boundary

Perception Runtime owns:

- snapshot assembly.
- snapshot detail level selection.
- nearby entity summarization.
- relevance scoring.
- bounded snapshot caches.
- LLM-safe perception summaries.
- batch status summaries.
- perception audit events.

It does not own:

- action execution.
- plan scheduling.
- capability logic.
- profile storage.
- economy valuation.
- catalog building.
- direct server mutation.

## Suggested Layout

```text
agent-perception-runtime/
  api/
    PerceptionService.java
    PerceptionSnapshotProvider.java
    PerceptionRefreshPolicy.java
    NearbyEntityRanker.java
    LlmPerceptionSummarizer.java
  model/
    PerceptionRequest.java
    PerceptionSnapshot.java
    PerceptionLevel.java
    AgentLocationSummary.java
    AgentStatusSummary.java
    NearbySummary.java
    NearbyNpcSummary.java
    NearbyMobSummary.java
    NearbyDropSummary.java
    NearbyPlayerSummary.java
    NearbyAgentSummary.java
    NearbyPortalSummary.java
    NearbyReactorSummary.java
    ObjectiveContextSummary.java
    MemoryHintSummary.java
    PerceptionLimits.java
    BatchPerceptionRow.java
  runtime/
    PerceptionRuntime.java
    SnapshotCache.java
    LastSeenEntityCache.java
    PerceptionBudgetLimiter.java
  audit/
    PerceptionSnapshotEvent.java
    PerceptionLimitEvent.java
```

## Core Interface

```java
public interface PerceptionService {
    PerceptionSnapshot snapshot(PerceptionRequest request);
    List<BatchPerceptionRow> batchSnapshot(BatchPerceptionRequest request);
}
```

## Request DTO

```java
record PerceptionRequest(
    String agentId,
    PerceptionLevel level,
    String consumer,
    Integer planId,
    String objectiveId,
    int mapId,
    long nowMs,
    PerceptionLimits limits,
    boolean forceRefresh
) {}
```

Consumers:

- `PLAN_RUNTIME`
- `CAPABILITY_RUNTIME`
- `RECOVERY_POLICY`
- `PROFILE_ADAPTATION`
- `ECONOMY_ENGINE`
- `LLM_GATEWAY`
- `AGENT_CONSOLE`
- `SOAK_TEST`

## Perception Level

```java
enum PerceptionLevel {
    URGENT,
    ACTIVE,
    STRATEGIC,
    BATCH
}
```

## Snapshot DTO

```java
record PerceptionSnapshot(
    int schemaVersion,
    String agentId,
    long timestampMs,
    PerceptionLevel level,
    AgentLocationSummary location,
    AgentStatusSummary status,
    NearbySummary nearby,
    ObjectiveContextSummary objective,
    MemoryHintSummary memoryHints,
    List<String> affordances,
    List<String> warnings
) {}
```

## Location Summary

```java
record AgentLocationSummary(
    int mapId,
    String mapName,
    String region,
    int channel,
    int x,
    int y,
    Integer footholdId,
    String simulationMode,
    boolean realPlayersInMap
) {}
```

## Status Summary

```java
record AgentStatusSummary(
    int level,
    int jobId,
    String jobName,
    double hpPercent,
    double mpPercent,
    int mesos,
    String busyState,
    String dangerLevel,
    boolean dead,
    boolean stuck,
    String blockerReason
) {}
```

## Nearby Summary

```java
record NearbySummary(
    List<NearbyNpcSummary> npcs,
    List<NearbyMobSummary> mobs,
    List<NearbyDropSummary> drops,
    List<NearbyPlayerSummary> players,
    List<NearbyAgentSummary> agents,
    List<NearbyPortalSummary> portals,
    List<NearbyReactorSummary> reactors
) {}
```

All lists must be bounded by `PerceptionLimits`.

## Nearby Entity DTOs

```java
record NearbyNpcSummary(
    int npcId,
    String name,
    int distance,
    boolean canInteract,
    List<String> knownActions,
    List<Integer> relevantQuestIds,
    String risk,
    double relevanceScore
) {}
```

```java
record NearbyMobSummary(
    int mobId,
    String name,
    int level,
    int distance,
    int estimatedHp,
    String estimatedThreat,
    boolean objectiveRelevant,
    boolean futureLootRelevant,
    List<Integer> relevantItemIds,
    double relevanceScore
) {}
```

```java
record NearbyDropSummary(
    int objectId,
    int itemId,
    String name,
    int distance,
    boolean ownedByAgent,
    boolean questRelevant,
    boolean marketRelevant,
    int estimatedValue,
    double relevanceScore
) {}
```

```java
record NearbyPortalSummary(
    String portalName,
    int distance,
    Integer toMapId,
    String toMapName,
    boolean routeKnown,
    boolean objectiveRelevant,
    double relevanceScore
) {}
```

## Objective Context Summary

```java
record ObjectiveContextSummary(
    String planId,
    String objectiveId,
    String objectiveType,
    String focusMode,
    Integer targetMapId,
    Integer targetNpcId,
    Integer targetMobId,
    Integer targetItemId,
    int progressCurrent,
    int progressRequired,
    String lastResult,
    String blockedReason
) {}
```

## Memory Hint Summary

```java
record MemoryHintSummary(
    List<String> recentFailures,
    List<String> usefulRoutes,
    List<String> npcHints,
    List<String> combatHints,
    List<String> economyHints,
    List<String> relationshipHints
) {}
```

Each list is a compact human-readable or LLM-readable hint, not raw history.

## Perception Limits

```java
record PerceptionLimits(
    int maxNpcs,
    int maxMobs,
    int maxDrops,
    int maxPlayers,
    int maxAgents,
    int maxPortals,
    int maxReactors,
    int maxMemoryHints,
    int maxTextChars
) {}
```

Default limits should be conservative. LLM consumers can request larger limits
only through policy.

## Relevance Scoring

Base score:

```text
score = objectiveWeight
      + futureQuestWeight
      + distanceWeight
      + dangerWeight
      + marketWeight
      + profileInterestWeight
      + relationshipWeight
      + routeAffordanceWeight
```

Distance contribution:

```text
distanceWeight = clamp(1.0 - distance / maxRelevantDistance, 0.0, 1.0)
```

Objective relevance should dominate distance. A far quest NPC can outrank a
near irrelevant NPC.

## Refresh Policy

```java
public interface PerceptionRefreshPolicy {
    boolean shouldRefresh(PerceptionRequest request, CachedSnapshot cached);
    long nextRefreshDelayMs(PerceptionRequest request, SimulationMode mode);
}
```

Suggested cadence:

```text
URGENT:
  refresh immediately or every short recovery tick

ACTIVE + PRESENTATION:
  normal capability cadence

ACTIVE + BACKGROUND_ACTIVE:
  slower cadence, bounded by objective needs

ACTIVE + BACKGROUND_ABSTRACT:
  objective-slice cadence

STRATEGIC:
  periodic or on meaningful events

BATCH:
  cached rows unless forced
```

## Snapshot Cache

Cache key:

```text
agentId + perceptionLevel + consumer
```

Invalidation triggers:

- map change.
- objective change.
- HP/MP danger threshold crossing.
- death/respawn.
- inventory mutation relevant to objective.
- nearby player enters/leaves map.
- materialization.
- blocker state change.
- force refresh.

## LLM Summary API

```java
public interface LlmPerceptionSummarizer {
    LlmPerceptionSummary summarize(PerceptionSnapshot snapshot, LlmSummaryPolicy policy);
}
```

LLM summaries should include:

- compact location/status.
- top action-relevant entities.
- current objective.
- warnings/blockers.
- action affordances.
- confidence/uncertainty.

They should omit:

- raw object references.
- full inventories.
- full map entity lists.
- raw player private data.
- server implementation details.

## Batch Snapshot Row

```java
record BatchPerceptionRow(
    String agentId,
    int mapId,
    String mapName,
    String region,
    String state,
    String planId,
    String objectiveId,
    boolean blocked,
    String blockerReason,
    String lastEvent,
    long lastUpdatedMs
) {}
```

Batch rows are for Agent Console, soak tests, and LLM multi-Agent assignment.

## Audit Events

Emit:

- snapshot requested.
- cache hit/miss.
- limits applied.
- refresh skipped.
- entity lists truncated.
- LLM summary generated.
- perception source unavailable.

Minimum fields:

- agent id.
- consumer.
- level.
- map id.
- snapshot age.
- counts per entity type.
- warnings.

## Tests

Unit tests:

- snapshots never exceed `PerceptionLimits`.
- objective-relevant NPC outranks irrelevant nearby NPC.
- objective-relevant drop outranks low-value irrelevant drop.
- LLM summary hides raw object references.
- cache invalidates on map change.
- cache invalidates on objective change.
- urgent request bypasses stale cache.
- batch rows remain compact.

Integration tests later:

- Maple Island active objective snapshot includes correct NPC/mob/item target.
- combat objective snapshot includes target mobs and relevant future-loot mobs.
- recovery snapshot includes low HP/potion/death hints.
- background abstract mode uses lower refresh cadence.
- LLM gateway receives bounded summaries for many Agents.

## Implementation Gates

Do not implement live integration until:

- Server Adapter exposes stable read-only snapshots.
- Catalog Platform exposes fast map/NPC/mob/drop/portal indexes.
- Plan Runtime exposes objective context.
- Simulation Tier Runtime exposes current mode.
- Observability can record perception metrics.
