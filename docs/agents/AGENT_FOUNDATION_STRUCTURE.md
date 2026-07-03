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
  navigation, swim/airborne steering, and fallback movement. Movement command
  distances and caps also live there while the remaining legacy
  movement/physics runtime still awaits reconstruction. Movement tick countdown
  helpers live in
  `server.agents.capabilities.movement.AgentMovementTimers`. Packet-visible
  movement broadcasts live in
  `server.agents.capabilities.movement.AgentMovementBroadcastService`.
  Movement reset and transient cleanup live in
  `server.agents.capabilities.movement.AgentMovementStateResetService`.
  Foothold index construction lives in
  `server.agents.capabilities.movement.AgentFootholdIndexService`.
  Walk-step, climb-step, jump-force, gravity, and jump/rope range kinematics
  live in `server.agents.capabilities.movement.AgentMovementKinematicsService`.
  Pose and stance side effects enter through
  `server.agents.capabilities.movement.AgentMovementPoseService`.
  Ground point and foothold lookup enters through
  `server.agents.capabilities.movement.AgentGroundingService`.
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
- `movement`
  Ground horizontal step decisions plus precise-navigation stop/drop-edge rules
  are split between pure `AgentGroundMovementPolicy` and stateful
  `AgentGroundMovementService`; the old bot movement methods now delegate.
  Navigation jump/rope reach probes
  route through `AgentJumpProbeService`; ground and rope jump initiation routes
  through `AgentJumpActionService`; movement phase dispatch routes through
  `AgentMovementPhaseDispatchService` while the underlying physics implementation and
  phase bodies remain later migration slices. Movement speed/jump profile
  refresh lives in `AgentMovementProfileService`, and movement timing/distance
  reads come from `AgentMovementPhysicsConfig`. Stuck recovery lives in
  `AgentMovementRecoveryService`. Swim runtime lives in
  `AgentSwimMovementService`; airborne runtime lives in
  `AgentAirborneMovementService`; climb runtime lives in
  `AgentClimbMovementService`; grind target shaping lives in
  `AgentGroundTargetService`; mob-avoidance lane scanning and landing checks
  live in `AgentMobAvoidanceService`; ground action planning/execution routes
  through `AgentGroundActionPlanner` and `AgentGroundActionExecutor`.
  `AgentGroundMovementRuntimeService` owns grounded tick orchestration.
  Dead bot-side grounded, climb, swim, and airborne phase-body clutter has been
  removed; remaining cleanup slices continue to remove temporary navigation and
  physics seams. BotNavigationManager now calls Agent movement services directly
  for movement side effects and reads navigation-facing movement thresholds
  from `AgentMovementPhysicsConfig`; climb-step reads route through
  `AgentMovementKinematicsService`; remaining references are navigation and
  physics runtime seams for later extraction. Packet-facing movement snapshot
  reads enter through `AgentMovementSnapshotService` and use
  `AgentMovementPacketSnapshot` so broadcast code no longer depends on the bot
  physics snapshot type. Continuous walking integration state is represented as
  `AgentGroundTravelState` while the old physics implementation continues to
  supply the same `physX`, `hspeed`, and carry-ms values. Queued down-jump and
  top-rope entry actions enter through `AgentQueuedMovementActionService`.
  Grounded physics entry points enter through `AgentGroundPhysicsService`, and
  movement timer countdowns enter through `AgentMotionTimerService`. Grounded
  collision and ledge queries enter through `AgentGroundCollisionService`.
  Rope, ladder, and jump-launch actions enter through
  `AgentRopeMovementService`. Combat-driven knockback movement enters through
  `AgentKnockbackMovementService`. Airborne and swim integration enter through
  `AgentAirbornePhysicsService` and `AgentSwimPhysicsService`.
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

