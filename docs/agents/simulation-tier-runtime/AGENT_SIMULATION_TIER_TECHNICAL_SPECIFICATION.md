# Agent Simulation Tier Runtime Technical Specification

Purpose:

```text
Define the interfaces, DTOs, policy flow, materialization rules, metrics, and
tests for the future Agent Simulation Tier Runtime package.
```

Package name:

```text
agent-simulation-tier-runtime
```

This specification is documentation only until the Agent reconstruction is
stable. It must not be wired into the current live Agent runtime yet.

Portable JSON contracts:

- `docs/agents/simulation-tier-runtime/simulation-tier-decision.schema.json`
- `docs/agents/simulation-tier-runtime/materialization-plan.schema.json`

Contract verifier:

- `tools/agent-contracts/Test-AgentScalingContracts.ps1`

## Package Boundary

Simulation Tier Runtime owns:

- simulation mode selection.
- map sensitivity classification.
- allowed background shortcut declaration.
- materialization policy.
- per-Agent mode transition auditing.
- cost budget hints for scheduler/capabilities.

It does not own:

- Agent intent or plan selection.
- direct movement, combat, loot, NPC, shop, or quest execution.
- server validation rules.
- profile/economy/LLM decisions.
- packet generation.
- catalog building.

## Suggested Layout

```text
agent-simulation-tier-runtime/
  api/
    SimulationTierService.java
    SimulationTierPolicy.java
    MapSensitivityClassifier.java
    MaterializationPlanner.java
    SimulationTierConfigProvider.java
  model/
    SimulationMode.java
    SimulationTierRequest.java
    SimulationTierDecision.java
    MapSensitivitySnapshot.java
    AgentSimulationSnapshot.java
    AllowedShortcutSet.java
    MaterializationRequest.java
    MaterializationPlan.java
    MaterializationResult.java
    SimulationCostBudget.java
  runtime/
    SimulationTierRuntime.java
    ModeTransitionJournal.java
    AgentModeStateStore.java
  audit/
    SimulationTierAuditEvent.java
    MaterializationAuditEvent.java
```

## Simulation Modes

```java
enum SimulationMode {
    PRESENTATION,
    BACKGROUND_ACTIVE,
    BACKGROUND_ABSTRACT,
    STRATEGIC_OFFLINE
}
```

Mode meanings:

- `PRESENTATION`: full visible behavior because at least one real player can
  observe the Agent map.
- `BACKGROUND_ACTIVE`: reduced cadence but strong shared-state consistency.
- `BACKGROUND_ABSTRACT`: route ETA, abstract combat, direct validated loot, and
  no visual packets when the map is safe to abstract.
- `STRATEGIC_OFFLINE`: coarse plan progress without continuous map character
  simulation. This is later-stage and not part of Maple Island MVP.

## Core Interface

```java
public interface SimulationTierService {
    SimulationTierDecision decide(SimulationTierRequest request);
}
```

## Request DTO

```java
record SimulationTierRequest(
    String agentId,
    int mapId,
    String currentPlanId,
    String currentObjectiveId,
    String currentCapability,
    AgentSimulationSnapshot agent,
    MapSensitivitySnapshot map,
    SimulationMode previousMode,
    long nowMs
) {}
```

## Decision DTO

```java
record SimulationTierDecision(
    SimulationMode mode,
    AllowedShortcutSet allowedShortcuts,
    SimulationCostBudget costBudget,
    boolean materializationRequired,
    List<String> reasons,
    List<String> warnings
) {}
```

Reason codes:

- `REAL_PLAYER_PRESENT`
- `MAP_PINNED_PRESENTATION`
- `SENSITIVE_EVENT_MAP`
- `SENSITIVE_PQ_MAP`
- `SENSITIVE_BOSS_MAP`
- `SENSITIVE_FM_OR_MERCHANT_MAP`
- `SAFE_TO_ABSTRACT`
- `CAPABILITY_REQUIRES_ACTIVE_STATE`
- `SERVER_LOAD_SHED`
- `MATERIALIZATION_REQUIRED`
- `STRATEGIC_OFFLINE_ALLOWED`

## Agent Snapshot

```java
record AgentSimulationSnapshot(
    boolean loggedIn,
    boolean alive,
    boolean inCombat,
    boolean trading,
    boolean inNpcScript,
    boolean inPartyQuest,
    boolean carryingVirtualLoot,
    boolean hasPendingVisibleEffects,
    int hp,
    int mp,
    Point position
) {}
```

## Map Sensitivity Snapshot

```java
record MapSensitivitySnapshot(
    int mapId,
    int realPlayerCount,
    int agentCount,
    boolean pinnedPresentation,
    boolean noAbstractByCatalog,
    boolean eventInstance,
    boolean partyQuest,
    boolean bossMap,
    boolean areaBossActive,
    boolean freeMarketOrMerchant,
    boolean scriptSensitive,
    boolean highValuePublicDrops,
    List<String> reasons
) {}
```

The classifier should use both live server state and catalog metadata.

## Allowed Shortcut Set

```java
record AllowedShortcutSet(
    boolean suppressVisualPackets,
    boolean routeEtaMovement,
    boolean sameMapEtaMovement,
    boolean abstractCombat,
    boolean directLootCredit,
    boolean virtualLootBuffer,
    boolean batchedInventoryReconcile,
    boolean directNpcQuestAction,
    boolean directShopAction,
    boolean deferredAgentSave
) {}
```

Rules:

- `PRESENTATION` allows none of the invisible-only shortcuts.
- `BACKGROUND_ACTIVE` allows reduced cadence and cosmetic suppression only
  when no real player can observe the map.
- `BACKGROUND_ABSTRACT` allows abstract movement/combat/loot after validation.
- `STRATEGIC_OFFLINE` allows coarse plan outcome simulation only when the plan
  and map explicitly permit it.

## Cost Budget

```java
record SimulationCostBudget(
    long nextTickDelayMs,
    int maxCapabilitySteps,
    int maxPerceptionRefreshes,
    int maxCombatRounds,
    int maxInventoryMutations,
    boolean allowExpensivePathfinding
) {}
```

Example budget intent:

- `PRESENTATION`: normal tick cadence.
- `BACKGROUND_ACTIVE`: slower perception/movement cadence.
- `BACKGROUND_ABSTRACT`: objective-slice cadence.
- `STRATEGIC_OFFLINE`: coarse periodic cadence.

## Mode Selection Algorithm

```text
if map.realPlayerCount > 0:
    mode = PRESENTATION
else if map.pinnedPresentation:
    mode = PRESENTATION
else if map.noAbstractByCatalog or map.eventInstance or map.partyQuest:
    mode = BACKGROUND_ACTIVE
else if map.bossMap or map.areaBossActive or map.freeMarketOrMerchant:
    mode = BACKGROUND_ACTIVE
else if current capability is safe to abstract:
    mode = BACKGROUND_ABSTRACT
else:
    mode = BACKGROUND_ACTIVE
```

Server load can only bias from `BACKGROUND_ACTIVE` to
`BACKGROUND_ABSTRACT` when:

- no real player is present.
- the map is not sensitive.
- the capability declares that abstraction is safe.
- materialization can be performed before visibility.

## Materialization Interface

```java
public interface MaterializationPlanner {
    MaterializationPlan plan(MaterializationRequest request);
    MaterializationResult apply(MaterializationPlan plan);
}
```

## Materialization Request

```java
record MaterializationRequest(
    String agentId,
    int mapId,
    SimulationMode fromMode,
    SimulationMode toMode,
    String reason,
    Point lastKnownPosition,
    Point virtualPosition,
    String currentCapability,
    List<VirtualStateMutation> pendingMutations
) {}
```

## Materialization Plan

```java
record MaterializationPlan(
    String agentId,
    int mapId,
    Point visiblePosition,
    Integer footholdId,
    List<VirtualStateMutation> mutationsToCommit,
    List<VirtualStateMutation> mutationsToDiscard,
    List<String> warnings
) {}
```

Materialization should:

1. pause the background action.
2. resolve pending virtual progress.
3. choose a valid foothold/materialization point.
4. validate HP/MP/inventory/quest/map state.
5. commit or discard virtual mutations.
6. switch to `PRESENTATION`.
7. resume capability execution.

## Virtual State Mutation Rules

Background paths may accumulate virtual mutations such as:

- route progress.
- expected mob kills.
- rolled loot.
- meso pickup.
- potion consumption.
- quest item count.
- objective progress.

Before commit:

- each mutation must be validated against server rules.
- inventory capacity must be checked.
- quest requirements must be checked.
- rare drops must use explicit roll evidence.
- mutation order must be deterministic and auditable.

## Capability Integration Contract

Capabilities should ask the tier decision before choosing execution path:

```text
command + tier decision -> presentation path or background path
```

Examples:

- Navigation:
  - `PRESENTATION`: normal movement.
  - `BACKGROUND_ABSTRACT`: route ETA movement.
- Combat:
  - `PRESENTATION`: visible combat path.
  - `BACKGROUND_ABSTRACT`: abstract combat rounds.
- Loot:
  - `PRESENTATION`: normal drops/pickup.
  - `BACKGROUND_ABSTRACT`: direct validated loot credit.
- NPC/Quest:
  - `PRESENTATION`: normal approach and interaction timing.
  - `BACKGROUND_ABSTRACT`: direct validated action after range/map check.

## Observability

Emit an event on:

- mode decision.
- mode transition.
- denied shortcut.
- materialization start.
- materialization commit.
- materialization discard.
- virtual mutation commit.
- policy violation.

Minimum metrics:

- agents per mode.
- mode transitions per minute.
- materialization count.
- materialization failures.
- shortcut savings estimates.
- abstract combat rounds.
- direct loot credits.
- deferred saves.
- maps pinned to presentation.
- maps blocked from abstraction.

## Safety Invariants

- Real-player same-map presence always forces `PRESENTATION`.
- Presentation mode must not use invisible-only shortcuts.
- Background shortcuts must never affect player-visible state before
  materialization.
- Server validation remains authoritative.
- The same virtual action must not be committed twice.
- Materialization must not place an Agent on invalid footholds.
- Failed materialization must fail closed into `BACKGROUND_ACTIVE` or paused
  recovery, not silent teleport/commit.

## Tests

Unit tests:

- real player present returns `PRESENTATION`.
- pinned presentation map returns `PRESENTATION`.
- PQ/event/boss/FM-sensitive maps avoid `BACKGROUND_ABSTRACT`.
- safe unobserved map returns `BACKGROUND_ABSTRACT`.
- unsupported capability returns `BACKGROUND_ACTIVE`.
- presentation decision disables invisible shortcuts.
- background abstract decision enables only declared safe shortcuts.
- server load cannot force abstraction on sensitive map.

Materialization tests:

- virtual route materializes on valid foothold.
- virtual loot is committed once.
- full inventory rejects direct loot commit.
- real player entering map triggers materialization.
- failed materialization emits warning and fails closed.

Integration tests later:

- 100 Agents in empty training maps use background mode.
- Agents switch to presentation when a player enters.
- no player receives visual packets from background-only action.
- strict debug mode compares presentation outcome against abstract outcome.
- soak reports mode distribution and estimated savings.

## Implementation Gates

Do not implement live integration until:

- reconstructed Agent runtime has stable scheduler/capability boundaries.
- Server Adapter can provide real-player map counts.
- Catalog Platform can mark sensitive/no-abstract maps.
- Observability can record mode and materialization events.
- background movement/combat/loot paths have their own package contracts.
