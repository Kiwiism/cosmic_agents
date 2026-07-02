# Agent Reconstruction Rules

The reconstruction model is: move behavior from one large bot bin into specialized Agent bins without changing observable behavior.

Rules:

1. Preserve NuTNNuT source/master bot behavior until a later removal phase explicitly changes it.
2. Refactor first, redesign behavior later.
3. Do not rename packages only and call it migrated.
4. Every moved subsystem needs parity tests before old code is deleted.
5. Temporary compatibility wrappers must be obvious and removable.
6. New Agent modules must not depend on `server.bots` as a permanent design.
7. Cosmic writes should gradually move behind `server.agents.integration` gateways.
8. Dialogue, metrics, and side effects should move toward events/listeners.
9. Profiles and policies should make old behavior replaceable without changing runtime infrastructure.
10. Removal decisions happen only after the behavior is isolated and documented.

Recent reconstruction notes:

- Common tick hook construction now lives in
  `server.agents.runtime.AgentCommonTickRuntime`. BotManager should only pass
  the temporary script-task tick callback until script tasks are reconstructed.
- Standalone move-target hook construction now lives in
  `server.agents.runtime.AgentStandaloneMoveTargetRuntime`. BotManager passes
  only legacy movement config values for this ownerless movement path.
- Movement-only tick hook construction now lives in
  `server.agents.runtime.AgentMovementOnlyRuntime`. BotManager keeps only the
  temporary follow-anchor callback and legacy movement config values for this
  path.
- Movement-core hook construction now lives in
  `server.agents.runtime.AgentMovementTickRuntime`. BotManager should pass only
  the legacy unstuck and stop-distance config until movement config ownership
  moves into Agent runtime.
- Movement-only map-change hook construction now lives in
  `server.agents.runtime.AgentMovementOnlyMapChangeRuntime`. Keep it separate
  from the broader movement-only tick pipeline until that pipeline can move as
  its own focused slice.
- Map transition hook construction now lives in
  `server.agents.runtime.AgentMapTransitionRuntime`. BotManager should pass
  only temporary mode callbacks until follow/grind mode commands are fully
  reconstructed out of BotManager.
- Recovery teleport hook construction now lives in
  `server.agents.runtime.AgentRecoveryTeleportRuntime`. BotManager should pass
  only legacy distance thresholds until movement recovery configuration is
  moved into Agent-owned runtime config.
- Follow map-sync hook construction now lives in
  `server.agents.runtime.AgentFollowMapSyncRuntime`. The temporary public
  `BotMovementManager.resetEntryState` bridge exists only until movement state
  reset behavior moves fully into Agent movement modules.
- Idle/trade physics hook construction now lives in
  `server.agents.runtime.AgentIdlePhysicsRuntime`. The temporary public
  `BotPhysicsEngine.resolveIdleGroundStance` accessor exists only until physics
  internals move into Agent movement/physics modules.
- Attack-lock physics hook construction now lives in
  `server.agents.runtime.AgentActionLockPhysicsRuntime`. Keep this as the
  temporary bridge for attack-cooldown movement dispatch until movement is
  reconstructed out of BotMovementManager.
- Movement phase hook construction now lives in
  `server.agents.runtime.AgentMovementPhaseRuntime`. The temporary public
  BotMovementManager phase methods exist only until movement internals are
  reconstructed into Agent movement modules.
- Movement stuck-detection hook construction now lives in
  `server.agents.runtime.AgentStuckDetectionRuntime`. Keep temporary
  `BotMovementManager` and `BotPhysicsEngine` calls isolated there until the
  movement bin is reconstructed; BotManager should pass only the legacy enable
  flag.
- Death respawn hook construction now lives in
  `server.agents.runtime.AgentRespawnRuntime`. Keep the temporary direct
  dependencies on `BotPhysicsEngine` and `BotMovementManager` there until the
  movement/physics bins are reconstructed; `BotManager` should only delegate
  respawn recovery.
- Tick preflight hook wiring now lives in
  `server.agents.runtime.AgentTickPreflightRuntime`. BotManager no longer owns
  the airshow, skip-delay, removed-Agent cleanup, heartbeat, pending-offer
  expiry, AI cadence, movement tick, AI tick, or heartbeat interval hook bundle
  for tick preflight.
- Tick failure side-effect wiring now lives in
  `server.agents.runtime.AgentTickFailureRuntime`. BotManager supplies only the
  temporary stop-mode callback and logger while Agent runtime owns movement
  reset, runtime removal, forced-idle reply delivery, missing-entry logging,
  warning/error log formatting, and failure escalation hook wiring.
- Spawn hook wiring now lives in `server.agents.runtime.AgentSpawnRuntime`.
  BotManager supplies only the temporary spawned-registration and follow-start
  callbacks plus logger while Agent runtime owns ownership resolution, spawn
  position resolution, offline load delegation, online placement, cross-map
  force-change handling, and SQL-failure warning wiring.
- Relogin hook wiring now lives in `server.agents.runtime.AgentReloginRuntime`.
  BotManager supplies only the temporary spawned-registration callback and
  logger while Agent runtime owns leader lookup, spawn position resolution,
  offline load delegation, delayed scheduling, return announcement delivery,
  delay selection, and SQL-failure warning wiring.
- Transfer lifecycle hook wiring now lives in
  `server.agents.runtime.AgentTransferRuntime`. BotManager supplies only the
  temporary stop-mode and registration callbacks while Agent runtime owns
  mutable live-entry lookup, target lookup, control authorization, task
  cancellation, delayed greeting scheduling, reply delivery, delay selection,
  and legacy transfer greeting selection.
- Recruit lifecycle hook wiring now lives in
  `server.agents.runtime.AgentRecruitRuntime`. BotManager supplies only the
  temporary registration callback while Agent runtime owns ownerless online
  Agent lookup and control authorization hook construction.
- Dismiss lifecycle hook wiring now lives in
  `server.agents.runtime.AgentDismissRuntime`. BotManager supplies only the
  temporary stop-mode callback while Agent runtime owns scheduled-task
  cancellation, delayed farewell scheduling, reply delivery, delay selection,
  and legacy farewell text selection.
- Agent registration hook wiring now lives in
  `server.agents.runtime.AgentRegistrationRuntime`. BotManager supplies only
  the temporary live tick callback while Agent runtime owns the legacy tick
  period, timer registration, replacement-task cancellation, default formation,
  spawn normalization, and spawn-status delay hook construction.
- Offline Agent load hook wiring now lives in
  `server.agents.runtime.AgentOfflineLoadRuntime`. BotManager keeps only the
  `loadOfflineBot` compatibility delegate while Agent runtime owns the
  BotClient creation, DB character load, disease restore, map lookup, spawn
  position, rate initialization, channel/world/map registration, and map-add
  hook wiring.
- Spawn placement hook wiring now lives in
  `server.agents.runtime.AgentSpawnPlacementRuntime`. BotManager no longer owns
  the legacy BotPhysics/BotMovement placement hook list for online placement or
  spawn normalization; the same teleport, movement reset, death-state clear,
  map tracking, graph warmup, cadence reset, broadcast invalidation, movement
  broadcast, HP update, and party join hooks are preserved. The legacy
  foothold-index builder is temporarily public until movement internals are
  reconstructed into Agent movement modules.
- Spawn SQL failure handling now lives in
  `server.agents.runtime.AgentLifecycleService.spawnAgentForLeaderQuietly`.
  BotManager supplies the temporary logger hook and compatibility result
  mapping, while Agent lifecycle owns the try/catch boundary and the same
  `"Failed to load bot character '<name>'."` failure text.
- Relogin SQL failure handling now lives in
  `server.agents.runtime.AgentLifecycleService.reloginAgentQuietly`.
  BotManager supplies the temporary logger hook, but Agent lifecycle owns the
  try/catch boundary and the same false-return-on-failure behavior.
- Leader-scoped Agent removal map wiring now lives in
  `server.agents.runtime.AgentRuntimeCleanupService.removeAgentsForLeader`.
  BotManager no longer passes the live registry, formation map, and town-anchor
  map into lifecycle removal; it delegates to the Agent cleanup service while
  preserving scheduled tick cancellation and leader-state cleanup.
- Scheduled tick cancellation for lifecycle removal now lives in
  `server.agents.runtime.AgentLifecycleService.cancelScheduledTickIfPresent`.
  BotManager's leader-removal compatibility wrapper delegates to the Agent
  lifecycle helper, preserving the same null/no-task guard and
  `ScheduledFuture.cancel(false)` behavior.
- Pending-offer chat response hook wiring now lives in
  `server.agents.capabilities.trade.AgentPendingOfferChatRouteService`.
  BotManager keeps only the temporary live-entry-group source. The same expiry,
  target validation, targeted-command resolution, response handling, and
  speaker feedback wiring are preserved.
- Top-level chat ingress ordering now lives in
  `server.agents.capabilities.dialogue.AgentChatIngressService`. BotManager
  keeps temporary hooks for pending-offer response routing, recruit/transfer/
  formation/dismiss command routing, active-entry lookup, targeted routing, and
  untargeted routing. The same early-return order and no-entry fall-through are
  preserved.
- Untargeted chat routing now lives in
  `server.agents.capabilities.dialogue.AgentUntargetedChatRouteService`.
  BotManager keeps temporary hooks for follow-target command application,
  group-supply classification/responder selection, reply-channel state,
  Agent chat dispatch, typo suggestions, and reply queueing. The same
  follow-all priority, single group-supply responder, one-shot typo suggestion,
  and broadcast-to-all fallback are preserved.
- Targeted name-prefix chat routing now lives in
  `server.agents.capabilities.dialogue.AgentTargetedChatRouteService`.
  BotManager keeps temporary hooks for targeted-command resolution,
  follow-target command application, reply-channel state, typo suggestions,
  Agent chat dispatch, command activity recording, LLM fallback, and leader
  feedback. The same targeted follow shortcut, typo reply, owner-command
  recording gate, LLM fall-through, and feedback behavior are preserved.
- Pending loot-offer target validation now lives in
  `server.agents.capabilities.trade.AgentPendingOfferResponseService`.
  BotManager keeps only the temporary route-entry hook. The same pending-offer,
  recipient, and same-map checks are preserved.
- Follow-target candidate assembly now lives in
  `server.agents.runtime.AgentFollowTargetCandidateService`. BotManager keeps
  only a temporary sibling-entry lookup hook. The same leader, party-member,
  online sibling-Agent ordering and duplicate filtering are preserved.
- Follow-target command application now lives in
  `server.agents.runtime.AgentFollowTargetCommandService`. BotManager keeps
  temporary hooks for target resolution, reply queueing, delay scheduling,
  auto-equip, potion-share checks, and follow-mode entry. The same skip rules,
  reply variants, random delay window, and side-effect order are preserved.
- Follow-target name resolution now lives in
  `server.agents.runtime.AgentFollowTargetResolutionService`. BotManager keeps
  temporary candidate-list assembly and follow-mode application hooks. The same
  exact-name match, two-letter prefix rule, ambiguous-target message, and
  missing-target message are preserved.
- Transfer chat command routing now lives in
  `server.agents.runtime.AgentTransferCommandService`. BotManager keeps a
  temporary compatibility hook bundle for transfer lifecycle delegation and
  leader yellow-message delivery. The same parser, no-op fall-through,
  success message, and lifecycle error messages are preserved.
- Recruit chat command parsing now lives in
  `server.agents.runtime.AgentRecruitCommandService`. BotManager keeps a
  temporary compatibility hook bundle for ownerless-Agent recruitment and
  leader yellow-message delivery. The same `recruit`, `adopt`, `hire`, and
  `claim` aliases plus the legacy success/error reply behavior are preserved.
- Dismiss chat command parsing now lives in
  `server.agents.runtime.AgentDismissCommandService`. BotManager keeps a
  temporary compatibility hook bundle for dismiss lifecycle delegation and
  leader yellow-message delivery. The same `dismiss`, `disown`, and `release`
  aliases plus the legacy success/failure messages are preserved.
- Formation chat command parsing and formation-state mutation now lives in
  `server.agents.runtime.AgentFormationCommandService`. BotManager keeps a
  temporary compatibility hook bundle for active entries, stored formation
  state, offset application, first-Agent replies, and leader yellow messages.
  The same help text, snap status, snap on/off/default behavior, type/px
  parsing, offset application, and reply labels are preserved.
- Group supply responder selection now lives in
  `server.agents.capabilities.supplies.AgentGroupSupplyResponderSelector`.
  BotManager delegates the same group-supply routing rule: prefer an Agent on
  the leader's current map so visible replies/trades stay local, otherwise use
  the first active group entry.
- Pending loot-offer response routing now lives in
  `server.agents.capabilities.trade.AgentPendingOfferResponseService`.
  BotManager keeps a temporary compatibility hook bundle for offer expiry,
  target validation, targeted-command resolution, offer-response handling, and
  speaker feedback. The same all-entry expiry sweep, target filtering,
  targeted response handling, feedback delivery, single-match default response,
  ambiguous confirmation message, and no-match fall-through are preserved.
- Ownerless Agent recruit lifecycle now lives in
  `server.agents.runtime.AgentRecruitService`. BotManager keeps a temporary
  compatibility hook bundle for unclaimed online Agent lookup, control
  authorization, and registration under the current leader. The same missing
  ownerless-Agent message, authorization failure propagation, and registration
  behavior are preserved.
- Agent transfer between leaders now lives in
  `server.agents.runtime.AgentTransferService`. BotManager keeps a temporary
  compatibility hook bundle for current-entry lookup, same-map target lookup,
  authorization, scheduled-task cancel, stop-mode entry, re-registration,
  delayed greeting scheduling, and greeting delivery. The same no-agent,
  missing-agent, missing-target, self-target, authorization failure, remove,
  cancel, stop, register, and delayed greeting behavior is preserved.
- Offline Agent loading now lives in
  `server.agents.runtime.AgentOfflineLoadService`. BotManager keeps a temporary
  compatibility hook bundle for BotClient creation, character DB loading,
  disease restore, map resolution, spawn-position resolution, rate
  initialization, channel/world registration, and map registration. The same
  client/account binding, stored disease restore, target-map fallback, local
  stat recalculation, rate setup, entered-channel-world marker, map visit, and
  disease-expiry task ordering are preserved.
- Spawn placement and normalization runtime reset ordering now lives in
  `server.agents.runtime.AgentSpawnPlacementService`. BotManager keeps a
  temporary compatibility hook bundle for spawn-position resolution, physics
  teleport, movement reset, death-state clearing, map tracking, navigation
  warmup, tick cadence reset, movement broadcast invalidation, movement
  broadcast, party HP update, and leader-party join. The same null-entry
  online placement path, HP restoration, map-id fallback, foothold-gated
  warmup, and reset/broadcast ordering are preserved.
- Top-level tick-core sequencing now lives in
  `server.agents.runtime.AgentTickCoreService`. BotManager keeps a temporary
  compatibility hook bundle for preflight, leader resolution, inactive-leader
  handling, ownerless fallback, dead-state handling, live context setup, live
  gate dispatch, and live-mode dispatch. The same preflight early return,
  leader/offline/dead/ownerless gates, performance-enabled lookup point, and
  final live-mode handoff are preserved.
- Live tick gate ordering now lives in
  `server.agents.runtime.AgentLiveTickGateService`. BotManager keeps a
  temporary compatibility hook bundle for common tick systems, trade-window
  physics, idle-mode physics, recovery, and tracked map-change handling. The
  same all-mode common-system early return, trade-window movement suppression,
  idle-mode short-circuit, recovery ordering, and map-change snap/reset gate
  are preserved before live-mode dispatch.
- Live-mode tick ordering now lives in
  `server.agents.runtime.AgentLiveModeTickService`. BotManager keeps a
  temporary compatibility hook bundle for shop-visit, follow-opportunity,
  follow-idle fast path, scripted move combat, anchored farm, grind dispatch,
  and final movement tail execution. The same shop-target override,
  follow-opportunity target propagation, scripted-move non-propagation,
  anchored-farm short-circuit, grind fall-through target update, and final
  movement tail order are preserved.
- Live tick context setup now lives in
  `server.agents.runtime.AgentLiveTickContextService`. BotManager keeps a
  temporary compatibility hook bundle for movement-profile refresh,
  follow-anchor resolution, target snapshot capture, leader-motion tracking,
  map-change cleanup, and follow action-window cleanup. The same refresh,
  snapshot, raw leader-position, cleanup, and target-position ordering is
  preserved.
- Tick failure escalation and volatile-action cleanup now live in
  `server.agents.runtime.AgentTickFailurePolicy.handleFailure`. BotManager keeps
  only temporary hooks for logging, movement-state reset, disable/removal, and
  forced-idle reply delivery. The same missing-entry error, failure-window
  counting, pending action/drop cleanup, grind target/loot cleanup, patrol
  wander cleanup, second-failure idle fallback, third-failure disable, and
  warning/error log metadata are preserved.
- Scheduled tick guard orchestration now lives in
  `server.agents.runtime.AgentTickOrchestrator.runGuardedTick`. BotManager keeps
  `tickCore` and failure handling as temporary hooks, while the Agent
  orchestrator owns tick-total timing, success failure-reset, failure routing,
  and final performance recording.
- Common tick system ordering now lives in
  `server.agents.runtime.AgentCommonTickService`. BotManager keeps only a
  compatibility hook bundle for the existing subsystem implementations. The same
  mob-damage/death early return, monster-control release, trade-window passive
  loot suppression, potion/recovery/build/AFK/trade/PQ/script ordering, NPC-lock
  early return, action-lock decay, AI-only combat cache/heal/buff/pot sequence,
  performance labels, and final action-locked physics return are preserved.
- Dismiss-by-name lifecycle now lives in
  `server.agents.runtime.AgentLifecycleService.dismissAgentByName`. BotManager
  keeps `dismissBot` as a compatibility wrapper and supplies temporary hooks
  for task cancellation, stop-mode entry, delayed scheduling, reply delivery,
  delay selection, and legacy farewell text selection. The same missing-entry
  false return, registry removal, cancel-before-stop order, delayed farewell,
  and reply text pool are preserved.
- Monster-control release now lives in
  `server.agents.runtime.AgentMonsterControlService`. BotManager's common tick
  calls the Agent service directly, preserving the same controlled-monster scan
  and `aggroRedirectController` handoff for every monster assigned to a headless
  Agent.
- Death respawn recovery now lives in
  `server.agents.runtime.AgentDeathTickService.respawnNearLeader`. BotManager
  keeps only temporary hooks for map-change portal selection, ground lookup,
  physics teleport, movement reset, movement broadcast, and map chat. The same
  death-state clear, HP restore, cross-map leader warp, ground fallback,
  `"back!"` line, and glare face expression are preserved.
- Offline Agent relogin orchestration now lives in
  `server.agents.runtime.AgentLifecycleService.reloginAgent`. BotManager keeps
  `reloginBot` as a compatibility wrapper and supplies temporary hooks for
  leader lookup, spawn-position resolution, offline loading, registration,
  delayed scheduling, and map chat. The same leader-offline skip, leader-map
  spawn positioning, offline character load, spawned registration, delayed
  `"back!!"` line, and happy face expression are preserved.
- Live Agent registration now lives in
  `server.agents.runtime.AgentLifecycleService.registerAgent`. BotManager keeps
  `registerBot` and `registerSpawnedBot` as compatibility wrappers and supplies
  temporary hooks for tick scheduling, tick callback dispatch, replacement-task
  cancellation, spawn normalization, and status-check delay selection. The same
  same-character replacement, captured-entry tick lambda, movement-profile
  refresh, navigation warmup, formation offset application, optional spawn
  normalization, and delayed spawn status check are preserved.
- Spawn-for-leader branching now lives in
  `server.agents.runtime.AgentLifecycleService.spawnAgentForLeader`. BotManager
  keeps the public `spawnBotForOwner` compatibility API and supplies temporary
  hooks for spawn-position resolution, online placement, offline DB loading,
  registry mutation, map-change portal choice, and follow startup. The same
  missing-character, real-player-online, authorization, controlled-by-other-
  leader, online placement, offline loading, and auto-registration result
  behavior is preserved.
- Active Agent lookup helpers now live behind live-store methods on
  `server.agents.runtime.AgentRuntimeRegistry`. BotManager and the temporary
  Agent session lifecycle gateway keep compatibility delegates, but no longer
  need to pass the backing registry map into lookup helpers for active leader,
  first Agent, named Agent, character-id, entry-list, or first-entry checks.
- Ownerless online Agent lookup now lives in
  `server.agents.runtime.AgentRuntimeRegistry`. BotManager's recruit path
  delegates to the registry for the same server/channel scan, BotClient guard,
  and active-leader exclusion behavior.
- Whisper command ingress now lives in
  `server.agents.capabilities.dialogue.AgentWhisperCommandService`.
  `WhisperHandler` calls the Agent service directly, and BotManager keeps a
  compatibility delegate. The same null checks, BotClient target guard,
  leader-owned active Agent lookup, whisper reply-channel assignment, and Agent
  chat runtime dispatch are preserved.
- Agent runtime cleanup/removal by backing character ID now lives in
  `server.agents.runtime.AgentRuntimeCleanupService`. Character client swap,
  BotClient disconnect, and delete-character cleanup call the Agent runtime
  service directly; BotManager keeps compatibility delegates. Scheduled-task
  cancellation, live registry removal, formation cleanup, town-cluster cleanup,
  and bot-only autopot cleanup remain behavior-equivalent.
- Inactive-leader town-cluster anchor storage now lives in
  `server.agents.runtime.AgentLeaderSafetyService`. BotManager keeps a
  compatibility reference to the Agent-owned map while existing leader-safety
  return cleanup, town scroll clustering, and lifecycle removal semantics remain
  unchanged.
- Bot autopot/potion retry requests now live in
  `server.agents.capabilities.supplies.AgentPotionCheckRequestService`.
  Character stat-loss autopot and pet-autopot depletion paths call the Agent
  service directly; BotManager keeps a compatibility delegate. BotClient-only
  filtering, active leader/entry lookup, and configured retry-delay clamping
  are unchanged.
- Scroll reaction notification scheduling now lives in
  `server.agents.capabilities.social.AgentScrollReactionNotificationService`.
  Normal scrolls and cash-item scrolls call the Agent service directly;
  BotManager keeps a compatibility delegate. Delay clamping and forwarding into
  `AgentScrollReactionService` over the live Agent registry are unchanged.
- Owner item/equipment pickup and trade-item notification routing now lives in
  `server.agents.capabilities.trade.AgentOwnerItemNotificationService`. Shop
  equip purchases, field item pickup, and trade completion call the Agent
  service directly; BotManager keeps compatibility delegates. Equip-only
  filtering, delayed upgrade scan scheduling, and circular own-Agent trade
  suppression are unchanged.
- BotManager party Bot quest start/progress/complete mirroring now lives in
  `server.agents.capabilities.quest.AgentPartyQuestSyncService`. Quest start,
  force-start, completion, force-completion, and Character quest-progress hooks
  call the Agent service directly; BotManager keeps compatibility delegates.
  The same party-member filtering, BotClient-only Agent selection, already-
  started skip, forced action calls, progress update, and Maple Administrator
  NPC fallback are preserved.
- BotEquipHandler active-Agent count and character-list lookup now reads
  `server.agents.runtime.AgentRuntimeRegistry` directly. BotManager keeps
  `spawnedBotCount` and `getOwnedBotCharacters` as compatibility delegates, and
  the same active-entry count plus null-agent filtering behavior is preserved.
- BotManager bot-only autopot cleanup now lives in
  `server.agents.runtime.AgentAutopotRuntimeCleanupService`. Runtime release
  still removes the live entry first, then clears HP/MP autopot alerts and
  normalizes pet autopot keys 91/92 to type 7 with the same action values.
- BotManager spawn-position grounding now lives in
  `server.agents.runtime.AgentSpawnPositionService`. The same null map/position
  bypass, one-pixel-above ground lookup, and desired-position fallback are
  preserved; production still uses `BotPhysicsEngine.findGroundPoint` as a
  temporary physics seam.
- BotManager post-spawn party join orchestration now lives in
  `server.agents.runtime.AgentPartyLifecycleService`. `BotManager.joinBotToOwnerParty`
  remains only as a compatibility delegate, while Agent spawn command and
  Messenger respawn paths call the Agent runtime service directly. Party
  creation, same-party online refresh, different-party leave, join, and HP
  update behavior are unchanged.
- BotManager inactive-leader town-warp eligibility now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The same null-map,
  alive-monster, and different-return-map requirements are preserved; BotManager
  still owns the temporary offline/dead leader side effects and town-cluster
  movement wiring.
- BotManager inactive-leader idle preparation now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The reset order and state
  effects are unchanged: script tasks, shop visit, mode, move target, grind
  target, degenerate attack, buff consumables, and away-safe-mode state.
- BotManager active-leader return cleanup now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The away-safe no-op,
  inactive-state clear, move-target clear, and single welcome-back announcement
  rule are unchanged; BotManager still owns the temporary town-anchor map and
  visible announcement callback.
- BotManager inactive-leader timer gating now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The first inactive tick,
  returned-to-town away-safe timer start, and configured delay comparison are
  unchanged; BotManager still owns the safe-mode side effects after the gate.
- BotManager non-town inactive safe-mode idle now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The idle-on-ground,
  movement-broadcast, and returned-to-town state order is unchanged; BotManager
  still supplies the temporary physics and packet callbacks.
- BotManager inactive-town cluster target calculation now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The formation index,
  platform-edge clamp, map-bounds clamp, ground lookup, and anchor fallback
  order are unchanged; BotManager still supplies temporary formation storage and
  ground-physics callbacks.
- BotManager inactive-town return completion state sequencing now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. No-return-map handling and
  cluster movement still mark returned-to-town at the same point; BotManager
  still supplies the temporary movement reset and precise move-start callbacks.
- BotManager inactive safe-mode entry branching now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. Preparation still runs
  before the town-vs-idle branch, and the town branch still returns the scroll
  result while idle returns false.
- BotManager inactive town-scroll orchestration now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The null-map, no-return-map,
  idle-before-scroll, return-scroll fallback, map-change grounding, cluster
  anchor, target resolution, movement reset, precise move start, and returned
  state order are unchanged; BotManager still supplies Cosmic side-effect
  callbacks.
- BotManager first-entry/representative lookup now delegates to
  `server.agents.runtime.AgentRuntimeRegistry`. BotManager still owns the
  temporary runtime map, but the first-entry predicate is Agent-owned.
- BotManager leader away-safe-mode entry loop now delegates to
  `server.agents.runtime.AgentLeaderSafetyService`. The no-map skip and
  `town && eligible` behavior are unchanged; BotManager still supplies
  temporary runtime snapshots and safe-mode callbacks.
- BotManager script-task start dispatch now delegates to
  `server.agents.runtime.AgentScriptTaskStartService`. The same task-type to
  side-effect mapping is preserved; BotManager still supplies temporary move,
  follow, grind, stop, and drop callbacks.
- BotManager script-task tick loop now delegates to
  `server.agents.runtime.AgentScriptTaskTickService`. The no-agent skip,
  activate-next behavior, start-on-activation, incomplete retention, and
  clear-and-continue loop are unchanged.
- BotManager script-task queue helpers now delegate to
  `server.agents.runtime.AgentScriptTaskQueueService`. Clear-and-bump,
  null-safe queueing, move queueing, move-then-drop, follow-then-drop, and
  queued-task checks are unchanged.
- Agent script context and runner queue operations now call
  `server.agents.runtime.AgentScriptTaskQueueService` directly instead of
  routing through BotManager. The same queued task shapes and clear-and-bump
  behavior are preserved.
- Agent script context now receives a narrow cheap-move callback instead of a
  `BotManager` instance, plus a narrow drop-item callback for KPQ coupon drops.
  `AgentScriptRunner` still supplies the current BotManager-backed callbacks
  temporarily, preserving the exact cheap-move and drop results while shrinking
  the context dependency.
- BotManager local near-distance checks now delegate to
  `server.agents.runtime.AgentPositionService`. The null handling and
  inclusive per-axis distance behavior are unchanged.
- BotManager move/farm/patrol/follow/grind/stop command-mode preparation now delegates to
  `server.agents.runtime.AgentCommandModeService`. The null-entry skip and
  guard-before-clear, clear-script-tasks, cancel-shop, start-mode order are
  unchanged.
- Agent movement command facade now calls Agent runtime mode/queue services
  directly for follow-owner, stop, move-to, farm-here, and grind. Patrol still
  delegates to BotManager temporarily for graph-region validation and the
  visible "can't find a patrol region here" reply.
- Build, potion, and combat ammo fallback paths now request follow-owner via
  `AgentBotMovementCommandRuntime` instead of direct BotManager calls. The same
  follow-owner mode transition and visible dialogue behavior are preserved.
- Session relog/logout/away prompts and equipment unequip-all now request stop
  via `AgentBotMovementCommandRuntime` instead of direct BotManager calls. The
  same pending-action, reply timing, and equipment side effects are preserved.
- Patrol command graph-region validation, visible failure reply, and mode-state
  transition now live in `AgentBotMovementCommandRuntime`; `BotManager.issuePatrol`
  is a compatibility delegate.
- Session first-agent checks, away-town offer checks, and away-safe command
  routing now enter through `AgentBotSessionControlRuntime`; actual away-safe
  map/state side effects still use the temporary BotManager lifecycle bridge.
- Support-heal jump-anchor resolution now uses `AgentFollowAnchorService` and
  the Agent session lifecycle gateway instead of calling `BotManager` directly;
  heal targeting, cooldown, jump, and packet behavior are unchanged.
- Combat grind region-occupancy scoring now reads sibling Agents through the
  Agent session lifecycle gateway instead of directly calling BotManager; the
  occupancy filter and penalty math are unchanged.
- Ammo-share donor selection, potion-share donor selection, and sibling gear
  offer scans now read sibling Agents through the Agent session lifecycle
  gateway; donor scoring, cooldowns, and offer behavior are unchanged.
- Bot-inventory-drop loot delay detection now asks the Agent session lifecycle
  gateway whether a drop owner is an active Agent, instead of calling
  `BotManager.getActiveOwnerByBotCharId` directly.
- Airshow named-Agent lookup now enters through the Agent session lifecycle
  gateway instead of calling `BotManager.getBotEntry` directly; validation
  messages and airshow side effects are unchanged.
- Manual trade greeting selection now lives in `AgentDialogueCatalog` and
  `AgentTradeDialogueService`; `BotManager.manualTradeGreeting` remains only a
  compatibility delegate.
- Navigation debug overlay bot selection now reads Agent entries through the
  Agent session lifecycle gateway instead of direct BotManager lookups; overlay
  messages and path logging behavior are unchanged.
- BotNavigationManager follow-anchor region resolution now uses
  `AgentFollowAnchorService` with sibling entries from the Agent session
  lifecycle gateway instead of calling `BotManager.resolveFollowAnchor`;
  follow, sibling-target, and rope/climb targeting behavior are unchanged.
- BotManager follow-owner, grind, and stop compatibility hooks now delegate to
  `AgentBotMovementCommandRuntime`; command preparation, script-task clearing,
  shop cancellation, mode transitions, and navigation clearing remain
  behavior-equivalent.
- Return-scroll use for inactive leader safety now lives in
  `AgentReturnScrollService`; BotManager only calls the Agent runtime helper
  from the existing leader-safety callback, preserving 2030000 lookup, effect
  application, and one-scroll consumption behavior.
- Swim-map classification now lives in `AgentMapEnvironmentService`; BotManager
  movement, idle-physics, and action-lock paths call the Agent runtime helper
  while preserving the same null-map and `MapleMap.isSwim()` behavior.
- Grind-loot retry suppression now uses
  `AgentBotGrindLootStateRuntime::isRetrySuppressed` directly; BotManager no
  longer owns the pass-through predicate used by Agent loot targeting.
- Script-driven item dropping now lives in `AgentScriptItemActionService`;
  BotManager keeps only the compatibility `issueDropItem` wrapper, and
  `AgentScriptRunner` calls the Agent service directly for script contexts.
- Legacy command combat-config routing now calls `AgentCombatConfig` directly
  through `AgentLegacyCommandBridge` instead of passing through BotManager's
  static compatibility wrappers.
- Random dialogue selection now lives in `AgentDialogueSelector`; BotManager's
  `randomReply` is a compatibility delegate and Agent capability/runtime modules
  no longer call it directly.
- Random delay selection now lives in `AgentRandom`; BotManager's `randMs`
  remains a compatibility delegate, and Agent fidget/shop timing uses the
  Agent-owned helper while preserving the same lower-inclusive, upper-exclusive
  delay behavior.
- Script cheap-move target classification now lives in
  `AgentScriptMoveTargetService`; BotManager's `isCheapScriptMoveTarget`
  remains a compatibility delegate that supplies the legacy loot-radius config,
  and `AgentScriptRunner` uses the Agent-owned runtime bridge directly.
- General runtime config now lives in `AgentRuntimeConfig`; `BotManager.cfg`
  remains a compatibility alias to the same mutable config object, while Agent
  reporting, shop, supplies, inventory adapters, and script move checks read
  config through the Agent-owned runtime config.
- Live formation storage now lives in `AgentFormationService`; BotManager still
  routes formation commands and lifecycle cleanup through compatibility calls,
  but no longer owns the in-memory leader-to-formation map.
- Follow-target position resolution now lives in
  `AgentFollowTargetPositionService`, and `AgentBotMovementTargetSideEffects`
  captures movement snapshots directly through Agent runtime services instead
  of calling `BotManager.captureTargetSnapshot`.
- Live runtime entry storage now lives in `AgentRuntimeRegistry`; BotManager's
  `bots` field remains a compatibility alias, and Agent session lookup bridges
  read entries directly from the Agent-owned registry.
- BotManager target snapshot assembly moved to
  `server.agents.runtime.AgentTargetSnapshotService`. BotManager still supplies
  temporary follow-anchor, formation, and follow-target-position callbacks, but
  primary target precedence and snapshot construction are Agent-owned.
- BotManager target snapshot data lives in `server.agents.runtime.AgentTargetSnapshot`;
  movement/navigation callers and tests consume the Agent-owned record.
- BotManager formation type/state and follow-offset application moved to
  `server.agents.runtime.AgentFormationService`. BotManager still owns the
  formation command text flow and temporary per-leader formation map, but the
  formation model and legacy offset math are Agent-owned.
- BotManager tick failure counting and escalation thresholds moved to
  `server.agents.runtime.AgentTickFailurePolicy`. BotManager still performs
  failure cleanup, logging, forced idle, and removal side effects with the same
  3-failure/10-second behavior.
- BotManager AI-cadence consumption and tick timestamp recording now enter
  through `server.agents.runtime.AgentTickOrchestrator.prepareTick`. The same
  movement tick, AI tick, accumulator, and last-tick semantics are preserved.
- BotManager leader-wide removal and agent-character removal map mutation now
  delegates to `server.agents.runtime.AgentLifecycleService`. BotManager still
  supplies the scheduled-task cancellation callback and keeps the temporary
  backing maps, preserving cleanup timing while moving removal rules into Agent
  runtime.
- BotManager active-runtime lookup helpers for agent ID, agent name, active
  leader, first agent, first entry, and per-leader entry snapshots now delegate
  to `server.agents.runtime.AgentRuntimeRegistry`. The live storage map remains
  in BotManager for this slice, preserving registration/removal behavior while
  moving lookup ownership into Agent runtime.
- Agent performance monitor inventory/trade section notes now point at the
  Agent-owned looting/trade runtime services instead of the temporary
  `BotInventoryManager` compatibility shell. Section keys and timing behavior
  are unchanged.
- Bot-inventory runtime hook factory ownership moved from `BotInventoryManager`
  to `server.agents.integration.AgentBotInventoryRuntimeAdapters`. The bot
  inventory class is now a compatibility shell over Agent-owned looting,
  transfer, manual-trade, trade-tick, and availability runtimes.
- The dead `BotInventoryManager.collectItems` compatibility helper was removed
  after passive loot, trade tick, transfer availability, and trade collection
  paths all routed through Agent-owned runtime services.
- Passive loot runtime callback assembly moved from `BotInventoryManager` to
  `server.agents.capabilities.looting.AgentPassiveLootRuntimeService`. The bot
  inventory shell still supplies temporary loot-inhibit, trade-sequence,
  cooldown, config, reply, owner, offer, item-presence, auto-equip, cleanup,
  and pickup hooks while Agent looting owns passive-loot callback construction.
- Transfer availability/count runtime callback assembly moved from
  `BotInventoryManager` to
  `server.agents.capabilities.trade.AgentTradeTransferAvailabilityRuntimeService`.
  The bot inventory shell still supplies temporary owner, named-item, and
  equipped-slot counter hooks while Agent trade owns the availability/count
  callback construction and item collection hookup.
- Trade tick runtime callback assembly moved from `BotInventoryManager` to
  `server.agents.capabilities.trade.AgentTradeTickRuntimeService`. The bot
  inventory shell still supplies temporary timing, current-trade, owner,
  recipient, and equipment-refill hooks while Agent trade now owns the
  between-batch, closed-window, invite-wait, item-add, confirmation, and
  lifecycle wiring for active trade sequences.
- Trade lifecycle runtime callback construction moved from `BotInventoryManager`
  to `server.agents.capabilities.trade.AgentTradeLifecycleRuntimeService`. The
  bot inventory shell still supplies temporary restore, manual-clear, owner,
  refill, reply-delay, and reply-pool hooks while Agent trade owns the callback
  assembly plus freebie/glare randomness.
- Manual-trade runtime callback assembly moved from `BotInventoryManager` to
  `server.agents.capabilities.trade.AgentManualTradeRuntimeService`. The bot
  inventory shell still supplies temporary runtime hooks for active-sequence
  suppression, movement countdown, tick cadence, peer authorization, greeting
  text, and equipment refill, while the Agent trade runtime now owns the
  manual/peer/owner branch wiring.
- Trade item collection plus ammo/equip classification runtime composition moved
  from `BotInventoryManager` to
  `server.agents.capabilities.trade.AgentInventoryTradeRuntimeService`. The bot
  inventory shell still supplies temporary runtime hooks for recommendations,
  equipped weapon type, projectile WATK, quest-item checks, untradeable config,
  equip profiling, self-reserve items, reservation checks, and owner lookup,
  while the Agent trade capability now owns how those hooks are assembled.
- Grouped trade category navigation moved from `BotInventoryManager` private
  helpers to `server.agents.capabilities.trade.AgentTradeGroupNavigationService`.
  Equip and ammo "next group" selection still delegates to the same Agent-owned
  equip/ammo group policies.
- Recommended equipment trade item selection moved from `BotInventoryManager`
  to `server.agents.capabilities.trade.AgentTradeRecommendationService`. The
  legacy null-owner empty-list behavior and recommendation collector call are
  unchanged.
- Equip trade slow-classification warning ownership moved from
  `BotInventoryManager` to
  `server.agents.capabilities.inventory.AgentEquipTradeSlowLogService`. The
  legacy 50 ms threshold, one-decimal millisecond formatting, and warning fields
  are unchanged.
- Manual trade's legacy 60-second timeout constant moved from
  `BotInventoryManager` to
  `server.agents.capabilities.trade.AgentManualTradeService`. The bot inventory
  shell now calls the Agent-owned default timeout path while preserving the same
  timeout duration and tick-down behavior.
- `BotInventoryManager` no longer owns the private trade-sequence/open-batch
  wrapper cluster. Legacy sequence runtime wiring moved to
  `server.agents.capabilities.trade.AgentTradeSequenceRuntimeService`, including
  recipient resolution, unavailable-recipient cancellation, trade-start/invite
  calls, invitation dialogue selection, and reply delivery.
- `BotInventoryManager` transfer availability/count callback construction moved
  to `server.agents.capabilities.trade.AgentTradeTransferAvailabilityCallbackService`.
  The bot inventory shell still supplies temporary named-item, equipped-slot,
  and category collection hooks while the availability callback object is
  Agent-owned.
- Trade dialogue reply selection moved to
  `server.agents.capabilities.trade.AgentTradeDialogueService`. Bot inventory
  and inventory transfer code now call Agent-owned selectors for invitation,
  all-done, thanks, freebie, and reserved-equipment group replies while keeping
  the same `BotManager.randomReply` selection behavior and dialogue pools.
- Duplicate reserved-equip group reply selection was removed from
  `BotInventoryManager`; between-batch trade progression now uses
  `server.agents.capabilities.trade.AgentInventoryTransferService.equipsGroupMessage`
  for the same Agent dialogue catalog reply pools.
- `BotInventoryManager` equip-trade classification callback construction moved
  to `server.agents.capabilities.inventory.AgentEquipTradeCallbackService`. The
  bot inventory shell still supplies temporary profiling, bag-scan,
  self-reserve, reservation, owner, and slow-log hooks while the equip
  classification callback object is Agent-owned.
- `BotInventoryManager` ammo-trade callback construction moved to
  `server.agents.capabilities.inventory.AgentAmmoTradeCallbackService`. The bot
  inventory shell still supplies temporary weapon-type, projectile-WATK,
  quest-item, and untradeable-config hooks while the ammo callback object is
  Agent-owned.
- `BotInventoryManager` item-collection callback construction moved to
  `server.agents.capabilities.trade.AgentTradeItemCollectionCallbackService`.
  The bot inventory shell still supplies temporary recommended-item,
  equip-group, and ammo-group hooks while the collection callback object is
  Agent-owned.
- `BotInventoryManager.tickPassiveLoot` passive-loot callback construction moved
  to `server.agents.capabilities.looting.AgentPassiveLootCallbackService`. The
  bot inventory shell still supplies temporary runtime-state, countdown,
  config, reply, owner, auto-equip, offer, cleanup, and pickup hooks while the
  passive-loot callback object is Agent-owned.
- `BotInventoryManager` trade lifecycle callback construction moved to
  `server.agents.capabilities.trade.AgentTradeLifecycleCallbackService`. The
  bot inventory shell still supplies temporary restore, manual-clear, owner,
  refill, reply-delay, reply-pool, and reaction-randomness hooks while the
  lifecycle callback object is Agent-owned.
- `BotInventoryManager.tickManualTrade` callback construction moved to
  `server.agents.capabilities.trade.AgentManualTradeCallbackService`. The bot
  inventory shell still supplies the same temporary active-sequence, timeout,
  authorization, invite-accept, greeting, completion, and refill hooks while the
  manual/peer/owner callback objects are Agent-owned.
- `BotInventoryManager.tickTrade` item-add callback construction moved to
  `server.agents.capabilities.trade.AgentTradeItemAddTickCallbackService`.
  The bot inventory shell still wires the exact legacy insufficient-meso cancel
  reply, all-done reply pool, and delay callbacks, but the callback object is
  Agent-owned.
- `BotInventoryManager.tickTrade` between-batch callback construction moved to
  `server.agents.capabilities.trade.AgentTradeBetweenBatchCallbackService`.
  The bot inventory shell still supplies temporary item-collection, equip/ammo
  group selection, open-batch, and reset hooks, but the callback object is now
  Agent-owned and uses the same countdown and batch-advance behavior.
- Shop visit and purchase orchestration has moved from
  `server.bots.BotShopManager` to
  `server.agents.capabilities.shop.AgentShopService`. Bot manager and utility
  callbacks now call the Agent-owned service while preserving the same
  resupply triggers, sell-trash visit routing, NPC approach timing, stuck/timeout
  behavior, purchase/recharge order, delayed step scheduling, and visible shop
  dialogue.
- Gear offer orchestration has moved from `server.bots.BotOfferManager` to
  `server.agents.capabilities.trade.AgentOfferService`. Bot manager,
  inventory, active-mode, build-status, supply, and offer runtime callbacks now
  call the Agent-owned service while preserving the same owner/sibling offer
  selection, reservation checks, confirmation parsing, prompt text, delayed
  trade transfer timing, and pending-offer state behavior.
- Potion supply orchestration has moved from `server.bots.BotPotionManager` to
  `server.agents.capabilities.supplies.AgentPotionService`. Legacy bot manager,
  shop, movement, and supply callbacks now call the Agent-owned service while
  preserving the same autopot selection, low-supply request/backoff timing,
  donor selection, passive recovery, map-visible dialogue, and transfer timing.
- Combat config ownership moved to
  `server.agents.capabilities.combat.AgentCombatConfig`. `BotCombatManager.cfg`
  remains as a compatibility alias to the same live config object, so existing
  bot config commands and tests keep the same behavior while Agent combat code
  no longer reads config through `BotCombatManager`. Combat config field
  listing, case-insensitive lookup, live mutation, and legacy value parsing now
  also live on `AgentCombatConfig`; `BotCombatManager` keeps compatibility
  wrappers for existing command paths.
- Combat ammo counting ownership moved to
  `server.agents.capabilities.combat.AgentCombatAmmoCounter`. Ranged-ammo weapon
  classification, Soul Arrow/Shadow Claw unlimited-ammo behavior, and USE
  inventory arrow/star/bullet counting are unchanged; `BotCombatManager`
  retains compatibility wrappers for legacy bot-package callers.
- Client projectile hitbox ownership moved to
  `server.agents.capabilities.combat.AgentProjectileHitbox`. The Journey client
  base range, near inset, default vertical band, passive Eye of Amazon/Keen
  Eyes range bonus lookup, and horizontal scaling behavior are unchanged;
  `BotCombatManager` keeps compatibility wrappers for legacy skill-planning
  callers until that planner is extracted.
- Fall-damage curve ownership moved to
  `server.agents.capabilities.combat.AgentFallDamageCalculator`. The threshold,
  saturating-knee formula, linear tail, rounding behavior, and captured
  real-client sample outputs are unchanged; `BotCombatManager` keeps only a
  compatibility wrapper used by the current fall-damage side-effect path.
- Combat skill classification ownership moved to
  `server.agents.capabilities.combat.AgentCombatSkillClassifier`. Party-support
  skill ids, non-damage active-skill exclusions, offensive WZ-shape detection,
  rebuffable support filtering, summon statup detection, heal-skill checks,
  learned-skill cache signatures, and best single-target attack-skill
  comparison, plus cached attack/AOE skill id fallback ordering, are unchanged;
  `BotCombatManager` keeps package-private compatibility wrappers for the
  current skill-cache and export-test paths.
- Combat weapon policy ownership moved to
  `server.agents.capabilities.combat.AgentCombatWeaponPolicy`. Dragon Knight
  spear/polearm skill gating, forced Crusher/Fury damage weapon types, and
  stab/swing action-to-weapon normalization are unchanged; `BotCombatManager`
  keeps compatibility wrappers for current attack-plan construction.
- Combat skill hitbox policy ownership moved to
  `server.agents.capabilities.combat.AgentCombatSkillHitboxPolicy`. Strike-point
  anchored AOE detection, measured Iron Arrow/Avenger pierce-line vertical
  reach, close-range fallback reach, weapon afterimage fallback, and projectile
  skill fallback geometry are unchanged; `BotCombatManager` keeps compatibility
  wrappers for current attack-plan construction and tests.
- Combat hit-count policy ownership moved to
  `server.agents.capabilities.combat.AgentCombatHitCounter`. Effective
  attack-line resolution still uses the larger of attackCount and bulletCount,
  minimum one line, and Shadow Partner still doubles only ranged-route damage
  lines; `BotCombatManager` keeps compatibility wrappers for current attack
  plan construction.
- Combat range and airborne-use policy ownership moved to
  `server.agents.capabilities.combat.AgentCombatRangePolicy`. Basic attack
  reach, basic weapon reach rectangles, strike-point primary reach gating,
  diagonal jump-attack eligibility, and airborne ranged-route blocking for
  bow/crossbow/gun are unchanged; `BotCombatManager` keeps compatibility
  wrappers for current attack-plan gating and tests.
- Combat support safety policy ownership moved to
  `server.agents.capabilities.combat.AgentCombatSupportPolicy`. Dragon Roar
  HP/target/healer gating, heal-threshold checks, healer-skill detection, and
  nearby-heal-ally scanning are unchanged. Nearby-party filtering,
  missing-party-buff detection, and heal-in-bounds checks now live in the same
  Agent policy; `BotCombatManager` still supplies legacy config values and
  executes the existing support side effects.
- Combat support special-move packet layout ownership moved to
  `server.agents.capabilities.combat.AgentSupportSpecialMovePacketBuilder`.
  Self-only and party-support SPECIAL_MOVE packet shapes, timestamp, skill id,
  skill level, position fallback, facing mask, and trailing terminator bytes
  are unchanged; `BotCombatManager` still dispatches the packet through the
  legacy packet handler path.
- Combat skill-use affordability policy moved to
  `server.agents.capabilities.combat.AgentCombatSkillUsePolicy`. Skill lookup,
  non-positive-level rejection, and `StatEffect.canPaySkillCost` delegation are
  unchanged; `BotCombatManager` keeps a compatibility wrapper for current
  attack execution.
- Combat death/ammo/MP shortage reply pools moved to
  `server.agents.capabilities.dialogue.AgentDialogueCatalog`. The exact strings,
  legacy random selection through `BotManager.randomReply`, and map-visible
  delivery path are unchanged.
- Combat mob-touch geometry moved to
  `server.agents.capabilities.combat.AgentMobTouchPolicy`. Client-style
  inclusive foot-sweep bounds and lower-half mob contact checks are unchanged;
  `BotCombatManager` still supplies remembered touch checkpoints and mob WZ
  bounds for the current side-effect path.
- Combat mob-knockback decision policy moved to
  `server.agents.capabilities.combat.AgentMobKnockbackPolicy`. Climb/death
  blocking, stance-percent clamping, random-roll comparison, OpenStory-step
  tick scaling, and mob-hit direction/air-velocity resolution are unchanged;
  `BotCombatManager` still supplies the live movement state, HP, buff, random
  roll, config values, and executes existing knockback side effects.
- Combat hitbox/monster intersection ownership moved to
  `server.agents.capabilities.combat.AgentCombatHitboxIntersection`. Mob WZ
  bounds intersection, monster-position fallback, forward-projectile hitbox
  detection, and null handling are unchanged; `BotCombatManager` keeps only a
  compatibility wrapper for current target planning.
- Combat scoring math moved to
  `server.agents.capabilities.combat.AgentCombatScoringPolicy`. Expected damage
  capping by current HP, local travel-cost estimation, local grind-target
  score penalties, AOE cluster bonus calculation, cluster membership, and
  nearest-cluster-target selection, plus the AOE-vs-single-target score gate,
  keep the same formulas; larger attack-plan orchestration, cached skill
  resolution, and grind-target selection still remain in `BotCombatManager`.
- Combat grind-target locality classification moved to
  `server.agents.capabilities.combat.AgentCombatGrindTargetPolicy`. Same-
  foothold early acceptance, graph-unavailable rejection, and same-region
  acceptance are unchanged; `BotCombatManager` still supplies foothold lookup
  and the legacy navigation-region resolver lazily to preserve call ordering.
- Combat scored grind-target value and ordering ownership moved to
  `server.agents.capabilities.combat.AgentScoredGrindTarget` and
  `AgentCombatGrindTargetPolicy`. Graph-cost, local-score, and distance
  tie-break ordering are unchanged; `BotCombatManager` still builds the scored
  target list until the remaining graph scoring slice is extracted.
- Combat grind-region grouping and graph-score conversion moved to
  `server.agents.capabilities.combat.AgentGrindTargetGroup` and
  `AgentCombatGrindTargetPolicy`. Best-local-score target selection,
  distance tie-break within a region, crowd bonus cap/per-mob formula,
  unreachable graph-cost preservation, and occupancy-penalty application are
  unchanged; `BotCombatManager` still supplies legacy path cost, occupancy
  penalty, and navigation-region resolution.
- Combat attack-plan tie-break ordering moved to
  `server.agents.capabilities.combat.AgentAttackPlanTieBreakPolicy`. Cooldown
  priority and lower-skill-id fallback ordering are unchanged; larger
  attack-plan construction and scoring still remain in `BotCombatManager`.
- Combat attack-plan value model moved to
  `server.agents.capabilities.combat.AgentAttackPlan`. Skill, route, hitbox,
  target, packet-display, stance, timing, and damage-weapon fields plus
  `hasHitBox`, `primaryTarget`, and close-route helpers are unchanged;
  `BotCombatManager.AttackPlan` remains only as a temporary compatibility
  subclass for existing bot-package tests and callers.
- Combat attack-plan scoring and selection moved to
  `server.agents.capabilities.combat.AgentAttackPlanScoringPolicy`. Damage
  profile resolution, expected/useful/raw damage aggregation, full-HP minimum
  kill gating, animation-duration DPS normalization, guaranteed-kill
  selection preference, and tie-break ordering are unchanged; `BotCombatManager`
  keeps only a temporary score wrapper for current AOE reposition code.
- Combat target eligibility moved to
  `server.agents.capabilities.combat.AgentCombatTargetEligibilityPolicy`. The
  null/dead/friendly escort/PQ monster exclusion rule is unchanged;
  `BotCombatManager` keeps a compatibility wrapper for current target scans.
- Combat hitbox target selection moved to
  `server.agents.capabilities.combat.AgentCombatTargetSelector`. Primary-first
  target inclusion, hostile/living secondary filtering, hitbox intersection,
  nearest-to-primary sorting, max-target capping, and hostile/living in-range
  candidate filtering, forward-projectile effective-primary selection, and
  closest-alive target selection are unchanged. Opposite-facing mirrored
  basic-attack target pivoting and strike-point primary resolution through
  basic-weapon reach are also Agent-owned; `BotCombatManager` still supplies
  the legacy hitbox builder and effective-primary resolver callbacks.
  Heal-range undead target filtering and cap handling are also Agent-owned;
  `BotCombatManager` still supplies the map monster iterable, map-object query
  results, and caller range values.
- Combat immediate projectile target policy moved to
  `server.agents.capabilities.combat.AgentCombatImmediateTargetPolicy`. Basic
  ranged projectile hitbox gating, degenerate-ranged fallback rejection, cached
  ranged/magic attack-skill cooldown, affordability, route, skill-hitbox, and
  ranged-route checks are unchanged; `BotCombatManager` now only supplies
  legacy ammo and cached attack-skill state before delegating.
- Combat attack data provider ownership moved to
  `server.agents.capabilities.combat.data.AgentAttackDataProvider`. Weapon
  normal-attack profile loading, action-spec selection, body action id
  overrides, stance/action timing caches, afterimage bounds, and WZ root cache
  invalidation are unchanged; legacy combat/equipment code now imports the
  Agent-owned data provider.
- Combat defense data ownership moved to
  `server.agents.capabilities.combat.data.AgentDefenseDataProvider`. Standard
  PDD tables, job-family resolution, hit/miss delegation, and physical touch
  damage formula constants are unchanged.
- Combat mob hitbox provider ownership moved to
  `server.agents.capabilities.combat.data.AgentMobHitboxProvider`. Mob WZ
  cache invalidation, linked-mob fallback, stand/move/fly frame fallback, and
  facing-aware world-bounds calculation are unchanged.
- Combat WZ DOM helper ownership moved to
  `server.agents.capabilities.combat.data.AgentWzXml`. Named-child lookup,
  integer attribute parsing, and integer value parsing are unchanged; legacy
  attack-data and mob-hitbox providers now import the Agent-owned helper.
- Combat attack timing formula ownership moved to
  `server.agents.capabilities.combat.data.AgentAttackTiming`. Attack-speed
  normalization, attack-speed factor calculation, and delay adjustment preserve
  the same constants and rounding behavior.
- Combat attack execution ownership moved to
  `server.agents.capabilities.combat.AgentAttackExecutionProvider`. The
  basic/skill attack data records, packet stance/action helpers, attack-route
  application bridge, weapon route resolution, degenerate ranged retreat
  helpers, projectile hitbox helpers, and skill timing resolution are unchanged.
  Agent attack execution no longer depends on `BotCombatManager`; legacy combat
  planning still owns larger attack-plan construction until later slices.
- Combat attack-route ownership moved to
  `server.agents.capabilities.combat.AgentAttackRoute`. The CLOSE/RANGED/MAGIC
  route values are unchanged; `BotCombatManager`, `BotManager`, and attack
  execution now share the Agent-owned route type.
- Report helper orchestration now has an Agent-owned facade in
  `AgentBotChatReportRuntime`; `BotChatReportRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Status helper orchestration now has an Agent-owned facade in
  `AgentBotChatStatusRuntime`; `BotChatStatusRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Build/AP/SP/job callback orchestration now has an Agent-owned facade in
  `AgentBotBuildRuntime`; `BotChatBuildRuntime` remains only as a temporary
  compatibility shim for legacy bot package callers.
- Pending chat-action state, callbacks, and skill-report decision application
  now have an Agent-owned facade in `AgentBotPendingActionRuntime`;
  `BotChatPendingActionRuntime` remains only as a temporary compatibility shim
  for legacy bot package callers.
- Utility chat callbacks now have an Agent-owned facade in
  `AgentBotUtilityRuntime`; `BotChatUtilityRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Social/fame chat callbacks now have an Agent-owned facade in
  `AgentBotSocialRuntime`; `BotChatSocialRuntime` remains only as a temporary
  compatibility shim for legacy bot package callers.
- Transfer/item-query chat callbacks and async transfer result routing now have
  an Agent-owned facade in `AgentBotTransferRuntime`; `BotChatTransferRuntime`
  remains only as a temporary compatibility shim for legacy bot package callers.
- Supply request callbacks and request-upgrade supply routing now have an
  Agent-owned facade in `AgentBotSupplyRuntime`; `BotChatSupplyRuntime` remains
  only as a temporary compatibility shim for legacy bot package callers.
- Session/relog/logout/away callback orchestration now has an Agent-owned
  facade in `AgentBotSessionRuntime`; `BotChatSessionRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Movement/follow/grind/stop/fidget/greeting callback orchestration now has an
  Agent-owned facade in `AgentBotMovementRuntime`; `BotChatMovementRuntime`
  remains only as a temporary compatibility shim for legacy bot package callers.
- Follow/stop/move/farm/patrol/grind command dispatch now has an Agent-owned
  facade in `AgentBotMovementCommandRuntime`; `BotManager` remains the temporary
  side-effect implementation for the actual movement state mutations.
- Read-only movement state snapshots now have Agent-owned types in
  `AgentMovementSnapshot`/`AgentMovementMode` and an integration facade in
  `AgentBotMovementStateRuntime`; `BotEntry` remains the temporary state source.
- Read-only target/formation snapshots now have an Agent-owned type in
  `AgentMovementTargetSnapshot` and an integration facade in
  `AgentBotMovementTargetRuntime`; `BotManager.TargetSnapshot` remains the
  temporary target-resolution source.
- Navigation debug overlay and path logging now consume
  `AgentMovementTargetSnapshot` for read-only target/formation data; pathfinding
  and target resolution still remain in the legacy bot runtime.
- Movement simulation and perf harnesses now use `AgentMovementTargetSnapshot`
  for read-only owner/goal/steering reads, keeping tests aligned with the Agent
  snapshot boundary while preserving legacy movement execution.
- Movement stats reporting now consumes `AgentMovementKinematicsSnapshot`; the
  temporary integration adapter still reads legacy bot physics values, but
  Agent dialogue/report code no longer assembles those bot metrics inline.
- Top-level chat handled-state ownership now lives in `AgentChatRuntime`;
  the temporary `BotChatRuntime` adapter has been removed.
- Top-level chat orchestrator context ownership now lives in
  `AgentBotChatOrchestratorContext`; the old bot-side context class has been
  removed, and `BotManager` now creates the Agent integration adapter directly.
- Unused bot-side chat compatibility shims for build, control, equipment,
  movement, pending-action, social, transfer, and utility callbacks have been
  removed after the Agent orchestrator context switched directly to Agent
  integration facades.
- The bot-side chat report compatibility shim has been removed; remaining report
  tests and production callers use `AgentBotChatReportRuntime` directly.
- The bot-side chat status compatibility shim has been removed; production
  lifecycle, fidget, offer, build, starter-kit, and tests now call
  `AgentBotChatStatusRuntime` directly.
- Unused bot-side chat session and supply compatibility shims have been
  removed; session and supply chat orchestration is reached through Agent
  integration facades.
- `BotChatRuntime` and `BotChatManager` have been removed; `BotManager` now
  delegates directly to `AgentChatRuntime` with
  `AgentBotChatOrchestratorContext`.
- Immediate Agent integration reply delivery now routes through
  `AgentBotReplyRuntime.replyNow`, `visibleSayNow`, and `sayPartyNow`; scattered
  Agent integration facades no longer call `BotManager.botReply`/visible
  delivery directly. `AgentBotReplyRuntime` remains the temporary adapter to the
  legacy BotManager packet-delivery methods.
- Loot/gear offer owner-directed replies, queued offer prompts, estimated prompt
  delay reads, and delayed offer actions now enter through
  `AgentBotOfferRuntime`; `AgentOfferService` no longer reaches directly into the
  lower-level reply or scheduler runtime for offer-owned flows. The remaining
  bot-side map-only `botSay(Character, ...)` branch is intentionally unchanged
  until map-only visible delivery has an exact Agent adapter.
- AP build confirmation replies now enter through `AgentBotBuildRuntime`; the
  build manager no longer reaches directly into the lower-level reply runtime
  for AP-build selection confirmation, but it still owns the legacy AP
  assignment behavior for this reconstruction stage.
- Maker batch command replies and delayed batch steps now enter through
  `AgentBotMakerRuntime`; `AgentMakerService` no longer reaches directly into the
  lower-level reply or scheduler runtime for Maker-owned flows. It still lazily
  resolves `ItemInformationProvider` so guard paths and Agent adapter tests do
  not initialize database-backed item data before it is needed.
- Scroll-reaction jitter delays and queued reaction chat now enter through
  `AgentBotScrollReactionRuntime`; `BotScrollReactionManager` no longer reaches
  directly into the lower-level reply or scheduler runtime for scroll-reaction
  owned flows.
- KPQ Stage 1 progress/pass dialogue and Stage 5 reward dialogue now enter
  through `AgentBotPqRuntime`; the KPQ script classes no longer reach directly
  into the lower-level reply runtime for party-quest-owned dialogue.
- Sell-trash shop owner-directed replies and delayed shop step callbacks now
  enter through `AgentBotShopRuntime`; `AgentShopService` no longer reaches
  directly into the lower-level reply or scheduler runtime for shop-owned
  flows. Map-only resupply/shop chatter remains on the legacy visible-say path
  until exact map-visible delivery has an Agent adapter.
- Ammo-share donor selection delays and delayed transfer callbacks now enter
  through `AgentBotAmmoRuntime`; `AgentAmmoService` no longer reaches directly
  into the lower-level scheduler runtime for ammo-owned timing. Visible ammo
  request/offer chat remains unchanged on the legacy map-visible say path.
- Potion-share donor selection delays, low-supply fallback delay, and delayed
  transfer callbacks now enter through `AgentBotPotionRuntime`;
  `AgentPotionService` no longer reaches directly into the lower-level scheduler
  runtime for potion-owned timing. Visible potion request/offer chat remains
  unchanged on the legacy map-visible say path.
- Bot physics identity reads now enter through `AgentBotRuntimeIdentityRuntime`;
  `BotPhysicsEngine` no longer reads `entry.bot` directly for swim motion,
  stance resolution, movement snapshots, or character-state synchronization,
  while the same BotEntry-backed character reference and movement behavior are
  preserved.
- Bot physics stance and movement-snapshot reads now enter through
  `AgentBotMovementStateRuntime`, `AgentBotClimbStateRuntime`, and
  `AgentBotSwimStateRuntime`; `BotPhysicsEngine` still owns the legacy physics
  calculations, but stance selection no longer directly reads BotEntry movement,
  climb, or swim fields.
- Bot physics movement-profile reads now enter through
  `AgentBotMovementStateRuntime.movementProfile`; jump, rope-jump, swim-burst,
  landing-speed, and ground-motion calculations still use the same non-null
  BotEntry-backed profile semantics.
- Bot physics coordinate and horizontal-speed helper access now enters through
  `AgentBotMovementPhysicsStateRuntime`; ground-position sync, stop-ground
  motion, and rounded airborne position reads no longer touch `physX`,
  `physY`, or `hspeed` directly.
- Bot physics movement-packet velocity writes now enter through
  `AgentBotMovementStateRuntime.setMovementVelocity`; the legacy behavior that
  non-zero horizontal velocity also updates facing direction is preserved behind
  the Agent movement-state boundary.
- Bot physics top-rope entry intent now enters through
  `AgentBotClimbStateRuntime`; queue, consume, and clear operations no longer
  access `ropeEntryPending`, `ropeEntryRope`, or `ropeEntryY` directly.
- Bot physics down-jump and crouch state now enters through
  `AgentBotMovementStateRuntime`; prone, queued down-jump, down-jump failure,
  grace-period timer, landing, swim, airborne, climb, and reset paths no longer
  write `downJumpPending`, `downJumpGracePeriodMS`, or `crouching` directly.
- Bot physics blocked-rope-grab state now enters through
  `AgentBotClimbStateRuntime`; jump, fall, knockback, landing, and reset paths
  no longer write `blockedRopeGrab` directly.
- Bot physics climb attachment and climb-direction state now enters through
  `AgentBotClimbStateRuntime`; idle, airborne, landing, climb, collision, and
  reset paths no longer directly read or write `climbing`, `climbRope`, or
  `climbVerticalDir`.
- Bot physics airborne/grounded state now enters through
  `AgentBotMovementStateRuntime.setInAir`; movement transitions no longer write
  the `inAir` field directly.
- Bot physics swim mode and swim intent state now enters through
  `AgentBotSwimStateRuntime`; water jump launch, swim integration, landing
  handoff, swim input reads, swim-jump consumption, swim cooldown, and swim
  facing updates no longer access swim fields directly in `BotPhysicsEngine`.
- Bot physics fixed-air-arc state now enters through
  `AgentBotMovementPhysicsStateRuntime.setFixedAirArc`; grounded, swim,
  landing, collision, airborne launch, climb, and reset paths no longer clear
  `fixedAirArc` directly.
- Bot physics movement-direction intent now enters through
  `AgentBotMovementStateRuntime`; ground motion, air steering, airborne launch,
  idle, and reset paths no longer read or clear `moveDir` directly.
- Bot physics facing-direction preservation and air-steering facing updates now
  enter through `AgentBotMovementStateRuntime`; knockback and airborne steering
  paths no longer access `facingDir` directly.
- Bot physics vertical velocity now enters through
  `AgentBotMovementPhysicsStateRuntime`; jump launch, swim integration,
  airborne integration, collision, climb, landing, and reset paths no longer
  access `velY` directly.
- Bot physics committed air velocity and air-steering velocity now enter
  through `AgentBotMovementPhysicsStateRuntime`; swim entry, knockback,
  airborne launch, air steering, collision, climb, and reset paths no longer
  access `airVelX` or `airSteerVelX` directly.
- Bot physics climb-up intent now enters through `AgentBotClimbStateRuntime`;
  idle, swim launch, knockback, landing, ground motion, airborne launch, climb,
  and reset paths no longer access `climbUpIntent` directly.
- Bot physics fall-peak tracking now enters through
  `AgentBotMovementPhysicsStateRuntime`; fall-distance calculation, airborne
  peak recording, and landing reset no longer access `fallPeakPhysY` directly.
- Bot physics reset-only movement and rope cooldown flags now enter through
  `AgentBotMovementStateRuntime` and `AgentBotClimbStateRuntime`; full motion
  reset no longer writes `wasMovingX` or `ropeGrabCooldownMs` directly.
- Combat alert reset callbacks now enter through `AgentBotCombatRuntime`;
  `BotCombatManager` no longer reaches directly into the lower-level scheduler
  runtime for combat-owned alert timing, and alert timing and stance reset
  behavior are unchanged.
- Inventory, trade, meso-transfer, and drop owner-directed replies now route
  through `AgentBotReplyRuntime`; delayed trade thanks/freebie callbacks now use
  `AgentBotSchedulerRuntime` while preserving the legacy visible `botSay`
  delivery and random reply pools.
- LLM split-message owner-directed replies now enter through
  `AgentBotLlmRuntime`; `BotLlmReplyManager` no longer reaches directly into
  the lower-level reply runtime, and the existing LLM executor,
  multi-message delay, and sanitization/splitting behavior are unchanged.
- Bot dismissal acknowledgement scheduling now routes through
  `AgentBotSchedulerRuntime`; delivery intentionally remains on the local
  `BotManager.botReply` method because `AgentBotReplyRuntime` currently bridges
  back to that delivery method.
- Remaining BotManager fire-and-forget callback scheduling for follow-target
  activation, spawn status checks, recruit greetings, owner pickup scans, scroll
  reactions, and relog greetings now routes through `AgentBotSchedulerRuntime`.
- Immediate Agent reply delivery now lives in `AgentBotReplyRuntime` instead of
  bridging back through `BotManager.botReply`, `botVisibleSay`, or
  `botSayParty`. `BotManager` keeps those methods as compatibility wrappers,
  and chat text sanitization now lives in `AgentChatTextSanitizer`.
- BotManager's remaining internal owner-directed reply sites now call
  `AgentBotReplyRuntime.replyNow` directly; `BotManager.botReply` remains only a
  compatibility wrapper for callers that have not been migrated yet.
- `AgentBotSchedulerRuntime` now schedules directly through `TimerManager`; the
  legacy `BotManager.scheduleBotReplyAction`/`after` bridge has been removed.
- Agent integration tests for build, session, pending-action, transfer, social,
  and combat delayed callbacks now assert the Agent reply/scheduler runtime
  boundary instead of the removed BotManager delivery/scheduler bridge.
- Build-triggered status checks now enter through
  `AgentBotBuildStatusRuntime.checkBuildStatus`; `AgentBuildService` and
  `AgentStarterKitService` no longer call the broad chat-status facade directly
  for job/level build status prompts.
- Gear-offer idle gating now enters through
  `AgentBotOfferRuntime.isOwnerIdleForOffer`; `AgentOfferService` no longer
  reaches directly into the broad chat-status facade for offer prompt checks.
- Fidget idle gating now enters through
  `AgentBotFidgetRuntime.isLeaderIdleForFidget`; `AgentFidgetService` no longer
  reaches directly into the broad chat-status facade for fidget eligibility.
- Movement-triggered active-mode preparation, post-movement status checks, and
  random fidget expressions now enter through `AgentBotMovementStatusRuntime`;
  `AgentBotMovementRuntime` no longer reaches directly into the broad
  chat-status facade for those movement callbacks.
- BotManager-triggered spawn status checks, map-change status checks,
  shop-transition status checks, offline-return announcements, and AFK ticks
  now enter through `AgentBotManagerStatusRuntime`; `BotManager` no longer
  reaches directly into the broad chat-status facade for those lifecycle/tick
  callbacks.
- Bot performance-monitor diagnostics now label the common AFK check as
  `AgentBotManagerStatusRuntime.tickAfkCheck`, matching the Agent-owned
  BotManager status boundary used by the tick shell.
- BotManager-triggered queue/reply/map/party delivery now enters through
  `AgentBotManagerReplyRuntime`; `BotManager` no longer reaches directly into
  the lower-level `AgentBotReplyRuntime` for internal command/error/formation
  replies or compatibility delivery wrappers.
- BotManager-triggered delayed callbacks now enter through
  `AgentBotManagerSchedulerRuntime`; `BotManager` no longer reaches directly
  into the lower-level `AgentBotSchedulerRuntime` for follow-target, dismiss,
  ownership-transfer, pickup-scan, scroll-reaction, or relog-greeting delays.
- Inventory/trade/drop/meso reply delivery and trade-thanks delayed callbacks
  now enter through `AgentBotInventoryRuntime`; `BotInventoryManager` no longer
  reaches directly into the lower-level reply or scheduler runtime for those
  inventory-owned flows.
- Recommended-gear prompt reservation state now enters through
  `AgentBotOfferRuntime`; `AgentOfferService` no longer reads or clears the
  `pendingGearPromptAt` entry field directly, while the same legacy field and
  timing semantics remain intact behind the Agent-owned offer boundary.
- Agent reply queue ownership now uses narrow queue operations on
  `AgentReplyQueue.State` and `AgentBotMessageQueueStateRuntime`; production
  reply draining no longer depends on direct `Deque` access while the same
  BotEntry-backed queue, synchronization lock, spacing estimate, and dispatch
  behavior remain in place.
- Equipment optimizer debug/dump range report formatting now enters through
  `AgentBotRangeReportRuntime`; `BotEquipManager` no longer imports the broad
  chat-report facade just to render range text, and the underlying range
  formatter remains unchanged.
- Control-triggered buff-debug and skill-buff-debug report delivery now enters
  through `AgentBotControlReportRuntime`; `AgentBotControlRuntime` still owns
  the same 500-700 ms command delay, but no longer reaches directly into the
  broad chat-report facade for those control-owned report callbacks.
- Offer-manager map-visible rejection replies and bot-to-bot loot-offer accept
  replies now enter through `AgentBotOfferRuntime`; `AgentOfferService` no longer
  calls `BotManager.botSay` directly for offer-owned reply delivery, while the
  same reply channel, random reply pool, and delay behavior remain intact.
- Ammo low-supply request and ammo-donor offer visible replies now enter
  through `AgentBotAmmoRuntime`; `AgentAmmoService` no longer calls
  `BotManager.botSay` directly for ammo-owned reply delivery, while the same
  random reply pools and transfer timing remain intact.
- Potion grind-stop warnings, low-supply requests, no-qualified-donor
  deflections, and donor offer visible replies now enter through
  `AgentBotPotionRuntime`; `AgentPotionService` no longer calls
  `BotManager.botSay` directly for potion-owned reply delivery, while the same
  text, random reply pools, and transfer timing remain intact.
- Equipment auto-equip clutter warnings now enter through
  `AgentBotEquipmentRuntime`; `BotEquipManager` no longer calls
  `BotManager.botSay` directly for equipment-owned reply delivery, and the
  legacy try/catch still prevents chat failures from blocking equip passes.
- Inventory trade-thanks and freebie quip callbacks now enter through
  `AgentBotInventoryRuntime`; `BotInventoryManager` no longer calls
  `BotManager.botSay` directly for inventory-owned delayed trade reply
  delivery, while the same reply channel and random reply pools remain intact.
- Combat death, missing-MP-potion, low-ammo, and out-of-ammo visible replies
  now enter through `AgentBotCombatRuntime`; `BotCombatManager` no longer calls
  `BotManager.botSay` directly for combat-owned reply delivery, while the same
  random reply pools, follow-owner fallback, and warning flags remain intact.
- Shop resupply, approach-timeout, shopping, purchase summary, shortfall,
  sell-trash, and abort visible replies now enter through `AgentBotShopRuntime`;
  `AgentShopService` no longer calls `BotManager.botSay` directly for shop-owned
  reply delivery, while the same text, random reply pools, and delayed shop
  sequencing remain intact.
- The unused `BotManager.botVisibleSay` and `BotManager.botReply`
  compatibility shims were removed after all production and test callers moved
  to Agent reply runtimes; `botSay(...)` and `botSayParty(...)` remain for
  legacy channel delivery compatibility.
- Report delivery queueing now enters through the narrow
  `AgentBotReportReplyRuntime`; `AgentBotReportDeliveryRuntime` no longer
  reaches directly into the broad `AgentBotReplyRuntime` for report line
  delivery, while the same queued owner-directed reply behavior remains intact.
- Report callback scheduling now enters through the narrow
  `AgentBotReportSchedulerRuntime`; `AgentBotChatReportRuntime` no longer
  reaches directly into the broad `AgentBotSchedulerRuntime` when constructing
  report callbacks, while the same random delay scheduler remains underneath.
- AFK-return and offline-return status actions now enter through narrow
  `AgentBotStatusReplyRuntime` and `AgentBotStatusSchedulerRuntime` adapters;
  `AgentBotStatusRuntime` no longer reaches directly into the broad reply or
  scheduler runtimes for those status-owned side effects.
- Relog/logout/away session prompts, confirmations, and delayed lifecycle
  callbacks now enter through narrow `AgentBotSessionReplyRuntime` and
  `AgentBotSessionSchedulerRuntime` adapters; `AgentBotSessionRuntime` no
  longer reaches directly into the broad reply or scheduler runtimes for
  session-owned chat timing.
- Pending chat-action item choices, cancel replies, and skill-tree reply
  queueing now enter through narrow `AgentBotPendingActionReplyRuntime` and
  `AgentBotPendingActionSchedulerRuntime` adapters; pending-action orchestration
  no longer reaches directly into the broad reply or scheduler runtimes.
- Toggle, buff-query, and respec control callbacks now enter through narrow
  `AgentBotControlReplyRuntime` and `AgentBotControlSchedulerRuntime` adapters;
  control orchestration no longer reaches directly into the broad reply or
  scheduler runtimes for control-owned chat timing.
- Equipment visible replies, unequip, unequip-all, auto-equip-debug, and
  auto-equip callbacks now enter through narrow `AgentBotEquipmentReplyRuntime`
  and `AgentBotEquipmentSchedulerRuntime` adapters; equipment orchestration no
  longer reaches directly into the broad reply or scheduler runtimes.
- Inventory, trade, drop, and meso reply/timing bridge methods now enter
  through narrow `AgentBotInventoryReplyRuntime` and
  `AgentBotInventorySchedulerRuntime` adapters; inventory orchestration no
  longer reaches directly into the broad reply or scheduler runtimes.
- Combat warning/status reply and delay bridge methods now enter through
  narrow `AgentBotCombatReplyRuntime` and `AgentBotCombatSchedulerRuntime`
  adapters; combat orchestration no longer reaches directly into the broad
  reply or scheduler runtimes.
- Ammo-sharing reply, delay, random-delay, and delay-sampling bridge methods
  now enter through narrow `AgentBotAmmoReplyRuntime` and
  `AgentBotAmmoSchedulerRuntime` adapters; ammo orchestration no longer reaches
  directly into the broad reply or scheduler runtimes.
- Potion-sharing reply, delay, random-delay, and delay-sampling bridge methods
  now enter through narrow `AgentBotPotionReplyRuntime` and
  `AgentBotPotionSchedulerRuntime` adapters; potion orchestration no longer
  reaches directly into the broad reply or scheduler runtimes.
- Maker automation reply, delay, and random-delay bridge methods now enter
  through narrow `AgentBotMakerReplyRuntime` and
  `AgentBotMakerSchedulerRuntime` adapters; Maker orchestration no longer
  reaches directly into the broad reply or scheduler runtimes.
- Shop automation owner replies, map-visible replies, fixed-delay callbacks,
  and delay-sampling bridge methods now enter through narrow
  `AgentBotShopReplyRuntime` and `AgentBotShopSchedulerRuntime` adapters; shop
  orchestration no longer reaches directly into the broad reply or scheduler
  runtimes.
- LLM dialogue replies now enter through the narrow `AgentBotLlmReplyRuntime`
  adapter; LLM orchestration no longer reaches directly into the broad reply
  runtime.
- PQ queued dialogue now enters through the narrow `AgentBotPqReplyRuntime`
  adapter; PQ orchestration no longer reaches directly into the broad reply
  runtime.
- Scroll-reaction queued dialogue, fixed-delay callbacks, and delay-sampling
  bridge methods now enter through narrow `AgentBotScrollReactionReplyRuntime`
  and `AgentBotScrollReactionSchedulerRuntime` adapters; scroll-reaction
  orchestration no longer reaches directly into the broad reply or scheduler
  runtimes.
- Supply request queued replies and random-delay callbacks now enter through
  narrow `AgentBotSupplyReplyRuntime` and `AgentBotSupplySchedulerRuntime`
  adapters; supply orchestration no longer reaches directly into the broad
  reply or scheduler runtimes.
- Social/fame replies and random-delay callbacks now enter through narrow
  `AgentBotSocialReplyRuntime` and `AgentBotSocialSchedulerRuntime` adapters;
  social orchestration no longer reaches directly into the broad reply or
  scheduler runtimes.
- Utility chat trade-invite replies and trade/shop/Maker random-delay callbacks
  now enter through narrow `AgentBotUtilityReplyRuntime` and
  `AgentBotUtilitySchedulerRuntime` adapters; utility orchestration no longer
  reaches directly into the broad reply or scheduler runtimes.
- Build/AP/SP/job-advance immediate replies, queued build-status replies, and
  job-advance random-delay callbacks now enter through narrow
  `AgentBotBuildReplyRuntime` and `AgentBotBuildSchedulerRuntime` adapters;
  build orchestration no longer reaches directly into the broad reply or
  scheduler runtimes.
- Gear/loot offer immediate replies, queued replies, map/channel dialogue,
  queued-say delay estimation, fixed-delay callbacks, random-delay callbacks,
  and delay sampling now enter through narrow `AgentBotOfferReplyRuntime` and
  `AgentBotOfferSchedulerRuntime` adapters; offer orchestration no longer
  reaches directly into the broad reply or scheduler runtimes.
- Transfer/item-query immediate replies, fixed-delay callbacks, random-delay
  callbacks, and delay sampling now enter through narrow
  `AgentBotTransferReplyRuntime` and `AgentBotTransferSchedulerRuntime`
  adapters; transfer orchestration no longer reaches directly into the broad
  reply or scheduler runtimes.
- Manager spawn-status delayed callbacks now enter through the narrow
  `AgentBotManagerSchedulerRuntime` adapter; manager status orchestration no
  longer reaches directly into the broad scheduler runtime.
- Movement/follow/grind/stop/fidget/greeting immediate replies, queued replies,
  and random-delay callbacks now enter through narrow
  `AgentBotMovementReplyRuntime` and `AgentBotMovementSchedulerRuntime`
  adapters; movement chat orchestration no longer reaches directly into the
  broad reply or scheduler runtimes.
- Gear-prompt reservation state now enters through the narrow
  `AgentBotOfferStateRuntime` adapter; offer scheduling keeps BotEntry as the
  temporary backing store but no longer owns the pending gear prompt field
  directly in offer orchestration.
- Pending action and pending drop-choice state now enter through the narrow
  `AgentBotPendingActionStateRuntime` adapter; chat, transfer, manager cleanup,
  and offer orchestration keep BotEntry as the temporary backing store but no
  longer read or clear those fields directly.
- AP-build prompt state and SP-variant prompt state now enter through the
  narrow `AgentBotBuildStateRuntime` adapter; build orchestration keeps BotEntry
  as the temporary backing store but no longer reads or mutates AP/SP prompt
  fields directly.
- Chat message queue and message-sending state now enter through the narrow
  `AgentBotMessageQueueStateRuntime` adapter; reply queue orchestration and
  scroll-reaction readiness checks keep BotEntry as the temporary backing store
  but no longer read queue fields directly.
- Scroll reaction cooldown, load, and per-scroller streak state now enter
  through the narrow `AgentBotScrollReactionStateRuntime` adapter; scroll
  reaction orchestration keeps BotEntry as the temporary backing store but no
  longer reads or mutates those fields directly.
- Owner activity and AFK/welcome-back state now enter through the narrow
  `AgentBotActivityStateRuntime` adapter; status and welcome-back orchestration
  keep BotEntry as the temporary backing store but no longer read or mutate AFK
  fields directly.
- Last matched owner-command state now enters through the narrow
  `AgentBotActivityStateRuntime` adapter; command handling and LLM situation
  building keep BotEntry as the temporary backing store but no longer read or
  write `lastOwnerCommand` or `lastOwnerCommandAtMs` directly.
- Ammo share request episode state now enters through the narrow
  `AgentBotAmmoStateRuntime` adapter; ammo sharing keeps BotEntry as the
  temporary backing store but no longer reads or mutates the request flag
  directly.
- HP/MP potion share request episode state now enters through the narrow
  `AgentBotPotionStateRuntime` adapter; potion sharing keeps BotEntry as the
  temporary backing store but no longer reads or mutates the request flags
  directly.
- Build level-sync and job-prompt milestone state now enter through
  `AgentBotBuildStateRuntime`; build progression keeps BotEntry as the
  temporary backing store but no longer reads or mutates `lastKnownLevel` or
  `jobPromptSent` directly.
- Consumable buff scan and last-action summary state now enter through
  `AgentBotBuffStateRuntime`; buff consumable automation keeps BotEntry as the
  temporary backing store but no longer reads or mutates those fields directly.
- Manual trade invite accept-delay, trade reference, and timeout state now enter
  through `AgentBotManualTradeStateRuntime`; manual trade handling keeps
  BotEntry as the temporary backing store but no longer reads or mutates those
  fields directly.
- Pending trade active/idle guard checks now enter through
  `AgentBotPendingTradeStateRuntime`; ammo, potion, offer, utility, and
  inventory orchestration keep BotEntry as the temporary backing store but no
  longer scatter direct `pendingTradeCategory` null checks.
- Bot-initiated trade retry callback and delay state now enter through
  `AgentBotPendingTradeStateRuntime`; loot-offer, potion-share, and ammo-share
  retry scheduling keep BotEntry as the temporary backing store but no longer
  read, write, or clear retry fields directly.
- Potion/ammo share trade quantity budget state now enters through
  `AgentBotPendingTradeStateRuntime`; trade item quantity capping and trade
  reset keep BotEntry as the temporary backing store but no longer read,
  decrement, or clear the budget field directly.
- Pending trade category message state now enters through
  `AgentBotPendingTradeStateRuntime`; reserved/equip group trade announcements
  keep BotEntry as the temporary backing store but no longer set, read, or clear
  the message field directly.
- Pending trade recipient id state now enters through
  `AgentBotPendingTradeStateRuntime`; trade setup, reset, and recipient
  resolution keep BotEntry as the temporary backing store but no longer set,
  read, or clear the recipient id field directly.
- Pending trade invite-announced state now enters through
  `AgentBotPendingTradeStateRuntime`; trade batch opening and reset keep
  BotEntry as the temporary backing store but no longer read, mark, or clear
  the invitation announcement flag directly.
- Pending trade timer state now enters through
  `AgentBotPendingTradeStateRuntime`; trade accept, batch pause, item-add, and
  confirmation timeout handling keep BotEntry as the temporary backing store
  but no longer read, increment, tick down, set, or clear the timer field
  directly.
- Pending trade single-batch state now enters through
  `AgentBotPendingTradeStateRuntime`; trade setup, batch-completion decisions,
  and reset keep BotEntry as the temporary backing store but no longer read,
  set, or clear the single-batch field directly.
- Pending trade meso amount and meso-added state now enter through
  `AgentBotPendingTradeStateRuntime`; trade setup, meso-add, insufficient-meso
  checks, and reset keep BotEntry as the temporary backing store but no longer
  read, set, mark, or clear meso trade fields directly.
- Pending trade all-items-added and bot-done completion flags now enter through
  `AgentBotPendingTradeStateRuntime`; trade completion, cancel, timeout, and
  reset handling keep BotEntry as the temporary backing store but no longer
  read, mark, or clear completion fields directly.
- Pending trade item index state now enters through
  `AgentBotPendingTradeStateRuntime`; trade batch setup, item-add progression,
  and reset keep BotEntry as the temporary backing store but no longer read,
  increment, or clear the item index field directly.
- Pending trade item-list state now enters through
  `AgentBotPendingTradeStateRuntime`; trade batch setup, between-batch pause,
  item-add progression, and reset keep BotEntry as the temporary backing store
  but no longer set, read, null-check, or clear the batch item list directly.
- Pending trade category state now enters through
  `AgentBotPendingTradeStateRuntime`; trade setup, group advancement, supply
  share invitation suppression, and reset keep BotEntry as the temporary
  backing store but no longer set, read, compare, or clear the category field
  directly.
- Pending trade temporary equipment restore-slot state now enters through
  `AgentBotPendingTradeStateRuntime`; trade preparation, trade-window item
  remapping, restore checks, restore snapshots, and cleanup keep BotEntry as the
  temporary backing store but no longer operate on the restore map directly.
- Inventory full-warning cooldown and post-drop loot-inhibit cooldown state now
  enter through `AgentBotInventoryStateRuntime`; passive loot and drop-choice
  handling keep BotEntry as the temporary backing store but no longer read,
  tick down, or set those cooldown fields directly.
- Potion check and passive MP/HP recovery timer state now enter through
  `AgentBotPotionStateRuntime`; potion check retry, autopot cadence, and
  passive recovery keep BotEntry as the temporary backing store but no longer
  read, shorten, tick down, clear, or set those timer fields directly.
- Owner-inactive safe-mode state now enters through
  `AgentBotActivityStateRuntime`; offline/dead owner recovery keeps BotEntry as
  the temporary backing store but no longer reads, starts, clears, or sets the
  inactive timer, town-return flag, or away-safe-mode flag directly.
- Gear-prompt test assertions now read the reserved prompt timestamp through
  `AgentBotOfferStateRuntime`, keeping tests on the Agent-owned offer state
  boundary instead of the temporary BotEntry backing field.
- Session-request tests now assert pending chat action through
  `AgentBotPendingActionStateRuntime`, keeping relog/away prompt state checks on
  the Agent-owned pending-action boundary instead of the temporary BotEntry
  backing field.
- Combat attack-lock, post-attack movement-window, and mob-hit invulnerability
  cooldown state now enter through `AgentBotCombatCooldownStateRuntime`; combat,
  movement, and local attack orchestration keep BotEntry as the temporary backing
  store but no longer read, extend, tick down, clear, or set those cooldown fields
  directly.
- Movement broadcast duplicate-suppression cache state now enters through
  `AgentBotMovementBroadcastStateRuntime`; movement, combat, airshow, and mode
  reset paths keep BotEntry as the temporary backing store but no longer invalidate,
  compare, or record the last broadcast snapshot fields directly.
- Navigation debug path logger lifecycle and per-tick recording now enter
  through `AgentBotNavigationDebugStateRuntime`; navigation debug overlay and
  navigation resolution keep BotEntry as the temporary backing store but no
  longer create, clear, or record the path logger field directly.
- Navigation debug decision and edge-block reason state now enter through
  `AgentBotNavigationDebugStateRuntime`; navigation resolution, idle fast-path
  status, path logging, and focused tests keep BotEntry as the temporary backing
  store but no longer read or write those debug fields directly.
- Navigation graph warmup fallback state now enters through
  `AgentBotNavigationDebugStateRuntime`; navigation, movement, fallback steering,
  fidget gating, stuck checks, path logging, and focused tests keep BotEntry as
  the temporary backing store but no longer read or write the fallback flag
  directly.
- Navigation waypoint target state now enters through
  `AgentBotNavigationDebugStateRuntime`; navigation planning, committed-edge
  reuse, movement precision gates, fidget gates, target snapshots, debug overlay,
  path logging, simulation helpers, and focused tests keep BotEntry as the
  temporary backing store but no longer read or write `navTargetPos`,
  `navTargetRegionId`, or `navPreciseTarget` directly.
- Portal-use cooldown state now enters through
  `AgentBotNavigationDebugStateRuntime`; portal execution keeps BotEntry as the
  temporary backing store but no longer reads or writes `portalUseCooldownUntilMs`
  directly.
- Navigation jump-launch cache state now enters through
  `AgentBotNavigationDebugStateRuntime`; movement reset and jump waypoint
  selection keep BotEntry as the temporary backing store but no longer read or
  write `navJumpLaunchEdge` or `navJumpLaunchX` directly.
- Movement stuck/unstuck tracking now enters through
  `AgentBotMovementStuckStateRuntime`; stuck detection, path logging, and
  recovery cooldown setup keep BotEntry as the temporary backing store but no
  longer read or write `stuckMs`, `stuckCheckX`, `stuckCheckY`, or
  `unstuckCooldownMs` directly.
- Tick heartbeat, last-tick metadata, and follow-idle check timing now enter
  through `AgentBotTickStateRuntime`; BotManager, fidget eligibility, and path
  logging keep BotEntry as the temporary backing store but no longer read or
  write `lastTickWasAi`, `lastTickAtMs`, `lastHeartbeatAtMs`, or
  `nextFollowIdleMovementCheckAtMs` directly in production.
- Owner/leader motion observation now enters through
  `AgentBotOwnerMotionStateRuntime`; BotManager follow tracking, movement reset,
  and fidget decisions keep BotEntry as the temporary backing store but no
  longer read or write `lastOwnerPos`, `observedOwnerStepX`, or
  `observedOwnerStepY` directly in production.
- Explicit movement target state now enters through
  `AgentBotMoveTargetStateRuntime`; BotManager scripted movement, standalone
  move-target ticks, idle/follow gating, precise arrival checks, farm-anchor map
  cleanup, fidget gating, and navigation keep BotEntry as the temporary backing
  store but no longer read or write `moveTarget` or `moveTargetPrecise` directly
  in production.
- Agent reply queue state no longer exposes the temporary BotEntry-backed
  `Deque` through the integration adapter; callers use narrow queue operations
  or a read-only snapshot while `BotEntry.messageQueue()` remains only the
  temporary backing accessor.
- Farm/sentry anchor state now enters through `AgentBotFarmAnchorStateRuntime`;
  BotManager target snapshots, anchored-farm ticks, follow/active-mode cleanup,
  navigation gating, and LLM situation reporting keep BotEntry as the temporary
  backing store but no longer read or write `farmAnchor` or `farmAnchorMapId`
  directly in production.
- Patrol region and wander-target state now enters through
  `AgentBotPatrolStateRuntime`; BotManager patrol wandering, patrol loot
  steering, grind/combat patrol targeting, movement snapshots, map-change
  cleanup, and focused tests keep BotEntry as the temporary backing store but no
  longer read or write `patrolRegionId`, `patrolMapId`, or
  `patrolWanderTarget` directly outside the adapter.
- No-target grind wander direction now enters through
  `AgentBotGrindWanderStateRuntime`; BotManager no-target grind movement and
  focused tests keep BotEntry as the temporary backing store but no longer read,
  choose, or clear `wanderDirection` directly outside the adapter.
- Grind loot target and retry-suppression state now enters through
  `AgentBotGrindLootStateRuntime`; BotManager active grind-loot steering,
  passive-radius retry suppression, mode cleanup, tick-failure cleanup, and
  focused tests keep BotEntry as the temporary backing store but no longer read
  or write `grindLootTarget`, `ignoredGrindLootObjectId`, or
  `ignoredGrindLootUntilMs` directly outside the adapter.
- AoE reposition commitment state now enters through
  `AgentBotAoeRepositionStateRuntime`; BotManager AoE pre-attack movement keeps
  BotEntry as the temporary backing store but no longer reads or writes
  `aoeRepositionAnchor` or `aoeRepositionDeadlineMs` directly outside the
  adapter.
- Surround-breakout commitment state now enters through
  `AgentBotBreakoutStateRuntime`; BotManager ranged retreat breakout movement
  keeps BotEntry as the temporary backing store but no longer reads or writes
  `breakoutDirection` or `breakoutUntilMs` directly outside the adapter.
- Ranged retreat-hold commitment state now enters through
  `AgentBotRetreatHoldStateRuntime`; BotManager ranged retreat hysteresis,
  breakout cleanup, and grind-start cleanup keep BotEntry as the temporary
  backing store but no longer read or write `retreatHoldPos` or
  `retreatHoldUntilMs` directly outside the adapter.
- Combat skill-cache state now enters through
  `AgentBotCombatSkillCacheStateRuntime`; BotCombatManager skill cache rebuild,
  buff/heal selection, attack planning, projectile checks, AoE scoring, and
  focused tests keep BotEntry as the temporary backing store but no longer read
  or write cached attack, AoE, heal, buff, or summon skill fields directly.
- Combat buff/support toggle and cooldown state now enters through
  `AgentBotCombatBuffStateRuntime`; BotCombatManager skill-buff gating,
  per-skill rebuff cadence, support-heal gating, support-buff cadence, and
  focused tests keep BotEntry as the temporary backing store but no longer read
  or write `skillBuffsEnabled`, `supportHealsEnabled`, `nextBuffAt`, or
  `nextSupportBuffAt` directly.
- Ranged degenerate-hit retreat latch state now enters through
  `AgentBotDegenerateAttackStateRuntime`; BotManager grind combat and local
  opportunity combat keep BotEntry as the temporary backing store but no longer
  read or write `degenAttackDone` directly outside the adapter.
- Grind retarget-search cooldown state now enters through
  `AgentBotGrindSearchStateRuntime`; BotManager target-search cadence,
  BotMovementManager teleport/reset cleanup, and focused tests keep BotEntry as
  the temporary backing store but no longer read or write
  `nextGrindTargetSearchAtMs` directly outside the adapter.
- Active grind target state now enters through
  `AgentBotGrindTargetStateRuntime`; BotManager target snapshots, grind combat
  selection, failure/owner-idle cleanup, BotMovementManager reset cleanup,
  BotCombatManager death/debug handling, and BotBuffManager ACC reference
  selection keep BotEntry as the temporary backing store but no longer read or
  write `grindTarget` directly outside the adapter.
- High-level mode state now enters through `AgentBotModeStateRuntime`;
  BotManager follow/grind/stop transitions, BotMovementManager movement gates,
  AgentFidgetService social fidget gates, BotCombatManager buff/heal/ammo gates,
  AgentPotionService share/low-pot gates, BotNavigationManager follow/grind
  target adjustment, BotPathLogger mode reporting, LLM situation reporting,
  movement snapshots, and focused tests keep BotEntry as the temporary backing
  store but no longer read or write `following`, `grinding`, or
  `followTargetId` directly outside the adapter.
- Tick-failure window state now enters through
  `AgentBotTickFailureStateRuntime`; BotManager tick exception handling and
  recovery reset keep BotEntry as the temporary backing store but no longer read
  or write `tickFailureCount` or `tickFailureWindowStartedAtMs` directly outside
  the adapter.
- Reply-channel state now enters through `AgentBotReplyChannelStateRuntime`;
  BotManager command routing/reply delivery, Agent reply runtime delivery, and
  AgentOfferService auto-accept replies keep BotEntry as the temporary backing
  store but no longer read or write `replyChannel` directly outside the adapter.
- Tick cadence state now enters through `AgentBotTickCadenceStateRuntime`;
  BotManager spawn/normalize reset, tick skip-delay handling, AI tick cadence
  consumption, BotPathLogger cadence reporting, and focused tests keep BotEntry
  as the temporary backing store but no longer read or write `skipDelayMs` or
  `aiTickAccumulatorMs` directly outside the adapter.
- Death/respawn window state now enters through `AgentBotDeathStateRuntime`;
  BotManager spawn/normalize reset, dead-tick handling, common-tick death
  handling, respawn cleanup, BotCombatManager fatal-hit handling, and focused
  tests keep BotEntry as the temporary backing store but no longer read or write
  `deadUntil` directly outside the adapter.
- Map/foothold tracking state now enters through `AgentBotMapStateRuntime`;
  BotManager spawn/normalize map tracking, follow/grind map-change detection,
  standalone move-target map-change grounding, shop-mode map-change grounding,
  and focused tests keep BotEntry as the temporary backing store but no longer
  read or write `lastMapId` or `fhIndex` directly in production.
- Script task queue and activity-epoch state now enter through
  `AgentBotScriptTaskStateRuntime`; BotManager task clearing, queueing,
  active-task activation/completion, scripted local-combat checks, AgentMakerService
  batch-interruption checks, and focused tests keep BotEntry as the temporary
  backing store but no longer read or write `activityEpoch`, `scriptTasks`, or
  `activeScriptTask` directly in production.
- Formation spacing state now enters through `AgentBotFormationStateRuntime`;
  BotManager registration/reconfiguration offset assignment, follow-target
  snapshot construction, BotPathLogger reporting, and focused tests keep
  BotEntry as the temporary backing store but no longer read or write
  `followOffsetX` directly in production.
- Consumable buff toggle/mode state now enters through
  `AgentBotBuffStateRuntime`; BotManager owner-away safe-mode cleanup,
  BotBuffManager tick/debug/report paths, Agent control callbacks, and focused
  tests keep BotEntry as the temporary backing store but no longer read or write
  `buffConsumablesEnabled` or `buffCheapMode` directly outside the adapter.
- Pending loot-offer tuple state now enters through
  `AgentBotOfferStateRuntime`; BotManager offer-recipient checks, AgentOfferService
  reservation/prompt/expiry/accepted-transfer cleanup, BotInventoryManager
  pickup auto-equip, Agent active/equipment bridges, and focused tests keep
  BotEntry as the temporary backing store but no longer read or write
  `pendingLootOfferItem`, `pendingLootOfferRecipientId`,
  `pendingLootOfferExpiresAt`, or `pendingLootOfferBotRequesting` directly
  outside the adapter.
- Live leader/anchor reference refresh now enters through
  `AgentBotLeaderStateRuntime`; BotManager tick-owner refresh and focused tests
  keep BotEntry as the temporary backing store but no longer assign `owner`
  directly in production.
- Movement profile state now enters through `AgentBotMovementStateRuntime`;
  BotManager registration, spawn/map-change graph warmup, graph lookup, patrol
  region selection, retreat planning, and jumpability checks keep BotEntry as
  the temporary backing store but no longer read or write `movementProfile`
  directly in production.
- Horizontal movement intent state now enters through
  `AgentBotMovementStateRuntime`; BotManager spawn normalization and follow-idle
  fast-path gating keep BotEntry as the temporary backing store but no longer
  read or write `moveDir` directly in production.
- Active navigation-edge presence now enters through
  `AgentBotNavigationDebugStateRuntime`; BotManager retreat eligibility,
  follow-idle fast-path gating, precise-target setup, and stuck detection keep
  BotEntry as the temporary backing store but no longer check `navEdge`
  directly in production.
- Shop transition read state now enters through `AgentBotShopStateRuntime`;
  BotManager target snapshots, shop detour ticks, idle gating, map-sync gating,
  party-teleport recovery, and follow-idle fast-path gating keep BotEntry as the
  temporary backing store but no longer read `shopVisitPending`,
  `shopTargetPos`, `shopNpcPos`, `shopApproachDelayMs`, or
  `shopSequenceActive` directly in production.
- Physical movement status now enters through `AgentBotMovementStateRuntime`;
  BotManager retreat eligibility, local combat, trade/idle physics, follow idle
  gating, movement-phase dispatch, stuck detection, and action-lock physics
  keep BotEntry as the temporary backing store but no longer read `inAir`,
  `climbing`, `downJumpPending`, `wasMovingX`, `movementVelX`, or
  `movementVelY` directly in production.
- Movement physics flag state now enters through
  `AgentBotMovementPhysicsStateRuntime`; BotMovementManager landing cooldown
  reset, fixed-air-arc gating/setup, and broadcast foothold caching keep
  BotEntry as the temporary backing store but no longer read or write
  `jumpCooldownMs`, `fixedAirArc`, or `lastGroundFhId` directly in production.
- Fidget mode presence now enters through `AgentBotFidgetRuntime`; BotManager
  follow-idle fast-path gating keeps BotEntry as the temporary backing store but
  no longer reads `fidgetMode` directly in production.
- No-ammo combat gate state now enters through `AgentBotAmmoStateRuntime`;
  BotManager local combat opportunity checks keep BotEntry as the temporary
  backing store but no longer read `noAmmo` directly in production.
- KPQ Stage 5 claim reset state now enters through `AgentBotPqRuntime`;
  BotManager map-change handling keeps BotEntry as the temporary backing store
  but no longer writes `kpq.stage5Claimed` directly in production.
- Airshow-active tick gate state now enters through
  `AgentBotManagerStatusRuntime`; BotManager tick orchestration keeps BotEntry
  as the temporary backing store but no longer reads `airshowActive` directly in
  production.
- Scheduled tick task cancellation now enters through
  `AgentBotManagerSchedulerRuntime`; BotManager lifecycle removal keeps BotEntry
  as the temporary backing store but no longer reads or cancels `task` directly
  in production.
- Runtime identity lookup state now enters through
  `AgentBotRuntimeIdentityRuntime`; BotManager follow-target filtering,
  ownership transfer, active-owner lookup, and name lookup keep BotEntry as the
  temporary backing store but no longer read the corresponding `bot`/`owner`
  identity fields directly in those paths.
- Runtime identity local extraction now enters through
  `AgentBotRuntimeIdentityRuntime`; BotManager normalization, formation,
  target snapshots, retreat selection, grind loot, tick shell, tick-failure
  handling, follow setup, local movement checks, and movement-only stepping keep
  BotEntry as the temporary backing store but no longer initialize local
  `Character bot`/`Character owner` variables directly from `entry.bot` or
  `entry.owner`.
- Runtime identity side-effect arguments now enter through
  `AgentBotRuntimeIdentityRuntime`; BotManager pickup auto-equip, potion-share
  mode checks, spawn party join, owner-gained-equip notifications, and
  compatibility reply delivery keep BotEntry as the temporary backing store but
  no longer pass `entry.bot` or `entry.owner` directly in those calls.
- Runtime identity map/position reads now enter through
  `AgentBotRuntimeIdentityRuntime`; BotManager group supply responder
  selection, pending-offer map checks, owner-inactive town checks, away
  safe-mode map checks, town cluster targeting, farm/patrol setup, script task
  completion, swim-map detection, and movement cleanup/stuck detection keep
  BotEntry as the temporary backing store but no longer read `entry.bot`
  map/position fields directly in those paths.
- Remaining BotManager runtime identity reads now enter through
  `AgentBotRuntimeIdentityRuntime`; test tick execution, leader refresh,
  farm/patrol guards, owner-follow reset, script item drop, follow-target
  resolution, queued script ticking, cheap script movement checks, and
  movement-only stepping keep BotEntry as the temporary backing store but no
  longer read `entry.bot` or `entry.owner` directly in production code.
- BotMovementManager runtime identity reads now enter through
  `AgentBotRuntimeIdentityRuntime`; movement profile refresh, state reset,
  climb/air/swim/ground movement, mob-avoidance region checks, ground step
  resolution, unstuck jumps, and movement broadcast keep BotEntry as the
  temporary backing store but no longer read `entry.bot` directly in production.
- Active navigation-edge state in BotNavigationManager now enters through
  `AgentBotNavigationDebugStateRuntime`; path planning, committed-edge reuse,
  climb-exit refresh, ground-edge refresh, and post-ground edge execution keep
  BotEntry as the temporary backing store but no longer read or write
  `navEdge` directly in production.
- Navigation movement-profile reads now enter through
  `AgentBotMovementStateRuntime`; BotNavigationManager graph resolution,
  warmup, jump/drop planning, tolerance checks, and platform-margin calculation
  keep BotEntry as the temporary backing store but no longer read
  `movementProfile` directly in production.
- Navigation movement-phase reads now enter through
  `AgentBotMovementStateRuntime` and `AgentBotClimbStateRuntime`;
  BotNavigationManager committed-edge guards, edge reuse, portal/jump/drop/climb
  execution, waypoint selection, and current-region resolution keep BotEntry as
  the temporary backing store but no longer read `inAir`, `climbing`,
  `downJumpPending`, or `climbRope` directly in production.
- BotNavigationManager runtime identity reads now enter through
  `AgentBotRuntimeIdentityRuntime`; graph lookup/warmup, committed-edge
  execution, warmup notification, edge usability checks, waypoint selection,
  current-region resolution, and follow-anchor handling keep BotEntry as the
  temporary backing store but no longer read `entry.bot` or `entry.owner`
  directly in production.
- Remaining BotNavigationManager state reads now enter through
  `AgentBotMovementPhysicsStateRuntime` and `AgentBotShopStateRuntime`;
  directional-drop live landing simulation and follow-rope region targeting keep
  BotEntry as the temporary backing store but no longer read `physX`, `hspeed`,
  `groundPhysicsCarryMs`, or `shopVisitPending` directly in production.
- BotInventoryManager runtime identity reads now enter through
  `AgentBotRuntimeIdentityRuntime`; passive pickup ownership, patrol-loot
  lookup, manual trade handling, transfer setup, auto-equip handoff,
  transferable item selection, recipient resolution, and slow-path logging keep
  BotEntry as the temporary backing store but no longer read `entry.bot` or
  `entry.owner` directly in production.
- BotInventoryManager patrol-loot graph lookup now reads movement profile
  through `AgentBotMovementStateRuntime`; BotEntry remains the temporary backing
  store but inventory patrol-loot selection no longer reads `movementProfile`
  directly in production.
- BotInventoryManager owner-given trade item state now enters through
  `AgentBotPendingTradeStateRuntime`; trade reset and owner-given equipment
  capture keep BotEntry as the temporary backing store but no longer mutate
  `ownerGivenItems` directly in production.
- Combat animation/cooldown and alert-reset state now enters through
  `AgentBotCombatCooldownStateRuntime`; BotCombatManager attack cooldown,
  movement-window, alerted-stance timeout, and alert-reset scheduling keep
  BotEntry as the temporary backing store but no longer read or write
  `attackCooldownMs`, `moveWindowMs`, `alertedUntilMs`, or
  `alertResetScheduled` directly in production.
- Ammo/no-ammo combat gate state now enters through
  `AgentBotAmmoStateRuntime`; BotCombatManager attack gating and ammo/potion
  warning checks keep BotEntry as the temporary backing store but no longer read
  or write `noAmmo` or `ammoWarnSent` directly in production.
- Mob-touch sweep checkpoint state now enters through
  `AgentBotMobTouchStateRuntime`; BotCombatManager touch-damage sweep checks
  keep BotEntry as the temporary backing store but no longer read or write
  `lastMobTouchCheckPos` or `lastMobTouchMapId` directly in production.
- Skill-buff debug decision state now enters through
  `AgentBotSkillBuffDebugStateRuntime`; BotCombatManager buff decision logging
  and debug-line generation keep BotEntry as the temporary backing store but no
  longer read or write `lastSkillBuffActionAtMs` or
  `lastSkillBuffActionSummary` directly in production.
- Combat movement-facing state reads now enter through
  `AgentBotMovementStateRuntime`; BotCombatManager fall knockback, mob
  knockback, support-heal movement gating, attack-plan gating, attack-facing,
  action-lock movement, and grind graph profile selection keep BotEntry as the
  temporary backing store but no longer read or write `facingDir`, `inAir`,
  `climbing`, `moveDir`, or `movementProfile` directly in production.
- Combat runtime identity reads now enter through
  `AgentBotRuntimeIdentityRuntime`; BotCombatManager jump-heal leader
  resolution and delayed alert-stance broadcasts keep BotEntry as the temporary
  backing store but no longer read `entry.owner` or `entry.bot` directly in
  production.
- Movement intent and down-jump gate reads now enter through
  `AgentBotMovementStateRuntime`; BotMovementManager air steering, ground-action
  movement intent, down-jump grace gating, and pending down-jump dispatch keep
  BotEntry as the temporary backing store but no longer read or write
  `moveDir`, `downJumpPending`, or `downJumpGracePeriodMS` directly in
  production.
- Movement profile state now enters through `AgentBotMovementStateRuntime`;
  BotMovementManager profile refresh, graph lookup/warmup, jump velocity,
  walk-step, mob-avoidance, and unstuck calculations keep BotEntry as the
  temporary backing store but no longer read or write `movementProfile` directly
  in production.
- Horizontal movement hysteresis state now enters through
  `AgentBotMovementStateRuntime`; BotMovementManager idle-on-ground checks and
  follow-step hysteresis keep BotEntry as the temporary backing store but no
  longer read or write `movementVelX` or `wasMovingX` directly in production.
- Climb and rope movement state now enters through
  `AgentBotClimbStateRuntime`; BotMovementManager climb target steering,
  climb direction intent, rope transfer, rope snap, rope-grab filtering, and
  rope-entry dispatch keep BotEntry as the temporary backing store but no
  longer read or write `climbRope`, `climbVerticalDir`, `climbing`,
  `climbUpIntent`, `blockedRopeGrab`, or `ropeEntryPending` directly in
  production.
- Swim movement intent state now enters through `AgentBotSwimStateRuntime`;
  BotMovementManager airborne/grounded swim-mode clearing and swim input
  calculation keep BotEntry as the temporary backing store but no longer read
  or write `swimming`, `swimMoveDir`, `swimVerticalHold`,
  `swimJumpRequested`, or `swimNextJumpAtMs` directly in production.
- Active navigation edge state now enters through
  `AgentBotNavigationDebugStateRuntime`; BotMovementManager navigation-state
  clearing, climb steering, air-steering gating, grind target adjustment,
  ground action planning, wall-block recovery, directional drop execution, and
  mob-avoidance checks keep BotEntry as the temporary backing store but no
  longer read or write `navEdge` directly in production.
- Shop visit lifecycle state now enters through `AgentBotShopStateRuntime`;
  AgentShopService resupply/sell-trash visit setup, approach delay, sequence
  activation, stuck fallback, sequence validation, scheduled-step guard, abort,
  and cleanup keep BotEntry as the temporary backing store but no longer read or
  write shop visit fields directly in production. Shop approach graph profile
  lookup now reads through `AgentBotMovementStateRuntime`, and delayed abort
  identity reads through `AgentBotRuntimeIdentityRuntime`.
- Offer request/proactive-offer state now enters through
  `AgentBotOfferStateRuntime`; AgentOfferService owner upgrade request memory,
  proactive shared-loot offer checks, accepted/declined offer callbacks, sibling
  recipient scans, and reserved-offer recipient resolution keep BotEntry as the
  temporary backing store but no longer read `requestedUpgradeItemIds`,
  `proactiveUpgradeOffers`, `owner`, or `bot` directly in production.
- Potion sharing and passive recovery gates now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotMovementStateRuntime`;
  AgentPotionService owner lookup, donor bot selection, delayed low-supply
  replies, transfer donor identity, and standing-still recovery checks keep
  BotEntry as the temporary backing store but no longer read `owner`, `bot`,
  `inAir`, `climbing`, or `moveDir` directly in production.
- Ammo sharing identity now enters through `AgentBotRuntimeIdentityRuntime`;
  AgentAmmoService low-ammo request owner lookup, owner-request sharing, sibling
  donor scans, and delayed transfer donor identity keep BotEntry as the
  temporary backing store but no longer read `owner` or `bot` directly in
  production.
- AP build assignment identity now enters through
  `AgentBotRuntimeIdentityRuntime`; AgentBuildService set-build confirmation and
  immediate AP assignment keep BotEntry as the temporary backing store but no
  longer read `bot` directly in production.
- Maker automation bot identity now enters through
  `AgentBotRuntimeIdentityRuntime`; AgentMakerService crystal creation,
  disassembly, batch start, and delayed batch step checks keep BotEntry as the
  temporary backing store but no longer read `bot` directly in production.
- Scroll reaction bot identity now enters through
  `AgentBotRuntimeIdentityRuntime`; BotScrollReactionManager range filtering,
  delayed reaction eligibility, and emote side effects keep BotEntry as the
  temporary backing store but no longer read `bot` directly in production.
- Fidget bot identity and movement profile reads now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotMovementStateRuntime`;
  AgentFidgetService tick eligibility, fidget origin capture, walk-step
  calculations, grounded execution, diagonal/sideways direction selection, and
  prone visual broadcast keep BotEntry as the temporary backing store but no
  longer read `bot` or `movementProfile` directly in production.
- Fidget movement/nav gate state now enters through
  `AgentBotMovementStateRuntime` and `AgentBotNavigationDebugStateRuntime`;
  AgentFidgetService social fidget eligibility, active fidget eligibility,
  airborne/climb dispatch, air-steer movement intent, grounded sideways
  movement intent, and prone visual facing keep BotEntry as the temporary
  backing store but no longer read or write `inAir`, `climbing`, `navEdge`,
  `downJumpPending`, `moveDir`, or `facingDir` directly in production.
- Fidget state-machine fields now enter through `AgentBotFidgetStateRuntime`;
  AgentFidgetService fidget mode/trigger, timers, origin position, spam-air-steer,
  jump/sideways direction, crouch checks, visual cooldown, and idle/speed-roll
  scheduling keep BotEntry as the temporary backing store but no longer read or
  write fidget fields directly in production. `AgentFidgetMode` and
  `AgentFidgetTrigger` were moved to public enum files with the same values so
  the Agent adapter can keep a typed boundary without changing behavior.
- Physics engine movement, climb, swim, and airborne state now route through
  Agent runtime adapters; BotPhysicsEngine keeps BotEntry as the temporary
  backing store but no longer owns direct runtime field access for these state
  groups.
- Movement profile ownership moved to `AgentMovementProfile` under the Agent
  movement capability. The stat bucketing, base profile, forced-base field-limit
  handling, and physics multipliers are unchanged; legacy bot movement,
  navigation, combat, and shop code now consume the Agent-owned profile type.
- Loot eligibility ownership moved to `AgentLootEligibility` under the Agent
  looting capability. Coupon/pass/rice-cake filtering, quest-item checks,
  inventory-full checks, and bot-inventory-drop target delays are unchanged.
- Navigation map geometry loading moved to `AgentNavigationMapLoader` under the
  Agent navigation capability. WZ-backed map bounds, portals, footholds, ropes,
  swim flags, field limits, return map, and foothold speed loading are unchanged.
- Runtime performance monitoring moved to `AgentPerformanceMonitor` under the
  Agent runtime package. Section timing, pathfind timing, slow-sample/report
  thresholds, snapshots, and legacy perf command toggles are unchanged.
- Chat/social face-expression enum ownership moved to `AgentEmote` under the
  Agent dialogue capability. Numeric expression ids are unchanged.
- Airshow state now enters through `AgentBotAirshowStateRuntime`;
  BotAirshowManager active/trail timing, scripted frame physics fields, bot
  identity lookup, restore checks, and trail foothold reads keep BotEntry as the
  temporary backing store but no longer read or write BotEntry fields directly
  in production.
- Navigation debug overlay identity and active-edge reads now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotNavigationDebugStateRuntime`;
  BotNavigationDebugOverlay path rendering, path-log messages, multi-bot
  selection names, and committed-edge status keep BotEntry as the temporary
  backing store but no longer read `bot` or `navEdge` directly in production.
- Path logger identity, map, and movement-profile reads now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotMovementStateRuntime`;
  BotPathLogger tick capture, graph snapshot resolution, region resolution,
  movement graph summaries, walk-step calculation, and path query calls keep
  BotEntry as the temporary backing store but no longer read `bot` or
  `movementProfile` directly in production.
- Path logger movement-formatting state now enters through
  `AgentBotClimbStateRuntime`, `AgentBotMovementStateRuntime`,
  `AgentBotMovementPhysicsStateRuntime`, and
  `AgentBotNavigationDebugStateRuntime`; BotPathLogger physics-state and
  nav-edge summaries keep BotEntry as the temporary backing store but no longer
  read climb, airborne, crouch, down-jump, velocity, or active-edge fields
  directly in production.
- Bot command target-name resolution now enters through
  `AgentBotRuntimeIdentityRuntime` and the Agent-owned
  `AgentBotCommandParser`; targeted command matching behavior is unchanged, and
  the old bot-package command parser shim has been removed.
- Starter-kit job advancement now lives in `AgentStarterKitService` and reads
  identity through `AgentBotRuntimeIdentityRuntime`; job-change, starter-kit
  grant, auto-equip, and build-status ordering are unchanged.
- KPQ coupon-target loot eligibility now enters through `AgentBotPqRuntime`;
  AgentLootEligibility preserves coupon/pass/rice-cake and quest-item filtering
  behavior while no longer reading KPQ coupon target state directly from
  BotEntry in production.
- KPQ grind-requirement stage reads now enter through `AgentBotPqRuntime`;
  AgentPartyQuestHooks preserves stage-1 grind gating while no longer reading KPQ state
  directly from BotEntry in production.
- KPQ stage-5 reward-claim state now enters through `AgentBotPqRuntime`;
  AgentKpqStage5 preserves reward-claim and announcement behavior while no
  longer reading or writing stage-5 claimed state directly on BotEntry in
  production.
- LLM identity and reply-channel reads now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotReplyChannelStateRuntime`;
  BotLlmReplyManager, PromptBuilder, SituationBuilder, and SenderRelation
  preserve reply gating, prompt wording, memory keys, and relation resolution
  while no longer reading bot, owner, map, or reply-channel state directly from
  BotEntry in production.
- Script runtime state now enters through `AgentBotScriptTaskStateRuntime`;
  BotScriptContext and BotScriptRunner preserve script id reset, step entry,
  step advancement, script-local ints, wait timers, and queued-task behavior
  while no longer reading or writing `entry.script` directly in production.
- KPQ stage-1 state-machine fields now enter through `AgentBotPqRuntime`;
  AgentKpqStage1 preserves stage transitions, coupon target assignment, progress
  reporting, pass exchange, pass delivery, and reset behavior while no longer
  reading or writing KPQ or script runtime fields directly on BotEntry in
  production.
- Fallback movement identity, map, movement-profile, and movement-gate reads
  now enter through `AgentBotRuntimeIdentityRuntime`,
  `AgentBotMovementStateRuntime`, and `AgentBotMovementPhysicsStateRuntime`;
  `AgentFallbackMovementService` preserves rope attach/jump, swim jump-up,
  down-jump, ledge walk-off, and fallback jump behavior while no longer reading
  BotEntry runtime fields directly in production. The old
  `server.bots.BotFallbackMovementManager` source file has been removed.
- Navigation graph data and graph build/cache orchestration now live in
  `server.agents.capabilities.navigation.AgentNavigationGraph` and
  `AgentNavigationGraphService`. Graph region/edge/segment shape, cache
  versioning, warmup executors, build reports, collidable foothold caches, and
  graph lookup behavior are unchanged; BotNavigationManager and BotPhysicsEngine
  remain temporary explicit seams during navigation reconstruction. The old
  `server.bots.BotNavigationGraph` and `server.bots.BotNavigationGraphProvider`
  source files have been removed.
- Equipment map-damage benchmarking now lives in
  `server.agents.capabilities.equipment.AgentMapDamageProfile`. Mob selection,
  live-mob and spawn-template fallback behavior, and range-report inputs are
  unchanged while the remaining equipment optimizer still lives temporarily in
  `BotEquipManager`.
- Equipment weapon/job compatibility and weapon usefulness track-key selection
  now live in `server.agents.capabilities.equipment.AgentWeaponCompatibilityPolicy`.
  First-job skill-family detection, warrior weapon-family checks, mage grouping,
  and self-reserve weapon track labels are unchanged; `BotEquipManager` keeps
  compatibility wrapper methods while the larger optimizer is reconstructed.
- Equipment recommendation result data now uses
  `server.agents.capabilities.equipment.AgentEquipRecommendation`. Optimizer
  recommendation ordering, current/candidate/target-slot values, and trade/build
  consumers are unchanged while recommendation generation remains temporarily in
  `BotEquipManager`.
- Equipment self-reserve/usefulness policy now lives in
  `server.agents.capabilities.equipment.AgentEquipmentReservePolicy`. Relevant
  stat tracks, Pareto baseline filtering, owned-item reserve selection,
  incoming-item reserve checks, future own-class eligibility, text-slot
  filtering, and usefulness scoring are unchanged; `BotEquipManager` keeps
  wrapper methods for callers that still enter through the temporary bot seam.
- Equipment requirement-gate helpers now also enter through
  `AgentEquipmentReservePolicy`: stat-only blocked checks, own-class
  wearability checks, and future-only candidate gates preserve the same
  huge-stat requirement simulation while reducing direct BotEquipManager use
  from inventory grouping code.
- Equipment requirement comparison predicates now enter through
  `AgentEquipmentReservePolicy`: level/job/stat/fame ease checks and future
  slot/weapon-track matching preserve the same dominance-pruning behavior while
  `BotEquipManager` remains the temporary optimizer compatibility seam.
- Reserved-equipment trade ordering now enters through
  `AgentInventoryTradePolicy`: own-class reserved equip scoring and
  worst-to-best sort order preserve the same job-sensitive stat weighting while
  `BotInventoryManager` remains the temporary trade sequencing seam.
- Inventory item-id trade ordering now enters through `AgentInventoryTradePolicy`.
  Plain inventory-view sorting still orders by item id then bag position, and
  bot trade sequencing remains unchanged through compatibility delegates.
- Inventory trade category parsing now enters through `AgentInventoryTradePolicy`.
  Equip/ammo group category strings, next-group ordering, reserved-equip page
  detection, page parsing, and trade-window page sizing preserve the same
  legacy tokens while `BotInventoryManager` keeps item classification and trade
  sequencing.
- Duplicate-aware trade item prioritization now enters through
  `AgentInventoryTradePolicy`. ETC, scroll, and USE bucket ordering still sorts
  by item id/position first and then moves item ids already present in the
  recipient inventory ahead within the same legacy buckets.
- Potion-share stack selection now enters through `AgentPotionSharePolicy`.
  Donor USE-inventory scanning, recovery-pot filtering, HP/MP slot matching,
  worst-first recovery sorting, and nine-stack/share-budget limits are unchanged;
  trade start/retry side effects remain in the temporary inventory seam.
- Ammo-share stack selection now enters through `AgentInventoryAmmoPolicy`.
  Donor USE-inventory scanning, weapon-ammo filtering, projectile WATK/item-id
  ordering, and nine-stack/share-budget limits are unchanged; trade start/retry
  side effects remain in the temporary inventory seam.
- Trade-ammo weapon-type eligibility now enters through
  `AgentInventoryAmmoPolicy`; bow, crossbow, claw, and gun remain the only
  weapon families that request ammo-share behavior.
- Inventory item-presence checks now enter through `AgentInventoryItemPolicy`.
  Agent offer/shop code no longer calls the bot inventory seam to verify that
  the exact item object still occupies its recorded inventory slot; the old
  `BotInventoryManager.hasItem` method remains a compatibility delegate.
- Inventory drop-safety checks now enter through `AgentInventoryItemPolicy`.
  Untradeable and quest-item rejection rules are unchanged, with server config
  and item-info lookups passed in from the temporary bot drop execution seam.
- Inventory safe-bag collection now enters through `AgentInventoryItemPolicy`.
  Slot-order scans still filter through the same drop-safety rule and caller
  predicate before appending candidates for trade/drop flows.
- Inventory drop-slot selection now enters through `AgentInventoryItemPolicy`.
  Drop commands still execute through the bot seam, but the slot list is chosen
  by the same Agent-owned safety and caller predicate rules.
- Named item collection now enters through `AgentInventoryItemPolicy`. The
  inventory-type scan, normalized fragment matching, and safe-item filtering are
  Agent-owned while the temporary bot seam still supplies cached item-name
  lookup and command-side trade/drop wiring.
- Physics position, horizontal-speed, and ground-travel carry state now enter
  through `AgentBotMovementPhysicsStateRuntime`; BotPhysicsEngine preserves
  landing, grounded travel, swim, airborne collision, climb-position, and reset
  behavior while no longer reading or writing `physX`, `physY`, `hspeed`, or
  `groundPhysicsCarryMs` directly in production.
- Combat grind-region sibling occupancy and sibling gear-offer targeting now
  enter through `AgentBotRuntimeIdentityRuntime` and `AgentBotModeStateRuntime`;
  BotCombatManager and AgentOfferService preserve sibling filtering, map matching,
  and recipient selection while no longer reading sibling bot/owner/grinding
  fields directly in those paths.
- BotManager sibling target discovery, bot-id lookup, replacement cancellation,
  removal matching, and transfer authorization identity now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotManagerSchedulerRuntime`; the
  registry behavior, task cancellation timing, and authorization inputs remain
  unchanged while those paths stop reading BotEntry identity/task fields
  directly.
- BotManager first-bot lookup now enters through
  `AgentBotRuntimeIdentityRuntime`; public owner-to-bot lookup preserves the
  same first-entry behavior while no longer reading the BotEntry bot field
  directly.
- KPQ stage-1 script context access now enters through `BotScriptContext`
  accessors; the stage script preserves Cloto movement, coupon assignment,
  progress reporting, exchange, and pass delivery behavior while no longer
  reading script context runtime fields directly.
- Shop purchase sequencing now uses `AgentBotShopPurchaseSequence` and
  `AgentBotShopPurchaseAction`; AgentShopService preserves resupply, recharge,
  potion purchase, trash-sale, shortfall, announcement, and finish behavior
  while the active purchase context is owned by the Agent integration layer
  instead of a private bot runtime record.
- Shop purchase shortfall reporting now uses `AgentBotShopBuyReport` and
  `AgentBotShopShortfallReason`; AgentShopService preserves the same quantity,
  meso, space, and generic-failure reporting while the purchase report value
  object is owned by the Agent integration layer.
- Potion donor planning now uses `AgentBotPotionDonorPlan`; AgentPotionService
  preserves the same donor selection, qualification threshold, donation
  quantity, delay, and transfer behavior while the donor plan context is owned
  by the Agent integration layer instead of a private bot runtime record.
- Ammo donor planning now uses `AgentBotAmmoDonorPlan`; AgentAmmoService
  preserves the same donor selection ordering, same-ammo preference, donation
  quantity, delay, and transfer behavior while the donor plan context is owned
  by the Agent integration layer instead of a bot package record.
- Bot command target, transfer, targeted-command match, and bot-entry parser
  adapter now use `AgentBotCommandTarget`, `AgentBotTransferCommand`,
  `AgentBotTargetedCommandMatch`, and `AgentBotCommandParser`; parsed command
  boundary data and bot-entry adaptation are owned by the Agent integration
  layer while preserving the same `AgentCommandParser` matching behavior.
- First-job starter-kit service/data now live in `AgentStarterKitService`,
  `AgentStarterKitCatalog`, and `AgentStarterItemGrant`; the old
  `BotStarterKitManager` file has been removed without changing job-change,
  grant, auto-equip, or build-status behavior.
- Reply-channel state now uses the Agent-owned `AgentReplyChannel` enum across
  BotEntry, chat handlers, reply runtimes, offer replies, LLM gating, and tests;
  the legacy `server.bots.ReplyChannel` enum has been removed without changing
  MAP/PARTY/WHISPER routing behavior.
- Bot ownership lookup and authorization now live in
  `AgentOwnershipService` with `AgentResolvedCharacter` and
  `AgentAuthorizationResult`; the same DB-backed `bot_owners` behavior is
  preserved for this reconstruction stage, but the service is no longer owned
  by the bot package.
- Navigation path logging now lives in
  `server.agents.monitoring.AgentPathLogger`; the `BotEntry` attachment point
  and path-log output format are preserved while the diagnostic runtime is no
  longer owned by the bot package.
- Airshow side effects now live in
  `server.agents.capabilities.social.airshow.AgentAirshowService`; command
  routing, scripted frame timing, trail packet behavior, and restore/reset
  behavior are preserved.
- Navigation probe/report tooling now lives in
  `server.agents.capabilities.navigation.AgentNavigationProbe`; `@regennav`
  output, CLI probe formatting, graph build reports, and optimality measurement
  remain unchanged.
- Navigation debug overlay tooling now lives in
  `server.agents.capabilities.navigation.AgentNavigationDebugOverlay`; `!botnav`
  graph/path/pathlog/clear routing, fake-mist rendering, and auto-clear behavior
  remain unchanged.
- Scroll reaction behavior now lives in
  `server.agents.capabilities.social.AgentScrollReactionService`; reaction
  radius, load/streak math, chat/emote/fidget chances, and scheduler/reply
  adapter calls remain unchanged.
- Consumable buff-pot behavior now lives in
  `server.agents.capabilities.combat.AgentBuffService`; tick timing, relevant
  stat filtering, cheap-mode cap handling, chat summaries, and debug reports
  remain unchanged.
- Fidget mode and trigger state now use `AgentFidgetMode` and
  `AgentFidgetTrigger` under the Agent movement fidget capability; BotEntry and
  AgentFidgetService preserve the same NONE/WAIT/JUMP/DIAGONAL_JUMP/PRONE/
  SPAM_PRONE/SPAM_SIDEWAYS and NONE/AUTO_FOLLOW/IDLE/SOCIAL behavior while the
  enum ownership moves out of `server.bots`.
- AoE single-target detection and capped cluster-size policy now live in
  `AgentCombatScoringPolicy`; BotCombatManager preserves the same AoE skill-id,
  target-count, live-map cluster, and mob-count cap behavior through a thin
  compatibility delegate.
- Grind-region occupancy penalty math now lives in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still performs the legacy
  sibling-region scan but delegates the exact occupied-count, per-region
  penalty, and cap calculation to Agent combat policy.
- Grind graph path-cost branching now lives in `AgentCombatGrindTargetPolicy`;
  BotCombatManager still resolves regions and navigation paths, then delegates
  invalid-region, same-region local cost, no-path, and edge-cost summing
  semantics to Agent combat policy.
- Local grind-target scored-list construction now lives in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still supplies live
  foothold, local-score, and AoE-cluster callbacks while Agent combat owns the
  adjusted local-score, graph-score mirror, and distance-square record shape.
- Grind-region scored-list construction now lives in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still resolves live
  navigation region ids, path costs, occupancy, and local-score callbacks while
  Agent combat owns invalid-region skipping, region grouping, best-target
  selection per region, and scored-target conversion.
- Follow-mode local combat target scored-list construction now lives in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still supplies live
  local/immediate-projectile eligibility and scoring callbacks while Agent
  combat owns selectable filtering and adjusted scored-target record creation.
- Grind and patrol reachable-target selection now lives in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still supplies the scored
  candidates while Agent combat owns legacy ordering, reachable-target
  preference, and all-unreachable rejection.
- Grind-target local-vs-region scoring dispatch now lives in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still resolves the live
  navigation graph context and supplies lazy local/region scoring callbacks.
- Reachable grind-target decision logic now lives in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still resolves immediate
  projectile eligibility and graph target cost lazily while Agent combat owns
  the dead-target, missing-runtime, no-graph, and unreachable-cost decisions.
- Buff blacklist classification now lives in `AgentCombatSkillClassifier`;
  BotCombatManager preserves the same Dark Sight exclusion behavior while the
  never-cast skill list is owned by Agent combat classification.
- The legacy Dragon Roar minimum-target threshold now lives in
  `AgentCombatSupportPolicy`; BotCombatManager still supplies the nearby-healer
  state but no longer owns the hardcoded support-combat threshold.
- Combat skill-label formatting now lives in `AgentCombatDialogueReporter`;
  BotCombatManager preserves the same SkillFactory lookup and `skill#<id>`
  fallback for AoE reposition logs and skill-buff debug/failure summaries.
- Combat debug-stat and skill-buff debug report assembly now lives in
  `AgentBotCombatReportRuntime`; BotCombatManager keeps temporary compatibility
  delegates while debug-stat formatting, active-buff, cached-buff, cooldown, and
  last-action report lines are assembled through the Agent integration/reporting
  path. The debug-stat report still reads target/attack-plan inputs from the
  legacy combat planner until that planner slice migrates.
- Inventory trade page/meso policy now lives in `AgentInventoryTradePolicy`;
  BotInventoryManager preserves the same reserved-equips category, trade-page
  clamp, meso category parsing, requested-meso parsing, and insufficient-meso
  reply behavior through compatibility delegates, while Agent transfer routing
  uses the Agent policy directly.
- Sell-trash equipment protection policy now lives in
  `AgentInventorySellTrashPolicy`; BotInventoryManager preserves scrolled-equip,
  non-weapon WATK, class-stat, base-stat, pure-stat, and weapon attack/magic
  attack protection behavior through compatibility delegates.
- Ammo item-to-weapon classification now lives in `AgentInventoryAmmoPolicy`;
  BotInventoryManager preserves bow-arrow, crossbow-arrow, throwing-star, and
  bullet matching plus trade-ammo item detection through compatibility
  delegates.
- Autopot potion tier ranking and HP/MP slot choice policy now live in
  `AgentAutopotPolicy`; AgentPotionService preserves the same USE-inventory scan,
  keybinding assignment, alert thresholds, and debug-report wiring through
  compatibility delegates.
- Potion-share recovery scoring and HP/MP share-slot eligibility now live in
  `AgentPotionSharePolicy`; BotInventoryManager preserves the same item-effect
  lookup, candidate sorting, max-stack, and trade-transfer behavior through
  compatibility delegates.
- Ammo-share request eligibility, donor quantity math, and donor tie-break
  policy now live in `AgentAmmoSharePolicy`; AgentAmmoService preserves the same
  cooldown/backoff, donor scan, visible request/offer dialogue, delayed trade,
  and inventory transfer behavior through compatibility delegates.
- USE-item recovery-potion and buff-consumable classification now lives in
  `AgentUseItemClassificationPolicy`; BotInventoryManager and Agent inventory
  dialogue reporting preserve their existing item-effect lookup and category
  behavior through compatibility delegates.
- Pure recovery potion HP/MP stack counting now lives in
  `AgentPotionInventoryPolicy`; AgentPotionService preserves the same USE
  inventory scan, item-effect lookup, timing metric, and public count API
  through a compatibility delegate.
- Passive HP/MP recovery formula and legacy recovery skill-bonus lookup now
  live in `AgentPassiveRecoveryPolicy`; AgentPotionService preserves the same
  movement/air/climb/stance standing-still gate and MP recovery tick timing
  through compatibility delegates.
- Potion and ammo sharing request/offer dialogue pools now live in
  `AgentDialogueCatalog`; AgentPotionService and AgentAmmoService preserve the same
  random reply selection and visible map-chat delivery through compatibility
  delegates.
- Inventory trade invitation, thanks, freebie, all-done, and reserved-equip
  warning reply pools now live in `AgentDialogueCatalog`; BotInventoryManager
  preserves the same random reply selection, trade chat, and visible map-chat
  delivery through compatibility delegates.
- Shop resupply and shopping dialogue pools now live in
  `AgentDialogueCatalog`; AgentShopService preserves the same random reply
  selection and visible map-chat delivery through compatibility delegates.
- Gear/loot offer accept/decline replies, busy/no-upgrade fixed replies,
  owner-upgrade request prompts, and current/future loot-offer prompt templates
  now live in `AgentDialogueCatalog`; AgentOfferService preserves the same random
  reply selection, prompt formatting, delayed delivery, and trade-transfer
  behavior through compatibility delegates.
- Legacy AoE cluster target-selection radius/bonus defaults and default cluster
  helper wrappers now live in `AgentCombatScoringPolicy`; BotCombatManager still
  supplies live skill-cache and map monster state while delegating the pure
  cluster scoring policy to Agent combat.
- AoE reposition centroid, bounded-shift, and arrival-window math now live in
  `AgentCombatScoringPolicy`; BotCombatManager still owns plan construction,
  translated-hitbox target collection, DPS comparison, and debug logging.
- AoE reposition preflight gating now lives in `AgentCombatScoringPolicy`;
  BotCombatManager still supplies live config, runtime, selected-plan, and
  cached AoE-skill inputs while Agent combat owns the early decision to skip
  reposition work.
- Grind-region sibling occupant eligibility/counting rules now live in
  `AgentCombatGrindTargetPolicy`; BotCombatManager still gathers live sibling
  agents and resolves their navigation regions through the legacy bot runtime.
- Support-buff candidate gating now lives in `AgentCombatSupportPolicy`;
  BotCombatManager still resolves live skills/effects and dispatches the
  SPECIAL_MOVE packet through the legacy bot runtime.
- Support-cast readiness ordering now lives in `AgentCombatSupportPolicy` with
  lazy cost evaluation so the missing-skill and dead cases preserve the legacy
  short-circuit behavior; BotCombatManager still emits the existing debug text.
- Attack-plan range selection now lives in `AgentCombatRangePolicy`: hitbox
  plans use monster-hitbox intersection and plans without hitboxes fall back to
  the legacy basic attack range check. BotCombatManager only adapts AttackPlan
  fields to the Agent-owned policy.
- Skill-buff tick preflight decisions now live in `AgentCombatSupportPolicy`,
  including the legacy debug summaries for disabled, idle, and empty-cache
  states. BotCombatManager still performs live monster checks and cast dispatch.
- Support-heal tick preflight now lives in `AgentCombatSupportPolicy`; the
  legacy bot runtime still resolves the heal skill/effect, applies HP recovery,
  optionally jump-heals, and emits the captured Heal attack packet shape.
- The best single-target score floor used for AoE-vs-single-target comparison
  now lives in `AgentCombatScoringPolicy`; BotCombatManager still resolves the
  cached skill/effect before passing damage and hit-count inputs.
- Basic attack target selection and opposite-facing pivot orchestration now live
  in `AgentBasicAttackPlanner`; BotCombatManager still builds concrete attack
  data, applies Shadow Partner hit counts, and constructs the legacy AttackPlan.
- Attack execution preflight ordering now lives in
  `AgentCombatAttackExecutionPolicy`; BotCombatManager still builds AttackInfo,
  resolves damage profiles, applies attack routes, and mutates cooldown/facing
  runtime state.
- Skill attack preflight ordering now lives in `AgentSkillAttackPlanner`,
  preserving the legacy short-circuit order for missing id, cooldown, missing
  skill, missing level, skill cost, and weapon compatibility. BotCombatManager
  still resolves effects/actions/hitboxes and constructs the legacy AttackPlan.
- Skill ammo readiness now lives in `AgentSkillAttackPlanner`, preserving the
  legacy `max(bulletCount, bulletConsume) * hitMultiplier` ranged-only gate with
  lazy inventory counting. BotCombatManager still owns inventory access.
- Skill post-hitbox primary-target resolution now lives in
  `AgentSkillAttackPlanner`, preserving the strike-point basic-weapon reach
  gate, non-strike effective-primary replacement, and final monster hitbox
  intersection check. BotCombatManager still resolves concrete hitboxes and
  concrete hitboxes.
- Skill attack packet-field planning now lives in `AgentSkillAttackPlanner`,
  preserving the legacy close-range display/body-action mimic, ranged
  zero-display path, shared direction/ranged-direction value, and facing stance
  calculation. BotCombatManager still resolves concrete skill actions and
  attack timing.
- Support-heal cast trigger policy now lives in `AgentCombatSupportPolicy`,
  preserving the legacy rule that Heal casts when either the party needs HP or
  undead targets are inside the Heal hitbox. BotCombatManager still resolves
  the concrete skill bounds, live map monsters, packet broadcast, HP/MP
  application, and movement side effects.
- Combat ammo-check decision policy now lives in `AgentCombatAmmoPolicy`,
  preserving the legacy non-ammo clear, mage MP-potion outage, projectile
  low-ammo warning, projectile no-ammo stop, and already-warned no-op outcomes.
  BotCombatManager still counts concrete inventory items and performs follow,
  state, and dialogue side effects.
- AoE skill-cache score calculation now lives in `AgentCombatSkillClassifier`,
  preserving the legacy `max(0, damage) * max(1, hits) * max(1, mobs)` ranking
  formula while BotCombatManager still mutates the temporary skill-cache state.
- Support-buff skill-cache eligibility now lives in
  `AgentCombatSkillClassifier`, preserving the legacy active-support-skill plus
  buff-blacklist gate while BotCombatManager still mutates the temporary
  buff-skill cache and rebuff timers.
- Skill-cache bucket classification for attack, summon, and support-buff
  branches now uses `AgentCombatSkillClassifier.SkillCacheBucket`, preserving
  legacy cache mutation in BotCombatManager while moving branch ownership into
  Agent combat classification.
- Support-buff no-living-mobs preflight now lives in
  `AgentCombatSupportPolicy`, preserving the legacy skip when a map has no
  alive monsters while BotCombatManager still performs the live map scan and
  support-buff side effects.
- Support-buff cast readiness failure summaries now live on
  `AgentCombatSupportPolicy.SupportCastReadiness`, preserving the legacy
  missing-level, dead, and cannot-pay-cost debug wording while BotCombatManager
  still performs the actual support skill cast side effects.
- Support-buff cast outcome summaries now live in `AgentCombatSupportPolicy`,
  preserving the legacy special-move-failed and successful-cast debug wording
  while BotCombatManager still dispatches the SPECIAL_MOVE packet and applies
  cooldown/facing side effects.
- The fixed support-buff cooldown summary now lives in
  `AgentCombatSupportPolicy`, preserving the legacy “all skill buffs active or
  on cooldown” debug line while BotCombatManager still records it in the
  temporary skill-buff debug state.
- Support-buff refresh timing now lives in `AgentCombatSupportPolicy`,
  preserving the legacy `now + (long) (duration * 0.9)` rebuff schedule while
  BotCombatManager still writes the temporary next-buff runtime state.
- Support-buff cast cooldown calculation now lives in
  `AgentCombatSupportPolicy`, preserving the legacy 1000ms animation fallback
  and `max(skillTimingCooldown, animationMs)` cooldown floor while
  BotCombatManager still writes the temporary attack cooldown state.
- Heal skill-cache stop policy now lives in `AgentCombatSkillClassifier`,
  preserving the legacy rule that both active and inactive Heal skills stop
  further attack/summon/support cache classification while only active Heal
  writes the temporary heal-skill cache id.
- Physical mob touch damage now calls the Agent-owned
  `AgentDefenseDataProvider` directly from the temporary bot side-effect path;
  the leftover `BotCombatManager` wrapper has been removed while preserving the
  existing mob-hit damage and knockback flow.
- Skill classification export and support-buff checks now call
  `AgentCombatSkillClassifier`/`AgentCombatHitCounter` directly; the remaining
  bot-side skill-classification wrapper methods have been removed while
  preserving the same classification precedence.
- Effective hit-count reads inside combat planning now call
  `AgentCombatHitCounter` directly; the temporary `BotCombatManager` wrapper has
  been removed while preserving the legacy max(attackCount, bulletCount, 1)
  formula.
- Dragon Knight weapon-family attack gating now calls
  `AgentCombatWeaponPolicy` directly from skill planning and tests; the
  temporary `BotCombatManager` compatibility wrapper has been removed while
  preserving the same spear/polearm restrictions.
- Follow-mode projectile range checks now call
  `AgentProjectileHitbox.passiveProjectileRangeBonus` directly; the temporary
  `BotCombatManager` projectile-range wrapper has been removed while preserving
  the same Eye of Amazon/Keen Eyes range bonus behavior.
- Fall-damage application now calls `AgentFallDamageCalculator` directly; the
  temporary `BotCombatManager` fall-damage formula wrapper has been removed
  while preserving the captured legacy fall-distance damage curve.
- Skill-attack strike-point anchoring checks now call
  `AgentCombatSkillHitboxPolicy` directly; the temporary `BotCombatManager`
  anchor-check wrapper has been removed while preserving Arrow Bomb reach
  gating behavior.
- Ranged-ammo weapon classification now calls `AgentCombatAmmoCounter` directly
  from combat and manager flows; the temporary `BotCombatManager` ammo-weapon
  wrapper has been removed while preserving bow/crossbow/claw/gun detection.
- Ammo counting now calls `AgentCombatAmmoCounter` directly from combat,
  ammo-share, and shop-resupply flows; the temporary `BotCombatManager`
  count-ammo wrapper has been removed while preserving USE-inventory projectile
  matching and Soul Arrow/Shadow Claw unlimited-ammo behavior.
- The unused `BotCombatManager` airborne ranged-weapon compatibility wrapper has
  been removed; airborne ranged-route gating remains Agent-owned in
  `AgentCombatRangePolicy`.
- Mob-hit and fall-knockback code now calls `AgentMobKnockbackPolicy` directly;
  temporary `BotCombatManager` wrappers for knockback eligibility, direction,
  and OpenStory-step scaling have been removed while preserving the same stance,
  climbing, HP, direction, and tick-length inputs.
- Skill-cache signature, best single-target skill comparison, cached attack-skill
  id ordering, and support-heal self-threshold checks now call
  `AgentCombatSkillClassifier` and `AgentCombatSupportPolicy` directly from the
  remaining combat shell; the temporary `BotCombatManager` wrappers have been
  removed while preserving the same cache state and configured heal ratio inputs.
- Dragon Roar support-safety checks now call `AgentCombatSupportPolicy` directly;
  the temporary `BotCombatManager` Dragon Roar/healer-ally wrappers have been
  removed while preserving target-count and nearby-healer inputs.
- Damage weapon-type resolution now calls `AgentCombatWeaponPolicy` directly;
  temporary `BotCombatManager` weapon policy wrappers have been removed while
  preserving Dragon Knight spear/polearm and action-name normalization behavior.
- Shadow Partner hit multiplier checks now call `AgentCombatHitCounter`
  directly; the temporary `BotCombatManager` multiplier wrapper has been
  removed while preserving ranged-only Shadow Partner doubling behavior.
- Skill-hitbox construction now calls `AgentCombatSkillHitboxPolicy` directly
  from the remaining combat shell; temporary `BotCombatManager` fallback and
  projectile hitbox wrappers have been removed after moving their duplicate
  geometry coverage into Agent combat tests.
- Strike-point basic-reach checks and basic weapon reach rectangles now call
  `AgentCombatRangePolicy` directly; temporary `BotCombatManager` range wrappers
  have been removed while preserving the legacy MAGIC-route reach behavior.
- Skill target collection and hitbox intersection now call
  `AgentCombatTargetSelector` and `AgentCombatHitboxIntersection` directly from
  the combat shell; temporary `BotCombatManager` target-hitbox wrappers have
  been removed while preserving the same live map monster source.
- Local travel-cost estimation and nearest-cluster monster selection now call
  `AgentCombatScoringPolicy` directly; temporary `BotCombatManager` scoring
  wrappers have been removed while preserving the same points, movement profile,
  and cluster inputs.
- Hostile target eligibility and immediate projectile target checks now call
  `AgentCombatTargetEligibilityPolicy` and `AgentCombatImmediateTargetPolicy`
  directly; temporary `BotCombatManager` wrappers have been removed while
  preserving ammo-state and cached attack-skill inputs.
- Skill-cost affordability preflight now calls `AgentCombatSkillUsePolicy`
  directly from attack execution readiness; the temporary `BotCombatManager`
  skill-use wrapper has been removed while preserving SkillFactory/effect cost
  behavior.
- Support party missing-buff and heal-bounds checks now call
  `AgentCombatSupportPolicy` directly from support buff/heal orchestration; the
  temporary `BotCombatManager` wrappers have been removed while preserving the
  same support range, vertical range, heal hitbox, and heal-ratio inputs.
- Combat skill label formatting now calls `AgentCombatDialogueReporter`
  directly from the remaining combat shell; the temporary `BotCombatManager`
  reporting wrapper has been removed while preserving SkillFactory display-name
  fallback behavior.
- Support SPECIAL_MOVE packet layout tests and dispatch now call
  `AgentSupportSpecialMovePacketBuilder` directly; the temporary
  `BotCombatManager` packet-builder wrapper has been removed while preserving
  the captured self-buff and party-buff packet shapes.
- Local combat selector/planner pass-through helpers now call Agent policies
  directly from the remaining combat shell; temporary `BotCombatManager`
  wrappers for best-target picking, best-plan selection, AoE cluster lookup, and
  basic-attack data construction have been removed while preserving the same
  Agent policy inputs.
- AoE reposition scoring now uses
  `AgentAttackPlanScoringPolicy.AgentAttackPlanScore` directly; the temporary
  `BotCombatManager` `PlanScore` mirror and score wrapper have been removed
  while preserving the same DPS and guaranteed-kill comparisons.
- Bot combat config command facades now call `AgentCombatConfig` directly from
  `BotManager`; the temporary `BotCombatManager` config pass-through methods
  have been removed while preserving live config field listing, lookup, and
  mutation behavior.
- Jumpable target checks now call `AgentCombatRangePolicy` directly from
  `BotManager` and focused tests; the temporary `BotCombatManager`
  `isTargetJumpable` wrappers have been removed while preserving the same
  movement profile and `BotPhysicsEngine` jump-height input.
- Effective-primary and closest-alive target selection now call
  `AgentCombatTargetSelector` directly from combat planning and focused tests;
  the temporary `BotCombatManager` target-selector pass-through wrappers have
  been removed while preserving the same map monster source and bot-position
  inputs.
- AoE single-target detection and capped cluster-size comparison now call
  `AgentCombatScoringPolicy` directly from `BotManager` and focused tests; the
  temporary `BotCombatManager` AoE scoring wrappers have been removed while
  preserving the same cached AoE skill id and mob-count inputs.
- Attack-plan airborne/grounded readiness now calls `AgentCombatRangePolicy`
  directly from `BotManager`, `BotCombatManager.attackMonster`, and focused
  tests; the temporary `BotCombatManager` `canUseAttackPlanNow` wrapper has been
  removed while preserving the same grounded, weapon, and route inputs.
- Attack-plan target range checks now call `AgentCombatRangePolicy` directly
  from `BotManager`; the temporary `BotCombatManager`
  `isTargetInAttackRange` wrapper has been removed while preserving the same
  null-plan rejection, plan-hitbox intersection, and basic-range fallback.
- The unused bot-side inclusive-rectangle pass-through has been removed from
  `BotCombatManager`; mob-touch sweep geometry remains Agent-owned in
  `AgentMobTouchPolicy`.
- Map-backed alive-monster range collection now calls
  `AgentCombatTargetSelector.aliveMonstersInRange(Character, Point, double)`
  directly from grind, patrol, and follow target search; the temporary
  `BotCombatManager` helper has been removed while preserving the same current
  map monster source and hostile/living/range filters.
- Opposite-facing basic-attack target pivoting and strike-point primary
  resolution now call `AgentCombatTargetSelector` directly from the remaining
  combat planner; the temporary `BotCombatManager` target-selector pass-through
  helpers have been removed while preserving the same mirrored hitbox and basic
  weapon reach inputs.
- Support-heal undead target collection now calls
  `AgentCombatTargetSelector.collectUndeadMobsInHealRange(Character, StatEffect,
  Rectangle)` directly; the temporary `BotCombatManager` helper has been
  removed while preserving the same map-object query, WZ mob cap, and null-bounds
  empty-target behavior.
- The no-entry `BotCombatManager.findGrindTarget(Character)` compatibility
  overload has been removed; combat debug fallback target search now uses the
  entry-aware target-search path available to the caller.
- Skill-buff debug decision recording now calls
  `AgentBotSkillBuffDebugStateRuntime.rememberAction` directly from the
  remaining support-buff flow; the temporary `BotCombatManager` reporting helper
  has been removed while preserving the same timestamp source and summary text.
- AoE cluster target bias now calls
  `AgentCombatScoringPolicy.legacyAoeClusterBonus` directly from follow, local,
  and region target scoring; the temporary `BotCombatManager` cluster-bonus
  helper has been removed while preserving the same multi-mob cache-state inputs.
- Reachable-grind-target graph cost now resolves the target region directly at
  the decision site; the one-use `BotCombatManager` graph-target-cost helper has
  been removed while preserving the same unreachable fallback and graph path cost
  calculation.
- The unused `BotCombatManager.getSkillBuffDebugLines` reporting wrapper has
  been removed; skill-buff debug lines remain owned by
  `AgentBotCombatReportRuntime`.
- Support buff SPECIAL_MOVE dispatch now lives in
  `AgentSupportSpecialMoveExecutor`; `BotCombatManager` calls the Agent-owned
  executor while preserving the same packet builder, timestamp, packet handler
  lookup, validation, and dispatch flow.
- Attack-facing memory now lives in `AgentBotCombatFacingRuntime`;
  `BotCombatManager` calls the Agent-owned runtime while preserving the same
  attack-packet stance conversion, facing direction update, and character-state
  sync.
- Attack/move action-lock countdown routing now lives in
  `AgentBotCombatActionLockRuntime`; BotManager calls the Agent-owned runtime
  while preserving the same legacy movement tick-down cadence and attack-before-
  move-window priority.
- Combat alert stance timing and reset scheduling now live in
  `AgentBotCombatAlertRuntime`; BotCombatManager preserves the same damage,
  heal, attack, and support-buff alert triggers through the Agent-owned runtime.
- Mob-touch sweep bounds, lower-body touch checks, and last-check position
  memory now live in `AgentBotMobTouchRuntime`; BotCombatManager preserves the
  same hostile-living-monster filtering and damage application path while
  delegating touch detection to Agent runtime.
- Combat ammo/MP-potion shortage checks now live in
  `AgentBotCombatAmmoCheckRuntime`; potion and shop flows call the Agent-owned
  runtime while preserving the same ammo thresholds, no-ammo state, follow-owner
  fallback, and map-chat replies.
- Combat debug-stat report assembly now lives in
  `AgentBotCombatReportRuntime`; `BotCombatManager.describeDebugStats` is a
  temporary compatibility delegate while the Agent reporting runtime preserves
  the same target lookup, attack route, speed, cooldown, tick, and AI cadence
  output.
- Combat debug-stat target search and attack-plan lookup now call
  `AgentBotCombatTargetRuntime` and `AgentBotCombatPlanRuntime` directly from
  `AgentBotCombatReportRuntime`; the report path no longer calls back through
  `BotCombatManager.findGrindTarget` or `BotCombatManager.planAttack` while
  preserving the same shared combat config object and report output.
- Combat support-buff tick orchestration now lives in
  `AgentBotCombatBuffRuntime`; `BotCombatManager.tickBuffs` is a temporary
  compatibility delegate while the Agent runtime preserves the same buff-enable
  gates, living-mob preflight, party-support checks, SPECIAL_MOVE dispatch,
  cooldown windows, and debug summaries.
- Combat skill-cache rebuild orchestration now lives in
  `AgentBotCombatSkillCacheRuntime`; `BotCombatManager.rebuildSkillCacheIfNeeded`
  is a temporary compatibility delegate while the Agent runtime preserves the
  same skill signature guard, attack/AoE/heal/summon/support-buff bucket
  selection, best single-target skill ordering, and support-buff next-tick
  initialization.
- Combat support-heal orchestration now lives in
  `AgentBotCombatHealRuntime`; `BotCombatManager.tickSupportHealing` is a
  temporary compatibility delegate while the Agent runtime preserves the same
  heal-skill gate, HP/undead target decision, jump-heal movement, Heal attack
  packet construction, cooldown, move-window, alert, and movement-broadcast
  behavior.
- Combat action-state clearing now lives in
  `AgentBotCombatActionStateRuntime`; the damage/death path calls the Agent
  helper while preserving the same grind target, attack cooldown, move window,
  navigation state, and movement broadcast invalidation reset.
- Combat death-state entry now lives in `AgentBotCombatDeathRuntime`;
  `BotCombatManager.enterDeadState` is a temporary compatibility delegate while
  the Agent runtime preserves action-state clearing, physics dead stance sync,
  movement broadcast, dead-window timing, and optional death dialogue.
- Combat mob/fall damage application now lives in
  `AgentBotCombatDamageRuntime`; `BotCombatManager.applyMobHit` and
  `BotCombatManager.applyFallDamage` are temporary compatibility delegates while
  the Agent runtime preserves DAMAGE_PLAYER packet fields, HP/autopot mutation,
  mob-hit cooldown, alert stance, fatal-death routing, stance-buff knockback
  gating, and air/ground knockback physics dispatch.
- Combat mob-touch polling now lives in `AgentBotCombatDamageRuntime`;
  `BotCombatManager.tickMobDamage` is a temporary compatibility delegate while
  the Agent runtime preserves mob-hit cooldown countdown, hostile-only contact
  filtering, client-style touch sweep checks, first-hit return behavior, and
  last touch-check position memory.
- Combat attack execution now lives in `AgentBotCombatAttackRuntime`;
  `BotCombatManager.attackMonster` is a temporary compatibility delegate while
  the Agent runtime preserves attack readiness gates, AttackInfo packet fields,
  shared damage profile/target construction, route dispatch, attack cooldown,
  facing memory, and alert stance side effects.
- Basic attack planning now lives in `AgentBasicAttackPlanRuntime`;
  `BotCombatManager.planBasicAttack` is a temporary compatibility adapter while
  Agent-owned logic preserves basic attack target correction, opposite-facing
  pivot checks, Shadow Partner damage-line multiplier, packet field selection,
  timing, cooldown, and damage weapon type resolution.
- Skill attack planning now lives in `AgentSkillAttackPlanRuntime`;
  `BotCombatManager.planSkillAttack` is a temporary compatibility adapter while
  Agent-owned logic preserves skill readiness, MP/HP and ammo gates,
  strike-point AoE primary correction, hitbox calculation, target collection,
  Dragon Roar support safety, packet field selection, timing, cooldown, and
  damage weapon type resolution.
- Attack-plan orchestration now lives in `AgentBotCombatPlanRuntime`;
  `BotCombatManager.planAttack` is a temporary compatibility adapter while the
  Agent runtime preserves cached skill candidate ordering, basic attack fallback,
  best-plan scoring, and the existing `combat-plan` performance metric.
- Combat ground foothold lookup now lives in `AgentBotCombatGroundRuntime`;
  BotCombatManager no longer owns the null guards or physics-engine bridge used
  by grind, patrol, follow, and local target scoring.
- AoE repositioning now lives in `AgentBotCombatAoeRepositionRuntime`;
  `BotCombatManager.aoeRepositionTarget` is a temporary compatibility delegate
  while Agent-owned logic preserves the enable gate, cluster geometry, shifted
  hitbox scoring, full-HP kill priority, DPS threshold, and debug log payload.
- Combat target search now lives in `AgentBotCombatTargetRuntime`;
  `BotCombatManager.findGrindTarget`, `findPatrolTarget`,
  `findFollowAttackTarget`, and `isReachableGrindTarget` are temporary
  compatibility delegates while Agent-owned logic preserves candidate ranges,
  local-vs-graph scoring, patrol-region expansion, follow-mode local targeting,
  immediate projectile reach, sibling occupancy penalty, graph fallback, and the
  existing `combat-target-search` performance metric.
- BotManager grind, local-opportunity, priority-ranged, and AoE reposition paths
  now call `AgentBotCombatPlanRuntime`, `AgentBotCombatAttackRuntime`,
  `AgentBotCombatTargetRuntime`, and `AgentBotCombatAoeRepositionRuntime`
  directly for plan/target/reposition/attack execution. `BotCombatManager`
  remains a temporary compatibility facade for older callers and tests, while
  the main tick path preserves the same config object, target ordering, attack
  gating, cooldown checks, and packet side effects.
- Remaining production combat config reads in ammo, inventory, movement,
  potion, shop, and BotManager callers now read `AgentCombatConfig.cfg`
  directly instead of the `BotCombatManager.cfg` alias. The alias remains only
  for compatibility-focused tests and legacy facade callers, preserving the
  same mutable config object and live tuning behavior.
- BotManager common combat lifecycle ticks now call Agent combat runtimes
  directly for mob-touch damage, dead-state entry, skill-cache rebuild,
  support-heal, and support-buff orchestration. The subsystem order, performance
  metric labels, cooldown/death gates, movement tick-down callback, and shared
  config object are unchanged.
- BotPhysicsEngine landing fall-damage dispatch now calls
  `AgentBotCombatDamageRuntime.applyFallDamage` directly. The same
  peak-to-landing fall distance, threshold behavior, packet side effects, and
  shared combat config are preserved.
- Skill-cache focused combat tests now call
  `AgentBotCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded` directly instead
  of the temporary `BotCombatManager` compatibility delegate. The covered cache
  signature, attack/AOE/heal/support bucket, and rebuild behavior are unchanged.
- Combat planning, target-search, and AoE-reposition focused tests now call
  `AgentBotCombatPlanRuntime`, `AgentBotCombatTargetRuntime`, and
  `AgentBotCombatAoeRepositionRuntime` directly with `AgentAttackPlan` and
  `AgentCombatConfig.cfg`. The temporary `BotCombatManager.AttackPlan`,
  `planAttack`, `attackMonster`, target-search, reachable-target, and
  AoE-reposition compatibility delegates have been removed because production
  and focused test callers no longer need them.
- The remaining `BotCombatManager` compatibility facade has been removed after
  mob-hit tests moved to `AgentBotCombatDamageRuntime` and stale source/test
  comments stopped referring to the deleted class. Combat behavior remains
  owned by Agent combat runtimes; the legacy `BotCombatManagerTest` class name
  is retained only as the historical focused combat parity suite.
- The remaining `BotChatManager` compatibility facade has been removed.
  `BotManager` now calls `AgentChatRuntime` directly with
  `AgentBotChatOrchestratorContext`, preserving the same handled-state
  fall-through, targeted-command, group-supply, broadcast, and whisper behavior.
  The historical focused chat/dialogue parity suite has been renamed to
  `AgentChatRuntimeParityTest`.
- Command typo suggestion now lives in
  `server.agents.commands.AgentCommandTypoSuggester`; `BotManager` uses the
  Agent-owned suggester directly, and the old `server.bots.llm`
  implementation/test have been removed. Known verbs, denylist, bounded
  Levenshtein behavior, and suggestion thresholds are unchanged.
- LLM configuration now lives in
  `server.agents.capabilities.dialogue.llm.AgentLlmConfig`. Legacy LLM runtime,
  memory, Ollama client, command bridge, and chat routing code read and mutate
  the same static config fields through the Agent-owned class; defaults and
  runtime behavior are unchanged.
- LLM reply orchestration, memory storage, prompt/situation building, sender
  relation classification, and the Ollama HTTP client now live under
  `server.agents.capabilities.dialogue.llm`. The old `server.bots.llm` source
  and test package is empty; bot chat routing calls
  `AgentLlmReplyService.maybeRespond` directly. Prompt text, memory JSONL
  serialization, compaction behavior, in-flight gating, reply splitting,
  sanitization, and follow-up scheduling are unchanged.
- Fidget behavior now lives in
  `server.agents.capabilities.movement.fidget.AgentFidgetService`. The old
  `server.bots.BotFidgetManager` source file has been removed; active fidget
  ticks, idle/social/greeting rolls, speed-mismatch fidget gates, jump/prone/
  sideways execution, origin-return cleanup, and prone attack visuals are
  unchanged while BotEntry, BotManager, BotMovementManager, and
  BotPhysicsEngine remain temporary backing seams.
- Greeting/social fidget side-effect dispatch now enters through
  `AgentBotFidgetSideEffects`; the old bot-side fidget side-effect shim has
  been removed, while `AgentFidgetService` owns the legacy fidget behavior in
  the Agent movement capability.
- Relog and owner-bot session lifecycle bridge calls now enter through
  `AgentBotSessionLifecycleSideEffects`; the old bot-side session lifecycle
  shim has been removed while `BotManager` still performs the unchanged relog
  and owner-entry lookup side effects.
- Movement target snapshot capture now enters through
  `AgentBotMovementTargetSideEffects`; the old bot-side movement-target shim has
  been removed while `BotManager` still owns temporary target-snapshot
  construction and steering-source resolution.
- Script runtime mutable state now lives in
  `server.agents.plans.AgentScriptRuntimeState`; `BotEntry` remains the
  temporary container, but the script id, step, wait, and script-local integer
  state bag is no longer a bot-owned type.
- Generic script primitives now live in `server.agents.plans` as
  `AgentScript`, `AgentScriptContext`, `AgentScriptStep`, and
  `AgentScriptRunner`. KPQ still supplies legacy script content from the bot PQ
  package, and script tasks still queue through the same BotEntry/BotManager
  execution path.
- Script task primitives now live in `server.agents.plans.AgentTask`; BotEntry
  and BotManager still temporarily store and execute the task queue, but task
  type, target, movement, and drop-item value data are no longer bot-owned.
- Movement cooldown/delay countdown math now lives in
  `AgentMovementTimingPolicy`; BotMovementManager preserves the same
  physics-tick input and remains the temporary compatibility delegate for
  combat, inventory, potion, shop, and trade timers.
- Climb idle-hold, precise climb-target snap, and rope identity decisions now live in
  `AgentClimbMovementPolicy`; BotMovementManager preserves the same
  navigation-edge, grind-mode, rope-bound, precise-target, and climb-step
  inputs through compatibility delegates.
- Ground horizontal step hysteresis now lives in `AgentGroundMovementPolicy`;
  BotMovementManager preserves the same stop-distance, follow-distance,
  was-moving, direction, and walk-step inputs while keeping runtime movement
  state updates in the legacy compatibility layer.
- Potion-share low-donor deflection templates now live in
  `AgentDialogueCatalog`; AgentPotionService preserves the same delayed map-chat
  callback and random selection timing while delegating the wording and owner
  name formatting to Agent dialogue.
- Fixed shop visit, sell-trash, purchase-summary, and shortfall result messages
  now live in `AgentDialogueCatalog`; AgentShopService preserves the same shop
  state, item-name resolution, comma-count formatting, delayed step handling,
  and map/reply delivery behavior through compatibility delegates.
- Shop approach Manhattan distance and foothold candidate generation now live in
  `AgentShopApproachPolicy`; AgentShopService preserves the same NPC approach
  radius, graph reachability filtering, random candidate choice, and shop
  sequence timing through compatibility delegates.
- Shop fixed-ammo and rechargeable-ammo resupply policy now lives in
  `AgentShopAmmoPolicy`; AgentShopService preserves the same low-ammo
  thresholds, projectile attack ranking, slot-max lookup, shop item selection,
  and purchase/recharge side effects through compatibility delegates.
- Shop potion selection policy now lives in `AgentShopPotionPolicy`;
  AgentShopService preserves the same HP/MP recovery band, percent-potion skip,
  cheapest in-band preference, too-low/too-high fallback ordering, and purchase
  side effects through compatibility delegates.
- Fixed inventory trade preflight, recipient-missing, item-missing, cancel,
  decline, timeout, meso-validation, equipped-item preparation, and named-item
  missing replies now live in `AgentDialogueCatalog`; BotInventoryManager
  preserves the same trade sequencing, retry timing, item preparation, and
  reply delivery behavior through compatibility delegates.
- Inventory trade item-quantity summing now lives in `AgentInventoryTradePolicy`;
  BotInventoryManager preserves the same equip-as-one behavior, stack quantity
  summing, and negative quantity clamp through a compatibility delegate.
- The remaining fixed drop-limited-map and low-potion return notices now live in
  `AgentDialogueCatalog`; BotInventoryManager and AgentPotionService preserve the
  same drop gating, grind-stop/follow-owner behavior, emote change, and map/reply
  delivery behavior through compatibility delegates.
- Low-ammo request, donor selection, ammo-share scheduling, and owner-offer
  routing now live in `AgentAmmoService`; AgentPotionService and supply request
  callbacks preserve the same thresholds, cooldown/backoff timing, donor
  ordering, delayed transfer timing, and map-chat behavior through compatibility
  delegates.
- USE-item trade grouping now lives in `AgentInventoryTradePolicy`;
  BotInventoryManager preserves the same safe-item filtering, recovery/ammo,
  scroll/buff, uncategorized bucket split, and recipient-duplicate ordering
  through a compatibility delegate.
- Item query normalization call sites now use `AgentItemQueryNormalizer`
  directly; the old `BotInventoryManager.normalizeItemQuery` compatibility shim
  has been removed while preserving the same plural/punctuation/case handling.
- Inventory item-presence checks, meso-category checks, reserved-equip category
  formatting, and no-items reply selection now call Agent inventory/dialogue
  policies directly; the matching `BotInventoryManager` compatibility shims have
  been removed without changing trade, restore-slot, or loot-offer behavior.
- Sell-trash equipment protection checks now call
  `AgentInventorySellTrashPolicy` directly; the old `BotInventoryManager`
  protection helper shims have been removed while preserving the same scrolled,
  high-stat, weapon-attack, and class-gated protection rules.
- Sell-trash equipment collection for shop and Maker automation now lives in
  `AgentInventorySellTrashService`; Agent shop/build callers no longer depend on
  `BotInventoryManager.collectSellTrashEquips`, while the remaining bot
  compatibility method delegates to the Agent service.
- USE-item effect lookup plus recovery-potion and buff-consumable classification
  now call `AgentUseItemClassificationPolicy` directly from Agent combat,
  supplies, ammo-check, and remaining inventory compatibility paths; the old
  `BotInventoryManager` item-effect/classification shims have been removed while
  preserving the same null-on-lookup-failure behavior.
- USE-trade ammo-item classification now calls `AgentInventoryAmmoPolicy`
  directly from the remaining inventory compatibility path; the old private
  `BotInventoryManager` pass-through has been removed.
- Potion share item collection now has an Agent-owned entry point on
  `AgentPotionService`; the remaining `BotInventoryManager.collectPotShareItems`
  compatibility method delegates to it, while pot-share trade execution remains a
  later inventory/trade migration slice.
- Ammo share item collection now has an Agent-owned entry point on
  `AgentAmmoService`; the remaining `BotInventoryManager.collectAmmoShareItems`
  compatibility method delegates to it, while ammo-share trade execution remains
  a later inventory/trade migration slice.
- Trade-window share quantity capping and restore-slot transfer now call
  `AgentBotPendingTradeStateRuntime` directly; the old `BotInventoryManager`
  pass-through helpers have been removed while preserving split-stack and
  temporarily-unequipped restore behavior.
- Potion/ammo supply-share trade startup and busy-retry scheduling now live in
  `AgentSupplyShareTradeService`; the remaining `BotInventoryManager`
  compatibility methods delegate to it while generic trade transfer and trade
  ticking remain later migration slices.
- Trade command profiling category selection and slow-command logging decision
  now live in `AgentTradeCommandProfiler`; `BotInventoryManager` retains
  compatibility methods that delegate to the Agent profiler while generic
  transfer counting and start remain later slices.
- Agent transfer command, pending item-choice, and accepted-offer callers now
  enter through `AgentInventoryTransferService`; the service is a temporary
  Agent-owned boundary over the remaining `BotInventoryManager` transfer state
  machine until transfer sequencing is migrated into Agent trade/inventory
  modules.
- Transfer availability and item-count decisions now run inside
  `AgentInventoryTransferService` through Agent inventory collection policies;
  only the actual trade-sequence start remains delegated to the temporary
  `BotInventoryManager` state machine.
- Manual trade timeout cancellation, state clearing, and one-time greeting
  bookkeeping now live in `AgentManualTradeService`; `BotInventoryManager`
  still owns the enclosing manual-trade tick and peer/owner trade branching.
- Manual trade accept-delay countdown and invite-join side effect now also live
  in `AgentManualTradeService`; `BotInventoryManager` still decides whether the
  current manual trade is an owner or peer-bot trade.
- Equip/ammo grouped trade transfer routing now lives in
  `AgentGroupedTradeTransferService`; `BotInventoryManager` still supplies the
  temporary item-classification and trade-sequence callbacks.
- Reserved-equips transfer routing now lives in
  `AgentReservedEquipTradeTransferService`; `BotInventoryManager` still supplies
  the temporary reserved-page collection and trade-sequence callbacks.
- Generic prepared-items transfer routing now lives in
  `AgentPreparedTradeTransferService`; `BotInventoryManager` still supplies the
  temporary item-preparation and trade-sequence callbacks.
- Direct item trade reply/retry/start routing now lives in
  `AgentDirectItemTradeService`; `BotInventoryManager` still supplies temporary
  retry scheduling and trade-sequence callbacks.
- Meso trade reply/start routing now lives in `AgentMesoTradeService`;
  `BotInventoryManager` still supplies the temporary trade-sequence callback.
- Top-level category transfer routing now lives in `AgentTradeTransferRouter`;
  `AgentInventoryTransferService` owns the category/direct transfer start
  entry points, and `BotInventoryManager.startTradeTransfer` is now a
  compatibility pass-through for legacy callers.
- Dead BotInventoryManager transfer-start helper bodies for meso, prepared
  item, reserved-equipment page, and grouped equip/ammo starts were removed
  after the Agent transfer service became the owner of those paths.
- Queued bot-initiated trade retry ticking now lives in
  `AgentTradeQueuedRetryService`; `BotInventoryManager.tickTrade` delegates the
  idle retry-delay/run branch unchanged.
- Between-batch trade progression now lives in
  `AgentTradeBetweenBatchService`; `BotInventoryManager.tickTrade` still
  supplies temporary collection, category-advance, reset, and open-batch
  callbacks while preserving the same batching behavior.
- The bypassed BotInventoryManager closed-window fallback body was removed;
  the `trade == null` branch now delegates directly to
  `AgentTradeClosedWindowService`, which already owns all completion,
  cancellation, decline, reset, and refill decisions for that branch.
- Trade item-add tick ordering now lives in `AgentTradeItemAddTickService`;
  `BotInventoryManager.tickTrade` supplies the same delay/reply/cancel
  callbacks while the Agent service orders timer, meso, all-done, category
  announcement, and add-next-item behavior.
- The bypassed BotInventoryManager owner-confirm fallback body was removed;
  the confirmation branch now delegates directly to
  `AgentTradeConfirmWaitService`, which already owns completion, timeout,
  cancellation, and bot-recipient confirmation behavior.
- Top-level trade tick ordering now lives in `AgentTradeTickService`;
  `BotInventoryManager.tickTrade` remains as a compatibility wiring shell for
  current Cosmic trade lookup, open-batch, reset, delay, recipient, and reply
  callbacks.
- Trade sequence/open-batch orchestration now lives in
  `AgentTradeSequenceOrchestrator`; `BotInventoryManager` supplies only the
  temporary Cosmic callbacks for recipient lookup, unavailable-trade cancel,
  trade creation, invite packets, invitation reply selection, and reply emit.
- Dead `BotInventoryManager.resetTradeStateLegacy` was removed after all live
  reset paths used `AgentTradeResetService`.
- Manual peer-bot trade routing now lives in `AgentManualPeerTradeService`;
  `BotInventoryManager.tickManualTrade` supplies temporary callbacks for
  same-owner authorization, delayed invite accept, completion, refill, and
  greeting clearing while preserving the old branch behavior.
- Manual owner trade routing now lives in `AgentManualOwnerTradeService`;
  `BotInventoryManager.tickManualTrade` supplies temporary callbacks for
  delayed owner-invite accept, one-time greeting emission, completion, and
  post-trade auto-equip refill while preserving the old branch behavior.
- Manual trade tick orchestration now lives in `AgentManualTradeTickService`;
  `BotInventoryManager.tickManualTrade` supplies temporary callbacks for active
  transfer suppression, trade-window lookup, timeout handling, owner/peer
  branch routing, and owner-side trade handling.
- Transfer availability/count routing now lives in
  `AgentTradeTransferAvailabilityService`; `BotInventoryManager` supplies
  temporary callbacks for equipped-slot counts, named-item counts, and current
  category item collection.
- Equip trade classification orchestration now lives in
  `AgentEquipTradeClassificationService`; `BotInventoryManager` and
  `AgentInventoryTransferService` supply temporary callbacks for bag scans,
  self-reserve collection, other-recipient reservation checks, owner lookup,
  and slow-classification logging.
- Dead `BotInventoryManager` helper bodies for trash-equip collection and
  own-class equip checks were removed after their callers moved into Agent
  inventory/trade/equipment services. The private `startTradeSequence`
  compatibility wrapper remains because focused regression tests still exercise
  the legacy invite-once behavior through it.
- Passive loot tick orchestration now lives in `AgentPassiveLootService`;
  `BotInventoryManager.tickPassiveLoot` supplies temporary callbacks for loot
  inhibit, active trade suppression, inventory-full warning cooldowns, owner
  lookup, pickup side effects, auto-equip, loot-offer prompts, and ghost-drop
  cleanup.
- Trade sequence callback construction now lives in
  `AgentTradeSequenceCallbackService`; `BotInventoryManager` supplies temporary
  operations for recipient resolution, unavailable-trade cancellation, trade
  start/invite, invitation reply selection, and immediate reply delivery.
- Trade tick callback construction now lives in `AgentTradeTickCallbackService`;
  `BotInventoryManager.tickTrade` supplies temporary operations for countdown,
  current trade lookup, between-batch progression, closed-window handling,
  invite wait, item add, and confirmation wait handling.
- Stale `BotInventoryManager` imports for migrated inventory dialogue,
  inventory trade policy, USE-item classification, and manual-trade state
  helpers were removed after those responsibilities moved to Agent modules.
- Ammo trade classification orchestration now lives in
  `AgentAmmoTradeClassificationService`; `BotInventoryManager` and
  `AgentInventoryTransferService` supply temporary runtime hooks for equipped
  weapon type, projectile WATK lookup, quest-item checks, and tradeable config.
- Trade item collection wiring now lives in `AgentTradeItemCollectionService`;
  `BotInventoryManager` and `AgentInventoryTransferService` supply temporary
  callbacks for recommended equipment, equip groups, and ammo groups.
- The stale `BotInventoryManager` direct import of
  `AgentInventoryTradeCollectionService` was removed after item collection
  wiring moved behind `AgentTradeItemCollectionService`.
- Trade lifecycle helper wiring now lives in `AgentTradeLifecycleService`;
  `BotInventoryManager` supplies temporary callbacks for manual-state clearing,
  temporarily unequipped item restoration, owner lookup, equipment refill, reply
  delay/randomization, and trade reaction replies.
- Stale `BotInventoryManager` imports for direct trade cancellation,
  completion, and reset services were removed after lifecycle wiring moved
  behind `AgentTradeLifecycleService`.
- Item-choice trade/drop branching now lives in `AgentInventoryTransferService`;
  the legacy BotInventoryManager entry point delegates to it, while the Agent
  service preserves the same trade path, inventory-drop capability call, and
  post-drop loot-inhibit delay.
- Named-item trade/drop collection, count aggregation, query normalization, and
  item-name normalization cache now live in `AgentInventoryNamedItemService`;
  BotInventoryManager preserves the same named-item trade and drop behavior by
  calling the Agent inventory service while trade sequencing remains in the
  temporary compatibility layer.
- Inventory floor-drop command execution now lives in `AgentInventoryDropService`;
  BotInventoryManager preserves the same drop-limited-map guard, category
  routing, floor-drop side effect, named-item miss reply, and drop-count reply
  wording through a temporary dispatcher.
- Dead BotInventoryManager pass-through wrappers for inventory slot/summary
  reports, sell-trash collection, and pot/ammo share collection/startup have
  been removed; production callers already use the Agent reporter, inventory,
  supplies, and trade services directly.
- BotInventoryManager trade profiling pass-through wrappers have been removed;
  the remaining trade state machine now calls `AgentTradeCommandProfiler`
  directly for category profiling and slow-command logging.
- Grind and patrol loot target selection now lives in
  `AgentLootTargetService`; BotManager calls the Agent looting capability
  directly and supplies the existing grind-loot retry suppression rule so the
  target-selection behavior remains unchanged.
- Stale loot cleanup now lives in `AgentLootCleanupService`;
  BotInventoryManager preserves the same passive-loot pickup flow but delegates
  ghost-drop visibility cleanup and remove-item packet delivery to the Agent
  looting capability.
- Generic safe bag collection now lives in `AgentInventoryCollectionService`;
  BotInventoryManager preserves the same slot-order collection, quest-item
  exclusion, untradeable policy flag, and caller-supplied item filters for
  scroll, potion, buff, ETC, and equip trade collection.
- Equip trade group model, aggregate ordering, reserved-page slicing, reserved
  page message selection, reserved bucket message selection, and next/first
  group navigation now live in `AgentEquipTradeGroupService`; BotInventoryManager
  still owns the temporary classification loop and trade sequence side effects.
- Equip trade group classification now lives in `AgentEquipTradeGroupService`;
  BotInventoryManager still supplies the temporary self-upgrade and
  other-recipient reservation callbacks plus slow-classification log delivery,
  but the normal/reserved-for-other/reserved-for-self bucketing, bucket sorting,
  and profiling counters are Agent-owned.
- Trade item category collection and `name:` preparation routing now live in
  `AgentInventoryTradeCollectionService`; BotInventoryManager still supplies
  temporary callbacks for recommended gear, equipped-slot preparation,
  equip-group classification, and ammo-group classification while the enclosing
  transfer state machine remains in the bot compatibility layer.
- Transfer availability and transfer count decisions now live in
  `AgentInventoryTradeCollectionService`; BotInventoryManager still supplies
  temporary callbacks for equipped-slot counts and collected item quantity while
  the Agent transfer boundary continues to delegate the public compatibility
  methods.
- Direct item trade preflight now lives in `AgentDirectItemTradeService`;
  BotInventoryManager still owns the loot-offer trade sequence side effect and
  retry scheduling, but recipient-missing, item-missing, busy-retry, and
  start-trade decisions are Agent-owned.
- Trade sequence initialization, first-window batch setup, batch-progress clear,
  between-batch transition, and full pending-trade state clear now live in
  `AgentTradeStateService`; BotInventoryManager and AgentSupplyShareTradeService
  preserve the same trade side effects while sharing the Agent-owned state
  mutation helpers.
- Trade sequence recipient guard and initial sequence opening now live in
  `AgentTradeSequenceService`; BotInventoryManager still supplies the temporary
  first-batch callback while the Agent service preserves the same missing
  recipient reply and pending-trade initialization.
- Trade reset restore/manual-clear/sequence-clear/refill ordering now lives in
  `AgentTradeResetService`; BotInventoryManager still supplies temporary
  callbacks for equipped-slot restoration, manual-trade state clear, and
  auto-equip refill. The old bot reset body is retained only as an unused
  legacy helper until the surrounding trade tick state machine is migrated.
- Trade-window meso add handling now lives in `AgentTradeMesoAddService`;
  BotInventoryManager still owns the enclosing trade tick but delegates the
  pending-meso check, insufficient-meso cancel decision, `Trade.setMeso`,
  meso-added flag, and add-delay timer mutation to Agent trade code.
- Trade-window item add handling now lives in `AgentTradeItemAddService`;
  BotInventoryManager still owns the enclosing trade tick but delegates item
  index advancement, per-item delay, share quantity cap, inventory locking,
  trade-window item copy/position/quantity setup, restore-slot transfer,
  inventory removal, and trade item add packet sends to Agent trade code.
- Trade all-items-added handling now lives in `AgentTradeAllItemsAddedService`;
  BotInventoryManager still owns the enclosing trade tick but delegates the
  item-index completion check, all-items-added flag, timer clear, and all-done
  trade chat line to Agent trade code.
- Trade category announcement handling now lives in
  `AgentTradeCategoryAnnouncementService`; BotInventoryManager still owns the
  enclosing trade tick but delegates the first-item category-message check,
  message consumption, trade chat line, and announcement delay timer to Agent
  trade code.
- Trade invite-wait timeout handling now lives in
  `AgentTradeInviteWaitService`; BotInventoryManager still owns the enclosing
  trade tick but delegates the accept-wait timer, request-timeout reply,
  `NO_RESPONSE` trade cancellation, and reset callback to Agent trade code.
- Trade owner-confirm wait handling now lives in
  `AgentTradeConfirmWaitService`; BotInventoryManager still owns the enclosing
  trade tick but delegates the confirm-wait timer, bot-recipient/partner-confirm
  completion decision, bot-done/timer clear, confirm-timeout reply,
  `NO_RESPONSE` cancellation, and reset callback to Agent trade code. The old
  in-method body remains bypassed as compatibility text until the full trade
  tick state machine is migrated.
- Trade closed-window handling now lives in `AgentTradeClosedWindowService`;
  BotInventoryManager still owns the enclosing trade tick but delegates
  single-batch completion reset/refill, multi-batch between-batch transition,
  owner-cancelled reply/reset/refill, and declined-invite reply/reset to Agent
  trade code. The old in-method body remains bypassed as compatibility text
  until the full trade tick state machine is migrated.
- Trade transfer start guard replies now live in
  `AgentTradeTransferStartGuard`; BotInventoryManager still owns category
  routing and collection, but delegates owner-missing, bot-busy, and
  owner-busy reply selection to Agent trade code.
- Trade batch-open orchestration now lives in `AgentTradeBatchService`;
  BotInventoryManager and AgentSupplyShareTradeService still supply the
  temporary recipient lookup/cancel/start/invite/reply callbacks, but recipient
  availability checks, batch-state initialization, invite announcement guards,
  and invite reply delivery are Agent-owned.
- Trade cancellation reply/cancel/reset orchestration now lives in
  `AgentTradeCancellationService`; BotInventoryManager still supplies the
  temporary reset callback while the Agent service preserves the same reply,
  `NO_RESPONSE` cancel, and reset ordering.
- Trade completion owner-given equip snapshot, `Trade.completeTrade`, thank
  reaction, and empty-trade freebie reaction now live in
  `AgentTradeCompletionService`; BotInventoryManager still supplies the same
  random delay/reply/roll providers while trade tick orchestration remains a
  later migration slice.
- Equipped-slot named trade preparation now lives in
  `AgentEquippedSlotTradeService`; BotInventoryManager still owns the enclosing
  trade sequence but delegates slot counting, temporary unequip preparation,
  restore-slot recording, restore-to-equipped-slot execution, and legacy failure
  replies to the Agent inventory service.
- Trade recipient resolution now lives in `AgentTradeRecipientService`;
  BotInventoryManager still owns trade sequencing but delegates owner/map/party
  recipient lookup to the Agent trade capability.
- Meso trade start decisions now live in `AgentMesoTradeService`;
  BotInventoryManager still owns the enclosing trade sequence but delegates
  owner/busy/no-meso/invalid/insufficient-meso guards and selected meso amount
  to the Agent trade capability.
- Ammo trade grouping now lives in `AgentInventoryAmmoPolicy`;
  BotInventoryManager preserves the same safe-item filtering, own-ammo versus
  non-own-ammo split, non-own item-id ordering, and own-ammo projectile attack
  ordering through a compatibility delegate.
- Ammo trade category selection now lives in `AgentInventoryAmmoPolicy`;
  BotInventoryManager preserves the same first non-empty group selection and
  next-group advancement while keeping trade sequence side effects in the
  temporary compatibility layer.
- Reserved-equipment trade page message formatting now lives in
  `AgentInventoryTradePolicy`; BotInventoryManager preserves the same reserved
  equip count source and category-message delivery path.
- Equipment trade category selection now lives in `AgentInventoryTradePolicy`;
  BotInventoryManager preserves the same first non-empty group selection,
  next-group advancement, category-message selection, and trade-sequence side
  effects through compatibility delegates.
- Inventory trade prioritization compatibility wrappers have been removed from
  BotInventoryManager; production trade collection and historical parity tests
  now call `AgentInventoryTradePolicy` directly for ETC, scroll, and USE-bucket
  recipient-duplicate ordering.
- Inventory trade count, meso parsing/reply, and equip item-id sort
  compatibility wrappers have been removed from BotInventoryManager; production
  callers now use `AgentInventoryTradePolicy` directly for those policy
  decisions.
- Reserved-equipment category, page-clamp, and dead item-sort compatibility
  wrappers have been removed from BotInventoryManager; production and historical
  parity tests now use `AgentInventoryTradePolicy` directly for those helpers.
- Reserved-equipment trade page slicing now lives in `AgentInventoryTradePolicy`;
  BotInventoryManager still supplies the temporary reserved-item source but no
  longer owns page clamping or sublist selection.
- Equip group and reserved-equip score sort pass-through wrappers have been
  removed from BotInventoryManager; temporary equip classification still runs
  there, but the reserved self-sort call now goes directly to
  `AgentInventoryTradePolicy`.
- Maker crystal creation and trash-disassembly batch orchestration now live in
  `AgentMakerService`; utility chat callbacks preserve the same guard replies,
  lazy item-data lookup, five-second step cadence, ACTIVE set, and
  MakerProcessor player-path calls through Agent runtime adapters.
- KPQ party-quest automation now lives in `AgentPartyQuestHooks` and
  `server.agents.capabilities.partyquest.kpq`; BotManager and loot eligibility
  preserve the same tick order, stage-1 grind/follow defaults, NPC lock,
  coupon-loot suppression, pass exchange, and stage-5 reward claim behavior
  through Agent capability calls.
- Follow-anchor resolution now lives in
  `server.agents.runtime.AgentFollowAnchorService`; BotManager still supplies
  the temporary same-leader sibling entry list while preserving the owner,
  party-member, sibling-agent, and fallback resolution order exactly.
- Tick leader/session refresh now lives in
  `server.agents.runtime.AgentLeaderSessionService`; BotManager still supplies
  the temporary Cosmic player-storage lookup callback while preserving the
  cached-leader, mismatched-leader, offline-leader, and missing-leader behavior
  exactly.
- Formation state lookup now lives in
  `server.agents.runtime.AgentFormationService`; BotManager still owns the
  temporary per-leader formation map but no longer owns the default-vs-custom
  resolution rule.
- Target snapshot composition now lives in
  `server.agents.runtime.AgentTargetSnapshotService`; BotManager still supplies
  the temporary sibling entry list, per-leader formation map, and follow-target
  resolver callback, but no longer composes follow-anchor plus formation before
  snapshot capture.
- Mode transition state ownership for follow, grind, stop, move-to, farm-here,
  patrol, active-mode reset, and clear-mode now lives in
  `server.agents.runtime.AgentModeService`; BotManager still owns command/script
  entry-point side effects such as clearing script tasks, cancelling shop visits,
  and clearing movement navigation state.
- Tick heartbeat due-check, timestamp update, and heartbeat side-effect ordering
  now live in `server.agents.runtime.AgentHeartbeatService`; BotManager still
  supplies temporary packet freshness and movement broadcast callbacks.
- Idle and trade-window physics mode selection now lives in
  `server.agents.runtime.AgentIdlePhysicsService`; BotManager still supplies the
  temporary swim-map, movement, physics, and broadcast callbacks.
- Map-change grounding, map tracking, post-teleport reset, navigation graph
  warmup, and movement broadcast ordering now live in
  `server.agents.runtime.AgentMapTransitionService`; BotManager still supplies
  temporary foothold, physics, navigation, and broadcast callbacks.
- Attack-lock physics dispatch now lives in
  `server.agents.runtime.AgentActionLockPhysicsService`; BotManager still
  supplies temporary swim-map and movement physics callbacks, preserving the
  legacy climbing-airborne branch behavior.
- Dead-state tick handling now lives in
  `server.agents.runtime.AgentDeathTickService`; BotManager still supplies the
  temporary combat death-state entry and respawn side-effect callbacks.
- Scripted follow-target character resolution now lives in
  `server.agents.runtime.AgentFollowAnchorService`; BotManager still supplies
  the temporary same-leader sibling entry list for script task compatibility.
- Script task completion rules now live in
  `server.agents.runtime.AgentScriptTaskCompletionService`; BotManager still
  supplies temporary follow-target resolution and movement-distance configuration.
- Ownerless/offline-leader tick branching now lives in
  `server.agents.runtime.AgentOwnerlessTickService`; BotManager still supplies
  temporary grounding, standalone move-target, and idle callbacks. This preserves
  the same following-clear, map-change grounding early return, explicit
  move-target pass-through, and idle fallback ordering.
- Tick-time leader-motion observation and farm-anchor map-change cleanup now
  live in `server.agents.runtime.AgentTickStateMaintenanceService`; BotManager
  keeps the same call sites but no longer owns the delta calculation or precise
  move-target cleanup rule when a farm anchor is cleared after a map change.
- Tracked map-change tick handling now lives in
  `server.agents.runtime.AgentMapTransitionService`; BotManager still supplies
  temporary Cosmic grounding, graph-warm, mode-command, shop, and status
  callbacks. The same grounding order, KPQ grind-vs-follow-vs-reset decision,
  shop map-change notification, manager-status check, and performance timer
  behavior are preserved.
- Movement recovery teleport checks now live in
  `server.agents.runtime.AgentRecoveryTeleportService`; BotManager still
  supplies temporary Cosmic ground-point, teleport, reset, and broadcast hooks.
  The regular target-distance recovery, out-of-bounds recovery, grind-party
  anchor recovery, shop/move-target/farm-anchor guards, and distance multiplier
  behavior are preserved.
- Cross-map follow synchronization now lives in
  `server.agents.runtime.AgentFollowMapSyncService`; BotManager still supplies
  temporary Cosmic ground-point, idle-ground, change-map, and movement reset
  hooks. The same following/null/same-map guards and anchor-position grounding
  fallback are preserved.
- Parked follow-mode idle movement fast-path eligibility and recheck timing now
  live in `server.agents.runtime.AgentFollowIdleMovementService`; BotManager
  keeps a compatibility wrapper for existing tests and call sites. The same
  one-second recheck window, `idle-fast` debug marker, stuck-progress reset,
  and movement/owner-motion/shop/navigation guards are preserved.
- Reached move-target cleanup now lives in
  `server.agents.runtime.AgentTickStateMaintenanceService`; BotManager passes
  the existing stop-distance config and no longer owns the normal-vs-precise
  arrival cleanup rule.
- Patrol map-change cleanup now lives in
  `server.agents.runtime.AgentTickStateMaintenanceService`; BotManager no
  longer owns the null/map guard around the BotEntry-backed patrol clear rule.
- Movement phase dispatch now lives in
  `server.agents.runtime.AgentMovementPhaseService`; BotManager still supplies
  temporary BotMovementManager hooks. The climb, swim-while-airborne, airborne,
  then grounded ordering is preserved.
- Movement stuck detection and unstuck triggering now live in
  `server.agents.runtime.AgentStuckDetectionService`; BotManager still supplies
  temporary tick-down, movement tick duration, enable flag, and unstuck action
  hooks. The active-navigation guard, 8px movement threshold, 500ms stuck
  threshold, cooldown handling, and performance metric name are preserved.
- Precise move-target navigation marker maintenance now lives in
  `server.agents.runtime.AgentTickStateMaintenanceService`; BotManager no
  longer owns the precise-target flag rule after navigation target resolution.
- Movement-core tick orchestration now lives in
  `server.agents.runtime.AgentMovementTickService`; BotManager still supplies
  temporary navigation, fidget, movement phase, committed-edge, stuck-detection,
  and move-target cleanup hooks. The navigation-consumed early return, fidget
  early return, committed-edge grounded AI gate, stuck detection, and reached
  move-target cleanup ordering are preserved.
- Movement-only tick orchestration now lives in
  `server.agents.runtime.AgentMovementOnlyTickService`; BotManager still supplies
  temporary idle, follow-map sync, follow-anchor, recovery, map-change, shop,
  follow-idle, and movement-core hooks. The existing early-return order is
  preserved for movement simulations and movement-only tests.
- Anchored farm/sentry tick orchestration now lives in
  `server.agents.runtime.AgentAnchoredFarmTickService`; BotManager still
  supplies temporary local-opportunity attack, idle, ground-idle broadcast, and
  movement-core hooks. The farm-anchor map guard, attack-consumed early return,
  at-anchor idle/clear behavior, precise anchor target assignment, and movement
  dispatch order are preserved.
- Local-opportunity attack movement-window timing now lives in
  `server.agents.runtime.AgentLocalAttackMoveWindowService`; BotManager still
  passes the legacy movement distance config. The null clear behavior, 1000ms
  far window, 200ms medium window, immediate settle window, follow-mode guard,
  stop-band, and vertical-cap clearing rule are preserved.
- Grind target search/adoption policy now lives in
  `server.agents.capabilities.combat.AgentGrindTargetSearchPolicy`; BotManager
  still schedules the next search tick and performs the selected target lookup.
  The retarget interval guard, null-target adoption, in-range commitment,
  AoE-single-target scan exception, and larger-cluster hysteresis are preserved.
- Grind fallback target and opportunistic loot steering now live in
  `server.agents.runtime.AgentGrindTargetPositionService`; BotManager keeps
  compatibility delegates for existing tests and call sites. The no-graph
  wander fallback, region wander reuse, cached loot validation, passive pickup
  radius suppression, retry suppression window, loot travel-distance scoring,
  convenient-loot ratio, and patrol wander/loot priority are preserved.
- Ranged priority target selection now lives in
  `server.agents.capabilities.combat.AgentRangedPriorityTargetSelector`;
  BotManager keeps a compatibility delegate while grind-mode combat is still
  being reconstructed. The no-ammo/null guards, ranged-weapon gate,
  degenerate-target rejection, RANGED attack-plan requirement, range/cooldown
  gate, preferred-target fast path, and nearest valid fallback scan are
  preserved.
- AoE reposition commitment now lives in
  `server.agents.capabilities.combat.AgentAoeRepositionService`; BotManager
  keeps a compatibility delegate while `tickGrindMode` is still being
  reconstructed. Existing-anchor reuse, arrival/expiry/dead-target clearing,
  planner invocation, anchor storage, and max-duration deadline are preserved.
- Grind navigation/retreat target selection now lives in
  `server.agents.capabilities.combat.AgentGrindNavigationTargetSelector`;
  BotManager keeps compatibility delegates and supplies temporary
  BotNavigationManager hooks plus legacy movement constants. Surround-breakout
  latching, retreat-hold hysteresis, cross-region retreat scoring,
  projectile-reachable retreat selection, portal-path rejection, local retreat
  same-region guards, and region mob counting are preserved.
- Public movement mode commands for explicit move, farm-here, and concrete
  target-follow now delegate through
  `server.agents.integration.AgentBotMovementCommandRuntime`; BotManager keeps
  compatibility entry points and private script-task starters. The same script
  task clearing, shop-visit cancellation, null guards, mode-state transitions,
  and navigation-state clear hooks are preserved.
- Script-task follow-target lookup now uses
  `server.agents.runtime.AgentFollowAnchorService.resolveTargetFromRuntimeRegistry`;
  BotManager keeps only the temporary script hook wrapper. The same leader
  fallback, party-member priority, sibling-Agent fallback, and offline-target
  rejection behavior are preserved.
- Local opportunity attack decision flow now lives in
  `server.agents.capabilities.combat.AgentLocalOpportunityAttackService`;
  BotManager keeps a compatibility wrapper and supplies temporary hooks for
  grind-navigation target selection, jump height, jump initiation, and local
  attack move-window setup. The same no-ammo/null guards, follow-target search,
  retreat decision, jump-toward-target branch, in-air attack behavior,
  cooldown/move-window gate, degenerate ranged marker, and consumed-tick result
  semantics are preserved.
- Script-task start/completion hook wiring now lives in
  `server.agents.runtime.AgentScriptTaskExecutionService`; BotManager keeps
  temporary wrappers for tick integration only. The same move/follow/grind/stop
  command routing, drop-item action, runtime-registry follow-target resolution,
  and normal/precise arrival distance behavior are preserved.
- Dead BotManager private mode-start wrappers were removed after command and
  script execution moved to Agent-owned mode services. The inactive-leader
  town-cluster scroll hook now calls `AgentModeService.startMoveTo` directly to
  preserve its raw move-target behavior without clearing script/shop state.
- Grind-mode cached loot target validation and AI-tick loot-target refresh now
  live in `server.agents.capabilities.looting.AgentGrindLootTargetService`.
  BotManager delegates from `tickGrindMode`, preserving the picked-up/stale map
  object clear rule, patrol-region refresh suppression, retry-suppression
  callback, and legacy loot-radius input.
- Grind-mode target search orchestration now lives in
  `server.agents.capabilities.combat.AgentGrindTargetSearchService`.
  BotManager supplies temporary patrol/grind target finder hooks, while the
  service owns the AI-tick/search-policy gate, patrol-vs-grind finder choice,
  searched-target adoption, attack-plan invalidation, and next-search scheduling
  with the legacy retarget interval.
- Grind-mode no-target fallback handling now lives in
  `server.agents.runtime.AgentGrindNoTargetFallbackService`. BotManager supplies
  temporary swim, airborne, patrol/no-grind target resolution, and movement-step
  hooks. The same grind-target clear, swim/airborne early return, legacy
  wander-direction side effect, patrol-vs-free fallback target choice, and
  consumed movement-step result are preserved.
- Grind-mode target commitment and replacement now live in
  `server.agents.capabilities.combat.AgentGrindTargetCommitmentService`.
  BotManager supplies temporary ranged-priority and closer-threat hooks. The
  same grind-target assignment, wander/patrol target clearing, ranged-priority
  replacement, closer-threat crowding swap, attack-plan invalidation, and
  ranged-priority marker used by later spacing rules are preserved.
- Grind-mode ranged engagement now lives in
  `server.agents.capabilities.combat.AgentGrindRangedEngagementService`.
  BotManager supplies temporary hooks for weapon lookup, degenerate/retreat
  checks, cross-region retreat, AoE reposition, attack execution, jump, idle,
  and movement broadcast. The same degenerate ranged one-shot gate,
  cross-region retreat precompute, attack cooldown comparison, ranged degen
  latch, jump-toward-target branch, and stand-still-in-range behavior are
  preserved.
- Grind-mode navigation tail selection now lives in
  `server.agents.capabilities.combat.AgentGrindNavigationTailService`.
  BotManager supplies temporary navigation, retreat-policy, and convenient-loot
  hooks. The same cross-region retreat priority, AoE navigation selection,
  degenerate-retreat latch clearing, patrol guard, and convenient-loot detour
  override are preserved.
- Grind-mode tick pipeline orchestration now lives in
  `server.agents.capabilities.combat.AgentGrindModeTickService`. BotManager
  keeps a temporary compatibility adapter that assembles the existing search,
  no-target fallback, target commitment, ranged engagement, and navigation-tail
  hook bundles. The same ordering, seek-range, loot-radius, attack-plan reuse,
  consumed-tick propagation, and final movement target semantics are preserved.
- Standalone move-target tick sequencing now lives in
  `server.agents.runtime.AgentStandaloneMoveTargetTickService`. BotManager
  keeps a temporary compatibility adapter that supplies map-change grounding,
  movement-profile refresh, and movement-core hooks. The same post-warp early
  return, profile refresh ordering, stored move-target lookup, and run-AI flag
  propagation are preserved.
- Inactive leader tick gating now lives in
  `server.agents.runtime.AgentLeaderSafetyService.handleInactiveLeaderTick`.
  BotManager keeps a temporary compatibility adapter for active-leader return
  cleanup, town-warp eligibility, and inactive safe-mode entry side effects.
  The same active/inactive classification, inactive timer delay, town policy
  lookup, and consumed-tick result are preserved.
- Tick preflight sequencing now lives in
  `server.agents.runtime.AgentTickPreflightService`. BotManager keeps a
  temporary compatibility adapter for airshow skip, skip-delay consumption,
  removed-map cleanup, heartbeat refresh/broadcast, pending-offer expiry, and
  AI cadence preparation. The same early-return order and tick timestamp input
  are preserved.
- Trade-window tick dispatch now lives in
  `server.agents.runtime.AgentTradeWindowTickService`. BotManager keeps a
  temporary compatibility adapter for the physics-only tick body and performance
  timing. The same `getTrade() != null` consumed-tick behavior is preserved.
- Shop-visit tick dispatch now lives in
  `server.agents.runtime.AgentShopVisitTickService`. BotManager keeps a
  temporary compatibility adapter for the existing shop visit tick body,
  performance timing, and movement-core stepping. The same pending check,
  active target lookup, approach-delay early return, movement-step dispatch, and
  consumed-tick behavior are preserved.
- Idle-mode consumed-tick dispatch now lives in
  `server.agents.runtime.AgentIdleModeTickService`. BotManager keeps a
  temporary compatibility adapter for the existing idle physics/mode body and
  performance timing. The same consumed/fall-through result is preserved.
- Follow-mode local opportunity attack dispatch now lives in
  `server.agents.runtime.AgentFollowOpportunityTickService`. BotManager keeps a
  temporary compatibility adapter for the existing local opportunity attack body
  and performance timing. The same following, AI-cadence, climbing, same-map,
  anchor-distance, target-position update, and consumed-tick rules are
  preserved.
- Scripted move local-combat tick dispatch now lives in
  `server.agents.runtime.AgentScriptedMoveCombatTickService`. BotManager keeps
  a temporary compatibility adapter for action-window cleanup, local-opportunity
  attack execution, movement-core stepping, and performance timing. The same
  local-opportunity MOVE_TO predicate, consumed attack early return, movement
  target update, and movement-core tail are preserved.
- Anchored-farm mode dispatch now lives in
  `server.agents.runtime.AgentAnchoredFarmModeTickService`. BotManager keeps a
  temporary compatibility adapter for the existing anchored-farm tick body and
  performance timing. The same farm-anchor presence check and consumed-tick
  result are preserved.
- Follow map-sync and teleport recovery dispatch now lives in
  `server.agents.runtime.AgentRecoveryTickService`. BotManager keeps temporary
  compatibility hooks for follow-map synchronization, grind-party distance
  recovery, and target-distance recovery. The same shop-visit follow-sync guard,
  short-circuit order, and consumed/fall-through behavior are preserved.
- Tracked map-change tick dispatch now lives in
  `server.agents.runtime.AgentTrackedMapChangeTickService`. BotManager keeps a
  temporary compatibility adapter for the existing tracked-map-change body and
  performance timing. The same consumed/fall-through result and record-only-
  when-changed performance behavior are preserved.
- Final movement-tail dispatch now lives in
  `server.agents.runtime.AgentFinalMovementTailService`. BotManager keeps a
  temporary compatibility adapter for movement-core stepping and performance
  timing. The same default fall-through target and AI-tick flag are preserved.
- Grind-mode dispatch now lives in
  `server.agents.runtime.AgentGrindModeDispatchService`. BotManager keeps a
  temporary compatibility adapter for the existing grind tick hook and
  performance timing. The same grind-mode gate, consumed-tick result, and target
  update semantics are preserved.
- Movement-only map-change handling now lives in
  `server.agents.runtime.AgentMovementOnlyMapChangeService`. BotManager keeps
  temporary compatibility hooks for foothold indexing, ground lookup, teleport,
  physics reset, movement broadcast, shop map-change, and manager status checks.
  The same map-tracking guard, ground fallback, and side-effect sequence are
  preserved.

Initial reconstruction order:

1. Runtime shell and registry.
2. Command parser/router boundaries. Initial parser and GM command bridge completed; old bot runtime remains underneath.
3. Chat/reply/dialogue boundaries. Reply queue, dialogue catalogs, command and
   trade classification, utility/equipment/social/build routing, status/report
   formatting, reply delivery, pending-action/session/supply/transfer/social/
   control/equipment/utility/build/status/movement bridges, handled-state
   runtime, and top-level chat orchestration have moved to Agent-owned dialogue
   and integration modules. `BotChatRuntime`, `BotChatOrchestratorContext`,
   `BotChatReplyRuntime`, and `BotChatManager` have been removed; `BotManager`
   now enters `AgentChatRuntime` through `AgentBotChatOrchestratorContext`, and
   `AgentChatRuntimeParityTest` is the historical focused parity suite.
4. Movement and navigation.
5. Combat.
6. Loot and supplies.
7. Inventory, equipment, trade, shop, and build.
8. Quest, NPC, and PQ.
9. Dialogue, social, and LLM.
10. SPI/Cosmic gateway attachment.
11. Delete old bot-shaped runtime once all callers use Agent modules.
