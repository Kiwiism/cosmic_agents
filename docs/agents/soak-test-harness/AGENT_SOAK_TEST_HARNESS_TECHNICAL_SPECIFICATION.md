# Agent Soak Test Harness Technical Specification

Purpose:

```text
Define interfaces, DTOs, command parsing, runner state, scenario manifests,
snapshot output, pass/fail evaluation, and tests for the future Agent Soak Test
Harness package.
```

Package name:

```text
agent-soak-test-harness
```

This specification is documentation only until Agent reconstruction is stable.

## Package Boundary

Agent Soak Test Harness owns:

- soak command handling.
- run lifecycle state.
- scenario runner.
- population preset loading for tests.
- spawn wave runner.
- snapshot collection.
- CSV/JSONL/JSON output.
- pass/fail evaluation.
- cleanup verification.

It does not own:

- normal Agent runtime behavior.
- Agent gameplay capability implementation.
- LLM/economy behavior.
- player 500-concurrency testing.
- production monitoring outside explicit soak mode.

## Suggested Layout

```text
agent-soak-test-harness/
  api/
    AgentSoakCommandHandler.java
    AgentSoakRunner.java
    AgentSoakScenario.java
    AgentSoakSnapshotCollector.java
    AgentSoakReportWriter.java
    AgentSoakPassFailEvaluator.java
  model/
    AgentSoakRunConfig.java
    AgentSoakRunState.java
    AgentSoakCommand.java
    AgentSoakScenarioManifest.java
    AgentSoakPopulationPreset.java
    AgentSoakSpawnWave.java
    AgentSoakSnapshot.java
    AgentSoakMetricRow.java
    AgentSoakFailure.java
    AgentSoakSummary.java
    AgentSoakSafetyLimits.java
  runtime/
    DefaultAgentSoakRunner.java
    AgentSoakRunStore.java
    AgentSoakWaveRunner.java
    AgentSoakScenarioRegistry.java
    AgentSoakOutputDirectory.java
  commands/
    SoakAgentsCommand.java
  reports/
    JsonlSoakSnapshotWriter.java
    CsvSoakMetricWriter.java
    JsonSoakSummaryWriter.java
```

## Core Runner

```java
public interface AgentSoakRunner {
    AgentSoakRunState prepare(AgentSoakRunConfig config);
    AgentSoakRunState start(String runId);
    AgentSoakRunState pause(String runId, String reason);
    AgentSoakRunState resume(String runId);
    AgentSoakRunState stop(String runId, String reason);
    AgentSoakSnapshot snapshot(String runId);
    AgentSoakSummary export(String runId);
    AgentSoakRunState cleanup(String runId);
}
```

## Run Config

```java
record AgentSoakRunConfig(
    String runId,
    String stage,
    String scenario,
    String preset,
    long seed,
    int targetAgentCount,
    long durationMs,
    long captureIntervalMs,
    AgentSoakSafetyLimits safetyLimits,
    Map<String, String> options
) {}
```

Run id format:

```text
agent-soak-YYYYMMDD-HHmm-<stage>-<scenario>
```

## Run State

```java
enum AgentSoakRunPhase {
    IDLE,
    PREPARING,
    SPAWNING,
    RUNNING,
    PAUSED,
    STOPPING,
    STOPPED,
    FAILED,
    COMPLETED
}
```

```java
record AgentSoakRunState(
    String runId,
    AgentSoakRunPhase phase,
    String stage,
    String scenario,
    int targetAgentCount,
    int spawnedAgentCount,
    int activeAgentCount,
    int failedAgentCount,
    int currentWave,
    long startedAtMs,
    long updatedAtMs,
    List<String> warnings,
    List<AgentSoakFailure> failures
) {}
```

## Command Grammar

```text
!soak agents prepare <stage> <scenario> [preset] [seed]
!soak agents start
!soak agents start <count> <scenario> [preset] [seed]
!soak agents pause [reason...]
!soak agents resume
!soak agents stop [reason...]
!soak agents status
!soak agents snapshot
!soak agents export
!soak agents cleanup
!soak agents materialize <mapid|all>
!soak agents dematerialize <mapid|all>
!soak agents checkpoint
!soak agents killrandom <percent>
!soak agents resetstuck
!soak agents wave next
!soak agents wave status
!soak agents failures
!soak agents top <slow|stuck|maps|db>
```

Command rules:

- require GM/admin permission.
- require soak mode enabled.
- reject mutating commands when another run is active unless command targets
  that run.
- return compact status to chat and full details to soak output files.
- never expose the command on production by default.

## Scenario Manifest

Portable schema:

- `docs/agents/soak-test-harness/agent-soak-scenario-manifest.schema.json`

```json
{
  "scenario": "hidden_simulation",
  "description": "Most Agents run without real player observers.",
  "requires": [
    "simulation-tier-runtime",
    "background-action-runtime",
    "observability"
  ],
  "durationMs": 28800000,
  "captureIntervalMs": 300000,
  "waves": [
    {
      "name": "safe-town-wave",
      "ratio": 0.2,
      "delayWindowMs": [0, 300000],
      "maxSpawnPerMinute": 100
    }
  ],
  "safetyLimits": {
    "maxDbWaitingSamples": 3,
    "maxMaterializationFailures": 0,
    "maxInvalidPositions": 0,
    "maxSchedulerDelayP99Ms": 5000
  }
}
```

## Population Preset

Portable schema and first preset:

- `docs/agents/soak-test-harness/agent-soak-population-preset.schema.json`
- `docs/agents/soak-test-harness/presets/victoria_lt30_living_world_v1.population-preset.json`

```json
{
  "preset": "victoria_lt30_living_world_v1",
  "schemaVersion": 1,
  "levelBands": [
    { "name": "maple-island-beginner", "ratio": 0.10 },
    { "name": "islander", "ratio": 0.15 },
    { "name": "fresh-victoria-8-15", "ratio": 0.20 },
    { "name": "early-first-job-15-22", "ratio": 0.35 },
    { "name": "late-first-job-22-30", "ratio": 0.20 }
  ],
  "jobs": {
    "warrior": 0.22,
    "magician": 0.22,
    "bowman": 0.18,
    "thief": 0.25,
    "pirate": 0.13
  },
  "archetypes": {
    "quester": 0.25,
    "grinder": 0.25,
    "farmer": 0.15,
    "casual": 0.15,
    "social-idler": 0.10,
    "merchant": 0.05,
    "islander": 0.05
  }
}
```

Preset validation:

- ratios must total 1.0 within tolerance.
- required maps must exist in catalog.
- starter inventory rules must be valid.
- plan ids must exist.
- map caps must not be impossible for target count.

## Snapshot Collector

```java
public interface AgentSoakSnapshotCollector {
    AgentSoakSnapshot collect(String runId, long nowMs);
}
```

Snapshot groups:

- server metrics.
- Agent metrics.
- simulation mode metrics.
- scheduler metrics.
- event bus metrics.
- persistence metrics.
- map metrics.
- gameplay validity metrics.
- failure counters.

Capture interval default:

```text
5 minutes
```

Short debug runs may use 10 to 60 seconds.

## Metric Row

```java
record AgentSoakMetricRow(
    String runId,
    long timestampMs,
    long uptimeMs,
    int onlinePlayers,
    int onlineAgents,
    int presentationAgents,
    int backgroundActiveAgents,
    int backgroundAbstractAgents,
    int strategicOfflineAgents,
    long heapUsedMb,
    long heapMaxMb,
    int loadedMaps,
    int dbActive,
    int dbIdle,
    int dbWaiting,
    long schedulerDelayP95Ms,
    long schedulerDelayP99Ms,
    long agentTickP95Ms,
    long materializationCount,
    long materializationFailureCount,
    long backgroundActionCommits,
    long abstractCombatRounds,
    long routeEtaUsage,
    long broadcastsSuppressed,
    long invalidPositionCount,
    long invalidQuestCompletionCount,
    long inventoryReconciliationFailureCount
) {}
```

## Pass/Fail Evaluator

```java
public interface AgentSoakPassFailEvaluator {
    List<AgentSoakFailure> evaluate(AgentSoakSnapshot snapshot, AgentSoakRunConfig config);
    AgentSoakSummary summarize(String runId, List<AgentSoakSnapshot> snapshots);
}
```

Failure levels:

- `INFO`
- `WARN`
- `FAIL`
- `ABORT`

Abort examples:

- repeated DB waiting above threshold.
- repeated materialization failures.
- invalid positions.
- stuck logged-in accounts after cleanup.
- real player responsiveness probe fails.
- scheduler queue grows without draining.
- heap grows continuously after warmup.

## Output Writers

Writers:

- `snapshots.jsonl`: complete periodic snapshots.
- `metrics.csv`: stable numeric columns.
- `events.jsonl`: run events and scenario actions.
- `summary.json`: latest/final rollup.
- `failures.json`: pass/fail evidence.

Portable summary schema:

- `docs/agents/soak-test-harness/agent-soak-summary.schema.json`

Writers must flush periodically so a crashed soak run still leaves useful
evidence.

## Cleanup

Cleanup must:

- stop scenario work.
- despawn or pause test Agents according to mode.
- flush checkpoints.
- drain or report dirty Agent count.
- verify no duplicate registrations.
- verify no stuck logged-in accounts caused by soak Agents.
- write final summary.

Cleanup should not delete evidence files.

## Tests

Unit tests:

- command grammar parses valid commands and rejects malformed commands.
- mutating commands require soak mode.
- run state transitions reject invalid jumps.
- preset ratio validation catches bad totals.
- scenario manifest validation catches missing requirements.
- pass/fail evaluator reports DB waiting threshold violations.
- pass/fail evaluator reports materialization failures.
- CSV writer keeps stable columns.
- JSON summary includes run id, stage, scenario, latest metrics, and failures.

Integration tests later:

- prepare/start/pause/resume/stop lifecycle works with fake Agent adapter.
- spawn waves honor target counts and map caps.
- hidden simulation scenario records background mode counts.
- materialization storm scenario records materialization counts.
- DB pressure scenario records save queue metrics.
- cleanup leaves no duplicate Agent registrations.

## Implementation Gates

Do not implement live integration until:

- reconstructed Agent runtime exposes spawn/despawn/control hooks.
- Agent Observability exposes metric snapshots.
- Simulation Tier Runtime exposes mode counts.
- Background Action Runtime exposes commit/savings counters.
- Server Adapter can identify test Agents and cleanup safely.
- soak mode enable/disable policy is defined.
