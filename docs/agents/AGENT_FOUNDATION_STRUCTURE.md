# Agent Foundation Structure

The clean Agent base is intentionally neutral. It preserves old behavior during reconstruction, but does not encode old bot architecture as the final design.

Target package groups:

- `server.agents.api`: stable public service/query/command surfaces.
- `server.agents.runtime`: sessions, lifecycle, registry, scheduler, snapshots.
  Current reconstruction runtime boundaries include lifecycle command wiring
  such as `AgentLifecycleChatCommandRuntime` and
  `AgentFormationCommandRuntime`, plus live tick context preparation through
  `AgentLiveTickContextRuntime` and gate dispatch through
  `AgentLiveTickGateRuntime` / `AgentLiveModeTickRuntime`, with tick-core
  composition through `AgentTickCoreRuntime` and script task composition
  through `AgentScriptTaskRuntime`. Grind-mode hook composition now enters
  through `AgentGrindModeRuntime`, and local opportunity attack result
  adaptation stays in `AgentLocalOpportunityAttackRuntime`. Inactive leader
  safety and town-return composition now enter through
  `AgentLeaderSafetyRuntime`, and chat route composition enters through
  `AgentChatRouteRuntime`. Formation defaults and state helpers enter through
  `AgentFormationRuntime`. Spawn and relogin registration callback composition
  now enters through `AgentSpawnRuntime` and `AgentReloginRuntime`; manual and
  spawned registration entry points enter through `AgentRegistrationRuntime`.
  Spawn result ownership is `AgentLifecycleService.AgentSpawnResult`.
  Public server-facing chat/spawn/relogin and registration entry points now
  enter through `AgentInteractionRuntime`.
  Follow-anchor and target-snapshot runtime wiring enters through
  `AgentTargetSnapshotRuntime`.
  Formation state, target snapshots, and movement-only stepping are now called
  directly by Agent tests/harnesses through `AgentFormationRuntime`,
  `AgentTargetSnapshotRuntime`, and `AgentMovementOnlyStepRuntime` instead of
  temporary manager helper methods.
  Tick leader/session lookup enters through `AgentLeaderSessionRuntime`.
  Dead-state tick hook wiring enters through `AgentDeathTickRuntime`.
  Ownerless movement-only tick preparation enters through
  `AgentMovementOnlyStepRuntime`, including default movement-only config
  assembly for Agent runtime entry points.
  Movement-core config-bound stepping enters through `AgentMovementTickRuntime`.
  Anchored-farm config-bound dispatch enters through `AgentAnchoredFarmRuntime`.
  Standalone move-target config-bound dispatch enters through
  `AgentStandaloneMoveTargetRuntime`.
  Tick-failure default hook wiring enters through `AgentTickFailureRuntime`.
  Inactive-leader town-return timeout ownership enters through
  `AgentLeaderSafetyRuntime`.
  The production `server.bots.BotManager` compatibility shell has been
  deleted; runtime/lifecycle entry points are Agent-owned.
- `server.agents.model`: identity, mode, profile, leader reference.
- `server.agents.commands`: command parsing/routing/result boundaries.
- `server.agents.plans`: objective and plan execution framework.
- `server.agents.capabilities`: domain-specific action bins.
  Movement profile physics baselines live in
  `server.agents.capabilities.movement.AgentMovementPhysicsConfig`, including
  movement tick duration plus rope-grab and snap/slope thresholds used by
  navigation and fallback movement. Movement command distances and caps also
  live there while the remaining legacy movement/physics runtime still awaits
  reconstruction. Movement tick countdown helpers live in
  `server.agents.capabilities.movement.AgentMovementTimers`.
- `server.agents.events`: event bus and listener interfaces.
- `server.agents.policy`: replaceable decision rules.
- `server.agents.profiles`: configurable behavior profiles.
- `server.agents.legacy`: exact legacy bot behavior adapters while reconstructing.
- `server.agents.integration`: server-agnostic gateways.
- `server.agents.integration.cosmic`: Cosmic-specific gateway implementations.

Capability bins:

- `movement`
- `navigation`
- `combat`
- `looting`
- `inventory`
  Inventory, passive loot, and trade tick entry ownership now starts at
  `AgentInventoryTickRuntime`; the old `BotInventoryManager` production file
  has been removed after migrating its behavior to Agent modules.
- `equipment`
  Production equipment callers enter through `AgentEquipmentService`; the
  remaining legacy optimizer implementation is isolated behind that Agent
  capability boundary for future extraction. Slot alias resolution, ring slot
  detection, DP slot ordering, and display labels live in
  `AgentEquipmentSlotResolver`; pure scoring helpers live in
  `AgentEquipmentScoringPolicy`; auto-equip duplicate-trigger state lives in
  `AgentAutoEquipThrottle`; auto-equip execution and debug branch reporting
  live in `AgentEquipmentAutoEquipService`; auto-equip debug dump formatting
  lives in `AgentEquipmentDebugReportFormatter`; owned/incoming reserve entry
  points route directly to `AgentEquipmentReservePolicy`; recommendation
  immediate/future eligibility rules live in `AgentEquipmentRecommendationPolicy`;
  recommendation filtering, result construction, recommended-item collection,
  and summary formatting live in `AgentEquipmentRecommendationService`;
  fixed-weapon DP solve/score/scan/debug branch helpers live in
  `AgentEquipmentOptimizer`;
  live equip-plan move execution and post-plan infeasible-equipment cleanup
  live in `AgentEquipmentPlanExecutor`;
  offered-item and recommendation optimizer orchestration lives in
  `AgentEquipmentOptimizationService`;
  optimizer result data lives in `AgentEquipmentOptimizerResult`;
  fixed-weapon DP result and score data live in `AgentEquipmentDpResult` and
  `AgentEquipmentScore`;
  optimizer stat snapshot data lives in `AgentEquipmentStatSnapshot`;
  optimizer metadata and requirement hooks live in
  `AgentEquipmentOptimizerHooks`;
  weapon-branch debug score breakdown data lives in
  `AgentWeaponScoreBreakdown`;
  unequip command execution lives in `AgentEquipmentUnequipService`.
  The production `server.bots.BotEquipManager` file has been deleted; historical
  equipment tests still carry old class/package names until test cleanup.
- `supplies`
- `trade`
- `shop`
- `quest`
- `npc`
- `party`
- `dialogue`
- `social`
- `build`

The intended flow is:

`AgentPlan` -> capability -> gateway -> Cosmic server write.

Events describe what happened after actions complete. Listeners may handle dialogue, metrics, plan progress, safety, or UI updates.

