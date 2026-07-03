# Bot To Agent Reconstruction Map

Status values:

- `MIGRATE_TO_AGENT`: move behavior into one Agent module.
- `SPLIT_TO_MULTIPLE_AGENT_MODULES`: split behavior across multiple bins.
- `COMPATIBILITY_ALIAS_TEMPORARY`: keep only as a temporary wrapper during migration.
- `DELETE_AFTER_MIGRATION`: remove once behavior/callers are migrated.
- `LEGACY_PROFILE`: preserve as legacy behavior profile until deliberately replaced.

This map tracks reconstruction from the source/master bot baseline into neutral Agent modules.

Recent map updates:

- Production callers of `server.bots.BotEquipManager` now call
  `server.agents.capabilities.equipment.AgentEquipmentService`. The old bot
  class remains the legacy implementation behind that Agent boundary until
  equipment optimizer internals are moved slice by slice.
- `AgentEquipmentService.isMageJob` and `isWeaponCompatible` now call
  `AgentWeaponCompatibilityPolicy` directly, removing those production
  compatibility reads from the legacy bot optimizer surface.
- Equipment slot alias resolution moved from `BotEquipManager.slotsFromName`
  to `AgentEquipmentSlotResolver`; the old method delegates to the Agent
  resolver until remaining tests/callers are migrated.
- Useful-stat scoring and expected damage after monster defense moved from
  `BotEquipManager` to `AgentEquipmentScoringPolicy`; legacy optimizer methods
  delegate to Agent scoring policy.
- Auto-equip throttle state moved from `BotEquipManager` to
  `AgentAutoEquipThrottle`; the legacy optimizer delegates to the Agent-owned
  throttle state.
- Owned and incoming equipment reserve service entry points now call
  `AgentEquipmentReservePolicy` directly through `AgentEquipmentService`,
  removing another production compatibility hop through `BotEquipManager`.
- Equipment recommendation immediate/future candidate eligibility moved to
  `AgentEquipmentRecommendationPolicy`; the legacy optimizer still calls the
  same rule while DP extraction remains a later equipment slice.
- Equipment unequip command execution moved to `AgentEquipmentUnequipService`;
  `AgentEquipmentService` now handles unequip requests without entering the
  temporary `BotEquipManager` shell.
- Equipment recommendation filtering, recommendation result construction,
  single-item recommendation checks, recommended-item collection, and
  recommendation summary formatting moved to `AgentEquipmentRecommendationService`.
  That service still calls `BotEquipManager.runOptimizerWithExtras` as the
  explicitly temporary DP optimizer seam.
- Auto-equip debug dump header/item-row/map-id formatting moved to
  `AgentEquipmentDebugReportFormatter`; `BotEquipManager.autoEquipDebug` still
  owns the optimizer branch walk until the DP/debug runtime is extracted.
- The equipment optimizer external result type moved to
  `AgentEquipmentOptimizerResult`; `BotEquipManager.runOptimizerWithExtras`
  remains the temporary DP execution seam but no longer exposes a bot-owned
  nested result record to Agent callers.
- `server.bots.BotInventoryManager` inventory/trade tick entry points moved to
  `server.agents.capabilities.inventory.AgentInventoryTickRuntime`.
  Production common ticks and legacy parity tests now call Agent inventory
  runtime directly; the old bot class has been deleted.
- BotManager runtime/lifecycle/tick milestone audit: production source no
  longer imports `server.bots.BotManager` or calls `BotManager.getInstance()`.
  Remaining BotManager methods are legacy compatibility delegates.
- Manual and spawned registration entry points moved to
  `AgentInteractionRuntime`; BotManager no longer owns the private tick
  callback used by registration.
- Relogin entry wiring moved to `AgentInteractionRuntime`; the Agent session
  lifecycle bridge no longer imports BotManager for relogin side effects.
- Server-facing chat and spawn entry points moved to
  `server.agents.runtime.AgentInteractionRuntime`. Packet handlers and
  `AgentSpawnCommandExecutor` now call the Agent runtime facade directly;
  BotManager remains a compatibility facade for legacy callers.
- Dead BotManager script-task and script-item compatibility wrappers were
  removed. Agent script services now own queueing, item drop execution,
  queued-task checks, and cheap move-target checks directly.
- BotManager movement command wrappers were removed. Spawn, lifecycle, tick,
  movement chat tests, and movement parity tests now use
  `AgentBotMovementCommandRuntime` directly.
- BotManager leader-safety helpers for town-offer, primary-entry, and
  inactive-leader safe mode were removed. Agent session control/runtime now
  owns those checks and side effects without a BotManager compatibility hop.
- BotManager's remaining formation, target snapshot, and movement-only test
  shims were removed. The simulation lab and BotManager parity tests now call
  `AgentFormationRuntime`, `AgentTargetSnapshotRuntime`, and
  `AgentMovementOnlyStepRuntime` directly.
- BotManager test-only tick harness helpers were removed. Test/perf harnesses
  now call the Agent tick/common/movement runtime classes directly.
- Chat-route default registry and formation config handoff moved from
  BotManager to `server.agents.runtime.AgentChatRouteRuntime`. BotManager now
  supplies only temporary recruit/transfer/dismiss callbacks for chat routing.
- Dead BotManager reply/sanitizer compatibility helpers were removed; Agent
  dialogue/reply runtime classes own those utilities directly.
- Dead BotManager grind/navigation/combat compatibility helper wrappers were
  removed. Tests now exercise the Agent runtime/capability classes directly
  for no-target grind movement and convenient loot targeting.
- Guarded production tick entry moved from BotManager to
  `server.agents.runtime.AgentTickRuntime`. BotManager now delegates production
  ticks through that Agent runtime entry with only temporary grind/follow
  callbacks.
- Tick-core default hook-bundle ownership moved from BotManager to
  `server.agents.runtime.AgentTickCoreRuntime`. BotManager now passes only the
  temporary grind/follow mode callbacks into the compact Agent tick-core entry.
- Script move-target default near-target radius handoff moved from BotManager
  and `AgentBotScriptMoveTargetRuntime` to
  `server.agents.plans.AgentScriptMoveTargetService`.
- Grind-mode default loot-radius handoff moved from BotManager to
  `server.agents.runtime.AgentGrindModeRuntime`. Tick-core wiring now calls
  grind mode without BotManager reading `LOOT_RADIUS`.
- Script-task tick default stop-distance handoff moved from BotManager to
  `server.agents.runtime.AgentScriptTaskRuntime`. BotManager now passes
  `AgentScriptTaskRuntime::tick` directly into common tick and tick-core
  wiring.
- BotManager tick-core callback wiring now uses direct Agent runtime method
  references for leader/session lookup, inactive leader safety, map transition
  grounding, ownerless move-target ticking, death ticks, target snapshots,
  movement core, and anchored farm dispatch. The former private forwarding
  wrappers were removed.
- Movement-only default config assembly moved from BotManager to
  `server.agents.runtime.AgentMovementOnlyStepRuntime`. BotManager now keeps
  only compatibility step methods while Agent runtime owns legacy tick,
  distance, teleport, and unstuck config handoff for movement-only stepping.
- Inactive-leader town-return timeout ownership moved from BotManager to
  `server.agents.runtime.AgentLeaderSafetyRuntime`. BotManager now delegates
  inactive leader handling without reading the legacy runtime config itself.
- Tick-failure default hook wiring moved from BotManager to
  `server.agents.runtime.AgentTickFailureRuntime`. BotManager now passes the
  Agent runtime failure handler directly into `AgentTickOrchestrator`.
- Standalone move-target config-bound tick entry moved from BotManager to
  `server.agents.runtime.AgentStandaloneMoveTargetRuntime`. BotManager now
  delegates ownerless move-target dispatch without assembling
  unstuck/stop-distance config.
- Anchored-farm config-bound tick entry moved from BotManager to
  `server.agents.runtime.AgentAnchoredFarmRuntime`. BotManager now delegates
  anchored-farm dispatch without assembling unstuck/stop-distance config.
- Movement-core config-bound entry moved from BotManager to
  `server.agents.runtime.AgentMovementTickRuntime`. BotManager now delegates
  movement-core stepping without assembling unstuck/stop-distance config.
- Ownerless movement-only tick preparation moved from BotManager to
  `server.agents.runtime.AgentMovementOnlyStepRuntime`. BotManager now passes
  only legacy movement config values while Agent runtime owns cadence,
  snapshot, leader-motion, and movement-only hook handoff.
- Dead-state tick hook wiring moved from BotManager to
  `server.agents.runtime.AgentDeathTickRuntime`. BotManager now delegates
  dead-tick handling while Agent runtime owns dead-state entry, respawn, and
  current-time hook construction over `AgentDeathTickService`.
- Tick leader/session lookup wiring moved from BotManager to
  `server.agents.runtime.AgentLeaderSessionRuntime`. BotManager now delegates
  tick-owner resolution while Agent runtime owns the Cosmic world/player lookup
  callback over `AgentLeaderSessionService`.
- Follow-anchor resolution and target snapshot hook wiring moved from
  BotManager to `server.agents.runtime.AgentTargetSnapshotRuntime`. BotManager
  now keeps only compatibility delegates while Agent runtime owns live sibling
  lookup, formation lookup, and follow target resolver wiring.
- BotManager's duplicate spawn result wrapper was removed. `spawnBotForOwner`
  now returns `AgentLifecycleService.AgentSpawnResult`, and command/party/
  messenger callers consume the Agent lifecycle result directly.
- BotManager's private registration branch moved to
  `server.agents.runtime.AgentRegistrationRuntime.registerManualAgent` and
  `registerSpawnedAgent`. BotManager now keeps only legacy public method names
  for compatibility while Agent runtime owns the normalization-mode choice.
- Spawn/relogin registration callback construction moved from BotManager to
  `server.agents.runtime.AgentSpawnRuntime` and
  `server.agents.runtime.AgentReloginRuntime`. BotManager now passes only its
  temporary tick callback while Agent runtime composes
  `AgentRegistrationRuntime.registerAgent` for spawned and relogged Agents.
- Lifecycle chat command wiring for recruit, transfer, and dismiss moved from
  BotManager to `server.agents.runtime.AgentLifecycleChatCommandRuntime`.
  BotManager now passes only temporary compatibility lifecycle actions while
  Agent runtime owns command-service hook construction and preserves the same
  legacy yellow-message replies.
- Formation command hook wiring moved from BotManager to
  `server.agents.runtime.AgentFormationCommandRuntime`. BotManager now passes
  only the active-entry lookup and legacy formation config values while Agent
  runtime owns state lookup/write, offset application, reply routing, and
  fallback leader help messages.
- Live tick context hook wiring moved from BotManager to
  `server.agents.runtime.AgentLiveTickContextRuntime`. BotManager now passes
  only temporary follow-anchor and target-snapshot callbacks while Agent
  runtime owns movement-profile refresh, observed leader motion, remembered
  leader position, map-change cleanup, and follow action-window cleanup.
- Live tick gate hook wiring moved from BotManager to
  `server.agents.runtime.AgentLiveTickGateRuntime`. BotManager now passes only
  temporary script-task, grind, and follow callbacks plus teleport config
  values while Agent runtime owns common tick, trade-window physics, idle,
  recovery, tracked map-change, and matching performance timing labels.
- Live mode hook wiring moved from BotManager to
  `server.agents.runtime.AgentLiveModeTickRuntime`. BotManager now passes only
  temporary local-attack, movement-core, anchored-farm, and grind callbacks
  while Agent runtime owns shop visit, follow opportunity, follow idle,
  scripted move combat, anchored farm, grind dispatch, final movement tail, and
  matching performance timing labels.
- Tick-core hook wiring moved from BotManager to
  `server.agents.runtime.AgentTickCoreRuntime`. BotManager now passes only
  temporary leader/dead/script/mode callbacks while Agent runtime owns
  preflight, ownerless dispatch, live context, live gates, live modes, timing,
  and movement/recovery config handoff.
- Script task tick callback wiring moved from BotManager to
  `server.agents.runtime.AgentScriptTaskRuntime`. BotManager now passes only
  the legacy stop-distance value while Agent runtime owns start/completion
  callback composition over the existing script task services.
- Grind-mode hook wiring moved from BotManager to
  `server.agents.runtime.AgentGrindModeRuntime`. BotManager now passes only
  the movement-core callback and legacy loot-radius value while Agent runtime
  owns target search, no-target fallback, target commitment, ranged engagement,
  navigation tail, and combat/navigation side-effect hook composition.
- Local opportunity attack result adaptation moved from BotManager to
  `server.agents.runtime.AgentLocalOpportunityAttackRuntime`. BotManager now
  passes the Agent runtime method directly into tick-core wiring.
- Inactive leader safety and town-return hook wiring moved from BotManager to
  `server.agents.runtime.AgentLeaderSafetyRuntime`. BotManager now delegates
  inactive-leader tick handling and explicit away safe-mode requests while
  Agent runtime owns active-return cleanup, town scroll fallback, cluster
  positioning, movement reset, mode cleanup, script cleanup, and shop cleanup
  composition.
- Chat route hook wiring moved from BotManager to
  `server.agents.runtime.AgentChatRouteRuntime`. BotManager now passes only
  lifecycle callbacks, formation defaults, and the active-entry map while Agent
  runtime owns pending-offer, recruit, transfer, formation, dismiss, targeted,
  and untargeted chat routing hook construction.
- Formation state helper wiring moved from BotManager to
  `server.agents.runtime.AgentFormationRuntime`. BotManager now delegates
  default formation creation, state lookup, and state update/offset application
  for compatibility harness callers.
- The temporary BotManager grind-mode adapter method was removed; tick-core
  wiring now passes `AgentGrindModeRuntime.tickGrindMode` directly.
- Anchored farm hook wiring moved from BotManager to
  `server.agents.runtime.AgentAnchoredFarmRuntime`. BotManager now passes only
  legacy movement config values while Agent runtime owns local-opportunity,
  idle, ground-idle, broadcast, and movement-core hook construction.
- Local-opportunity attack hook wiring moved from BotManager to
  `server.agents.runtime.AgentLocalOpportunityAttackRuntime`. BotManager keeps
  only a temporary result adapter while Agent runtime owns grind-navigation,
  jump-height, jump-initiation, and local move-window hook construction.
- Common tick hook wiring moved from BotManager to
  `server.agents.runtime.AgentCommonTickRuntime`. BotManager now passes only
  the temporary script-task callback while Agent runtime owns combat damage,
  death-state, monster-control, loot, supplies, build, AFK, trade, PQ,
  action-lock, skill-cache, heal, buff, and buff-pot hook construction.
- Standalone move-target hook wiring moved from BotManager to
  `server.agents.runtime.AgentStandaloneMoveTargetRuntime`. BotManager now
  delegates ownerless move-target ticking while Agent runtime owns map-change,
  profile refresh, and movement-core hook construction.
- Movement-only tick hook wiring moved from BotManager to
  `server.agents.runtime.AgentMovementOnlyRuntime`. BotManager now passes only
  follow-anchor resolution and legacy movement distance/config values while
  Agent runtime owns idle, shop, follow-sync, recovery, map-change, follow-idle,
  and movement-core hook construction.
- Movement-core hook wiring moved from BotManager to
  `server.agents.runtime.AgentMovementTickRuntime`. BotManager now passes only
  the legacy unstuck flag and stop distance while Agent runtime owns navigation,
  fidget, phase, committed-edge, stuck-detection, and arrival-cleanup hooks.
- Movement-only map-change hook wiring moved from BotManager to
  `server.agents.runtime.AgentMovementOnlyMapChangeRuntime`. BotManager now
  delegates the movement-only map-change side-effect bundle while Agent runtime
  owns grounding, teleport, reset, broadcast, shop, and status hooks.
- Map transition hook wiring moved from BotManager to
  `server.agents.runtime.AgentMapTransitionRuntime`. BotManager now passes
  only its temporary grind/follow mode callbacks while Agent runtime owns
  grounding, graph warmup, PQ reset, shop map-change, and status hooks.
- Recovery teleport hook wiring moved from BotManager to
  `server.agents.runtime.AgentRecoveryTeleportRuntime`. BotManager now passes
  only the legacy distance thresholds while Agent runtime owns ground lookup,
  physics teleport, post-teleport reset, and movement broadcast hooks.
- Follow map-sync hook wiring moved from BotManager to
  `server.agents.runtime.AgentFollowMapSyncRuntime`. BotManager now delegates
  cross-map follow synchronization while Agent runtime owns the temporary
  ground, map-change, idle, and movement-reset hook construction.
- Idle/trade physics hook wiring moved from BotManager to
  `server.agents.runtime.AgentIdlePhysicsRuntime`. BotManager now delegates
  physics-only and idle-entry ticks while Agent runtime owns the temporary
  swim/air/stance/ground-idle/broadcast hook construction.
- Attack-lock physics hook wiring moved from BotManager to
  `server.agents.runtime.AgentActionLockPhysicsRuntime`. BotManager now
  delegates locked movement dispatch while Agent runtime owns the temporary
  swim/air/ground BotMovementManager hook construction.
- Movement phase hook wiring moved from BotManager to
  `server.agents.runtime.AgentMovementPhaseRuntime`. BotManager now delegates
  climb/swim/air/ground phase dispatch while Agent runtime owns the temporary
  BotMovementManager hook construction.
- Movement stuck-detection hook wiring moved from BotManager to
  `server.agents.runtime.AgentStuckDetectionRuntime`. BotManager now passes
  only the legacy unstuck-enable flag while Agent runtime owns tick-down,
  unstuck action, and movement tick duration hook construction.
- Death respawn hook wiring moved from BotManager to
  `server.agents.runtime.AgentRespawnRuntime`. BotManager now delegates the
  respawn side-effect bundle while Agent runtime owns cross-map leader portal
  selection, ground resolution, physics teleport, movement reset/broadcast,
  and the legacy "back!" map reply hook construction.
- Tick preflight hook wiring moved from BotManager to
  `server.agents.runtime.AgentTickPreflightRuntime`. Agent runtime now owns
  airshow, skip-delay, removed-Agent cleanup, heartbeat, pending-offer expiry,
  AI cadence, movement tick, AI tick, and heartbeat interval hook construction.
- Tick failure side-effect wiring moved from BotManager to
  `server.agents.runtime.AgentTickFailureRuntime`. BotManager now passes only
  the temporary stop-mode callback and logger while Agent runtime owns movement
  reset, runtime removal, forced-idle reply, missing-entry logging, warning/
  error log formatting, and failure escalation hook wiring.
- Spawn hook wiring moved from BotManager to
  `server.agents.runtime.AgentSpawnRuntime`. BotManager now passes only its
  temporary tick callback, follow-start callback, and logger while Agent
  runtime owns spawned-registration callback construction, ownership
  resolution, spawn position, offline load delegation, online placement,
  cross-map force-change, and failure warning wiring.
- Relogin hook wiring moved from BotManager to
  `server.agents.runtime.AgentReloginRuntime`. BotManager now passes only its
  temporary tick callback and logger while Agent runtime owns
  spawned-registration callback construction, leader lookup, spawn position,
  offline load delegation, delayed scheduling, map announcement, and failure
  warning wiring.
- Transfer lifecycle hook wiring moved from BotManager to
  `server.agents.runtime.AgentTransferRuntime`. BotManager now passes only the
  temporary stop-mode and registration callbacks while Agent runtime owns
  mutable entry lookup, target lookup, authorization, cancellation, delayed
  greeting scheduling, reply delivery, and legacy greeting selection.
- Recruit lifecycle hook wiring moved from BotManager to
  `server.agents.runtime.AgentRecruitRuntime`. BotManager now passes only the
  temporary registration callback while Agent runtime owns ownerless online
  lookup and control authorization hook wiring.
- Dismiss lifecycle hook wiring moved from BotManager to
  `server.agents.runtime.AgentDismissRuntime`. BotManager now passes only the
  temporary stop-mode callback while Agent runtime owns cancellation, delayed
  farewell scheduling, reply delivery, and legacy farewell selection.
- Agent registration hook wiring moved from BotManager to
  `server.agents.runtime.AgentRegistrationRuntime`. BotManager now passes only
  its temporary tick callback while Agent runtime owns timer, cancellation,
  default formation, spawn normalization, and status-delay hook wiring.
- Offline Agent load hook wiring moved from BotManager to
  `server.agents.runtime.AgentOfflineLoadRuntime`. BotManager now keeps only a
  compatibility `loadOfflineBot` delegate while Agent runtime owns the Cosmic
  bootstrap hook bundle for loading offline backing characters.
- Spawn placement hook wiring moved from BotManager to
  `server.agents.runtime.AgentSpawnPlacementRuntime`. BotManager now references
  Agent runtime placement entry points from spawn/register hooks, while Agent
  runtime owns the legacy placement hook bundle until movement/physics are
  reconstructed. `BotMovementManager.buildFhIndex` is temporarily public only
  for this Agent runtime hook seam.
- Spawn SQL failure handling moved from BotManager to
  `server.agents.runtime.AgentLifecycleService.spawnAgentForLeaderQuietly`.
  BotManager now keeps the compatibility `spawnBotForOwner` result wrapper and
  temporary hook construction, while Agent lifecycle owns SQL failure capture
  and the legacy failure text.
- Relogin SQL failure handling moved from BotManager to
  `server.agents.runtime.AgentLifecycleService.reloginAgentQuietly`. BotManager
  now keeps only the compatibility `reloginBot` entry point and temporary hook
  construction while Agent lifecycle owns the try/catch and false return.
- Leader-scoped Agent removal map wiring moved from BotManager to
  `server.agents.runtime.AgentRuntimeCleanupService.removeAgentsForLeader`.
  BotManager now keeps only the compatibility `removeBot(int)` entry point
  while Agent runtime owns the live-registry, formation, town-anchor, and
  scheduled-tick cleanup wiring.
- Scheduled tick cancellation for lifecycle removal moved from BotManager to
  `server.agents.runtime.AgentLifecycleService.cancelScheduledTickIfPresent`.
  BotManager now delegates the remove-leader callback to Agent lifecycle while
  preserving the same scheduled-task presence check and non-interrupting
  cancellation.
- Pending-offer chat response hook wiring moved from BotManager to
  `server.agents.capabilities.trade.AgentPendingOfferChatRouteService`.
  BotManager now supplies only the temporary live-entry-group source while
  Agent trade owns expiry, target validation, targeted-command resolution,
  response handling, and speaker feedback wiring.
- Top-level chat ingress ordering moved from BotManager to
  `server.agents.capabilities.dialogue.AgentChatIngressService`. BotManager now
  supplies temporary hooks for pending-offer response routing, recruit/
  transfer/formation/dismiss command routing, active-entry lookup, targeted
  routing, and untargeted routing while Agent dialogue owns the chat ingress
  early-return order.
- Untargeted chat routing moved from BotManager to
  `server.agents.capabilities.dialogue.AgentUntargetedChatRouteService`.
  BotManager now supplies temporary hooks for follow-target command
  application, group-supply classification/responder selection, reply-channel
  state, Agent chat dispatch, typo suggestions, and reply queueing while Agent
  dialogue owns the untargeted route ordering.
- Targeted name-prefix chat routing moved from BotManager to
  `server.agents.capabilities.dialogue.AgentTargetedChatRouteService`.
  BotManager now supplies temporary hooks for targeted-command resolution,
  follow-target command application, reply-channel state, typo suggestions,
  Agent chat dispatch, command activity recording, LLM fallback, and leader
  feedback while Agent dialogue owns the targeted route ordering.
- Pending loot-offer target validation moved from BotManager to
  `server.agents.capabilities.trade.AgentPendingOfferResponseService`.
  BotManager now supplies only the temporary route-entry hook while Agent trade
  owns the pending-offer, recipient, and same-map checks.
- Follow-target candidate assembly moved from BotManager to
  `server.agents.runtime.AgentFollowTargetCandidateService`. BotManager now
  supplies only a temporary sibling-entry lookup hook while Agent runtime owns
  the leader/party/sibling ordering and duplicate filtering.
- Follow-target command application moved from BotManager to
  `server.agents.runtime.AgentFollowTargetCommandService`. BotManager now
  supplies temporary hooks for target resolution, reply queueing, delay
  scheduling, auto-equip, potion-share checks, and follow-mode entry while
  Agent runtime owns the per-entry command application order.
- Follow-target name resolution moved from BotManager to
  `server.agents.runtime.AgentFollowTargetResolutionService`. BotManager now
  supplies temporary candidate-list assembly and follow-mode application while
  Agent runtime owns the exact/prefix/ambiguous/missing-target rules.
- Transfer chat command routing moved from BotManager to
  `server.agents.runtime.AgentTransferCommandService`. BotManager now supplies
  temporary hooks for transfer lifecycle delegation and leader yellow-message
  delivery while Agent runtime owns the command routing response behavior.
- Recruit chat command parsing moved from BotManager to
  `server.agents.runtime.AgentRecruitCommandService`. BotManager now supplies
  temporary hooks for ownerless-Agent recruitment and leader yellow-message
  delivery while the Agent runtime owns the legacy aliases and response text.
- Dismiss chat command parsing moved from BotManager to
  `server.agents.runtime.AgentDismissCommandService`. BotManager now supplies
  temporary hooks for dismiss lifecycle delegation and leader yellow-message
  delivery while the Agent runtime owns the legacy aliases and response text.
- Formation chat command parsing and formation-state mutation moved from
  BotManager to `server.agents.runtime.AgentFormationCommandService`.
  BotManager now supplies temporary hooks for active entries, stored formation
  state, offset application, first-Agent replies, and leader yellow messages.
- Group supply responder selection moved from BotManager to
  `server.agents.capabilities.supplies.AgentGroupSupplyResponderSelector`.
  BotManager now delegates the same same-map preference and first-entry
  fallback used before forwarding group supply chat to a single Agent.
- Pending loot-offer response routing moved from BotManager to
  `server.agents.capabilities.trade.AgentPendingOfferResponseService`.
  BotManager now supplies temporary hooks for offer expiry, target validation,
  targeted-command resolution, offer-response handling, and speaker feedback.
- Ownerless Agent recruit lifecycle moved from BotManager to
  `server.agents.runtime.AgentRecruitService`. BotManager now supplies
  temporary hooks for unclaimed online Agent lookup, control authorization, and
  registration under the current leader.
- Agent transfer between leaders moved from BotManager to
  `server.agents.runtime.AgentTransferService`. BotManager now supplies
  temporary hooks for current-entry lookup, same-map target lookup,
  authorization, scheduled-task cancel, stop-mode entry, re-registration,
  delayed greeting scheduling, and greeting delivery.
- Offline Agent loading moved from BotManager to
  `server.agents.runtime.AgentOfflineLoadService`. BotManager now supplies
  temporary hooks for BotClient creation, character DB loading, disease restore,
  map resolution, spawn-position resolution, rate initialization, channel/world
  registration, and map registration.
- Spawn placement and normalization runtime reset ordering moved from
  BotManager to `server.agents.runtime.AgentSpawnPlacementService`. BotManager
  now supplies temporary hooks for spawn-position resolution, physics
  teleport, movement reset, death-state clearing, map tracking, navigation
  warmup, tick cadence reset, movement broadcast invalidation, movement
  broadcast, party HP update, and leader-party join.
- Top-level tick-core sequencing moved from BotManager to
  `server.agents.runtime.AgentTickCoreService`. BotManager now supplies
  temporary hooks for preflight, leader resolution, inactive-leader handling,
  ownerless fallback, dead-state handling, live context setup, gate dispatch,
  and live-mode dispatch while Agent runtime owns the tick-core order.
- Live tick gate ordering moved from BotManager to
  `server.agents.runtime.AgentLiveTickGateService`. BotManager now supplies
  temporary hooks for common tick systems, trade-window physics, idle-mode
  physics, recovery, and tracked map-change handling while Agent runtime owns
  the short-circuit order before live-mode dispatch.
- Live-mode tick ordering moved from BotManager to
  `server.agents.runtime.AgentLiveModeTickService`. BotManager now supplies
  temporary hooks for shop-visit, follow-opportunity, follow-idle fast path,
  scripted move combat, anchored farm, grind dispatch, and final movement tail
  execution while Agent runtime owns the dispatch order and target propagation.
- Live tick context setup moved from BotManager to
  `server.agents.runtime.AgentLiveTickContextService`. BotManager now supplies
  temporary hooks for movement-profile refresh, follow-anchor resolution,
  target snapshot capture, leader motion tracking, map-change cleanup, and
  follow action-window cleanup while Agent runtime owns the setup order.
- Movement-only map-change handling moved from BotManager to
  `server.agents.runtime.AgentMovementOnlyMapChangeService`. BotManager now
  supplies temporary foothold, grounding, teleport, reset, broadcast, shop, and
  status hooks.
- Grind-mode dispatch moved from BotManager to
  `server.agents.runtime.AgentGrindModeDispatchService`. BotManager now supplies
  only the temporary grind tick hook and performance timing; the grind decision
  pipeline itself remains in `AgentGrindModeTickService`.
- Final movement-tail dispatch moved from BotManager to
  `server.agents.runtime.AgentFinalMovementTailService`. BotManager now
  supplies only the temporary movement-core hook and performance timing.
- Tracked map-change tick dispatch moved from BotManager to
  `server.agents.runtime.AgentTrackedMapChangeTickService`. BotManager now
  supplies the temporary tracked-map-change handler and performance timing.
- Follow map-sync and teleport recovery dispatch moved from BotManager to
  `server.agents.runtime.AgentRecoveryTickService`. BotManager now supplies
  temporary hooks for follow map sync, grind-party recovery teleport, and
  target-distance recovery teleport.
- Anchored-farm mode dispatch moved from BotManager to
  `server.agents.runtime.AgentAnchoredFarmModeTickService`. BotManager now
  supplies only the temporary anchored-farm tick hook and performance timing.
- Scripted move local-combat tick dispatch moved from BotManager to
  `server.agents.runtime.AgentScriptedMoveCombatTickService`. BotManager now
  supplies temporary hooks for action-window cleanup, local-opportunity attack,
  movement-core stepping, and performance timing.
- Follow-mode local opportunity attack dispatch moved from BotManager to
  `server.agents.runtime.AgentFollowOpportunityTickService`. BotManager now
  supplies the temporary local-opportunity attack hook and performance timing.
- Idle-mode consumed-tick dispatch moved from BotManager to
  `server.agents.runtime.AgentIdleModeTickService`. BotManager now supplies the
  temporary idle physics/mode hook and performance timing.
- Shop-visit tick dispatch moved from BotManager to
  `server.agents.runtime.AgentShopVisitTickService`. BotManager now supplies
  temporary hooks for the existing shop visit tick body and movement-core
  stepping while Agent runtime owns the pending/delay/target consumed-tick flow.
- Trade-window tick dispatch moved from BotManager to
  `server.agents.runtime.AgentTradeWindowTickService`. BotManager now supplies
  only the temporary physics-only tick hook while the Agent runtime owns the
  trade-open consumed-tick decision.
- Tick preflight sequencing moved from BotManager to
  `server.agents.runtime.AgentTickPreflightService`. BotManager now supplies
  temporary hooks for airshow skip, skip-delay consumption, removed-map cleanup,
  heartbeat, pending-offer expiry, and AI cadence preparation.
- Inactive leader tick gating moved from BotManager to
  `server.agents.runtime.AgentLeaderSafetyService.handleInactiveLeaderTick`.
  BotManager now supplies temporary hooks for active-leader return cleanup,
  town-warp eligibility, and inactive safe-mode entry side effects.
- Standalone move-target tick sequencing moved from BotManager to
  `server.agents.runtime.AgentStandaloneMoveTargetTickService`. BotManager now
  supplies temporary hooks for map-change grounding, movement-profile refresh,
  and movement-core stepping.
- Grind-mode tick pipeline orchestration moved from BotManager to
  `server.agents.capabilities.combat.AgentGrindModeTickService`. BotManager
  now remains a compatibility adapter that supplies temporary search, fallback,
  commitment, ranged-engagement, and navigation-tail hook bundles.
- Tick failure escalation and volatile-action cleanup moved from BotManager to
  `server.agents.runtime.AgentTickFailurePolicy.handleFailure`; BotManager now
  supplies compatibility hooks for logging, movement reset, removal, and idle
  reply side effects.
- Scheduled tick guard orchestration moved from BotManager to
  `server.agents.runtime.AgentTickOrchestrator.runGuardedTick`; BotManager
  remains the temporary hook provider for `tickCore` and failure handling.
- Common tick system ordering moved from BotManager to
  `server.agents.runtime.AgentCommonTickService`; BotManager now supplies a
  compatibility hook bundle for existing subsystem implementations.
- Dismiss-by-name lifecycle moved from BotManager to
  `server.agents.runtime.AgentLifecycleService.dismissAgentByName`. BotManager
  remains the compatibility wrapper for stop-mode, scheduler, and reply hooks.
- Monster-control release moved from BotManager to
  `server.agents.runtime.AgentMonsterControlService`; common tick now delegates
  to the Agent-owned service.
- Death respawn recovery moved from BotManager to
  `server.agents.runtime.AgentDeathTickService.respawnNearLeader`. BotManager
  remains the temporary hook provider for portal selection, physics, broadcast,
  and map chat side effects.
- Offline Agent relogin moved from BotManager to
  `server.agents.runtime.AgentLifecycleService.reloginAgent`. BotManager
  remains the temporary compatibility wrapper for server lookup, DB loading,
  registration hooks, delayed scheduling, and map chat.
- Live Agent registration moved from BotManager to
  `server.agents.runtime.AgentLifecycleService.registerAgent`. BotManager's
  register APIs remain temporary compatibility wrappers around the Agent-owned
  registration sequence.
- Spawn lifecycle branching moved from BotManager to
  `server.agents.runtime.AgentLifecycleService.spawnAgentForLeader`. BotManager
  remains the compatibility facade and temporary side-effect provider for
  loading, registering, placing, map changing, and starting follow mode.
- Active Agent lookup helpers moved to live-store methods on
  `server.agents.runtime.AgentRuntimeRegistry`. BotManager and
  `AgentBotSessionLifecycleSideEffects` remain temporary compatibility
  delegates for older callers, while lookup ownership sits in Agent runtime.
- Whisper-to-Agent command ingress moved from BotManager to
  `server.agents.capabilities.dialogue.AgentWhisperCommandService`.
  `WhisperHandler` now calls the Agent dialogue service directly, while
  BotManager keeps a temporary compatibility delegate.
- Runtime cleanup/removal by Agent character ID moved to
  `server.agents.runtime.AgentRuntimeCleanupService`. Character/Client
  disconnect paths and the delete-character command now call Agent runtime
  directly, while BotManager keeps temporary compatibility delegates.
- BotManager inactive-leader town-cluster anchor storage moved to
  `server.agents.runtime.AgentLeaderSafetyService`. BotManager now references
  the Agent-owned map while lifecycle cleanup and return clustering keep the
  same behavior.
- Bot autopot/potion retry request routing moved from BotManager to
  `server.agents.capabilities.supplies.AgentPotionCheckRequestService`.
  Character and pet-autopot paths call the Agent capability directly, while
  BotManager keeps a temporary compatibility delegate.
- Scroll reaction notification scheduling moved from BotManager to
  `server.agents.capabilities.social.AgentScrollReactionNotificationService`.
  Scroll packet handlers call the Agent capability directly, while BotManager
  keeps a temporary compatibility delegate.
- Owner equip pickup/trade notification behavior moved from BotManager to
  `server.agents.capabilities.trade.AgentOwnerItemNotificationService`.
  `Shop`, `Trade`, and field item pickup now call the Agent capability
  directly, and BotManager keeps temporary compatibility delegates for older
  callers.
- Party Agent quest mirroring moved from BotManager to
  `server.agents.capabilities.quest.AgentPartyQuestSyncService`. `Quest` and
  `Character` now call the Agent capability directly for start/complete/progress
  sync, while BotManager keeps temporary compatibility delegates.
- Active Agent count/list access for the bot equip window moved to
  `server.agents.runtime.AgentRuntimeRegistry`. `BotEquipHandler` no longer
  imports BotManager for those lookups, while BotManager keeps compatibility
  accessors for older callers.
- BotManager bot-only autopot cleanup moved to
  `server.agents.runtime.AgentAutopotRuntimeCleanupService`. BotManager
  `cleanupBotRuntimeState` remains the temporary lifecycle wrapper that removes
  the entry and then calls the Agent-owned cleanup helper.
- BotManager spawn-position resolution moved to
  `server.agents.runtime.AgentSpawnPositionService`. BotManager keeps
  `resolveSpawnPosition` as a compatibility delegate while offline load,
  online placement, and spawn normalization continue to use the same
  BotPhysicsEngine ground lookup through the Agent runtime service.
- BotManager post-spawn party lifecycle side effects moved to
  `server.agents.runtime.AgentPartyLifecycleService`. BotManager keeps
  `joinBotToOwnerParty` as a temporary compatibility delegate, and Agent spawn
  command plus Messenger respawn paths now use the Agent runtime service
  directly.
- BotManager inactive-leader town-warp eligibility moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager still owns the
  temporary offline/dead leader side effects, return-scroll execution, and town
  cluster target wiring.
- BotManager inactive-leader idle preparation sequence moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager only supplies
  temporary callbacks for script-task clearing, shop cancellation, and mode
  clearing.
- BotManager active-leader return cleanup and single-representative welcome-back
  rule moved to `server.agents.runtime.AgentLeaderSafetyService`; BotManager
  only supplies temporary callbacks for move-target clearing, town-cluster anchor
  removal, and party-visible return announcement.
- BotManager inactive-leader timer gate moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager still performs
  the temporary safe-mode entry side effects once the Agent-owned gate says the
  delay has elapsed.
- BotManager non-town inactive safe-mode idle sequence moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager only supplies
  temporary physics and movement-broadcast callbacks.
- BotManager inactive-town cluster target calculation moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager only supplies
  temporary leader-entry snapshots, formation state, edge-inset config, and
  ground-point lookup.
- BotManager inactive-town return completion state sequencing moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager only supplies
  temporary movement reset and precise move-start callbacks.
- BotManager inactive safe-mode entry branching moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager only supplies
  temporary prepare, town-scroll, and in-place idle callbacks.
- BotManager inactive town-scroll orchestration moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager only supplies
  temporary Cosmic callbacks for current map, return-scroll use, map changing,
  map-change grounding, town-cluster anchor storage, target resolution, movement
  reset, and precise move start.
- BotManager first-entry/representative lookup rule moved to
  `server.agents.runtime.AgentRuntimeRegistry`; BotManager only supplies the
  temporary runtime map.
- BotManager leader away-safe-mode entry loop moved to
  `server.agents.runtime.AgentLeaderSafetyService`; BotManager only supplies
  temporary entry snapshots, map-presence checks, town eligibility, and
  safe-mode entry callbacks.
- BotManager script task completion rules moved to
  `server.agents.runtime.AgentScriptTaskCompletionService`; BotManager only
  supplies temporary follow-target resolution and movement distance config.
- BotManager script task start dispatch moved to
  `server.agents.runtime.AgentScriptTaskStartService`; BotManager only supplies
  temporary callbacks for move, follow, grind, stop, and drop side effects.
- BotManager script task tick loop moved to
  `server.agents.runtime.AgentScriptTaskTickService`; BotManager only supplies
  temporary callbacks for task start and completion checks.
- BotManager script task queue helpers moved to
  `server.agents.runtime.AgentScriptTaskQueueService`; BotManager remains a
  temporary compatibility facade for clear, queue, move, move-then-drop,
  follow-then-drop, and has-queued checks.
- Agent script context and runner task queue operations now call
  `server.agents.runtime.AgentScriptTaskQueueService` directly instead of
  routing queue/clear checks through BotManager; BotManager remains only for the
  temporary cheap-move helper used by script context.
- Agent script context no longer stores `BotManager`; it receives a narrow
  cheap-move callback and drop-item callback from `AgentScriptRunner`, reducing
  the script subsystem's direct BotManager dependency while preserving the same
  cheap-move and drop side-effect results.
- BotManager local near-distance helper moved to
  `server.agents.runtime.AgentPositionService`; BotManager movement/combat tick
  paths now use the Agent-owned geometry helper.
- BotManager move/farm/patrol/follow/grind/stop command-mode preparation moved to
  `server.agents.runtime.AgentCommandModeService`; BotManager only supplies
  temporary guards and callbacks for script-task clearing, shop cancellation,
  and mode start side effects.
- Agent movement command facade now routes follow-owner, stop, move-to,
  farm-here, grind, and patrol through Agent runtime mode/queue services
  directly, including patrol graph-region validation and visible failure
  replies.
- Build level-up, potion stop, and combat ammo-stop paths now request
  follow-owner through `AgentBotMovementCommandRuntime` instead of calling
  `BotManager.issueFollowOwner` directly.
- Session relog/logout/away prompts and equipment unequip-all now request stop
  through `AgentBotMovementCommandRuntime` instead of calling
  `BotManager.issueStop` directly.
- Patrol command graph-region validation, visible failure reply, and active
  patrol mode transition moved into `AgentBotMovementCommandRuntime`; the
  former `BotManager.issuePatrol` compatibility delegate has been removed.
- Session first-agent checks, away-town offer checks, and away-safe command
  routing moved behind `AgentBotSessionControlRuntime`; BotManager remains only
  the temporary side-effect bridge for away-safe state changes.
- Support-heal jump-anchor resolution now uses `AgentFollowAnchorService` plus
  `AgentBotSessionLifecycleSideEffects.getBotEntries`, removing the direct
  `BotManager.resolveFollowAnchor` call from Agent combat heal runtime.
- Combat grind region-occupancy scoring now reads sibling Agents through
  `AgentBotSessionLifecycleSideEffects.getBotEntries`, removing another direct
  `BotManager.getBotEntries` call from Agent combat target runtime.
- Ammo-share, potion-share, and sibling gear offer scans now read sibling
  Agents through `AgentBotSessionLifecycleSideEffects.getBotEntries`, removing
  direct BotManager entry-list calls from those capability donor scans.
- Bot-inventory-drop loot delay detection now reads active Agent ownership via
  `AgentBotSessionLifecycleSideEffects.activeLeaderByAgentCharacterId`, removing
  the direct BotManager lookup from `AgentLootEligibility`.
- Airshow named-Agent lookup now reads through
  `AgentBotSessionLifecycleSideEffects.getBotEntry`, removing the direct
  BotManager lookup from `AgentAirshowService`.
- Manual trade greeting selection moved to `AgentTradeDialogueService` backed
  by `AgentDialogueCatalog`; inventory runtime adapters no longer call
  `BotManager.getInstance().manualTradeGreeting`.
- Navigation debug overlay bot selection now reads Agent entries through
  `AgentBotSessionLifecycleSideEffects`, removing direct BotManager lookup from
  `AgentNavigationDebugOverlay`.
- BotNavigationManager follow-anchor region resolution now reads sibling Agent
  entries through `AgentBotSessionLifecycleSideEffects` and resolves anchors
  through `server.agents.runtime.AgentFollowAnchorService`, removing its direct
  `BotManager.resolveFollowAnchor` call while preserving follow target
  behavior.
- BotManager follow-owner, grind, and stop entry points were removed. Callers
  now use `AgentBotMovementCommandRuntime` directly while preserving command
  setup behavior.
- BotManager inactive-town return-scroll item use moved to
  `server.agents.runtime.AgentReturnScrollService`; BotManager remains only the
  leader-safety callback site for this action.
- BotManager swim-map helper moved to
  `server.agents.runtime.AgentMapEnvironmentService`; BotManager no longer owns
  that map-environment predicate for movement/tick physics routing.
- BotManager grind-loot retry suppression predicate was removed; Agent loot
  targeting now consumes `AgentBotGrindLootStateRuntime::isRetrySuppressed`
  directly.
- BotManager script item-drop behavior moved to
  `server.agents.plans.AgentScriptItemActionService`; BotManager remains a
  compatibility delegate and AgentScriptRunner uses the Agent service directly.
- Legacy command combat-config behavior now routes directly through
  `server.agents.capabilities.combat.AgentCombatConfig`; the Agent command
  bridge no longer imports BotManager for those compatibility wrappers.
- Random dialogue selection moved to
  `server.agents.capabilities.dialogue.AgentDialogueSelector`; Agent runtime and
  capability modules no longer depend on BotManager for `randomReply`.
- Random delay selection moved to `server.agents.runtime.AgentRandom`;
  BotManager remains a compatibility delegate for legacy callers, while Agent
  fidget/shop timing no longer asks BotManager for random delay values.
- Script cheap-move target classification moved to
  `server.agents.plans.AgentScriptMoveTargetService`; BotManager now delegates
  the legacy method, and AgentScriptRunner calls the Agent runtime bridge
  instead of BotManager.
- BotManager general runtime config moved to
  `server.agents.runtime.AgentRuntimeConfig`; BotManager keeps `cfg` as a
  compatibility alias while Agent modules consume the Agent-owned config source.
- BotManager formation map storage moved to
  `server.agents.runtime.AgentFormationService`; BotManager still contains the
  temporary command parsing and snapshot wiring around that Agent-owned store.
- BotManager follow-target position resolver moved to
  `server.agents.runtime.AgentFollowTargetPositionService`; the Agent movement
  target gateway now captures snapshots without calling BotManager.
- BotManager live entry map moved to
  `server.agents.runtime.AgentRuntimeRegistry`; BotManager keeps a compatibility
  alias while Agent session lookup bridges read the Agent-owned registry.
- BotManager scripted follow-target resolution moved to
  `server.agents.runtime.AgentFollowAnchorService`; BotManager only supplies
  the temporary sibling entry list.
- BotManager dead-state tick handling moved to
  `server.agents.runtime.AgentDeathTickService`; BotManager only supplies
  temporary combat death-entry and respawn callbacks.
- BotManager attack-lock physics dispatch moved to
  `server.agents.runtime.AgentActionLockPhysicsService`; BotManager only
  supplies temporary swim-map and movement physics callbacks.
- BotManager map-change grounding moved to
  `server.agents.runtime.AgentMapTransitionService`; BotManager only supplies
  temporary foothold/physics/navigation callbacks.
- BotManager idle/trade physics mode selection moved to
  `server.agents.runtime.AgentIdlePhysicsService`; BotManager only supplies
  temporary movement/physics callbacks.
- BotManager tick heartbeat ownership moved to
  `server.agents.runtime.AgentHeartbeatService`; BotManager only supplies
  temporary packet freshness and movement broadcast callbacks.
- BotManager mode transition state ownership moved to
  `server.agents.runtime.AgentModeService`; BotManager remains the temporary
  command/script side-effect wrapper for follow, grind, stop, move-to,
  farm-here, and patrol entry points.
- BotManager target snapshot composition moved further into
  `server.agents.runtime.AgentTargetSnapshotService`; BotManager now only wires
  temporary sibling/formation storage and the follow-target resolver callback.
- BotManager formation state lookup moved to
  `server.agents.runtime.AgentFormationService`; BotManager still stores the
  temporary per-leader formation map for command compatibility.
- BotManager tick leader/session refresh moved to
  `server.agents.runtime.AgentLeaderSessionService`; BotManager remains a
  compatibility wrapper that supplies the current Cosmic player-storage lookup.
- BotManager follow-anchor resolution moved to
  `server.agents.runtime.AgentFollowAnchorService`; BotManager remains a
  compatibility wrapper that supplies the temporary sibling list until runtime
  registry ownership is fully moved.
- BotManager target snapshot assembly moved to
  `server.agents.runtime.AgentTargetSnapshotService`; BotManager still supplies
  temporary follow-anchor and follow-target-position callbacks for this slice.
- BotManager target snapshot record moved to
  `server.agents.runtime.AgentTargetSnapshot`; movement/navigation callers and
  tests consume the Agent-owned record.
- BotManager formation type/state and offset calculation moved to
  `server.agents.runtime.AgentFormationService`; command parsing and the
  temporary per-leader map remain in BotManager for now.
- BotManager tick failure count/window/disable/force-idle decision ownership
  moved to `server.agents.runtime.AgentTickFailurePolicy`; BotManager still owns
  side effects for this slice.
- BotManager AI-cadence consumption and tick timestamp recording moved to
  `server.agents.runtime.AgentTickOrchestrator`; full tick dispatch remains in
  BotManager for later slices.
- `BotManager.removeBot` and `BotManager.removeBotByCharId` now delegate map
  mutation to `server.agents.runtime.AgentLifecycleService`; BotManager still
  owns the temporary backing maps and cancellation side-effect callback.
- `BotManager` active-runtime lookup loops moved to
  `server.agents.runtime.AgentRuntimeRegistry`; BotManager still owns the
  temporary backing map and lifecycle mutation in this slice, but lookup
  behavior is Agent-owned.
- Agent performance monitor notes for passive loot, trade tick, manual trade,
  and grind-loot scanning now name Agent-owned runtime services while preserving
  the same section keys and timing behavior.
- `BotInventoryManager` runtime hook factories moved to
  `AgentBotInventoryRuntimeAdapters`; `BotInventoryManager` is now a thin
  compatibility shell over Agent looting/trade/inventory services.
- Dead `BotInventoryManager.collectItems` compatibility body was removed after
  all active item collection paths routed through Agent-owned runtime services.
- `BotInventoryManager.tickPassiveLoot` runtime callback assembly now lives in
  `AgentPassiveLootRuntimeService`; the bot inventory shell only supplies
  temporary loot-inhibit, active-sequence, cooldown, config, reply, owner,
  offer, item-presence, auto-equip, cleanup, and pickup hooks.
- `BotInventoryManager` transfer availability/count runtime callback assembly
  now lives in `AgentTradeTransferAvailabilityRuntimeService`; the bot inventory
  shell only supplies temporary owner, named-item, and equipped-slot counter
  hooks.
- `BotInventoryManager.tickTrade` runtime callback assembly now lives in
  `AgentTradeTickRuntimeService`; the bot inventory shell only supplies
  temporary timing, current-trade, owner, recipient, and refill hooks.
- `BotInventoryManager` trade lifecycle runtime callback construction now lives
  in `AgentTradeLifecycleRuntimeService`; the bot inventory shell only supplies
  temporary restore, manual-clear, owner, refill, reply-delay, and reply-pool
  hooks.
- `BotInventoryManager.tickManualTrade` runtime callback assembly now lives in
  `AgentManualTradeRuntimeService`; the bot inventory shell only supplies
  temporary active-sequence, countdown, tick cadence, peer authorization,
  greeting, and refill hooks.
- `BotInventoryManager` trade item collection plus ammo/equip classification
  runtime composition now lives in
  `AgentInventoryTradeRuntimeService`; the bot inventory shell only supplies
  temporary runtime hooks for recommendation, weapon/projectile, config,
  profiling, self-reserve, reservation, and owner lookup behavior.
- Grouped trade category navigation moved to
  `AgentTradeGroupNavigationService`; `BotInventoryManager` no longer owns the
  private next-equip-group or next-ammo-group helpers.
- Recommended trade item selection moved to `AgentTradeRecommendationService`;
  `BotInventoryManager` no longer owns the null-owner recommendation rule.
- Equip trade slow-classification warning threshold and formatting moved to
  `AgentEquipTradeSlowLogService`; `BotInventoryManager` no longer owns that
  logger, threshold, or warning message body.
- Manual trade timeout ownership moved to `AgentManualTradeService`; the
  `BotInventoryManager` shell no longer carries the legacy 60-second timeout
  constant.
- `BotInventoryManager` private trade-sequence/open-batch wrappers moved to
  `AgentTradeSequenceRuntimeService`; legacy tests now cover the Agent-owned
  runtime service directly for first-batch invite announcement behavior.
- `BotInventoryManager` transfer availability/count callback construction now
  lives in `AgentTradeTransferAvailabilityCallbackService`; the bot inventory
  shell only wires temporary named-item, equipped-slot, and category collection
  hooks.
- Trade dialogue reply selection now lives in `AgentTradeDialogueService`.
  `BotInventoryManager` and `AgentInventoryTransferService` use Agent-owned
  selectors for invitation, all-done, thanks, freebie, and reserved-equipment
  group replies while preserving the same `BotManager.randomReply` behavior.
- `BotInventoryManager` no longer owns duplicate reserved-equip group reply
  selection; it now uses `AgentInventoryTransferService.equipsGroupMessage` for
  the same Agent dialogue catalog pools.
- `BotInventoryManager` equip-trade classification callback construction now
  lives in `AgentEquipTradeCallbackService`; the bot inventory shell only wires
  temporary profiling, bag-scan, self-reserve, reservation, owner, and slow-log
  hooks.
- `BotInventoryManager` ammo-trade callback construction now lives in
  `AgentAmmoTradeCallbackService`; the bot inventory shell only wires temporary
  weapon-type, projectile-WATK, quest-item, and untradeable-config hooks.
- `BotInventoryManager` item-collection callback construction now lives in
  `AgentTradeItemCollectionCallbackService`; the bot inventory shell only wires
  temporary recommended-item, equip-group, and ammo-group hooks.
- `BotInventoryManager.tickPassiveLoot` passive-loot callback construction now
  lives in `AgentPassiveLootCallbackService`; the bot inventory shell only wires
  temporary runtime-state, countdown, config, reply, owner, auto-equip, offer,
  cleanup, and pickup hooks.
- `BotInventoryManager` trade lifecycle callback construction now lives in
  `AgentTradeLifecycleCallbackService`; the bot inventory shell only wires
  temporary restore, manual-clear, owner, refill, reply-delay, reply-pool, and
  reaction-randomness hooks.
- `BotInventoryManager.tickManualTrade` manual/peer/owner callback construction
  now lives in `AgentManualTradeCallbackService`; the bot inventory shell only
  wires temporary active-sequence, timeout, authorization, invite-accept,
  greeting, completion, and refill hooks.
- `BotInventoryManager.tickTrade` item-add callback construction now lives in
  `AgentTradeItemAddTickCallbackService`; the bot inventory shell only wires
  temporary cancel, delay, and all-done reply hooks.
- `BotInventoryManager.tickTrade` between-batch callback construction now lives
  in `AgentTradeBetweenBatchCallbackService`; the bot inventory shell only wires
  temporary countdown, category item collection, equip/ammo group selection,
  open-batch, and reset hooks.
- Stale `BotInventoryManager` direct imports of trade cancellation, completion,
  and reset services were removed after lifecycle wiring moved to
  `AgentTradeLifecycleService`.
- `BotInventoryManager` trade lifecycle helper wiring now lives in
  `AgentTradeLifecycleService`; the bot inventory shell only supplies temporary
  callbacks for restore/clear/owner/refill/reply-randomization operations.
- `BotInventoryManager` no longer imports `AgentInventoryTradeCollectionService`
  directly after item collection wiring moved to `AgentTradeItemCollectionService`.
- `BotInventoryManager` and `AgentInventoryTransferService` trade item
  collection wiring now lives in `AgentTradeItemCollectionService`; callers
  only supply temporary recommended/equip/ammo group callbacks.
- `BotInventoryManager` and `AgentInventoryTransferService` ammo trade
  classification orchestration now lives in
  `AgentAmmoTradeClassificationService`; callers only wire temporary runtime
  hooks for weapon type, projectile WATK, quest-item checks, and config.
- Stale `BotInventoryManager` imports for migrated inventory dialogue,
  inventory trade policy, USE-item classification, and manual-trade state
  helpers were removed.
- `BotInventoryManager.tickTrade` trade tick callback construction now lives in
  `AgentTradeTickCallbackService`; the bot inventory shell only wires temporary
  countdown/current-trade/between-batch/closed-window/invite/item/confirm
  operations.
- `BotInventoryManager` trade sequence callback construction now lives in
  `AgentTradeSequenceCallbackService`; the bot inventory shell only wires
  temporary recipient/cancel/start/invite/reply operations.
- `BotInventoryManager.tickPassiveLoot` passive pickup orchestration now lives
  in `AgentPassiveLootService`; the bot inventory shell only wires temporary
  callbacks for runtime state, owner lookup, pickup/auto-equip side effects,
  offer prompts, inventory-full warnings, and ghost-drop cleanup.
- Dead `BotInventoryManager` helper bodies for trash-equip collection and
  own-class equip checks were removed; their active callers already route
  through Agent inventory/trade/equipment services. The private
  `startTradeSequence` compatibility wrapper remains for legacy invite-once
  regression coverage.
- `BotInventoryManager` and `AgentInventoryTransferService` equip trade
  classification orchestration now lives in
  `AgentEquipTradeClassificationService`; callers only wire temporary bag scan,
  self-reserve, reservation-check, owner-lookup, and slow-log callbacks.
- `BotInventoryManager` transfer availability/count routing now lives in
  `AgentTradeTransferAvailabilityService`; the bot inventory shell only wires
  current equipped-slot, named-item, and category-collection callbacks.
- `BotInventoryManager.tickManualTrade` top-level manual trade tick
  orchestration now lives in `AgentManualTradeTickService`; the bot inventory
  shell only supplies temporary callbacks for active transfer suppression,
  trade-window lookup, timeout handling, and owner/peer branch wiring.
- `BotInventoryManager.tickManualTrade` owner-side manual trade routing now
  lives in `AgentManualOwnerTradeService`; the bot inventory shell only supplies
  temporary callbacks for delayed owner-invite accept, one-time greeting,
  completion, and post-trade auto-equip refill.
- `BotCombatManager` debug-stat target search and attack-plan lookup no longer
  sit on the report path. `AgentBotCombatReportRuntime` now calls
  `AgentBotCombatTargetRuntime` and `AgentBotCombatPlanRuntime` directly, while
  `BotCombatManager.describeDebugStats` remains only a temporary compatibility
  delegate.
- `BotManager` grind/local-opportunity combat now uses Agent combat plan,
  target, reposition, and attack runtimes directly. The bot combat facade still
  exists for older callers, but the main tick path no longer depends on
  `BotCombatManager.planAttack`, `attackMonster`, `findGrindTarget`,
  `findPatrolTarget`, `findFollowAttackTarget`, or `aoeRepositionTarget`.
- Ammo, inventory, movement, potion, shop, and BotManager production callers now
  read live combat config through `AgentCombatConfig.cfg` instead of the
  temporary `BotCombatManager.cfg` compatibility alias.
- BotManager common tick combat lifecycle now calls Agent runtimes directly for
  mob damage, death-state entry, skill-cache rebuild, support healing, and
  combat buff ticks.
- BotPhysicsEngine fall-damage dispatch now calls
  `AgentBotCombatDamageRuntime.applyFallDamage` directly instead of the
  temporary `BotCombatManager` facade.
- Combat skill-cache tests now exercise
  `AgentBotCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded` directly, reducing
  remaining `BotCombatManager` usage to compatibility-specific plan, target,
  damage, and config checks.
- Combat plan, target-search, and AoE-reposition tests now exercise Agent-owned
  runtimes directly. The old `BotCombatManager.AttackPlan`, `planAttack`,
  `attackMonster`, target-search, reachable-target, and AoE-reposition
  compatibility delegates have been removed; remaining `BotCombatManager`
  surface is now limited to config, damage/death, support, skill-cache, and
  debug-stat compatibility.
- `src/main/java/server/bots/BotCombatManager.java` has been deleted. Remaining
  combat behavior is reached through Agent combat modules and integration
  runtimes; `BotCombatManagerTest` remains only as a historical parity test
  class name.
- `src/main/java/server/bots/BotChatManager.java` has been deleted. `BotManager`
  now calls `AgentChatRuntime` with `AgentBotChatOrchestratorContext` directly;
  the historical parity suite is now named `AgentChatRuntimeParityTest`.
- `server.bots.llm.CommandTypoSuggester` has moved to
  `server.agents.commands.AgentCommandTypoSuggester`; production and focused
  tests now use the Agent-owned command typo utility directly.
- `server.bots.llm.BotLlmConfig` has moved to
  `server.agents.capabilities.dialogue.llm.AgentLlmConfig`; existing LLM
  runtime, memory, Ollama client, command bridge, and chat routing code now
  share the Agent-owned static config.
- The remaining `server.bots.llm` runtime cluster has moved to
  `server.agents.capabilities.dialogue.llm`: `AgentLlmReplyService`,
  `AgentMemoryStore`, `AgentPromptBuilder`, `AgentSituationBuilder`,
  `AgentSenderRelation`, and `OllamaClient`. The old source/test package is
  empty, and `BotManager` calls the Agent reply service directly.
- `server.bots.BotOwnershipService` has moved to
  `server.agents.auth.AgentOwnershipService`. Character resolution,
  `bot_owners` lookup/registration, same-account adoption, denial messages, and
  authorization results are unchanged.
- `server.bots.BotPathLogger` has moved to
  `server.agents.monitoring.AgentPathLogger`. Navigation tick capture, path-log
  formatting, graph snapshot resolution, and file output are unchanged.
- Static skill build profile tables have moved from `server.bots.build` to
  `server.agents.capabilities.build.profiles`. Warrior, bowman, thief, mage,
  and build-step ordering data are unchanged.
- `server.bots.BotAirshowManager` has moved to
  `server.agents.capabilities.social.airshow.AgentAirshowService`. Airshow
  command syntax, frame timing, trail monster packets, and restore behavior are
  unchanged.
- `server.bots.BotNavigationProbe` has moved to
  `server.agents.capabilities.navigation.AgentNavigationProbe`. `@regennav`,
  CLI probe formatting, graph build reporting, point/edge/path probes, and
  optimality measurement behavior are unchanged.
- `server.bots.BotNavigationDebugOverlay` has moved to
  `server.agents.capabilities.navigation.AgentNavigationDebugOverlay`. `!botnav`
  graph/path/pathlog/clear command routing, fake-mist drawing, active-edge
  highlighting, and auto-clear behavior are unchanged.
- `server.bots.BotScrollReactionManager` has moved to
  `server.agents.capabilities.social.AgentScrollReactionService`. Range
  filtering, reaction chances, streak/load math, emote/chat/fidget behavior, and
  scheduler/reply adapter calls are unchanged.
- `server.bots.BotBuffManager` has moved to
  `server.agents.capabilities.combat.AgentBuffService`. Consumable buff-pot
  tick behavior, relevant-stat filtering, cheap-mode attack cap, chat summary,
  and debug-line formatting are unchanged.
- `server.bots.BotCommandParser` has moved to
  `server.agents.integration.AgentBotCommandParser`. Bot-entry target
  adaptation, transfer command wrapping, and targeted-command feedback are
  unchanged; `AgentCommandParser` remains the shared parser core.
- `server.bots.BotFidgetSideEffects` has moved to
  `server.agents.integration.AgentBotFidgetSideEffects`. Greeting/social
  fidget dispatch still delegates to the unchanged legacy fidget runtime, but
  the Agent movement callback no longer imports the bot-side shim.
- `server.bots.BotSessionLifecycleSideEffects` has moved to
  `server.agents.integration.AgentBotSessionLifecycleSideEffects`. Relog and
  owner-entry lookup behavior still delegates to `BotManager`, but session
  orchestration no longer imports a bot-side lifecycle shim.
- `server.bots.BotMovementTargetSideEffects` has moved to
  `server.agents.integration.AgentBotMovementTargetSideEffects`. Snapshot
  conversion and raw navigation-target override behavior are unchanged while
  BotManager remains the temporary target-snapshot source.
- `server.bots.BotScriptRuntime` has moved to
  `server.agents.plans.AgentScriptRuntimeState`. `BotEntry` still carries the
  state during reconstruction, but the script runtime state bag is Agent-owned.
- `server.bots.BotScript`, `BotScriptContext`, `BotScriptStep`, and
  `BotScriptRunner` have moved to `server.agents.plans` as `AgentScript`,
  `AgentScriptContext`, `AgentScriptStep`, and `AgentScriptRunner`. KPQ script
  content still lives under the legacy PQ package and behavior is unchanged.
- `server.bots.BotTask` has moved to `server.agents.plans.AgentTask`. The
  queue/execution path is still temporarily backed by BotEntry and BotManager,
  but the task value model is Agent-owned.
- `server.bots.BotStarterKitManager` has moved to
  `server.agents.capabilities.build.AgentStarterKitService`. Job-change,
  starter-kit grant, auto-equip, and build-status behavior are unchanged.
- `server.bots.BotFidgetManager` has moved to
  `server.agents.capabilities.movement.fidget.AgentFidgetService`. Active
  fidget ticking, idle/social/greeting fidget rolls, speed-mismatch fidget
  eligibility, jump/prone/sideways fidget execution, origin-return cleanup, and
  prone attack visuals are unchanged; BotEntry, BotMovementManager, BotManager,
  and BotPhysicsEngine remain temporary backing seams during movement
  reconstruction.
- `server.bots.BotFallbackMovementManager` has moved to
  `server.agents.capabilities.movement.AgentFallbackMovementService`. Rope
  fallback target selection, immediate rope attach/jump, swim jump-up, down-jump
  fallback, ledge walk-off targeting, and jump-probe fallback behavior are
  unchanged; BotEntry, BotMovementManager, and BotPhysicsEngine remain
  temporary backing seams during movement reconstruction.
- `server.bots.BotNavigationGraph` and `server.bots.BotNavigationGraphProvider`
  have moved to `server.agents.capabilities.navigation.AgentNavigationGraph`
  and `AgentNavigationGraphService`. Graph cache shape, graph version, warmup
  executors, build reports, region/edge construction, collidable-footing caches,
  and pathfinding inputs are unchanged; BotNavigationManager and BotPhysicsEngine
  remain temporary movement/navigation backing seams.

| Current file | Target Agent destination | Status |
| --- | --- | --- |
| `src/main/java/client/BotClient.java` | `client.AgentClient` or `server.agents.integration.cosmic.CosmicAgentClientAdapter` | `MIGRATE_TO_AGENT` |
| `src/main/java/client/creator/BotCreator.java` | `client.creator.AgentCreator` | `MIGRATE_TO_AGENT` |
| `src/main/java/client/command/commands/gm0/RegisterBotCommand.java` | `server.agents.commands` or later deletion if ownership is removed | `LEGACY_PROFILE` |
| `src/main/java/client/command/commands/gm3/SpawnBotCommand.java` | `server.agents.commands.AgentSpawnCommandExecutor` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/TakeBotOwnerCommand.java` | `server.agents.commands` or later deletion if ownership is removed | `LEGACY_PROFILE` |
| `src/main/java/client/command/commands/gm3/BotCfgCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/BotLlmCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/BotNavCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/client/command/commands/gm3/BotPerfDebugCommand.java` | `server.agents.commands.AgentLegacyCommandBridge` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/BotAirshowManager.java` | `server.agents.capabilities.social.airshow.AgentAirshowService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotAmmoManager.java` | `server.agents.capabilities.supplies.AgentAmmoService`, `server.agents.capabilities.supplies.AgentAmmoSharePolicy`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; ammo-share request eligibility, donor selection, scheduling, owner-offer routing, donor quantity math, donor tie-break policy, and ammo request/offer dialogue pools are Agent-owned |
| `src/main/java/server/bots/BotAttackExecutionProvider.java` | `server.agents.capabilities.combat.AgentAttackExecutionProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotBuffManager.java` | `server.agents.capabilities.combat.AgentBuffService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotBuildManager.java` | `server.agents.capabilities.build.AgentBuildService` and `server.agents.capabilities.build.profiles.*` | `MIGRATED_TO_AGENT`; AP/SP/job prompt and assignment orchestration moved unchanged, with later internal splitting still recommended |
| `src/main/java/server/bots/BotChatManager.java` | `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.dialogue.AgentChatRuntime`, `server.agents.capabilities.dialogue.AgentChatCommandClassifier`, `server.agents.capabilities.dialogue.AgentTradeDialogueClassifier`, `server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier`, `server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier`, `server.agents.capabilities.dialogue.AgentSocialDialogueClassifier`, `server.agents.capabilities.dialogue.AgentBuildDialogueClassifier`, `server.agents.capabilities.dialogue.AgentDialogueReportFormatter`, `server.agents.commands.AgentReplyQueue`, `server.agents.events` | `MIGRATED_TO_AGENT`; source file deleted after named random reply pools, reply queue, movement/follow/fidget, supply-request/direct supply, query/toggle, support/heal/buff toggle, logout/relog/away session request and confirmation normalization, report/debug, trade/drop/item and pending drop-choice, trade-invite/shop/maker utility, equipment/autoequip, greeting/fame, build/job/AP/SP classification, skill-tree choice resolution, job advancement resolution, report/AP-build/job-display/skill-tree/learned-skill formatting, upgrade-request classification, handled-state, and top-level chat orchestration moved to Agent-owned modules |
| `src/main/java/server/bots/BotCombatManager.java` | `server.agents.capabilities.combat`, `server.agents.capabilities.dialogue`, and `server.agents.integration.AgentBotCombatReportRuntime` | `MIGRATED_TO_AGENT`; source file deleted after config, combat planning, target search, AoE reposition, damage/death, support, skill-cache, debug-stat, packet execution, and combat reply behavior moved to Agent-owned modules |
| `src/main/java/server/bots/BotCommandParser.java` | `server.agents.integration.AgentBotCommandParser` and `server.agents.commands.AgentCommandParser` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotEntry.java` | `server.agents.runtime.AgentSession` and capability state objects | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; message queue now uses Agent-owned queued message type |
| `src/main/java/server/bots/BotEquipManager.java` | `server.agents.capabilities.equipment.AgentEquipmentService` and equipment capability classes | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; production callers now enter through `AgentEquipmentService`. Map-damage benchmark snapshot/selection lives in `AgentMapDamageProfile`, production weapon/job compatibility plus self-reserve weapon track labels live in `AgentWeaponCompatibilityPolicy`, slot alias resolution lives in `AgentEquipmentSlotResolver`, useful-stat and defense-adjusted damage scoring lives in `AgentEquipmentScoringPolicy`, auto-equip duplicate-trigger state lives in `AgentAutoEquipThrottle`, auto-equip debug report formatting lives in `AgentEquipmentDebugReportFormatter`, recommendation result data uses `AgentEquipRecommendation`, optimizer result data uses `AgentEquipmentOptimizerResult`, fixed-weapon DP result/score data uses `AgentEquipmentDpResult` and `AgentEquipmentScore`, optimizer stat snapshot data uses `AgentEquipmentStatSnapshot`, optimizer metadata/requirement hooks use `AgentEquipmentOptimizerHooks`, weapon-branch debug score breakdown data uses `AgentWeaponScoreBreakdown`, recommendation candidate eligibility lives in `AgentEquipmentRecommendationPolicy`, recommendation filtering/result construction/summary formatting lives in `AgentEquipmentRecommendationService`, unequip command execution lives in `AgentEquipmentUnequipService`, and owned/incoming equipment reserve, requirement-gate, requirement-comparison, and future-track policy lives in `AgentEquipmentReservePolicy`; reserve, recommendation, and unequip service entry points no longer delegate through BotEquipManager, while optimizer DP, debug branch walking, and auto-equip execution remain temporary bot seams behind the Agent service boundary |
| `src/main/java/server/bots/BotFallbackMovementManager.java` | `server.agents.capabilities.movement.AgentFallbackMovementService` | `MIGRATED_TO_AGENT`; fallback steering, rope/drop/swim/jump immediate actions, and ledge targeting moved unchanged |
| `src/main/java/server/bots/BotFidgetManager.java` | `server.agents.capabilities.movement.fidget.AgentFidgetService` | `MIGRATED_TO_AGENT`; active fidget state machine and social/greeting fidget start behavior moved unchanged, while BotEntry and movement/physics helpers remain temporary backing seams |
| `src/main/java/server/bots/BotFidgetSideEffects.java` | `server.agents.integration.AgentBotFidgetSideEffects` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotInventoryManager.java` | `server.agents.capabilities.inventory.AgentInventoryTickRuntime`, `looting`, `trade`, `server.agents.capabilities.dialogue.AgentItemQueryNormalizer`, `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.supplies.AgentAmmoService`, `server.agents.capabilities.supplies.AgentPotionService`, `server.agents.capabilities.supplies.AgentPotionSharePolicy`, `server.agents.capabilities.trade.AgentSupplyShareTradeService`, `server.agents.capabilities.trade.AgentTradeCommandProfiler`, `server.agents.capabilities.trade.AgentInventoryTransferService`, `server.agents.capabilities.trade.AgentManualTradeService`, `server.agents.capabilities.trade.AgentManualPeerTradeService`, `server.agents.capabilities.trade.AgentGroupedTradeTransferService`, `server.agents.capabilities.trade.AgentReservedEquipTradeTransferService`, `server.agents.capabilities.trade.AgentPreparedTradeTransferService`, `server.agents.capabilities.trade.AgentTradeTransferRouter`, `server.agents.capabilities.trade.AgentTradeRecipientService`, `server.agents.capabilities.trade.AgentMesoTradeService`, `server.agents.capabilities.trade.AgentDirectItemTradeService`, `server.agents.capabilities.trade.AgentTradeStateService`, `server.agents.capabilities.trade.AgentTradeBatchService`, `server.agents.capabilities.trade.AgentTradeCancellationService`, `server.agents.capabilities.trade.AgentTradeCompletionService`, `server.agents.capabilities.trade.AgentTradeSequenceService`, `server.agents.capabilities.trade.AgentTradeResetService`, `server.agents.capabilities.trade.AgentTradeMesoAddService`, `server.agents.capabilities.trade.AgentTradeItemAddService`, `server.agents.capabilities.trade.AgentTradeAllItemsAddedService`, `server.agents.capabilities.trade.AgentTradeCategoryAnnouncementService`, `server.agents.capabilities.trade.AgentTradeInviteWaitService`, `server.agents.capabilities.trade.AgentTradeConfirmWaitService`, `server.agents.capabilities.trade.AgentTradeClosedWindowService`, `server.agents.capabilities.trade.AgentTradeTransferStartGuard`, `server.agents.capabilities.trade.AgentTradeQueuedRetryService`, `server.agents.capabilities.trade.AgentTradeBetweenBatchService`, `server.agents.capabilities.trade.AgentTradeItemAddTickService`, `server.agents.capabilities.trade.AgentTradeTickService`, `server.agents.capabilities.trade.AgentTradeSequenceOrchestrator` | `MIGRATED_TO_AGENT`; source file deleted after item query normalization, USE-item classification, passive loot, transfer availability/count, trade tick, manual trade, trade state, trade sequencing, drop behavior, supply sharing, and reply/result pools moved to Agent-owned modules |
| `src/main/java/server/bots/BotLootEligibility.java` | `server.agents.capabilities.looting.AgentLootEligibility` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotMakerManager.java` | `server.agents.capabilities.build.AgentMakerService` | `MIGRATED_TO_AGENT`; Maker crystal and trash-disassembly batch orchestration moved unchanged |
| `src/main/java/server/bots/BotManager.java` | `server.agents.runtime`, `commands`, `events`, capability orchestrators | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; ownerless/offline-leader tick branching now delegates to `AgentOwnerlessTickService`, preserving following clear, map-change grounding, explicit move-target pass-through, and idle fallback ordering. Tick-time leader-motion observation, farm-anchor map-change cleanup, reached move-target cleanup, patrol map-change cleanup, and precise move-target navigation marker maintenance now delegate to `AgentTickStateMaintenanceService`. Tracked map-change tick handling now delegates to `AgentMapTransitionService`. Movement recovery teleport checks now delegate to `AgentRecoveryTeleportService`. Cross-map follow synchronization now delegates to `AgentFollowMapSyncService`. Follow-target candidate lookup, target resolution, and follow-target chat command execution now delegate to `AgentFollowTargetRuntime` over the Agent follow-target services. Parked follow-mode idle movement fast-path timing and eligibility now delegate to `AgentFollowIdleMovementRuntime` over `AgentFollowIdleMovementService`. Movement phase dispatch now delegates to `AgentMovementPhaseService`. Stuck detection and unstuck triggering now delegate to `AgentStuckDetectionService`. Movement-core tick orchestration now delegates to `AgentMovementTickService`. Movement-only tick orchestration now delegates to `AgentMovementOnlyTickService`. Anchored farm/sentry tick orchestration now delegates to `AgentAnchoredFarmTickService`. Local attack move-window timing and config-bound settled callbacks now delegate to `AgentLocalAttackMoveWindowRuntime` over `AgentLocalAttackMoveWindowService`. Grind target search/adoption policy now delegates to `AgentGrindTargetSearchPolicy`. Grind fallback target and opportunistic loot steering now delegate to `AgentGrindTargetRuntime` over `AgentGrindTargetPositionService`. Ranged priority target selection and AoE reposition commitment hook wiring now delegate to `AgentGrindCombatRuntime` over the Agent combat selectors/services. Grind navigation/retreat target selection and hook wiring now delegate to `AgentGrindNavigationRuntime` over `AgentGrindNavigationTargetSelector`. Ownerless online Agent lookup now delegates to `AgentRuntimeRegistry`. Public move/farm/follow-target commands now delegate to `AgentBotMovementCommandRuntime`. Script-task follow-target lookup now delegates to `AgentFollowAnchorService` over the live runtime registry. Local opportunity attack decision flow now delegates to `AgentLocalOpportunityAttackService`. Script-task start/completion hook wiring now delegates to `AgentScriptTaskExecutionService`. Grind-mode loot target validation/refresh now delegates to `AgentGrindLootTargetService`. Grind-mode target search orchestration now delegates to `AgentGrindTargetSearchService`. Grind-mode no-target fallback now delegates to `AgentGrindNoTargetFallbackService`. Grind-mode target commitment/replacement now delegates to `AgentGrindTargetCommitmentService`. Grind-mode ranged engagement now delegates to `AgentGrindRangedEngagementService`. Grind-mode navigation tail now delegates to `AgentGrindNavigationTailService` |
| `src/main/java/server/bots/BotMovementManager.java` | `server.agents.capabilities.movement` | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; cooldown/delay countdown math, climb idle/snap/rope identity decision policy, and ground horizontal step policy are Agent-owned |
| `src/main/java/server/bots/BotMovementTargetSideEffects.java` | `server.agents.integration.AgentBotMovementTargetSideEffects` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotMovementProfile.java` | `server.agents.capabilities.movement.AgentMovementProfile` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationDebugOverlay.java` | `server.agents.capabilities.navigation.AgentNavigationDebugOverlay` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationGraph.java` | `server.agents.capabilities.navigation.AgentNavigationGraph` | `MIGRATED_TO_AGENT`; graph model, region/edge/segment data, cache serialization shape, and lookup helpers moved unchanged |
| `src/main/java/server/bots/BotNavigationGraphProvider.java` | `server.agents.capabilities.navigation.AgentNavigationGraphService` | `MIGRATED_TO_AGENT`; graph build/warmup/cache/report/collidable helper behavior moved unchanged, with BotPhysicsEngine/BotMovementManager as temporary explicit seams |
| `src/main/java/server/bots/BotNavigationManager.java` | `server.agents.capabilities.navigation` | `SPLIT_TO_MULTIPLE_AGENT_MODULES` |
| `src/main/java/server/bots/BotNavigationMapLoader.java` | `server.agents.capabilities.navigation.AgentNavigationMapLoader` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationProbe.java` | `server.agents.capabilities.navigation.AgentNavigationProbe` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotOfferManager.java` | `server.agents.capabilities.trade.AgentOfferService`, `equipment`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; owner/sibling gear offer orchestration, pending offer responses, loot-offer prompts, reservation checks, and best-upgrade request routing now live in Agent trade |
| `src/main/java/server/bots/BotOwnershipService.java` | `server.agents.auth.AgentOwnershipService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPathLogger.java` | `server.agents.monitoring.AgentPathLogger` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPerformanceMonitor.java` | `server.agents.runtime.AgentPerformanceMonitor` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPhysicsEngine.java` | `server.agents.capabilities.movement.AgentPhysicsEngine` | `MIGRATE_TO_AGENT` |
| `src/main/java/server/bots/BotPotionManager.java` | `server.agents.capabilities.supplies.AgentPotionService`, `server.agents.capabilities.supplies.AgentAutopotPolicy`, `server.agents.capabilities.supplies.AgentPotionInventoryPolicy`, `server.agents.capabilities.supplies.AgentPassiveRecoveryPolicy`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; potion tick orchestration, autopot setup/debug reporting, low-pot supply sharing, donor selection, passive recovery, and grind-start supply reporting now live in Agent supplies |
| `src/main/java/server/bots/BotScript.java` | `server.agents.plans.AgentScript` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptContext.java` | `server.agents.plans.AgentScriptContext` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptRunner.java` | `server.agents.plans.AgentScriptRunner` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptRuntime.java` | `server.agents.plans.AgentScriptRuntimeState` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptStep.java` | `server.agents.plans.AgentScriptStep` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScrollReactionManager.java` | `server.agents.capabilities.social.AgentScrollReactionService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotSessionLifecycleSideEffects.java` | `server.agents.integration.AgentBotSessionLifecycleSideEffects` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotShopManager.java` | `server.agents.capabilities.shop.AgentShopService`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; shop visit orchestration, sell-trash visit routing, resupply/recharge purchases, shop approach selection, timeout handling, and purchase sequence callbacks now live in Agent shop |
| `src/main/java/server/bots/BotStarterKitManager.java` | `server.agents.capabilities.build.AgentStarterKitService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotTask.java` | `server.agents.plans.AgentTask` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/Emote.java` | `server.agents.capabilities.dialogue.AgentEmote` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/ReplyChannel.java` | `server.agents.commands.AgentReplyChannel` | `COMPATIBILITY_ALIAS_TEMPORARY` |
| `src/main/java/server/bots/build/BowmanBuilds.java` | `server.agents.capabilities.build.profiles.BowmanBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/BuildStep.java` | `server.agents.capabilities.build.profiles.BuildStep` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/MageBuilds.java` | `server.agents.capabilities.build.profiles.MageBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/ThiefBuilds.java` | `server.agents.capabilities.build.profiles.ThiefBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/build/WarriorBuilds.java` | `server.agents.capabilities.build.profiles.WarriorBuilds` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackDataProvider.java` | `server.agents.capabilities.combat.data.AgentAttackDataProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotAttackTiming.java` | `server.agents.capabilities.combat.data.AgentAttackTiming` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotDefenseDataProvider.java` | `server.agents.capabilities.combat.data.AgentDefenseDataProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotMobHitboxProvider.java` | `server.agents.capabilities.combat.data.AgentMobHitboxProvider` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/combat/BotWzXml.java` | `server.agents.capabilities.combat.data.AgentWzXml` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/BotLlmConfig.java` | `server.agents.capabilities.dialogue.llm.AgentLlmConfig` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/BotLlmReplyManager.java` | `server.agents.capabilities.dialogue.llm.AgentLlmReplyService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/BotMemoryStore.java` | `server.agents.capabilities.dialogue.llm.AgentMemoryStore` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/CommandTypoSuggester.java` | `server.agents.commands.AgentCommandTypoSuggester` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/OllamaClient.java` | `server.agents.capabilities.dialogue.llm.OllamaClient` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/PromptBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentPromptBuilder` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/SenderRelation.java` | `server.agents.capabilities.dialogue.llm.AgentSenderRelation` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/SituationBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentSituationBuilder` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/pq/BotKpqStage1.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage1` | `MIGRATED_TO_AGENT`; KPQ stage-1 scripted movement, coupon target, grind, exchange, and pass delivery behavior moved unchanged |
| `src/main/java/server/bots/pq/BotKpqStage5.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage5` | `MIGRATED_TO_AGENT`; KPQ stage-5 reward claim and announcement behavior moved unchanged |
| `src/main/java/server/bots/pq/BotKpqState.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqState` | `MIGRATED_TO_AGENT`; temporary BotEntry-backed KPQ state bag now uses Agent-owned type |
| `src/main/java/server/bots/pq/BotPqHooks.java` | `server.agents.capabilities.partyquest.AgentPartyQuestHooks` | `MIGRATED_TO_AGENT`; PQ tick, NPC lock, grind/follow defaults, and coupon-loot gating moved unchanged |
| `src/main/resources/db/tables/025-bot-ownership.sql` | `server.agents.legacy` documentation initially; later external registry or deletion | `LEGACY_PROFILE` |
