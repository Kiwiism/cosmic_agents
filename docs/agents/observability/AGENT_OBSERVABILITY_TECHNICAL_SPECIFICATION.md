# Agent Observability Technical Specification

Purpose:

```text
Define DTOs, collectors, metrics windows, query APIs, snapshots, exports, and
tests for the future Agent Observability package.
```

Package name:

```text
agent-observability
```

This spec is documentation only until Agent reconstruction is stable.

## Package Boundary

Observability owns:

- metric counters.
- rolling windows.
- compact snapshots.
- top-N reports.
- query API.
- soak export records.
- Agent Console read models.

Observability does not own:

- plan execution.
- capability execution.
- profile adaptation.
- economy decisions.
- server mutation.
- LLM calls.

## Suggested Layout

```text
agent-observability/
  api/
    AgentObservabilityService.java
    AgentObservabilityQuery.java
    AgentObservabilitySnapshotProvider.java
  model/
    AgentSnapshot.java
    AgentMapSnapshot.java
    AgentSchedulerSnapshot.java
    AgentCapabilitySnapshot.java
    AgentPlanSnapshot.java
    AgentEventBusSnapshotView.java
    AgentMemorySnapshot.java
    AgentIncidentRecord.java
  metrics/
    RollingCounter.java
    RollingLatencyWindow.java
    TopNTracker.java
    ReasonCodeCounter.java
  collectors/
    AgentRuntimeCollector.java
    AgentPlanCollector.java
    AgentCapabilityCollector.java
    AgentSchedulerCollector.java
    AgentEventBusCollector.java
    AgentMemoryCollector.java
    ServerHealthCorrelationCollector.java
  export/
    AgentSoakSnapshotWriter.java
    AgentObservabilityJsonExporter.java
    AgentObservabilityCsvExporter.java
```

## Core API

```java
public interface AgentObservabilityService {
    AgentPopulationSnapshot population();
    Optional<AgentSnapshot> agent(String agentId);
    Optional<AgentMapSnapshot> map(int mapId);
    AgentSchedulerSnapshot scheduler();
    AgentCapabilitySnapshot capabilities(AgentObservabilityQuery query);
    AgentPlanSnapshot plans(AgentObservabilityQuery query);
    AgentMemorySnapshot memory();
    List<AgentIncidentRecord> recentIncidents(AgentObservabilityQuery query);
    List<AgentSnapshot> topSlowAgents(int limit);
    List<AgentMapSnapshot> topBusyMaps(int limit);
}
```

## Agent Snapshot

```java
record AgentSnapshot(
    String agentId,
    String displayName,
    Integer mapId,
    String simulationMode,
    String activePlanId,
    String currentObjectiveId,
    String activeCommandId,
    String activeCapabilityFamily,
    String lastStatus,
    String lastReasonCode,
    int retryCount,
    int deathCount,
    long lastTickDurationMs,
    long lastUpdatedMs
) {}
```

## Map Snapshot

```java
record AgentMapSnapshot(
    int mapId,
    String mapName,
    int realPlayerCount,
    int agentCount,
    int presentationAgents,
    int backgroundActiveAgents,
    int backgroundAbstractAgents,
    int strategicOfflineAgents,
    String sensitivityReason,
    long avgAgentTickMs,
    long p95AgentTickMs,
    int recentMaterializations,
    int recentBlockedActions
) {}
```

## Capability Snapshot

```java
record AgentCapabilitySnapshot(
    String family,
    long commandsTotal,
    long successTotal,
    long blockedTotal,
    long timeoutTotal,
    long cancelledTotal,
    long activeCommands,
    long avgDurationMs,
    long p95DurationMs,
    Map<String, Long> reasonCodeCounts
) {}
```

## Scheduler Snapshot

```java
record AgentSchedulerSnapshot(
    int queueDepth,
    int overdueWork,
    long p50DelayMs,
    long p95DelayMs,
    long p99DelayMs,
    long budgetOverruns,
    long shedWork,
    Map<String, Integer> priorityDepths,
    Map<String, Long> workKindCounts
) {}
```

## Plan Snapshot

```java
record AgentPlanSnapshot(
    long activePlans,
    long completedPlans,
    long failedPlans,
    long blockedObjectives,
    long sidetrackCount,
    long resumeReconciliationCount,
    Map<String, Long> planCounts,
    Map<String, Long> blockerReasonCounts
) {}
```

## Memory Snapshot

```java
record AgentMemorySnapshot(
    int runtimeEntries,
    int planProgressEntries,
    int routeStateEntries,
    int perceptionSnapshots,
    int combatTargetStates,
    int npcDialogueStates,
    int decisionJournalBuffers,
    int profileMemoryBuffers,
    int economyObservationBuffers,
    Map<String, Integer> highWatermarks
) {}
```

## Incident Record

```java
record AgentIncidentRecord(
    String incidentId,
    long occurredAtMs,
    String severity,
    String agentId,
    Integer mapId,
    String planId,
    String objectiveId,
    String commandId,
    String reasonCode,
    String summary,
    Map<String, String> evidence
) {}
```

## Collection Rules

Collectors should be push-first:

- consume Event Bus events.
- consume runtime snapshots at bounded intervals.
- update counters without scanning all Agents each tick.

Allowed periodic scans:

- low-frequency population summary.
- low-frequency memory count.
- soak snapshot interval.

Avoid:

- per-tick global Agent scans.
- unbounded per-Agent histories.
- full inventory/map snapshots.

## Rolling Windows

Recommended windows:

```text
1 minute
5 minutes
15 minutes
1 hour
```

Track:

- counts.
- avg/p50/p95/p99 latency.
- top reason codes.
- high-water marks.

## Top-N Reports

Maintain bounded top-N trackers for:

- slow Agents.
- busy maps.
- blocked objectives.
- noisy event types.
- slow capabilities.
- high retry Agents.
- frequent death Agents.

Top-N should update incrementally from events and snapshots.

## Soak Export

Periodic soak output:

```json
{
  "timeMs": 0,
  "agentsTotal": 0,
  "modeCounts": {},
  "activePlans": 0,
  "schedulerQueueDepth": 0,
  "eventQueueDepth": 0,
  "capabilityP95Ms": {},
  "blockedReasonCounts": {},
  "memoryCounts": {},
  "serverHealth": {}
}
```

Writers:

- JSONL for detailed samples.
- CSV for graphs.

## Server Health Correlation

Collect or link:

- DB pool stats.
- timer lane stats.
- save pressure.
- broadcast pressure.
- map runtime counts.
- slow-operation thresholds.
- heap usage.

Agent Observability should reference these through a read-only provider, not
direct dependency on concrete server internals.

## Privacy And Retention

For player-facing interactions:

- store relationship/event summaries, not raw chat logs by default.
- keep LLM prompts/responses in a separate audited store if enabled.
- allow debug trace retention to be disabled.

## Tests

Unit tests:

- event updates capability counters.
- terminal capability events update reason counts.
- top slow Agent tracker respects limit.
- rolling latency window calculates p95.
- high-frequency trace events are sampled.
- memory snapshot rejects negative counts.
- query filters by agent/map/plan.
- no snapshot stores raw server objects.

Integration tests later:

- Plan Runtime emits objective events visible in plan snapshot.
- Capability Runtime emits terminal results visible in capability snapshot.
- Event Bus pressure visible in observability snapshot.
- 2000-Agent soak sample writes bounded JSONL/CSV.
- Agent Console can query one Agent and one map without full scans.

## Implementation Gates

Do not implement live integration until:

- Event Bus exists.
- reconstructed Agent runtime exposes stable snapshots.
- Capability Runtime emits terminal events.
- Plan Runtime emits objective events.
- server health read-only provider exists.
