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

- Integration runtime extraction has started. `server.agents.integration` should
  keep Cosmic/server boundary work: Character/MapleMap access, packets, timers,
  inventory mutation, shop/trade/NPC/database/server internals, and gateway
  adapters. Runtime orchestration moves to `server.agents.runtime`, and pure
  domain behavior moves to `server.agents.capabilities.*` one slice at a time.
- `AgentChatOrchestratorContext` moved from `server.agents.integration` to
  `server.agents.runtime`. It still invokes the same callback adapters, so chat
  command routing, pending actions, supply/social/control/build/transfer/report
  callbacks, and job advancement behavior are unchanged.
- `AgentStatusStateRuntime` now holds status/AFK/gear-suggestion state adapter
  builders in `server.agents.runtime`. `AgentStatusRuntime` stays in
  `server.agents.integration` for live side effects only: delayed scheduling,
  party/reply emission, map-name reads, and face-expression changes.
- `AgentPendingActionStateRuntime` moved to `server.agents.runtime` because it
  is pure live-session state access. Pending-action callbacks, transfer
  decisions, replies, and session side effects stay in integration until those
  behavior slices are split.
- Tick state adapters moved to `server.agents.runtime`:
  `AgentTickStateRuntime`, `AgentTickCadenceStateRuntime`, and
  `AgentTickFailureStateRuntime`. These remain behavior-preserving wrappers
  over `AgentRuntimeEntry` state; scheduler/tick behavior and failure handling
  are unchanged.
- `AgentActivityStateRuntime` moved to `server.agents.runtime` as pure
  leader-activity/AFK/away state access. Dialogue return actions, replies, and
  scheduling stay outside this state adapter.
- Movement-mode state adapters moved to `server.agents.runtime`:
  `AgentModeStateRuntime`, `AgentFarmAnchorStateRuntime`, and
  `AgentPatrolStateRuntime`. These are still direct `AgentRuntimeEntry` state
  wrappers; movement/combat/navigation side effects stay in their existing
  callers.
- Explicit move-target state has moved from `server.agents.integration` to
  `server.agents.capabilities.movement.AgentMoveTargetStateRuntime`. It remains
  a pure `AgentRuntimeEntry` state adapter for target storage, precision flags,
  and arrival checks; movement execution, packet broadcasts, map access, and
  tick ordering stay in their existing runtime/capability/integration seams.
- Movement stuck-state counters have moved from `server.agents.integration` to
  `server.agents.capabilities.movement.AgentMovementStuckStateRuntime`. They
  still only adapt `AgentRuntimeEntry` stuck timing, cooldown, and last-check
  position; recovery jumps, movement execution, and path/log side effects stay
  in the existing movement/runtime callers.
- Climb/rope state has moved from `server.agents.integration` to
  `server.agents.capabilities.movement.AgentClimbStateRuntime`. The adapter
  still only reads/writes the `AgentRuntimeEntry` climb-state fields; rope
  movement, navigation edge execution, packet-visible movement, and map/rope
  lookup behavior stay in the existing capability/runtime callers.
- Swim intent state has moved from `server.agents.integration` to
  `server.agents.capabilities.movement.AgentSwimStateRuntime`. The adapter
  remains a pure `AgentRuntimeEntry` wrapper for swim mode, movement direction,
  vertical hold, jump request, and next-jump timing; swim physics and pose
  updates stay in the existing movement capability services.
- Movement broadcast cache state has moved from `server.agents.integration` to
  `server.agents.capabilities.movement.AgentMovementBroadcastStateRuntime`.
  It only tracks the last broadcast tuple and invalidation flag; actual packet
  broadcasting remains in the existing movement broadcast service.
- Movement physics state has moved from `server.agents.integration` to
  `server.agents.capabilities.movement.AgentMovementPhysicsStateRuntime`. The
  adapter still only reads/writes `AgentRuntimeEntry` physics fields such as
  jump cooldowns, air steering, fall peak, physics coordinates, ground carry,
  and last-ground foothold cache; collision checks, map reads, and packet
  broadcasting remain in existing movement/navigation services.
- Fidget state has moved from `server.agents.integration` to
  `server.agents.capabilities.movement.fidget.AgentFidgetStateRuntime`. The
  adapter still only reads/writes `AgentRuntimeEntry` fidget fields such as
  active mode, trigger, action/jump/visual timers, movement directions, origin
  position, and idle-roll cadence. Its crouch query continues through the
  existing movement-state seam; fidget side effects, visual packets, and
  movement broadcasting remain unchanged in the existing fidget/movement
  callers.
- Script move-target cost checking has moved from `server.agents.integration`
  to `server.agents.plans.AgentScriptMoveTargetRuntime`. It remains a thin
  behavior-preserving adapter over `AgentScriptMoveTargetService`; script
  execution order, fallback range checks, and move-target decisions are
  unchanged.
- Degenerate ranged-hit latch state has moved from `server.agents.integration`
  to `server.agents.capabilities.combat.AgentDegenerateAttackStateRuntime`.
  It remains a pure `AgentRuntimeEntry` state adapter for mark/clear/read
  behavior; local opportunity attacks, ranged engagement, and navigation-tail
  behavior are unchanged.
- Combat action-lock ticking has moved from `server.agents.integration` to
  `server.agents.capabilities.combat.AgentCombatActionLockRuntime`. It still
  decays attack cooldown before move-window cooldown through the same
  `AgentMovementTimers::tickDown` callback; common tick ordering and cooldown
  behavior are unchanged.
- Combat action-state clearing has moved from `server.agents.integration` to
  `server.agents.capabilities.combat.AgentCombatActionStateRuntime`. It still
  clears the current grind target through the existing target-state seam,
  clears attack and movement cooldown windows, clears navigation state, and
  invalidates movement broadcasts in the same order.
- Navigation debug/path-log state access has moved from
  `server.agents.integration` to
  `server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime`.
  It remains a behavior-preserving adapter over `AgentRuntimeEntry`
  navigation debug, target, portal cooldown, and active-edge state; path-log
  file creation, live Agent identity reads, and navigation execution behavior
  are unchanged.
- Grind loot target/retry-suppression state has moved from
  `server.agents.integration` to
  `server.agents.capabilities.looting.AgentGrindLootStateRuntime`. It still
  only adapts `AgentRuntimeEntry` loot-target and ignored-object fields; live
  map item selection, pickup execution, and inventory mutation behavior are
  unchanged.
- Fidget runtime status checks have moved from `server.agents.integration` to
  `server.agents.capabilities.movement.fidget.AgentFidgetRuntime`. The bridge
  still delegates leader-idle detection through the existing
  `AgentChatStatusRuntime` integration seam and reads fidget-active state from
  the fidget capability; fidget movement, visuals, and status behavior are
  unchanged.
- LLM reply bridge access has moved from `server.agents.integration` to
  `server.agents.capabilities.llm.AgentLlmRuntime`. It still delegates actual
  reply delivery through `AgentReplyRuntime`, so chat packet/reply-channel
  behavior is unchanged while LLM orchestration gains an Agent capability home.
- Inventory/trade/drop reply and delay bridge access has moved from
  `server.agents.integration` to
  `server.agents.capabilities.inventory.AgentInventoryRuntime`. It still
  delegates reply delivery and delayed callbacks through the existing
  integration seams, so chat output, timers, trade replies, drop replies, and
  inventory behavior are unchanged.
- Maker automation reply and delayed-step bridge access has moved from
  `server.agents.integration` to
  `server.agents.capabilities.build.AgentMakerRuntime`. It still delegates
  reply delivery and random/fixed delays through the existing integration
  seams, so Maker batch timing, replies, and execution behavior are unchanged.
- Scroll-reaction reply and timing bridge access has moved from
  `server.agents.integration` to
  `server.agents.capabilities.social.AgentScrollReactionRuntime`. It still
  delegates queued speech and delay/random-delay scheduling through the
  existing integration seams, so scroll reaction timing, chat, and visual
  behavior are unchanged.
- KPQ/PQ state and queued-dialogue bridge access has moved from
  `server.agents.integration` to
  `server.agents.capabilities.partyquest.AgentPqRuntime`. It still adapts
  `AgentRuntimeEntry` KPQ state and delegates PQ speech through the existing
  reply integration seam; script reset, PQ hooks, loot eligibility, and KPQ
  behavior are unchanged.
- Movement target snapshot facade access has moved from
  `server.agents.integration` to
  `server.agents.capabilities.movement.AgentMovementTargetRuntime`. It still
  delegates snapshot capture to `AgentMovementTargetSideEffects`, so live
  target capture, formation data, navigation waypoint steering, and movement
  behavior are unchanged.
- Combat attack-facing updates have moved from `server.agents.integration` to
  `server.agents.capabilities.combat.AgentCombatFacingRuntime`. Attack packet
  stance is still translated with the existing combat execution provider and
  pose sync still delegates to the movement pose service, so attack-facing and
  packet-visible stance behavior are unchanged.
- Death/respawn window state has moved from `server.agents.integration` to
  `server.agents.runtime.AgentDeathStateRuntime`. It still only adapts
  `AgentRuntimeEntry` death timing and respawn due checks; HP mutation,
  respawn placement, combat death entry, and packet-visible side effects remain
  in the existing runtime/combat integration callers.
- Leader/owner motion observation state has moved from
  `server.agents.integration` to
  `server.agents.runtime.AgentOwnerMotionStateRuntime`. It still only adapts
  remembered leader position and observed step deltas on `AgentRuntimeEntry`;
  follow movement, fidget decisions, and tick-context timing remain in their
  existing runtime/capability callers.
- Formation spacing state has moved from `server.agents.integration` to
  `server.agents.runtime.AgentFormationStateRuntime`. It still only adapts
  `AgentRuntimeEntry` formation offset fields; formation command handling,
  target snapshot resolution, follow movement, and path logging remain in their
  existing runtime/monitoring callers.
- Airshow capability state has moved from `server.agents.integration` to
  `server.agents.capabilities.social.airshow.AgentAirshowStateRuntime`. It
  still only adapts `AgentRuntimeEntry` airshow flags, trail timing, and
  scripted movement frame state; monster spawning, map broadcasts, timers, and
  session cleanup remain in the existing airshow service/integration callers.
- Pending trade sequence state has moved from `server.agents.integration` to
  `server.agents.capabilities.trade.AgentPendingTradeStateRuntime`. It still
  only adapts `AgentRuntimeEntry` trade retry, batch, item, meso, restore-slot,
  and owner-given item bookkeeping; trade windows, inventory mutation, shop
  APIs, timers, and client locks remain in existing trade/integration callers.
- Grind target search cadence state has moved from `server.agents.integration`
  to `server.agents.capabilities.combat.AgentGrindSearchStateRuntime`. It
  still only adapts the next-retarget-search timestamp on `AgentRuntimeEntry`;
  live target selection and map/monster checks remain in the existing combat
  capability and integration target adapters.
- Mob-touch checkpoint state has moved from `server.agents.integration` to
  `server.agents.capabilities.combat.AgentMobTouchStateRuntime`. It still only
  adapts the previous sweep position per map on `AgentRuntimeEntry`; live
  character/map reads and touch-damage side effects remain in
  `AgentMobTouchRuntime`.
- Script task queue/runtime state has moved from `server.agents.integration` to
  `server.agents.plans.AgentScriptTaskStateRuntime`. It still only adapts
  queued task, active task, script id, step, scratch int, and wait-until fields
  on `AgentRuntimeEntry`; script execution, movement decisions, PQ hooks, and
  command side effects stay in existing plan/runtime callers.
- Final shell cleanup removed `src/main/java/server/bots/**` and
  `src/test/java/server/bots/**`. Tests that used the constructor-compatible
  shell now instantiate `AgentRuntimeEntry` directly. Source and test code no
  longer import or reference `server.bots`; behavior is unchanged.
- `AgentBot*` class names remain as documented Agent-owned adapter/runtime
  names after shell deletion. They are naming artifacts only, not dependencies
  on the removed bot package, and should be renamed by capability in a later
  semantic cleanup branch.
- Shop resupply and sell-trash metadata lookup now accepts an explicit
  `InventoryGateway` from runtime callers. `AgentShopService` no longer reaches
  directly into the Cosmic adapter for projectile attack, slot max, sell-trash
  classification, or item-name shortfall messages; behavior, timing, shop
  purchase ordering, and replies are unchanged.
- `AgentInventoryGatewayRuntime` centralizes the live Cosmic inventory gateway
  accessor inside `server.agents.integration`. Offer and inventory runtime
  adapter code that still needs legacy metadata now goes through that boundary
  instead of importing the Cosmic adapter directly; offer decisions and trade
  routing are unchanged.
- `AgentTradeGatewayRuntime` centralizes the live Cosmic trade gateway accessor
  inside `server.agents.integration`. `AgentInventoryTransferService` now uses
  inventory/trade gateway runtime boundaries instead of importing the Cosmic
  adapter directly; transfer routing, drop handling, item collection,
  classification, trade start/invite order, and replies are unchanged.
- Simple trade lifecycle services now route start/invite/cancel/complete/visit
  calls through `AgentTradeGatewayRuntime` instead of importing the Cosmic
  adapter directly. Manual trade, supply-share trade, sequence opening,
  invite/confirm timeouts, cancellations, completion reactions, and replies are
  unchanged.
- `AgentPacketGatewayRuntime` centralizes live packet gateway access inside
  `server.agents.integration`. Trade item-add packet emission now uses that
  boundary instead of importing the Cosmic adapter directly; item removal,
  trade slot assignment, partner packet mirroring, and restore-slot tracking are
  unchanged.
- `AgentMapGatewayRuntime` now centralizes the first live map gateway access
  inside `server.agents.integration`. Respawn-near-leader map changes and
  ground-point lookup route through `MapGateway`/`CosmicMapGateway`; death-state
  clearing, HP restore, teleport placement, movement reset, broadcast, reply,
  and face-expression behavior are unchanged.
- Lifecycle spawn map-change hooks now also route through
  `AgentMapGatewayRuntime`. Offline-loaded Agents still resolve the same spawn
  map/position, register through the same runtime path, place online Agents
  through `AgentSpawnPlacementRuntime`, and start follow behavior exactly as
  before.
- Cross-map follow synchronization now routes its live `changeMap(map,
  position)` operation through `AgentMapGatewayRuntime`. The follow map-change
  predicate, ground-point fallback, idle-on-ground call, and movement-state
  reset order are unchanged.
- Offline Agent loading now routes map resolution and channel/world/map player
  registration through `AgentMapGatewayRuntime`. Character loading, disease
  restoration, rate initialization, spawn-position resolution, client binding,
  entered-channel state, map visit, and disease-expiry task ordering are
  unchanged.
- Live leader lookup now routes through `AgentCharacterGatewayRuntime` and the
  new `CharacterGateway`/`CosmicCharacterGateway` boundary. Tick-time leader
  refresh and relogin leader resolution still read the same world player
  storage and preserve the same null/fallback behavior.
- The first semantic cleanup slice renamed the foundational identity,
  scheduler, reply-channel, reply-runtime, and message-queue adapters to
  neutral `Agent*` names. This was a type/file/import rename only; behavior and
  reply timing are unchanged.
- The next semantic cleanup slice renamed state adapters from
  `AgentBot*StateRuntime` to `Agent*StateRuntime`. This was a type/file/import
  rename only; state ownership and behavior are unchanged.
- The movement/fidget semantic cleanup slice renamed movement command,
  movement callback, movement status, movement target, movement kinematics,
  and fidget runtime adapters from `AgentBot*` to neutral `Agent*` names. This
  was a type/file/import rename only; movement, navigation targeting, fidget,
  and follow behavior are unchanged.
- The dialogue/control/status semantic cleanup slice renamed chat
  orchestrator, chat report, chat status, control, manager-status, pending
  action, and status runtime adapters from `AgentBot*` to neutral `Agent*`
  names. This was a type/file/import rename only; command handling, report
  delivery, AFK/offline status checks, and pending-action behavior are
  unchanged.
- The item/build/supply semantic cleanup slice renamed active-mode, ammo,
  build, equipment, inventory, Maker, offer, potion, shop, supply, transfer,
  and utility runtime adapters from `AgentBot*` to neutral `Agent*` names. This
  was a type/file/import rename only; item automation, trade/shop flows,
  build setup, upgrade offers, and supply sharing behavior are unchanged.
- The session/social/PQ semantic cleanup slice renamed LLM, party-quest,
  script move target, scroll reaction, session control, session lifecycle,
  session runtime, and social runtime adapters from `AgentBot*` to neutral
  `Agent*` names. This was a type/file/import rename only; session relog/logout,
  social reaction, LLM reply, PQ hook, and script movement behavior are
  unchanged.
- The command resolver semantic cleanup slice renamed the integration command
  target adapter from `AgentBotCommandParser` to `AgentCommandTargetResolver`
  and replaced bot-named method entry points with Agent-named equivalents.
  Transfer parsing and targeted command matching behavior are unchanged.
- The final combat semantic cleanup slice renamed combat and mob-touch runtime
  adapters from `AgentBot*` to neutral `Agent*` names. This was a
  type/file/import rename only; attack planning, attack packet shape, hitbox
  selection, ammo gates, damage rolls, heal/buff support, mob-touch handling,
  and cooldown behavior are unchanged.
- Airborne movement and physics services now take `AgentRuntimeEntry` directly.
  Air steering, wall/ceiling collision handling, landing, rope grabs, down-jump
  grace, fall damage, motion state sync, and movement broadcasts are unchanged.
- Ground movement step resolution and mob avoidance now take
  `AgentRuntimeEntry` directly. Warmup fallback stepping, hysteresis,
  was-moving state updates, walk-lane mob detection, region checks, and jump
  landing simulation preserve the same behavior.
- Ground action planning/execution now exposes `AgentRuntimeEntry` at its
  public boundary. Ground step resolution, ledge fallback, mob avoidance, and
  ground physics remain explicit temporary compatibility calls into
  BotEntry-backed movement helpers, preserving walk/jump/idle/crouch selection,
  movement velocity updates, fixed-arc jumps, and broadcasts.
- Movement phase dispatch and stuck recovery no longer import `BotEntry`.
  Airborne/grounded movement and unstuck recovery still call deeper
  BotEntry-backed movement services through explicit temporary compatibility
  casts, preserving phase selection, random unstuck jump direction, cooldown,
  navigation reset, and movement broadcast behavior.
- Anchored farm runtime now exposes `AgentRuntimeEntry` at its public boundary.
  Local opportunity attack, idle physics, idle-on-ground broadcast, and
  movement-core stepping still route through explicit temporary compatibility
  casts where those deeper modules remain BotEntry-backed; anchored farm
  behavior is unchanged.
- Grind target runtime and target-position service now use `AgentRuntimeEntry`
  for fallback wander, patrol wander, active loot targeting, retry suppression,
  convenient-loot selection, and navigation region resolution. Existing random
  wander, patrol target reuse, loot radius, retry suppression, and grind tail
  behavior are unchanged.
- Navigation debug overlay selection/path helpers now use `AgentRuntimeEntry`
  locally instead of importing `BotEntry`. The temporary session lookup still
  supplies compatibility-shell instances, while graph/path overlay messages,
  active-edge display, path logging, and clear behavior are unchanged.
- Script task execution no longer imports `BotEntry`; movement command calls are
  now explicit temporary compatibility casts at the Agent movement-command
  boundary. Script task start routing, follow-target resolution, grind/stop
  dispatch, item dropping, and completion checks are unchanged.
- Transfer service hooks now use `AgentRuntimeEntry` for entry lookup,
  scheduled-task cancellation, and stop callbacks. The transfer runtime still
  adapts the temporary BotEntry-backed registry list at the boundary, preserving
  legacy removal order, authorization checks, stop/cancel/register sequencing,
  delayed greeting timing, and reply text.
- Scripted move-combat tick service now uses `AgentRuntimeEntry` for its public
  service and hook signatures. The live-mode bridge still adapts to legacy
  movement/combat callbacks where required, but local-opportunity move gating,
  action-window cleanup, attack fall-through, and movement-core stepping are
  unchanged.
- Tick failure runtime now accepts `AgentRuntimeEntry` and Agent-entry stop
  hooks. Failure-window counting, movement-state reset, cleanup on disable,
  forced idle, failure reply text, and logging behavior are unchanged while the
  default movement-stop hook remains an explicit temporary compatibility edge.
- Common tick runtime and the live-gate common tick hook now use
  `AgentRuntimeEntry` directly. Per-tick mob damage, death handling, passive
  loot, potion checks, level-up/build checks, AFK checks, trade ticks, PQ hooks,
  script-task ticks, combat locks, skill cache rebuilds, support healing, buff
  logic, and action-locked physics preserve the same ordering and behavior.
- Tick runtime now accepts `AgentRuntimeEntry` and Agent-entry movement command
  callbacks at its public boundary. The deeper tick core and failure handler
  still use explicit temporary BotEntry casts until those runtime slices are
  migrated, but guarded tick orchestration and movement command behavior are
  unchanged.
- Whisper command runtime now uses `AgentRuntimeEntry` as its Agent-facing hook
  type instead of importing `BotEntry`. The temporary registry still returns the
  compatibility shell internally, but whisper target resolution, reply-channel
  selection, and chat routing are unchanged.
- Chat route runtime now uses generic `AgentRuntimeEntry` handles instead of
  importing `BotEntry`. Pending-offer routing, lifecycle chat commands,
  formation commands, targeted/untargeted routing, typo suggestions, reply
  channel updates, LLM fallback, and owner command activity tracking are
  unchanged.
- LLM reply runtime now accepts `AgentRuntimeEntry` instead of `BotEntry`.
  Sender relation, reply channel, prompt context, mode/farm-anchor flags,
  owner-command context, and asynchronous reply delivery are unchanged.
- Inventory runtime adapter callbacks no longer import `BotEntry`. Passive
  loot, manual trade, trade tick, lifecycle, transfer availability, and trade
  runtime callback wiring are unchanged.
- Recommended-gear offer report actions now call `AgentOfferService` with
  `AgentRuntimeEntry` directly. Owner-present checks, best-gear offering, and
  queued report replies are unchanged while the offer adapter no longer casts
  through `BotEntry`.
- Chat orchestrator context now adapts from `AgentRuntimeEntry` instead of
  `BotEntry`. Pending actions, session/supply/social/control/equipment/
  movement/build/utility/transfer/report callbacks, current job/level, and
  job-advancement routing preserve the same behavior.
- Pending-action dialogue callbacks now use `AgentRuntimeEntry` instead of
  `BotEntry`. Pending action/drop-category reads, item-choice execution,
  item-choice cancellation, owner-away choice routing, relog/logout
  confirmation, skill-tree choice handling, and queued skill-report replies
  are unchanged.
- Session dialogue callbacks now use `AgentRuntimeEntry` instead of `BotEntry`.
  Relog/logout prompts, owner-away prompts, stay/town/logout decisions,
  delayed replies, save/disconnect scheduling, and temporary lifecycle lookup
  behavior are unchanged.
- Control dialogue callbacks now use `AgentRuntimeEntry` instead of `BotEntry`.
  Support/heal toggles, consumable buff toggles, proactive-offer toggles,
  buff debug reports, and AP/SP respec replies preserve the same delayed
  side effects and reply text.
- Airshow service internals now use `AgentRuntimeEntry` instead of `BotEntry`.
  Named-session lookup, map validation, active-state checks, timed horizontal
  and vertical frames, trail spawning, restoration, movement packets, and
  transient-state cleanup are unchanged.
- Scroll reaction service entry handling now uses `AgentRuntimeEntry` instead
  of `BotEntry` on the Agent-owned public surface. Range filtering, delayed
  reaction scheduling, streak/load tracking, emotes, chat queueing, fidget
  triggering, and cooldown behavior are unchanged.
- Combat cooldown compatibility wrappers were removed from `BotEntry`.
  Combat, movement-lock, damage, and alert callers already use
  `AgentCombatCooldownStateRuntime` over `AgentCombatCooldownState`.
- Grind wander and grind-loot compatibility wrappers were removed from
  `BotEntry`. Grind fallback and loot targeting callers already use
  `AgentGrindWanderStateRuntime` and `AgentGrindLootStateRuntime` over
  Agent-owned state.
- Move target, farm-anchor, and patrol compatibility wrappers were removed
  from `BotEntry`. Movement mode callers already use
  `AgentMoveTargetStateRuntime`, `AgentFarmAnchorStateRuntime`, and
  `AgentPatrolStateRuntime` over the Agent-owned runtime state objects.
- Death window and portal cooldown compatibility wrappers were removed from
  `BotEntry`. Runtime callers already use `AgentDeathStateRuntime` and
  `AgentNavigationDebugStateRuntime` over Agent-owned state.
- Shop transition compatibility wrappers were removed from `BotEntry`.
  Shop visit/sequence callers already enter through
  `AgentShopStateRuntime`, backed by the Agent-owned `AgentShopState`.
- Pending loot-offer and bot trade-retry compatibility wrappers were removed
  from `BotEntry`. Offer/trade callers now use
  `AgentOfferStateRuntime` and `AgentPendingTradeStateRuntime` over the
  Agent-owned trade state objects.
- Message queue compatibility wrappers were removed from `BotEntry`.
  Chat/reply queue callers already use `AgentMessageQueueStateRuntime` over
  the Agent-owned `AgentMessageQueueState`.
- Pending chat action compatibility wrappers were removed from `BotEntry`.
  Pending action reads/writes now enter through
  `AgentPendingActionStateRuntime` and the Agent-owned
  `AgentPendingActionState` hosted temporarily by the shell.
- Live Agent/leader character identity moved to
  `server.agents.runtime.AgentRuntimeIdentityState`. `BotEntry` temporarily
  delegates `bot()`, `owner()`, `setOwner(...)`, and legacy getters to the
  Agent-owned state object so relog/session refresh behavior remains unchanged.
- Scheduled runtime task handle state moved to
  `server.agents.runtime.AgentScheduledTaskState`. `BotEntry` temporarily hosts
  the Agent-owned state object for compatibility, while Agent scheduler and
  lifecycle callers now route has/cancel behavior through the Agent runtime
  state.
- The live runtime/session implementation moved to
  `server.agents.runtime.AgentRuntimeEntry`. `server.bots.BotEntry` remains only
  as a deprecated constructor-compatible shell while remaining signatures are
  migrated to Agent runtime types; behavior is unchanged.
- Ammo and potion runtime state adapters now take
  `server.agents.runtime.AgentRuntimeEntry` directly. This preserves behavior
  because the temporary `BotEntry` shell extends the Agent runtime entry, while
  removing another supplies-state dependency on `server.bots`.
- Build and consumable-buff runtime state adapters now take
  `server.agents.runtime.AgentRuntimeEntry` directly. The AP/SP build prompts,
  job prompt markers, consumable-buff toggles, scan cadence, and last-action
  summaries still use the same Agent-owned state objects.
- Activity, mode, tick, and tick-failure runtime state adapters now take
  `server.agents.runtime.AgentRuntimeEntry` directly. Follow/grind mode flags,
  leader activity state, heartbeat cadence, and tick failure windows preserve
  the same Agent-owned state backing without importing `server.bots`.
- Movement target, farm anchor, patrol, and stuck-state runtime adapters now
  take `server.agents.runtime.AgentRuntimeEntry` directly. Explicit move
  targets, sentry/farm anchors, patrol regions, and stuck recovery counters keep
  the same behavior and state backing.
- Reply-channel, message-queue, and pending-action runtime state adapters now
  take `server.agents.runtime.AgentRuntimeEntry` directly. Whisper routing,
  queued reply delivery, pending confirmations, and pending drop categories are
  unchanged.
- Inventory cooldown and manual-trade runtime state adapters now take
  `server.agents.runtime.AgentRuntimeEntry` directly. Loot inhibit timers,
  inventory-full warning cooldowns, manual trade references, accept delays, and
  timeout state keep the same behavior.
- Death, map tracking, live leader, and formation runtime state adapters now
  take `server.agents.runtime.AgentRuntimeEntry` directly. Respawn windows,
  foothold tracking, live leader references, and follow offsets keep the same
  behavior and state backing.
- Combat cooldown, combat skill cache, and combat support-buff runtime state
  adapters now take `server.agents.runtime.AgentRuntimeEntry` directly. Attack
  windows, mob-hit cooldowns, cached attack/support skills, and buff timing keep
  the same behavior and state backing.
- Grind target, grind search, grind wander, grind loot, retreat hold, breakout,
  and degenerate ranged-attack runtime state adapters now take
  `server.agents.runtime.AgentRuntimeEntry` directly. Target references, search
  cadence, wander direction, loot retry suppression, retreat holds, breakout
  commitments, and degenerate-hit markers keep the same behavior and state
  backing.
- Climb, swim, movement physics, movement broadcast, and mob-touch runtime state
  adapters now take `server.agents.runtime.AgentRuntimeEntry` directly. Rope
  state, swim input, physics integration fields, movement broadcast cache, and
  mob-touch sweep memory keep the same behavior and state backing.
- Airshow, owner-motion, tick-cadence, scroll-reaction, and skill-buff debug
  runtime state adapters now take `server.agents.runtime.AgentRuntimeEntry`
  directly. Airshow trail timing, observed leader movement, tick
  accumulator/skip delay, social reaction cooldown/streak tracking, and
  skill-buff debug summaries keep the same behavior and state backing.
- AoE reposition, shop transition, and script-task runtime state adapters now
  take `server.agents.runtime.AgentRuntimeEntry` directly. AoE reposition
  anchors, shop visit/sequence transition state, and scripted task queues/runtime
  step state keep the same behavior and state backing.
- Live Agent identity runtime now takes `server.agents.runtime.AgentRuntimeEntry`
  directly. Bot/leader character lookup, ids, map lookup, and copied position
  snapshots keep the same behavior and state backing.
- Movement state facade and fidget runtime state adapter now take
  `server.agents.runtime.AgentRuntimeEntry` directly. Movement snapshots,
  profile/input/down-jump state, fidget mode/timing/input state, and crouch
  checks keep the same behavior and state backing.
- Offer and pending-trade runtime state adapters now take
  `server.agents.runtime.AgentRuntimeEntry` directly. Gear prompt reservations,
  pending loot offers, proactive upgrade flags, trade sequence category/timers,
  item/meso batch state, restore slots, and queued retries keep the same
  behavior and state backing.
- Script task completion checks now take `server.agents.runtime.AgentRuntimeEntry`
  directly, and an unused `server.bots.BotEntry` import was removed from the
  transfer runtime. Script movement/follow completion behavior is unchanged.
- Navigation debug/path logging, movement state reset, follow-anchor resolution,
  navigation region classification, and combat action state reset now take
  `server.agents.runtime.AgentRuntimeEntry` directly. Path-log output,
  navigation edge/debug state, transient movement cleanup, follow-anchor
  selection, and combat action cleanup behavior are unchanged.
- Movement pose reset/sync helpers and the fidget clear helper now take
  `server.agents.runtime.AgentRuntimeEntry` directly so navigation reset can
  clear transient movement state without re-entering the `server.bots` shell.
- Pure trade state services now take `server.agents.runtime.AgentRuntimeEntry`
  directly for sequence initialization, batch setup, meso insertion, category
  announcement, and reset behavior. Trade state storage and trade-window side
  effects are unchanged.
- Movement profile refresh now takes `server.agents.runtime.AgentRuntimeEntry`
  directly. Profile comparison, graph warmup, profile storage, and navigation
  state reset behavior are unchanged.
- AoE combat reposition service/runtime now take
  `server.agents.runtime.AgentRuntimeEntry` directly. AoE reposition eligibility,
  target clustering, scoring, debug logging, and anchor commitment behavior are
  unchanged.
- Fixed-weapon equipment DP execution now enters through
  `server.agents.capabilities.equipment.AgentEquipmentOptimizer`.
  `BotEquipManager` auto-equip, debug, and future-recommendation branches call
  the Agent optimizer directly, and optimizer parity tests now exercise the
  Agent-owned class. The stale private bot-side DP solver and local scoring
  helpers were removed after the caller migration compiled and passed focused
  equipment tests.
- Equipment plan application side effects now live in
  `AgentEquipmentPlanExecutor`. `BotEquipManager` no longer owns the
  `InventoryManipulator` move loop or post-plan infeasible-equipment sweep.
- Offered-item and recommendation optimizer orchestration now lives in
  `AgentEquipmentOptimizationService`. `AgentEquipmentRecommendationService`
  no longer imports `BotEquipManager`; the old `BotEquipManager`
  `runOptimizerWithExtras` methods are compatibility delegates.
- Dead `BotEquipManager` recommendation wrapper methods were removed after
  scans showed no source or test callers. Recommendation entry points remain
  Agent-owned.
- Auto-equip execution and debug branch reporting now enter through
  `AgentEquipmentAutoEquipService`. `AgentEquipmentService` calls the Agent
  service directly, and the old `server.bots.BotEquipManager` production file
  has been deleted after compile and focused equipment tests passed.
- Production equipment callers now enter through
  `server.agents.capabilities.equipment.AgentEquipmentService`.
  Equipment runtime behavior now lives in Agent-owned equipment capability
  classes; remaining historical test names can be renamed in a later test
  cleanup without changing behavior.
- Equipment service job/weapon compatibility now calls
  `AgentWeaponCompatibilityPolicy` directly instead of delegating through the
  legacy bot optimizer.
- Equipment slot alias resolution moved to
  `AgentEquipmentSlotResolver`; the legacy bot method now delegates to the
  Agent resolver to preserve old chat aliases while moving ownership.
- Equipment useful-stat scoring and defense-adjusted expected damage moved to
  `AgentEquipmentScoringPolicy`; the legacy bot optimizer delegates to the
  Agent scoring policy for these pure calculations.
- Auto-equip throttle state moved to `AgentAutoEquipThrottle`; the legacy bot
  optimizer delegates the same force and 30-second duplicate-trigger behavior
  to Agent-owned state.
- Inventory/trade tick entry ownership moved fully to
  `server.agents.capabilities.inventory.AgentInventoryTickRuntime`.
  `AgentCommonTickRuntime` and legacy parity tests now call the Agent inventory
  tick facade directly; `server.bots.BotInventoryManager` has been deleted.
- BotManager runtime/lifecycle/tick reconstruction milestone is complete:
  production source no longer imports `server.bots.BotManager` or calls
  `BotManager.getInstance()`, focused runtime/config/supply/combat parity tests
  passed, and the production `server.bots.BotManager` file has been deleted.
- Movement profile physics baseline ownership moved to
  `server.agents.capabilities.movement.AgentMovementPhysicsConfig`.
  `AgentMovementProfile` no longer imports `server.bots.BotMovementManager` or
  `server.bots.BotPhysicsEngine` for walk, horizontal-force, jump, or rope-jump
  baseline values; the temporary bot movement/physics runtime reads the same
  Agent-owned constants to preserve behavior.
- Rope-grab, snap-drop, and slope-up physics threshold reads also moved to
  `AgentMovementPhysicsConfig`. Agent movement fallback and navigation graph
  code now read those thresholds from Agent-owned config while the legacy
  physics engine keeps the collision/simulation body for later slices.
- Movement tick duration ownership moved to `AgentMovementPhysicsConfig`.
  Agent registration, preflight, movement-only, stuck detection, combat
  knockback/reporting, fidget delay, and navigation edge timing reads now use
  the Agent-owned movement tick value directly where they do not also require
  legacy bot timing callbacks.
- Movement command distance/config ownership moved to
  `AgentMovementPhysicsConfig`: stop distance, follow distance, grind edge
  margin, jump-Y threshold, teleport distance, out-of-bounds teleport distance,
  and follow Y cap. Agent runtime, fallback movement, fidget, target snapshot,
  and navigation graph call sites now read these values from Agent-owned config
  while `BotMovementManager` delegates its compatibility accessors to the same
  source.
- Navigation-facing movement config reads moved to
  `AgentMovementPhysicsConfig`. `BotNavigationManager` no longer reads
  `BotMovementManager.cfg` for jump-Y, stop-distance, rope-grab, grind-margin,
  or climb-speed thresholds while preserving the same legacy values.
- External climb-step calculation reads moved to
  `AgentMovementKinematicsService.climbStepPerTick()`. Agent navigation,
  climb movement, movement report snapshots, and remaining bot shims now use
  the Agent-owned calculation while `BotPhysicsEngine` keeps its internal
  simulation body for later migration.
- External swim steering threshold reads moved to `AgentMovementPhysicsConfig`.
  Agent swim and airborne movement now read swim arrival radius, jump cooldown,
  level/down bands, jump trigger, and rope-grab tolerance from Agent-owned
  config. `BotPhysicsEngine` keeps compatibility accessors that delegate to the
  same values while its swim integrator remains a later migration slice.
- External jump/rope kinematics range reads moved to
  `AgentMovementKinematicsService`. Agent movement report snapshots and
  navigation graph edge generation now use Agent-owned kinematics methods for
  jump force, rope-jump force, gravity-per-tick, max jump height, jump travel,
  rope-jump travel, and rope-grab simulation travel. These methods delegate to
  the current physics implementation until the simulation body moves.
- Remaining external walk-step calculation reads moved to
  `AgentMovementKinematicsService.walkStep(...)`. Fidget, fallback movement,
  path logging, and the remaining navigation shim no longer read walk-step
  directly from `BotPhysicsEngine`; side-effect physics calls still migrate in
  later slices.
- Pose and stance side-effect entry points moved to
  `AgentMovementPoseService`. Agent idle physics, anchored farm, leader safety,
  combat death/facing, fidget, airshow restore, potion stance checks, ground
  action idle, and movement reset now enter through the Agent pose seam for
  idle, prone, reset, teleport, dead, resolved stance, and sync operations.
  The service delegates to the current physics engine until those internals
  migrate.
- Ground lookup entry points moved to `AgentGroundingService`. Follow target
  snapping, leader safety town clustering, combat ground lookup, movement
  broadcast foothold IDs, mob avoidance, fidget movement, fallback movement,
  and navigation graph lookup now call the Agent grounding seam for
  `findGroundPoint` and `findGroundFoothold`.
- Movement countdown helpers moved to
  `server.agents.capabilities.movement.AgentMovementTimers`. Agent combat,
  common-tick, inventory/trade, shop, supplies, and stuck-detection callback
  bundles now use Agent-owned tick-down and delay-after-current-tick helpers;
  `BotMovementManager` delegates its compatibility timer methods to the same
  Agent helper.
- Packet-visible movement broadcast ownership moved to
  `server.agents.capabilities.movement.AgentMovementBroadcastService`. The
  movement packet byte layout, foothold z-layer caching, duplicate broadcast
  suppression, and `broadcast-move` performance timing are preserved; Agent
  callers now enter the Agent service directly and `BotMovementManager` keeps
  only a compatibility delegate.
- Movement reset and transient-state cleanup ownership moved to
  `server.agents.capabilities.movement.AgentMovementStateResetService`. Agent
  map-transition, respawn, spawn-placement, leader-safety, action-state,
  fidget, airshow, and command-mode callers now clear navigation/transient
  movement state through the Agent service; `BotMovementManager` keeps only
  compatibility delegates.
- Foothold-index construction moved to
  `server.agents.capabilities.movement.AgentFootholdIndexService`. Agent
  spawn-placement and map-change hook bundles no longer call
  `BotMovementManager.buildFhIndex`; the bot method remains a compatibility
  delegate for legacy tests and harnesses.
- Walk-step kinematics entry ownership moved to
  `server.agents.capabilities.movement.AgentMovementKinematicsService`. Agent
  navigation graph/probe and movement report runtime callers now enter through
  the Agent kinematics service; the old `BotMovementManager.walkStep`
  compatibility delegates have been removed. Deeper physics simulation still
  migrates in later slices.
- Dead `BotMovementManager` compatibility wrappers for movement timers and
  jump-probe landing/reach helpers were removed after scans showed no
  remaining source or test callers. Agent-owned timer and jump-probe services
  keep the behavior surface for active code.
- Movement profile refresh, teleport-state reset, navigation-state clearing,
  and foothold-index compatibility wrappers were removed from
  `BotMovementManager`. Remaining tests and harnesses now call
  `AgentMovementProfileService`, `AgentMovementStateResetService`, and
  `AgentFootholdIndexService` directly.
- Movement policy and ground-target compatibility wrappers were removed from
  `BotMovementManager`. Tests now call `AgentGroundTargetService`,
  `AgentClimbMovementService`, `AgentGroundMovementPolicy`, and
  `AgentGroundMovementService` directly for grind target clamping, climb
  hold/snap decisions, precise navigation stop distance, and ground step
  resolution.
- Movement execution compatibility wrappers were removed from
  `BotMovementManager`. Movement and navigation tests now call
  `AgentAirborneMovementService`, `AgentClimbMovementService`,
  `AgentGroundMovementRuntimeService`, and `AgentMovementRecoveryService`
  directly; `BotMovementManager` remains only as a temporary physics-config
  binding shell until `BotPhysicsEngine` config ownership is migrated.
- The temporary `BotMovementManager` physics-config shell was deleted after
  the final config identity test moved to Agent movement physics config parity
  checks. Production `server.bots` later shrank to `BotEntry` and
  `BotPhysicsEngine`; after the physics shell was drained, only `BotEntry`
  remains.
- Physics pose and movement-snapshot test callers entered through
  `AgentMovementPoseService` and `AgentMovementSnapshotService` instead of
  calling `BotPhysicsEngine` directly before the physics shell was deleted.
- Physics kinematics and movement-report test callers now enter through
  `AgentMovementKinematicsService` instead of calling `BotPhysicsEngine`
  directly for walk step, climb step, jump force, jump height, and horizontal
  reach calculations.
- Ground lookup implementation for `findGroundFoothold` and
  `findGroundPoint` moved into `AgentGroundingService`; later slices removed
  the temporary `BotPhysicsEngine` delegates after callers moved to Agent
  services.
- Simple grounded collision checks for down-jump eligibility and far-ground
  detection moved into `AgentGroundCollisionService`. `BotPhysicsEngine`
  keeps temporary delegates while larger ground-step preview and wall-collision
  internals remain in the physics body.
- Public ground-step collision queries now live in
  `AgentGroundCollisionService`, including wall-blocked step detection,
  walk-region snapping, map-side boundary checks, and collidable-wall fallback
  lookup. `BotPhysicsEngine` delegates those public query methods while its
  private ground integrator preview remains temporarily bot-owned.
- Ground physics runtime entry points now live in
  `AgentGroundPhysicsService`. Ground position synchronization, no-ground fall
  launch, force/friction stepping, blocked-step handling, walk-off fall launch,
  and packet velocity updates preserve the legacy algorithm while
  `BotPhysicsEngine` keeps only compatibility delegates for those methods.
- Jump probe simulation now lives in `AgentJumpProbeService`. Jump, rope-jump,
  fall, down-jump, walk-off, rope-grab, and post-landing probes preserve the
  same physics/collision rules while `BotPhysicsEngine` converts Agent DTOs
  back into temporary legacy DTOs for callers not yet migrated.
- Down-jump launch state now lives in
  `AgentQueuedMovementActionService`, with shared airborne setup in
  `AgentAirborneLaunchService`. `BotPhysicsEngine.beginDownJump` remains only
  as a temporary compatibility delegate; the Agent path preserves the same
  down-jump velocity and grace-period constants through
  `AgentMovementPhysicsConfig`.
- Ground jump, climb-up jump, jump-off-rope, and rope-transfer launch state now
  live in `AgentRopeMovementService`. The swim-map ground-jump branch stays
  behavior-identical, including the zeroed air X velocity, swim impulse, swim
  cooldown, and unconverted packet movement velocity.
- Rope/ladder climb hold and advance now live in `AgentRopeMovementService`.
  The Agent service owns idle climb sync, vertical step advancement, top-rope
  landing, and bottom-rope fall launch while `BotPhysicsEngine` keeps only
  temporary delegate entry points for legacy callers.
- Movement timer countdown implementation moved into `AgentMotionTimerService`.
  `BotPhysicsEngine.tickMotionTimers` remains a temporary delegate while
  physics callers migrate.
- Stance resolution, stance sync, and packet movement snapshot construction
  moved into `AgentMovementPoseService` and `AgentMovementSnapshotService`.
  `BotPhysicsEngine` keeps temporary delegates for older physics callers while
  the remaining physics body is migrated.
- Non-legacy Agent config and supply tests now use `AgentRuntimeConfig` and
  `AgentRuntimeRegistry` directly instead of compiling through
  `server.bots.BotManager`; this removed the final compatibility-shell test
  gate before deleting the BotManager production file.
- Movement simulation, follow perf harness, and BotManager parity tests now
  read shared tick/loot/follow config from `AgentRuntimeConfig` instead of
  `BotManager.cfg`. This preserves the same values while removing another
  test-only dependency on the BotManager compatibility alias.
- BotManager parity tests for owner-gained trade item notifications, cleanup
  on leaving Agent control, and combat sibling target occupancy now call
  Agent-owned notification, cleanup, and runtime-registry services directly
  instead of reaching those behaviors through `BotManager.getInstance()`.
- Dead `BotManager.getInstance()` declarations were removed from BotManager
  parity tests after those tests moved to direct Agent services.
- Inventory legacy parity tests no longer mock `BotManager.getInstance()` for
  patrol-loot target selection; the test now exercises the Agent loot target
  service directly with only the navigation graph seam mocked.
- Manual and spawned registration entry points now enter through
  `AgentInteractionRuntime`, which supplies the Agent tick callback. BotManager
  no longer owns a private tick callback.
- Relogin entry wiring now enters through `AgentInteractionRuntime`; the
  session lifecycle bridge no longer imports BotManager for relogin, and
  BotManager's legacy relogin method delegates back to the Agent facade.
- Server chat/spawn integration now enters through
  `AgentInteractionRuntime`. General chat, party chat, messenger invite
  fallback, party invite fallback, and `@spawnbot` no longer call
  `BotManager.getInstance()` for chat/spawn behavior. The later BotManager
  deletion removed those legacy compatibility delegates after callers moved to
  Agent-owned entry points.
- Dead BotManager script-task and script-item compatibility wrappers were
  removed. Script queueing, drop-item execution, queued-task checks, and cheap
  move-target checks now use Agent script services directly.
- BotManager movement command wrappers were removed. Spawn, transfer/dismiss,
  live tick mode callbacks, movement chat tests, and movement parity tests now
  call `AgentMovementCommandRuntime` directly for follow, stop, grind,
  farm-here, patrol, and fixed move commands.
- BotManager leader-safety compatibility helpers were removed. Session
  side-effect wiring now calls `AgentLeaderSafetyRuntime` directly for
  inactive-leader safe mode, and primary-session / town-offer checks live in
  Agent session control runtime.
- BotManager formation, target snapshot, and movement-only test shims were
  removed. Bot movement simulation and parity tests now call
  `AgentFormationRuntime`, `AgentTargetSnapshotRuntime`, and
  `AgentMovementOnlyStepRuntime` directly, so BotManager no longer exposes
  these non-public runtime helper entry points.
- BotManager test-only tick harness methods were removed. The perf harness now
  calls `AgentCommonTickRuntime`, `AgentScriptTaskRuntime`, and
  `AgentMovementOnlyStepRuntime` directly, so BotManager no longer owns
  test-only common tick or full tick execution hooks.
- Chat-route default registry and formation config ownership now lives in
  `AgentChatRouteRuntime.handleChat(...)`. BotManager no longer stores the
  active-entry registry map or passes follow stagger / snap range config into
  chat routing.
- Dead BotManager reply/sanitizer utility wrappers were removed. Reply routing
  and chat sanitization remain in Agent-owned dialogue/reply runtime classes.
- Dead BotManager grind/navigation/combat helper wrappers were removed after
  tests were switched to `AgentGrindTargetRuntime` and existing Agent
  navigation/combat runtime classes. BotManager no longer exposes those
  static helper shims.
- The test-only BotManager `tryFollowIdleMovementFastPath` compatibility
  wrapper was removed. Tests now call
  `AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath` directly.
- Guarded tick entry ownership now lives in
  `AgentTickRuntime.tick(entry, leaderId, agentId, issueGrind, issueFollow)`.
  BotManager no longer calls `AgentTickOrchestrator.runGuardedTick` directly
  for production ticks; it only supplies temporary grind/follow compatibility
  callbacks.
- Tick-core default hook-bundle ownership now lives in
  `AgentTickCoreRuntime.tickCore(entry, leaderId, agentId, issueGrind, issueFollow)`.
  BotManager no longer assembles leader lookup, safety, map transition,
  ownerless movement, death, target snapshot, script-task, local opportunity,
  movement-core, anchored-farm, or grind-mode callbacks for ordinary ticks.
- Script move-target default near-target radius ownership now lives in
  `AgentScriptMoveTargetService.isCheapMoveTarget(...)`. BotManager and the
  Agent integration wrapper no longer pass `LOOT_RADIUS` into script move
  target checks.
- Grind-mode default loot-radius ownership now lives in
  `AgentGrindModeRuntime.tickGrindMode(...)`. BotManager no longer passes
  `LOOT_RADIUS` into live grind-mode tick dispatch.
- Script-task tick default stop-distance ownership now lives in
  `AgentScriptTaskRuntime.tick(entry)`. BotManager no longer passes
  `BotMovementManager` stop-distance config into common tick or tick-core
  script-task callbacks.
- Tick-core default movement distance config ownership now lives in
  `AgentTickCoreRuntime.tickCore(...)`. BotManager no longer passes teleport,
  out-of-bounds teleport, grind-party teleport multiplier, or follow distance
  into tick-core wiring.
- The former `shouldOfferTownForAwayCommand` compatibility API was removed;
  Agent session control now owns the town-warp prompt decision directly.
- Tick-core callback wiring now points directly at Agent runtime modules for
  leader lookup, leader safety, map grounding, standalone move target, death
  handling, target snapshots, movement core, and anchored farm dispatch.
  BotManager no longer keeps private forwarding wrappers for those runtime
  seams, and an unused private character-id registry lookup was removed.
- Movement-only default config ownership now lives in
  `AgentMovementOnlyStepRuntime.stepMovementOnly(entry, tickAtMs)` and
  `stepMovementOnly(entry, target, runAiTick)`. BotManager no longer builds
  `MovementOnlyStepConfig`; explicit config overloads remain for focused
  parity tests and future profile/config migration.
- The unused BotManager `tickStuckDetection` compatibility wrapper was removed
  after movement-core dispatch moved to `AgentMovementTickRuntime`. Stuck
  detection still runs through `AgentStuckDetectionRuntime` from Agent movement
  runtime, preserving behavior without a BotManager helper.
- Inactive-leader town-return timeout ownership now lives in
  `server.agents.runtime.AgentLeaderSafetyRuntime.handleInactiveLeaderTick(entry, agent, leader, nowMs, leaderId)`.
  BotManager no longer passes `OWNER_INACTIVE_TOWN_RETURN_MS`; it only keeps a
  compatibility callback into the Agent runtime leader-safety entry.
- Tick-failure default hook wiring now lives in
  `server.agents.runtime.AgentTickFailureRuntime.handleFailure(entry, leaderId, agentId, failure)`.
  BotManager no longer owns the private tick-failure callback or supplies the
  stop-mode callback; Agent runtime owns logger and forced-idle hook wiring.
- Standalone move-target config-bound tick entry now lives in
  `server.agents.runtime.AgentStandaloneMoveTargetRuntime.tickStandaloneMoveTarget(entry, agent, runAiTick)`.
  BotManager no longer passes unstuck/stop-distance config directly for the
  ownerless move-target path.
- Anchored-farm config-bound tick entry now lives in
  `server.agents.runtime.AgentAnchoredFarmRuntime.tickAnchoredFarm(entry, agent, pos, runAiTick)`.
  BotManager no longer passes unstuck/stop-distance config directly for
  anchored-farm mode dispatch.
- Movement-core config-bound entry now lives in
  `server.agents.runtime.AgentMovementTickRuntime.stepMovementCore(entry, target, runAiTick)`.
  BotManager no longer passes unstuck/stop-distance config directly for its
  movement-core compatibility wrapper.
- Ownerless movement-only tick preparation now lives in
  `server.agents.runtime.AgentMovementOnlyStepRuntime`. BotManager delegates
  the compatibility step methods while Agent runtime owns the bot-present
  guard, cadence preparation, target snapshot capture, observed-leader motion
  update, remembered leader position update, follow-anchor resolver wiring, and
  movement-only config handoff.
- Dead-state tick wiring now lives in `server.agents.runtime.AgentDeathTickRuntime`.
  BotManager delegates the compatibility dead-tick method while Agent runtime
  owns the dead-state entry, respawn action, and current-time hook composition
  over `AgentDeathTickService`.
- Tick leader/session lookup wiring now lives in
  `server.agents.runtime.AgentLeaderSessionRuntime`. BotManager delegates the
  compatibility tick-owner method while Agent runtime owns the Cosmic server
  lookup callback over the existing cached-leader resolution rule.
- Follow-anchor and target snapshot runtime wiring now lives in
  `server.agents.runtime.AgentTargetSnapshotRuntime`. BotManager delegates the
  compatibility methods while Agent runtime owns sibling lookup, formation
  lookup, default formation selection, and the legacy platform-edge inset used
  by follow target resolution.
- The spawn result contract is now the Agent-owned
  `AgentLifecycleService.AgentSpawnResult`. BotManager no longer defines a
  duplicate `SpawnResult` wrapper; spawn command, party invite, and messenger
  invite callers consume the Agent lifecycle result directly.
- Manual and spawned registration compatibility entry points now live in
  `server.agents.runtime.AgentRegistrationRuntime` as `registerManualAgent`
  and `registerSpawnedAgent`. BotManager no longer owns the private
  normalize-spawn-state branch; it only delegates legacy public registration
  methods to Agent runtime with the temporary tick callback.
- Spawn and relogin registration-callback construction now lives in
  `server.agents.runtime.AgentSpawnRuntime` and
  `server.agents.runtime.AgentReloginRuntime`. BotManager supplies only the
  temporary tick callback, follow-start callback for spawn, and logger while
  Agent runtime owns the register-spawned hook composition over
  `AgentRegistrationRuntime`.
- Anchored farm hook construction now lives in
  `server.agents.runtime.AgentAnchoredFarmRuntime`. BotManager keeps only
  legacy movement config values for this mode until config ownership moves.
- Grind navigation hook construction now lives in
  `server.agents.runtime.AgentGrindNavigationRuntime`. BotManager keeps
  compatibility wrappers for legacy tests/callers, but no longer assembles the
  navigation callback bundle directly; local-opportunity attack now calls the
  Agent runtime helper instead of calling back into BotManager.
- Grind fallback, patrol-wander, and convenient-loot target hook construction
  now lives in `server.agents.runtime.AgentGrindTargetRuntime`. BotManager keeps
  compatibility wrappers for legacy tests/callers, but production grind-mode
  fallback/tail hooks now call the Agent runtime bridge directly.
- Grind combat helper hook construction for priority ranged target selection
  and AoE repositioning now lives in
  `server.agents.runtime.AgentGrindCombatRuntime`. BotManager keeps
  compatibility wrappers for legacy tests/callers, but production grind
  commitment/engagement hooks now call Agent runtime helpers directly.
- Local-opportunity attack hook construction now lives in
  `server.agents.runtime.AgentLocalOpportunityAttackRuntime`. Its temporary
  grind navigation dependency now points at `AgentGrindNavigationRuntime`.
- Local attack move-window config-bound operations now live in
  `server.agents.runtime.AgentLocalAttackMoveWindowRuntime`. BotManager no
  longer owns the follow/script move-window settled callbacks; production live
  tick context and scripted move combat hooks call the Agent runtime bridge.
- Common tick hook construction now lives in
  `server.agents.runtime.AgentCommonTickRuntime`. BotManager should only pass
  the temporary script-task tick callback until script tasks are reconstructed;
  production live tick gates now call the Agent common tick runtime directly.
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
  reconstructed out of BotManager. The live tick gate now calls
  `AgentMapTransitionRuntime.handleTrackedMapChange` directly instead of a
  BotManager wrapper.
- Recovery teleport hook construction now lives in
  `server.agents.runtime.AgentRecoveryTeleportRuntime`. BotManager should pass
  only legacy distance thresholds until movement recovery configuration is
  moved into Agent-owned runtime config.
- Follow map-sync hook construction now lives in
  `server.agents.runtime.AgentFollowMapSyncRuntime`. The temporary public
  `BotMovementManager.resetEntryState` bridge exists only until movement state
  reset behavior moves fully into Agent movement modules.
- Follow idle movement fast-path config-bound wiring now lives in
  `server.agents.runtime.AgentFollowIdleMovementRuntime`. BotManager keeps only
  a compatibility wrapper for legacy tests/callers; production live-mode hooks
  call the Agent runtime bridge directly.
- Follow-target candidate lookup, target resolution, and follow-target chat
  command execution now live in `server.agents.runtime.AgentFollowTargetRuntime`.
  BotManager chat route hooks no longer own the candidate/resolve/apply wrappers;
  the Agent runtime bridge preserves the legacy reply list, delay, auto-equip,
  potion-share check, and follow-start order.
- Legacy BotManager random reply and delay utility wrappers were removed after
  follow-target routing moved to Agent runtime. Agent modules now call
  `AgentDialogueSelector` and `AgentRandom` directly.
- Idle/trade physics hook construction now lives in
  `server.agents.runtime.AgentIdlePhysicsRuntime`. The temporary public
  `BotPhysicsEngine.resolveIdleGroundStance` accessor exists only until physics
  internals move into Agent movement/physics modules. BotManager no longer keeps
  local `tickTradePhysicsOnly` or `tickIdleEntry` wrappers; live tick gates call
  the Agent idle physics runtime directly.
- Attack-lock physics hook construction now lives in
  `server.agents.runtime.AgentActionLockPhysicsRuntime`. Keep this as the
  temporary bridge for attack-cooldown movement dispatch until movement is
  reconstructed out of BotMovementManager.
- Movement phase hook construction now lives in
  `server.agents.runtime.AgentMovementPhaseRuntime`. The temporary public
  BotMovementManager phase methods exist only until movement internals are
  reconstructed into Agent movement modules. BotManager no longer keeps a dead
  movement-phase wrapper; movement-core runtime owns the call.
- Movement stuck-detection hook construction now lives in
  `server.agents.runtime.AgentStuckDetectionRuntime`. Keep temporary
  `BotMovementManager` and `BotPhysicsEngine` calls isolated there until the
  movement bin is reconstructed; BotManager should pass only the legacy enable
  flag.
- Death respawn hook construction now lives in
  `server.agents.runtime.AgentRespawnRuntime`. Keep the temporary direct
  dependencies on `BotPhysicsEngine` and `BotMovementManager` there until the
  movement/physics bins are reconstructed; `BotManager` now calls the Agent
  respawn runtime directly from death-tick handling.
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
  BotManager supplies only the temporary tick callback, follow-start callback,
  and logger while Agent runtime owns spawned-registration callback
  construction, ownership resolution, spawn position resolution, offline load
  delegation, online placement, cross-map force-change handling, and
  SQL-failure warning wiring.
- Relogin hook wiring now lives in `server.agents.runtime.AgentReloginRuntime`.
  BotManager supplies only the temporary tick callback and logger while Agent
  runtime owns spawned-registration callback construction, leader lookup, spawn
  position resolution, offline load delegation, delayed scheduling, return
  announcement delivery, delay selection, and SQL-failure warning wiring.
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
- AgentEquipHandler active-Agent count and character-list lookup now reads
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
  directly for follow-owner, stop, move-to, farm-here, grind, and patrol,
  including patrol graph-region validation and the visible failure reply.
- Build, potion, and combat ammo fallback paths now request follow-owner via
  `AgentMovementCommandRuntime` instead of direct BotManager calls. The same
  follow-owner mode transition and visible dialogue behavior are preserved.
- Session relog/logout/away prompts and equipment unequip-all now request stop
  via `AgentMovementCommandRuntime` instead of direct BotManager calls. The
  same pending-action, reply timing, and equipment side effects are preserved.
- Patrol command graph-region validation, visible failure reply, and mode-state
  transition now live in `AgentMovementCommandRuntime`; the former
  `BotManager.issuePatrol` compatibility delegate has been removed.
- Session first-agent checks, away-town offer checks, and away-safe command
  routing now enter through `AgentSessionControlRuntime`; actual away-safe
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
- BotManager follow-owner, grind, and stop compatibility hooks were removed.
  Spawn/lifecycle/tick call sites now use `AgentMovementCommandRuntime`
  directly; command preparation, script-task clearing, shop cancellation,
  mode transitions, and navigation clearing remain behavior-equivalent.
- Return-scroll use for inactive leader safety now lives in
  `AgentReturnScrollService`; BotManager only calls the Agent runtime helper
  from the existing leader-safety callback, preserving 2030000 lookup, effect
  application, and one-scroll consumption behavior.
- Swim-map classification now lives in `AgentMapEnvironmentService`; BotManager
  movement, idle-physics, and action-lock paths call the Agent runtime helper
  while preserving the same null-map and `MapleMap.isSwim()` behavior.
- Grind-loot retry suppression now uses
  `AgentGrindLootStateRuntime::isRetrySuppressed` directly; BotManager no
  longer owns the pass-through predicate used by Agent loot targeting.
- Script-driven item dropping now lives in `AgentScriptItemActionService`;
  the former BotManager `issueDropItem` compatibility wrapper was removed,
  and `AgentScriptRunner` calls the Agent service directly for script
  contexts.
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
  `AgentScriptMoveTargetService`; the former BotManager
  `isCheapScriptMoveTarget` compatibility delegate was removed, and
  `AgentScriptRunner` uses the Agent-owned runtime bridge directly.
- General runtime config now lives in `AgentRuntimeConfig`; `BotManager.cfg`
  remains a compatibility alias to the same mutable config object, while Agent
  reporting, shop, supplies, inventory adapters, and script move checks read
  config through the Agent-owned runtime config.
- Live formation storage now lives in `AgentFormationService`; BotManager still
  routes formation commands and lifecycle cleanup through compatibility calls,
  but no longer owns the in-memory leader-to-formation map.
- Follow-target position resolution now lives in
  `AgentFollowTargetPositionService`, and `AgentMovementTargetSideEffects`
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
  to `server.agents.integration.AgentInventoryRuntimeAdapters`. The bot
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
  `AgentChatReportRuntime`; `BotChatReportRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Status helper orchestration now has an Agent-owned facade in
  `AgentChatStatusRuntime`; `BotChatStatusRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Build/AP/SP/job callback orchestration now has an Agent-owned facade in
  `AgentBuildRuntime`; `BotChatBuildRuntime` remains only as a temporary
  compatibility shim for legacy bot package callers.
- Pending chat-action state, callbacks, and skill-report decision application
  now have an Agent-owned facade in `AgentPendingActionRuntime`;
  `BotChatPendingActionRuntime` remains only as a temporary compatibility shim
  for legacy bot package callers.
- Utility chat callbacks now have an Agent-owned facade in
  `AgentUtilityRuntime`; `BotChatUtilityRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Social/fame chat callbacks now have an Agent-owned facade in
  `AgentSocialRuntime`; `BotChatSocialRuntime` remains only as a temporary
  compatibility shim for legacy bot package callers.
- Transfer/item-query chat callbacks and async transfer result routing now have
  an Agent-owned facade in `AgentTransferRuntime`; `BotChatTransferRuntime`
  remains only as a temporary compatibility shim for legacy bot package callers.
- Supply request callbacks and request-upgrade supply routing now have an
  Agent-owned facade in `AgentSupplyRuntime`; `BotChatSupplyRuntime` remains
  only as a temporary compatibility shim for legacy bot package callers.
- Session/relog/logout/away callback orchestration now has an Agent-owned
  facade in `AgentSessionRuntime`; `BotChatSessionRuntime` remains only as a
  temporary compatibility shim for legacy bot package callers.
- Movement/follow/grind/stop/fidget/greeting callback orchestration now has an
  Agent-owned facade in `AgentMovementRuntime`; `BotChatMovementRuntime`
  remains only as a temporary compatibility shim for legacy bot package callers.
- Follow/stop/move/farm/patrol/grind command dispatch now has an Agent-owned
  facade in `AgentMovementCommandRuntime`; BotManager no longer exposes
  temporary movement-command wrapper methods.
- Read-only movement state snapshots now have Agent-owned types in
  `AgentMovementSnapshot`/`AgentMovementMode` and an integration facade in
  `AgentMovementStateRuntime`; `BotEntry` remains the temporary state source.
- Read-only target/formation snapshots now have an Agent-owned type in
  `AgentMovementTargetSnapshot` and an integration facade in
  `AgentMovementTargetRuntime`; `BotManager.TargetSnapshot` remains the
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
  `AgentChatOrchestratorContext`; the old bot-side context class has been
  removed, and `BotManager` now creates the Agent integration adapter directly.
- Unused bot-side chat compatibility shims for build, control, equipment,
  movement, pending-action, social, transfer, and utility callbacks have been
  removed after the Agent orchestrator context switched directly to Agent
  integration facades.
- The bot-side chat report compatibility shim has been removed; remaining report
  tests and production callers use `AgentChatReportRuntime` directly.
- The bot-side chat status compatibility shim has been removed; production
  lifecycle, fidget, offer, build, starter-kit, and tests now call
  `AgentChatStatusRuntime` directly.
- Unused bot-side chat session and supply compatibility shims have been
  removed; session and supply chat orchestration is reached through Agent
  integration facades.
- `BotChatRuntime` and `BotChatManager` have been removed; `BotManager` now
  delegates directly to `AgentChatRuntime` with
  `AgentChatOrchestratorContext`.
- Immediate Agent integration reply delivery now routes through
  `AgentReplyRuntime.replyNow`, `visibleSayNow`, and `sayPartyNow`; scattered
  Agent integration facades no longer call `BotManager.botReply`/visible
  delivery directly. `AgentReplyRuntime` remains the temporary adapter to the
  legacy BotManager packet-delivery methods.
- Loot/gear offer owner-directed replies, queued offer prompts, estimated prompt
  delay reads, and delayed offer actions now enter through
  `AgentOfferRuntime`; `AgentOfferService` no longer reaches directly into the
  lower-level reply or scheduler runtime for offer-owned flows. The remaining
  bot-side map-only `botSay(Character, ...)` branch is intentionally unchanged
  until map-only visible delivery has an exact Agent adapter.
- AP build confirmation replies now enter through `AgentBuildRuntime`; the
  build manager no longer reaches directly into the lower-level reply runtime
  for AP-build selection confirmation, but it still owns the legacy AP
  assignment behavior for this reconstruction stage.
- Maker batch command replies and delayed batch steps now enter through
  `AgentMakerRuntime`; `AgentMakerService` no longer reaches directly into the
  lower-level reply or scheduler runtime for Maker-owned flows. It still lazily
  resolves `ItemInformationProvider` so guard paths and Agent adapter tests do
  not initialize database-backed item data before it is needed.
- Scroll-reaction jitter delays and queued reaction chat now enter through
  `AgentScrollReactionRuntime`; `BotScrollReactionManager` no longer reaches
  directly into the lower-level reply or scheduler runtime for scroll-reaction
  owned flows.
- KPQ Stage 1 progress/pass dialogue and Stage 5 reward dialogue now enter
  through `AgentPqRuntime`; the KPQ script classes no longer reach directly
  into the lower-level reply runtime for party-quest-owned dialogue.
- Sell-trash shop owner-directed replies and delayed shop step callbacks now
  enter through `AgentShopRuntime`; `AgentShopService` no longer reaches
  directly into the lower-level reply or scheduler runtime for shop-owned
  flows. Map-only resupply/shop chatter remains on the legacy visible-say path
  until exact map-visible delivery has an Agent adapter.
- Ammo-share donor selection delays and delayed transfer callbacks now enter
  through `AgentAmmoRuntime`; `AgentAmmoService` no longer reaches directly
  into the lower-level scheduler runtime for ammo-owned timing. Visible ammo
  request/offer chat remains unchanged on the legacy map-visible say path.
- Potion-share donor selection delays, low-supply fallback delay, and delayed
  transfer callbacks now enter through `AgentPotionRuntime`;
  `AgentPotionService` no longer reaches directly into the lower-level scheduler
  runtime for potion-owned timing. Visible potion request/offer chat remains
  unchanged on the legacy map-visible say path.
- Bot physics identity reads now enter through `AgentRuntimeIdentityRuntime`;
  `BotPhysicsEngine` no longer reads `entry.bot` directly for swim motion,
  stance resolution, movement snapshots, or character-state synchronization,
  while the same BotEntry-backed character reference and movement behavior are
  preserved.
- Bot physics stance and movement-snapshot reads now enter through
  `AgentMovementStateRuntime`, `AgentClimbStateRuntime`, and
  `AgentSwimStateRuntime`; `BotPhysicsEngine` still owns the legacy physics
  calculations, but stance selection no longer directly reads BotEntry movement,
  climb, or swim fields.
- Bot physics movement-profile reads now enter through
  `AgentMovementStateRuntime.movementProfile`; jump, rope-jump, swim-burst,
  landing-speed, and ground-motion calculations still use the same non-null
  BotEntry-backed profile semantics.
- Bot physics coordinate and horizontal-speed helper access now enters through
  `AgentMovementPhysicsStateRuntime`; ground-position sync, stop-ground
  motion, and rounded airborne position reads no longer touch `physX`,
  `physY`, or `hspeed` directly.
- Bot physics movement-packet velocity writes now enter through
  `AgentMovementStateRuntime.setMovementVelocity`; the legacy behavior that
  non-zero horizontal velocity also updates facing direction is preserved behind
  the Agent movement-state boundary.
- Bot physics top-rope entry intent now enters through
  `AgentClimbStateRuntime`; queue, consume, and clear operations no longer
  access `ropeEntryPending`, `ropeEntryRope`, or `ropeEntryY` directly.
- Bot physics down-jump and crouch state now enters through
  `AgentMovementStateRuntime`; prone, queued down-jump, down-jump failure,
  grace-period timer, landing, swim, airborne, climb, and reset paths no longer
  write `downJumpPending`, `downJumpGracePeriodMS`, or `crouching` directly.
- Bot physics blocked-rope-grab state now enters through
  `AgentClimbStateRuntime`; jump, fall, knockback, landing, and reset paths
  no longer write `blockedRopeGrab` directly.
- Bot physics climb attachment and climb-direction state now enters through
  `AgentClimbStateRuntime`; idle, airborne, landing, climb, collision, and
  reset paths no longer directly read or write `climbing`, `climbRope`, or
  `climbVerticalDir`.
- Bot physics airborne/grounded state now enters through
  `AgentMovementStateRuntime.setInAir`; movement transitions no longer write
  the `inAir` field directly.
- Bot physics swim mode and swim intent state now enters through
  `AgentSwimStateRuntime`; water jump launch, swim integration, landing
  handoff, swim input reads, swim-jump consumption, swim cooldown, and swim
  facing updates no longer access swim fields directly in `BotPhysicsEngine`.
- Bot physics fixed-air-arc state now enters through
  `AgentMovementPhysicsStateRuntime.setFixedAirArc`; grounded, swim,
  landing, collision, airborne launch, climb, and reset paths no longer clear
  `fixedAirArc` directly.
- Bot physics movement-direction intent now enters through
  `AgentMovementStateRuntime`; ground motion, air steering, airborne launch,
  idle, and reset paths no longer read or clear `moveDir` directly.
- Bot physics facing-direction preservation and air-steering facing updates now
  enter through `AgentMovementStateRuntime`; knockback and airborne steering
  paths no longer access `facingDir` directly.
- Bot physics vertical velocity now enters through
  `AgentMovementPhysicsStateRuntime`; jump launch, swim integration,
  airborne integration, collision, climb, landing, and reset paths no longer
  access `velY` directly.
- Bot physics committed air velocity and air-steering velocity now enter
  through `AgentMovementPhysicsStateRuntime`; swim entry, knockback,
  airborne launch, air steering, collision, climb, and reset paths no longer
  access `airVelX` or `airSteerVelX` directly.
- Bot physics climb-up intent now enters through `AgentClimbStateRuntime`;
  idle, swim launch, knockback, landing, ground motion, airborne launch, climb,
  and reset paths no longer access `climbUpIntent` directly.
- Bot physics fall-peak tracking now enters through
  `AgentMovementPhysicsStateRuntime`; fall-distance calculation, airborne
  peak recording, and landing reset no longer access `fallPeakPhysY` directly.
- Bot physics reset-only movement and rope cooldown flags now enter through
  `AgentMovementStateRuntime` and `AgentClimbStateRuntime`; full motion
  reset no longer writes `wasMovingX` or `ropeGrabCooldownMs` directly.
- Combat alert reset callbacks now enter through `AgentCombatRuntime`;
  `BotCombatManager` no longer reaches directly into the lower-level scheduler
  runtime for combat-owned alert timing, and alert timing and stance reset
  behavior are unchanged.
- Inventory, trade, meso-transfer, and drop owner-directed replies now route
  through `AgentReplyRuntime`; delayed trade thanks/freebie callbacks now use
  `AgentSchedulerRuntime` while preserving the legacy visible `botSay`
  delivery and random reply pools.
- LLM split-message owner-directed replies now enter through
  `AgentLlmRuntime`; `BotLlmReplyManager` no longer reaches directly into
  the lower-level reply runtime, and the existing LLM executor,
  multi-message delay, and sanitization/splitting behavior are unchanged.
- Bot dismissal acknowledgement scheduling now routes through
  `AgentSchedulerRuntime`; delivery intentionally remains on the local
  `BotManager.botReply` method because `AgentReplyRuntime` currently bridges
  back to that delivery method.
- Remaining BotManager fire-and-forget callback scheduling for follow-target
  activation, spawn status checks, recruit greetings, owner pickup scans, scroll
  reactions, and relog greetings now routes through `AgentSchedulerRuntime`.
- Immediate Agent reply delivery now lives in `AgentReplyRuntime` instead of
  bridging back through `BotManager.botReply`, `botVisibleSay`, or
  `botSayParty`. `BotManager` keeps those methods as compatibility wrappers,
  and chat text sanitization now lives in `AgentChatTextSanitizer`.
- BotManager's remaining internal owner-directed reply sites now call
  `AgentReplyRuntime.replyNow` directly; `BotManager.botReply` remains only a
  compatibility wrapper for callers that have not been migrated yet.
- `AgentSchedulerRuntime` now schedules directly through `TimerManager`; the
  legacy `BotManager.scheduleBotReplyAction`/`after` bridge has been removed.
- Agent integration tests for build, session, pending-action, transfer, social,
  and combat delayed callbacks now assert the Agent reply/scheduler runtime
  boundary instead of the removed BotManager delivery/scheduler bridge.
- Build-triggered status checks now enter through
  `AgentBuildStatusRuntime.checkBuildStatus`; `AgentBuildService` and
  `AgentStarterKitService` no longer call the broad chat-status facade directly
  for job/level build status prompts.
- Gear-offer idle gating now enters through
  `AgentOfferRuntime.isOwnerIdleForOffer`; `AgentOfferService` no longer
  reaches directly into the broad chat-status facade for offer prompt checks.
- Fidget idle gating now enters through
  `AgentFidgetRuntime.isLeaderIdleForFidget`; `AgentFidgetService` no longer
  reaches directly into the broad chat-status facade for fidget eligibility.
- Movement-triggered active-mode preparation, post-movement status checks, and
  random fidget expressions now enter through `AgentMovementStatusRuntime`;
  `AgentMovementRuntime` no longer reaches directly into the broad
  chat-status facade for those movement callbacks.
- BotManager-triggered spawn status checks, map-change status checks,
  shop-transition status checks, offline-return announcements, and AFK ticks
  now enter through `AgentManagerStatusRuntime`; `BotManager` no longer
  reaches directly into the broad chat-status facade for those lifecycle/tick
  callbacks.
- Bot performance-monitor diagnostics now label the common AFK check as
  `AgentManagerStatusRuntime.tickAfkCheck`, matching the Agent-owned
  BotManager status boundary used by the tick shell.
- BotManager-triggered queue/reply/map/party delivery now calls
  `AgentReplyRuntime` directly; the pure manager reply pass-through adapter
  was removed while preserving internal command/error/formation replies and
  compatibility delivery wrappers.
- BotManager-triggered delayed callbacks now call `AgentSchedulerRuntime`
  directly; the pure manager scheduler pass-through was removed for
  follow-target, dismiss, ownership-transfer, pickup-scan, scroll-reaction, and
  relog-greeting delays.
- Inventory/trade/drop/meso reply delivery and trade-thanks delayed callbacks
  now enter through `AgentInventoryRuntime`; `BotInventoryManager` no longer
  reaches directly into the lower-level reply or scheduler runtime for those
  inventory-owned flows.
- Recommended-gear prompt reservation state now enters through
  `AgentOfferRuntime`; `AgentOfferService` no longer reads or clears the
  `pendingGearPromptAt` entry field directly, while the same legacy field and
  timing semantics remain intact behind the Agent-owned offer boundary.
- Agent reply queue ownership now uses narrow queue operations on
  `AgentReplyQueue.State` and `AgentMessageQueueStateRuntime`; production
  reply draining no longer depends on direct `Deque` access while the same
  BotEntry-backed queue, synchronization lock, spacing estimate, and dispatch
  behavior remain in place.
- Equipment optimizer debug/dump range report formatting now enters through
  `AgentRangeReportService`; equipment code no longer imports an integration
  bridge or the broad chat-report facade just to render range text, and the
  underlying range formatter remains unchanged.
- The old control report pass-through bridge was removed.
  `AgentControlRuntime` still owns the same 500-700 ms command delay, but
  now calls the existing chat-report facade directly for buff-debug and
  skill-buff-debug reports.
- Offer-manager map-visible rejection replies and bot-to-bot loot-offer accept
  replies now enter through `AgentOfferRuntime`; `AgentOfferService` no longer
  calls `BotManager.botSay` directly for offer-owned reply delivery, while the
  same reply channel, random reply pool, and delay behavior remain intact.
- Ammo low-supply request and ammo-donor offer visible replies now enter
  through `AgentAmmoRuntime`; `AgentAmmoService` no longer calls
  `BotManager.botSay` directly for ammo-owned reply delivery, while the same
  random reply pools and transfer timing remain intact.
- Potion grind-stop warnings, low-supply requests, no-qualified-donor
  deflections, and donor offer visible replies now enter through
  `AgentPotionRuntime`; `AgentPotionService` no longer calls
  `BotManager.botSay` directly for potion-owned reply delivery, while the same
  text, random reply pools, and transfer timing remain intact.
- Equipment auto-equip clutter warnings now enter through
  `AgentEquipmentRuntime`; `BotEquipManager` no longer calls
  `BotManager.botSay` directly for equipment-owned reply delivery, and the
  legacy try/catch still prevents chat failures from blocking equip passes.
- Inventory trade-thanks and freebie quip callbacks now enter through
  `AgentInventoryRuntime`; `BotInventoryManager` no longer calls
  `BotManager.botSay` directly for inventory-owned delayed trade reply
  delivery, while the same reply channel and random reply pools remain intact.
- Combat death, missing-MP-potion, low-ammo, and out-of-ammo visible replies
  now enter through `AgentCombatRuntime`; `BotCombatManager` no longer calls
  `BotManager.botSay` directly for combat-owned reply delivery, while the same
  random reply pools, follow-owner fallback, and warning flags remain intact.
- Shop resupply, approach-timeout, shopping, purchase summary, shortfall,
  sell-trash, and abort visible replies now enter through `AgentShopRuntime`;
  `AgentShopService` no longer calls `BotManager.botSay` directly for shop-owned
  reply delivery, while the same text, random reply pools, and delayed shop
  sequencing remain intact.
- The unused `BotManager.botVisibleSay` and `BotManager.botReply`
  compatibility shims were removed after all production and test callers moved
  to Agent reply runtimes; `botSay(...)` and `botSayParty(...)` remain for
  legacy channel delivery compatibility.
- The old report reply and delivery pass-through bridges were removed.
  `AgentChatReportRuntime` now queues report lines directly through the
  existing reply runtime while preserving the same queued owner-directed reply
  behavior.
- The old report scheduler pass-through bridge was removed.
  `AgentChatReportRuntime` now supplies the existing random-delay scheduler
  directly when constructing report callbacks, preserving the same delay
  behavior.
- Report operation callback wiring now lives directly in
  `AgentChatReportRuntime`; the standalone report-operations bridge was
  removed while preserving the same help/request-upgrade/report dispatch table.
- Status reply and scheduler pass-through adapters were removed.
  AFK-return and offline-return actions now call the existing reply and
  random-delay scheduler runtimes directly while preserving the same status
  side effects and delay windows.
- Session reply and scheduler pass-through adapters were removed.
  Relog/logout/away prompts, confirmations, and delayed lifecycle callbacks now
  call the existing reply and random-delay scheduler runtimes directly while
  preserving the same session-owned chat timing.
- Pending-action reply and scheduler pass-through adapters were removed.
  Item choices, cancel replies, and skill-tree reply queueing now call the
  existing reply and random-delay scheduler runtimes directly while preserving
  the same pending-action behavior.
- Control reply and scheduler pass-through adapters were removed.
  `AgentControlRuntime` now calls the existing reply and random-delay
  scheduler runtimes directly while preserving the same toggle, buff-query, and
  respec timings and replies.
- Equipment visible replies, unequip, unequip-all, auto-equip-debug, and
  auto-equip callbacks now call the existing `AgentReplyRuntime` and
  `AgentSchedulerRuntime` directly; the pure equipment reply/scheduler
  pass-through adapters were removed while preserving equipment behavior.
- Inventory, trade, drop, and meso reply/timing bridge methods now call the
  existing `AgentReplyRuntime` and `AgentSchedulerRuntime` directly; the
  pure inventory reply/scheduler pass-through adapters were removed while
  preserving inventory behavior.
- Combat warning/status reply and delay bridge methods now call the existing
  `AgentReplyRuntime` and `AgentSchedulerRuntime` directly; the pure
  combat reply/scheduler pass-through adapters were removed while preserving
  combat behavior.
- Ammo-sharing reply, delay, random-delay, and delay-sampling bridge methods
  now call the existing `AgentReplyRuntime` and `AgentSchedulerRuntime`
  directly; the pure ammo reply/scheduler pass-through adapters were removed
  while preserving ammo-sharing behavior.
- Potion-sharing reply, delay, random-delay, and delay-sampling bridge methods
  now call the existing `AgentReplyRuntime` and `AgentSchedulerRuntime`
  directly; the pure potion reply/scheduler pass-through adapters were removed
  while preserving potion-sharing behavior.
- Maker automation reply, delay, and random-delay bridge methods now call the
  existing `AgentReplyRuntime` and `AgentSchedulerRuntime` directly; the
  pure Maker reply/scheduler pass-through adapters were removed while
  preserving Maker behavior.
- Shop automation owner replies, map-visible replies, fixed-delay callbacks,
  and delay-sampling bridge methods now call the existing
  `AgentReplyRuntime` and `AgentSchedulerRuntime` directly; the pure shop
  reply/scheduler pass-through adapters were removed while preserving shop
  behavior.
- LLM dialogue replies now call the existing `AgentReplyRuntime` directly;
  the pure LLM reply pass-through adapter was removed while preserving LLM
  reply delivery behavior.
- PQ queued dialogue now calls the existing `AgentReplyRuntime` directly;
  the pure PQ reply pass-through adapter was removed while preserving PQ
  dialogue behavior.
- Scroll-reaction queued dialogue, fixed-delay callbacks, and delay-sampling
  bridge methods now call the existing `AgentReplyRuntime` and
  `AgentSchedulerRuntime` directly; the pure scroll-reaction reply/scheduler
  pass-through adapters were removed while preserving scroll-reaction behavior.
- Supply reply and scheduler pass-through adapters were removed.
  Supply request queued replies and random-delay callbacks now call the existing
  reply and random-delay scheduler runtimes directly while preserving the same
  supply behavior.
- Social reply and scheduler pass-through adapters were removed.
  Fame replies and random-delay callbacks now call the existing reply and
  scheduler runtimes directly while preserving the same social behavior.
- Utility reply and scheduler pass-through adapters were removed.
  Trade-invite replies and trade/shop/Maker random-delay callbacks now call the
  existing reply and random-delay scheduler runtimes directly while preserving
  the same utility behavior.
- Build/AP/SP/job-advance immediate replies, queued build-status replies, and
  job-advance random-delay callbacks now call the existing
  `AgentReplyRuntime` and `AgentSchedulerRuntime` directly; the pure
  build reply/scheduler pass-through adapters were removed while preserving
  build behavior.
- Gear/loot offer immediate replies, queued replies, map/channel dialogue,
  queued-say delay estimation, fixed-delay callbacks, random-delay callbacks,
  and delay sampling now call the existing `AgentReplyRuntime` and
  `AgentSchedulerRuntime` directly; the pure offer reply/scheduler
  pass-through adapters were removed while preserving offer behavior.
- Transfer reply and scheduler pass-through adapters were removed.
  Transfer/item-query immediate replies, fixed-delay callbacks, random-delay
  callbacks, and delay sampling now call the existing reply and scheduler
  runtimes directly while preserving the same transfer behavior.
- Manager spawn-status delayed callbacks now call `AgentSchedulerRuntime`
  directly; the pure manager scheduler pass-through was removed while
  preserving status-check timing.
- Movement/follow/grind/stop/fidget/greeting immediate replies, queued replies,
  and random-delay callbacks now call the existing `AgentReplyRuntime` and
  `AgentSchedulerRuntime` directly; the pure movement reply/scheduler
  pass-through adapters were removed while preserving movement chat behavior.
- Gear-prompt reservation state now enters through the narrow
  `AgentOfferStateRuntime` adapter; offer scheduling keeps BotEntry as the
  temporary backing store but no longer owns the pending gear prompt field
  directly in offer orchestration.
- Pending action and pending drop-choice state now enter through the narrow
  `AgentPendingActionStateRuntime` adapter; chat, transfer, manager cleanup,
  and offer orchestration keep BotEntry as the temporary backing store but no
  longer read or clear those fields directly.
- AP-build prompt state and SP-variant prompt state now enter through the
  narrow `AgentBuildStateRuntime` adapter; build orchestration keeps BotEntry
  as the temporary backing store but no longer reads or mutates AP/SP prompt
  fields directly.
- Chat message queue and message-sending state now enter through the narrow
  `AgentMessageQueueStateRuntime` adapter; reply queue orchestration and
  scroll-reaction readiness checks keep BotEntry as the temporary backing store
  but no longer read queue fields directly.
- Scroll reaction cooldown, load, and per-scroller streak state now enter
  through the narrow `AgentScrollReactionStateRuntime` adapter; scroll
  reaction orchestration keeps BotEntry as the temporary backing store but no
  longer reads or mutates those fields directly.
- Owner activity and AFK/welcome-back state now enter through the narrow
  `AgentActivityStateRuntime` adapter; status and welcome-back orchestration
  keep BotEntry as the temporary backing store but no longer read or mutate AFK
  fields directly.
- Last matched owner-command state now enters through the narrow
  `AgentActivityStateRuntime` adapter; command handling and LLM situation
  building keep BotEntry as the temporary backing store but no longer read or
  write `lastOwnerCommand` or `lastOwnerCommandAtMs` directly.
- Ammo share request episode state now enters through the narrow
  `AgentAmmoStateRuntime` adapter; ammo sharing keeps BotEntry as the
  temporary backing store but no longer reads or mutates the request flag
  directly.
- HP/MP potion share request episode state now enters through the narrow
  `AgentPotionStateRuntime` adapter; potion sharing keeps BotEntry as the
  temporary backing store but no longer reads or mutates the request flags
  directly.
- Build level-sync and job-prompt milestone state now enter through
  `AgentBuildStateRuntime`; build progression keeps BotEntry as the
  temporary backing store but no longer reads or mutates `lastKnownLevel` or
  `jobPromptSent` directly.
- Consumable buff scan and last-action summary state now enter through
  `AgentBuffStateRuntime`; buff consumable automation keeps BotEntry as the
  temporary backing store but no longer reads or mutates those fields directly.
- Manual trade invite accept-delay, trade reference, and timeout state now enter
  through `AgentManualTradeStateRuntime`; manual trade handling keeps
  BotEntry as the temporary backing store but no longer reads or mutates those
  fields directly.
- Pending trade active/idle guard checks now enter through
  `AgentPendingTradeStateRuntime`; ammo, potion, offer, utility, and
  inventory orchestration keep BotEntry as the temporary backing store but no
  longer scatter direct `pendingTradeCategory` null checks.
- Bot-initiated trade retry callback and delay state now enter through
  `AgentPendingTradeStateRuntime`; loot-offer, potion-share, and ammo-share
  retry scheduling keep BotEntry as the temporary backing store but no longer
  read, write, or clear retry fields directly.
- Potion/ammo share trade quantity budget state now enters through
  `AgentPendingTradeStateRuntime`; trade item quantity capping and trade
  reset keep BotEntry as the temporary backing store but no longer read,
  decrement, or clear the budget field directly.
- Pending trade category message state now enters through
  `AgentPendingTradeStateRuntime`; reserved/equip group trade announcements
  keep BotEntry as the temporary backing store but no longer set, read, or clear
  the message field directly.
- Pending trade recipient id state now enters through
  `AgentPendingTradeStateRuntime`; trade setup, reset, and recipient
  resolution keep BotEntry as the temporary backing store but no longer set,
  read, or clear the recipient id field directly.
- Pending trade invite-announced state now enters through
  `AgentPendingTradeStateRuntime`; trade batch opening and reset keep
  BotEntry as the temporary backing store but no longer read, mark, or clear
  the invitation announcement flag directly.
- Pending trade timer state now enters through
  `AgentPendingTradeStateRuntime`; trade accept, batch pause, item-add, and
  confirmation timeout handling keep BotEntry as the temporary backing store
  but no longer read, increment, tick down, set, or clear the timer field
  directly.
- Pending trade single-batch state now enters through
  `AgentPendingTradeStateRuntime`; trade setup, batch-completion decisions,
  and reset keep BotEntry as the temporary backing store but no longer read,
  set, or clear the single-batch field directly.
- Pending trade meso amount and meso-added state now enter through
  `AgentPendingTradeStateRuntime`; trade setup, meso-add, insufficient-meso
  checks, and reset keep BotEntry as the temporary backing store but no longer
  read, set, mark, or clear meso trade fields directly.
- Pending trade all-items-added and bot-done completion flags now enter through
  `AgentPendingTradeStateRuntime`; trade completion, cancel, timeout, and
  reset handling keep BotEntry as the temporary backing store but no longer
  read, mark, or clear completion fields directly.
- Pending trade item index state now enters through
  `AgentPendingTradeStateRuntime`; trade batch setup, item-add progression,
  and reset keep BotEntry as the temporary backing store but no longer read,
  increment, or clear the item index field directly.
- Pending trade item-list state now enters through
  `AgentPendingTradeStateRuntime`; trade batch setup, between-batch pause,
  item-add progression, and reset keep BotEntry as the temporary backing store
  but no longer set, read, null-check, or clear the batch item list directly.
- Pending trade category state now enters through
  `AgentPendingTradeStateRuntime`; trade setup, group advancement, supply
  share invitation suppression, and reset keep BotEntry as the temporary
  backing store but no longer set, read, compare, or clear the category field
  directly.
- Pending trade temporary equipment restore-slot state now enters through
  `AgentPendingTradeStateRuntime`; trade preparation, trade-window item
  remapping, restore checks, restore snapshots, and cleanup keep BotEntry as the
  temporary backing store but no longer operate on the restore map directly.
- Inventory full-warning cooldown and post-drop loot-inhibit cooldown state now
  enter through `AgentInventoryStateRuntime`; passive loot and drop-choice
  handling keep BotEntry as the temporary backing store but no longer read,
  tick down, or set those cooldown fields directly.
- Potion check and passive MP/HP recovery timer state now enter through
  `AgentPotionStateRuntime`; potion check retry, autopot cadence, and
  passive recovery keep BotEntry as the temporary backing store but no longer
  read, shorten, tick down, clear, or set those timer fields directly.
- Owner-inactive safe-mode state now enters through
  `AgentActivityStateRuntime`; offline/dead owner recovery keeps BotEntry as
  the temporary backing store but no longer reads, starts, clears, or sets the
  inactive timer, town-return flag, or away-safe-mode flag directly.
- Gear-prompt test assertions now read the reserved prompt timestamp through
  `AgentOfferStateRuntime`, keeping tests on the Agent-owned offer state
  boundary instead of the temporary BotEntry backing field.
- Session-request tests now assert pending chat action through
  `AgentPendingActionStateRuntime`, keeping relog/away prompt state checks on
  the Agent-owned pending-action boundary instead of the temporary BotEntry
  backing field.
- Combat attack-lock, post-attack movement-window, and mob-hit invulnerability
  cooldown state now enter through `AgentCombatCooldownStateRuntime`; combat,
  movement, and local attack orchestration keep BotEntry as the temporary backing
  store but no longer read, extend, tick down, clear, or set those cooldown fields
  directly.
- Movement broadcast duplicate-suppression cache state now enters through
  `AgentMovementBroadcastStateRuntime`; movement, combat, airshow, and mode
  reset paths keep BotEntry as the temporary backing store but no longer invalidate,
  compare, or record the last broadcast snapshot fields directly.
- Navigation debug path logger lifecycle and per-tick recording now enter
  through `AgentNavigationDebugStateRuntime`; navigation debug overlay and
  navigation resolution keep BotEntry as the temporary backing store but no
  longer create, clear, or record the path logger field directly.
- Navigation debug decision and edge-block reason state now enter through
  `AgentNavigationDebugStateRuntime`; navigation resolution, idle fast-path
  status, path logging, and focused tests keep BotEntry as the temporary backing
  store but no longer read or write those debug fields directly.
- Navigation graph warmup fallback state now enters through
  `AgentNavigationDebugStateRuntime`; navigation, movement, fallback steering,
  fidget gating, stuck checks, path logging, and focused tests keep BotEntry as
  the temporary backing store but no longer read or write the fallback flag
  directly.
- Navigation waypoint target state now enters through
  `AgentNavigationDebugStateRuntime`; navigation planning, committed-edge
  reuse, movement precision gates, fidget gates, target snapshots, debug overlay,
  path logging, simulation helpers, and focused tests keep BotEntry as the
  temporary backing store but no longer read or write `navTargetPos`,
  `navTargetRegionId`, or `navPreciseTarget` directly.
- Portal-use cooldown state now enters through
  `AgentNavigationDebugStateRuntime`; portal execution keeps BotEntry as the
  temporary backing store but no longer reads or writes `portalUseCooldownUntilMs`
  directly.
- Navigation jump-launch cache state now enters through
  `AgentNavigationDebugStateRuntime`; movement reset and jump waypoint
  selection keep BotEntry as the temporary backing store but no longer read or
  write `navJumpLaunchEdge` or `navJumpLaunchX` directly.
- Movement stuck/unstuck tracking now enters through
  `AgentMovementStuckStateRuntime`; stuck detection, path logging, and
  recovery cooldown setup keep BotEntry as the temporary backing store but no
  longer read or write `stuckMs`, `stuckCheckX`, `stuckCheckY`, or
  `unstuckCooldownMs` directly.
- Tick heartbeat, last-tick metadata, and follow-idle check timing now enter
  through `AgentTickStateRuntime`; BotManager, fidget eligibility, and path
  logging keep BotEntry as the temporary backing store but no longer read or
  write `lastTickWasAi`, `lastTickAtMs`, `lastHeartbeatAtMs`, or
  `nextFollowIdleMovementCheckAtMs` directly in production.
- Owner/leader motion observation now enters through
  `AgentOwnerMotionStateRuntime`; BotManager follow tracking, movement reset,
  and fidget decisions keep BotEntry as the temporary backing store but no
  longer read or write `lastOwnerPos`, `observedOwnerStepX`, or
  `observedOwnerStepY` directly in production.
- Explicit movement target state now enters through
  `AgentMoveTargetStateRuntime`; BotManager scripted movement, standalone
  move-target ticks, idle/follow gating, precise arrival checks, farm-anchor map
  cleanup, fidget gating, and navigation keep BotEntry as the temporary backing
  store but no longer read or write `moveTarget` or `moveTargetPrecise` directly
  in production.
- Agent reply queue state no longer exposes the temporary BotEntry-backed
  `Deque` through the integration adapter; callers use narrow queue operations
  or a read-only snapshot while `BotEntry.messageQueue()` remains only the
  temporary backing accessor.
- Farm/sentry anchor state now enters through `AgentFarmAnchorStateRuntime`;
  BotManager target snapshots, anchored-farm ticks, follow/active-mode cleanup,
  navigation gating, and LLM situation reporting keep BotEntry as the temporary
  backing store but no longer read or write `farmAnchor` or `farmAnchorMapId`
  directly in production.
- Patrol region and wander-target state now enters through
  `AgentPatrolStateRuntime`; BotManager patrol wandering, patrol loot
  steering, grind/combat patrol targeting, movement snapshots, map-change
  cleanup, and focused tests keep BotEntry as the temporary backing store but no
  longer read or write `patrolRegionId`, `patrolMapId`, or
  `patrolWanderTarget` directly outside the adapter.
- No-target grind wander direction now enters through
  `AgentGrindWanderStateRuntime`; BotManager no-target grind movement and
  focused tests keep BotEntry as the temporary backing store but no longer read,
  choose, or clear `wanderDirection` directly outside the adapter.
- Grind loot target and retry-suppression state now enters through
  `AgentGrindLootStateRuntime`; BotManager active grind-loot steering,
  passive-radius retry suppression, mode cleanup, tick-failure cleanup, and
  focused tests keep BotEntry as the temporary backing store but no longer read
  or write `grindLootTarget`, `ignoredGrindLootObjectId`, or
  `ignoredGrindLootUntilMs` directly outside the adapter.
- AoE reposition commitment state now enters through
  `AgentAoeRepositionStateRuntime`; BotManager AoE pre-attack movement keeps
  BotEntry as the temporary backing store but no longer reads or writes
  `aoeRepositionAnchor` or `aoeRepositionDeadlineMs` directly outside the
  adapter.
- Surround-breakout commitment state now enters through
  `AgentBreakoutStateRuntime`; BotManager ranged retreat breakout movement
  keeps BotEntry as the temporary backing store but no longer reads or writes
  `breakoutDirection` or `breakoutUntilMs` directly outside the adapter.
- Ranged retreat-hold commitment state now enters through
  `AgentRetreatHoldStateRuntime`; BotManager ranged retreat hysteresis,
  breakout cleanup, and grind-start cleanup keep BotEntry as the temporary
  backing store but no longer read or write `retreatHoldPos` or
  `retreatHoldUntilMs` directly outside the adapter.
- Combat skill-cache state now enters through
  `AgentCombatSkillCacheStateRuntime`; BotCombatManager skill cache rebuild,
  buff/heal selection, attack planning, projectile checks, AoE scoring, and
  focused tests keep BotEntry as the temporary backing store but no longer read
  or write cached attack, AoE, heal, buff, or summon skill fields directly.
- Combat buff/support toggle and cooldown state now enters through
  `AgentCombatBuffStateRuntime`; BotCombatManager skill-buff gating,
  per-skill rebuff cadence, support-heal gating, support-buff cadence, and
  focused tests keep BotEntry as the temporary backing store but no longer read
  or write `skillBuffsEnabled`, `supportHealsEnabled`, `nextBuffAt`, or
  `nextSupportBuffAt` directly.
- Ranged degenerate-hit retreat latch state now enters through
  `AgentDegenerateAttackStateRuntime`; BotManager grind combat and local
  opportunity combat keep BotEntry as the temporary backing store but no longer
  read or write `degenAttackDone` directly outside the adapter.
- Grind retarget-search cooldown state now enters through
  `AgentGrindSearchStateRuntime`; BotManager target-search cadence,
  BotMovementManager teleport/reset cleanup, and focused tests keep BotEntry as
  the temporary backing store but no longer read or write
  `nextGrindTargetSearchAtMs` directly outside the adapter.
- Active grind target state now enters through
  `AgentGrindTargetStateRuntime`; BotManager target snapshots, grind combat
  selection, failure/owner-idle cleanup, BotMovementManager reset cleanup,
  BotCombatManager death/debug handling, and BotBuffManager ACC reference
  selection keep BotEntry as the temporary backing store but no longer read or
  write `grindTarget` directly outside the adapter.
- High-level mode state now enters through `AgentModeStateRuntime`;
  BotManager follow/grind/stop transitions, BotMovementManager movement gates,
  AgentFidgetService social fidget gates, BotCombatManager buff/heal/ammo gates,
  AgentPotionService share/low-pot gates, BotNavigationManager follow/grind
  target adjustment, BotPathLogger mode reporting, LLM situation reporting,
  movement snapshots, and focused tests keep BotEntry as the temporary backing
  store but no longer read or write `following`, `grinding`, or
  `followTargetId` directly outside the adapter.
- Tick-failure window state now enters through
  `AgentTickFailureStateRuntime`; BotManager tick exception handling and
  recovery reset keep BotEntry as the temporary backing store but no longer read
  or write `tickFailureCount` or `tickFailureWindowStartedAtMs` directly outside
  the adapter.
- Reply-channel state now enters through `AgentReplyChannelStateRuntime`;
  BotManager command routing/reply delivery, Agent reply runtime delivery, and
  AgentOfferService auto-accept replies keep BotEntry as the temporary backing
  store but no longer read or write `replyChannel` directly outside the adapter.
- Tick cadence state now enters through `AgentTickCadenceStateRuntime`;
  BotManager spawn/normalize reset, tick skip-delay handling, AI tick cadence
  consumption, BotPathLogger cadence reporting, and focused tests keep BotEntry
  as the temporary backing store but no longer read or write `skipDelayMs` or
  `aiTickAccumulatorMs` directly outside the adapter.
- Death/respawn window state now enters through `AgentDeathStateRuntime`;
  BotManager spawn/normalize reset, dead-tick handling, common-tick death
  handling, respawn cleanup, BotCombatManager fatal-hit handling, and focused
  tests keep BotEntry as the temporary backing store but no longer read or write
  `deadUntil` directly outside the adapter.
- Map/foothold tracking state now enters through `AgentMapStateRuntime`;
  BotManager spawn/normalize map tracking, follow/grind map-change detection,
  standalone move-target map-change grounding, shop-mode map-change grounding,
  and focused tests keep BotEntry as the temporary backing store but no longer
  read or write `lastMapId` or `fhIndex` directly in production.
- Script task queue and activity-epoch state now enter through
  `AgentScriptTaskStateRuntime`; BotManager task clearing, queueing,
  active-task activation/completion, scripted local-combat checks, AgentMakerService
  batch-interruption checks, and focused tests keep BotEntry as the temporary
  backing store but no longer read or write `activityEpoch`, `scriptTasks`, or
  `activeScriptTask` directly in production.
- Formation spacing state now enters through `AgentFormationStateRuntime`;
  BotManager registration/reconfiguration offset assignment, follow-target
  snapshot construction, BotPathLogger reporting, and focused tests keep
  BotEntry as the temporary backing store but no longer read or write
  `followOffsetX` directly in production.
- Consumable buff toggle/mode state now enters through
  `AgentBuffStateRuntime`; BotManager owner-away safe-mode cleanup,
  BotBuffManager tick/debug/report paths, Agent control callbacks, and focused
  tests keep BotEntry as the temporary backing store but no longer read or write
  `buffConsumablesEnabled` or `buffCheapMode` directly outside the adapter.
- Pending loot-offer tuple state now enters through
  `AgentOfferStateRuntime`; BotManager offer-recipient checks, AgentOfferService
  reservation/prompt/expiry/accepted-transfer cleanup, BotInventoryManager
  pickup auto-equip, Agent active/equipment bridges, and focused tests keep
  BotEntry as the temporary backing store but no longer read or write
  `pendingLootOfferItem`, `pendingLootOfferRecipientId`,
  `pendingLootOfferExpiresAt`, or `pendingLootOfferBotRequesting` directly
  outside the adapter.
- Live leader/anchor reference refresh now enters through
  `AgentLeaderStateRuntime`; BotManager tick-owner refresh and focused tests
  keep BotEntry as the temporary backing store but no longer assign `owner`
  directly in production.
- Movement profile state now enters through `AgentMovementStateRuntime`;
  BotManager registration, spawn/map-change graph warmup, graph lookup, patrol
  region selection, retreat planning, and jumpability checks keep BotEntry as
  the temporary backing store but no longer read or write `movementProfile`
  directly in production.
- Horizontal movement intent state now enters through
  `AgentMovementStateRuntime`; BotManager spawn normalization and follow-idle
  fast-path gating keep BotEntry as the temporary backing store but no longer
  read or write `moveDir` directly in production.
- Active navigation-edge presence now enters through
  `AgentNavigationDebugStateRuntime`; BotManager retreat eligibility,
  follow-idle fast-path gating, precise-target setup, and stuck detection keep
  BotEntry as the temporary backing store but no longer check `navEdge`
  directly in production.
- Shop transition read state now enters through `AgentShopStateRuntime`;
  BotManager target snapshots, shop detour ticks, idle gating, map-sync gating,
  party-teleport recovery, and follow-idle fast-path gating keep BotEntry as the
  temporary backing store but no longer read `shopVisitPending`,
  `shopTargetPos`, `shopNpcPos`, `shopApproachDelayMs`, or
  `shopSequenceActive` directly in production.
- Physical movement status now enters through `AgentMovementStateRuntime`;
  BotManager retreat eligibility, local combat, trade/idle physics, follow idle
  gating, movement-phase dispatch, stuck detection, and action-lock physics
  keep BotEntry as the temporary backing store but no longer read `inAir`,
  `climbing`, `downJumpPending`, `wasMovingX`, `movementVelX`, or
  `movementVelY` directly in production.
- Movement physics flag state now enters through
  `AgentMovementPhysicsStateRuntime`; BotMovementManager landing cooldown
  reset, fixed-air-arc gating/setup, and broadcast foothold caching keep
  BotEntry as the temporary backing store but no longer read or write
  `jumpCooldownMs`, `fixedAirArc`, or `lastGroundFhId` directly in production.
- Fidget mode presence now enters through `AgentFidgetRuntime`; BotManager
  follow-idle fast-path gating keeps BotEntry as the temporary backing store but
  no longer reads `fidgetMode` directly in production.
- No-ammo combat gate state now enters through `AgentAmmoStateRuntime`;
  BotManager local combat opportunity checks keep BotEntry as the temporary
  backing store but no longer read `noAmmo` directly in production.
- KPQ Stage 5 claim reset state now enters through `AgentPqRuntime`;
  BotManager map-change handling keeps BotEntry as the temporary backing store
  but no longer writes `kpq.stage5Claimed` directly in production.
- Airshow-active tick gate state now enters through
  `AgentManagerStatusRuntime`; BotManager tick orchestration keeps BotEntry
  as the temporary backing store but no longer reads `airshowActive` directly in
  production.
- Scheduled tick task cancellation now enters through
  `AgentScheduledTaskRuntime`; BotManager lifecycle removal keeps BotEntry as
  the temporary backing store but no longer reads or cancels `task` directly in
  production.
- Runtime identity lookup state now enters through
  `AgentRuntimeIdentityRuntime`; BotManager follow-target filtering,
  ownership transfer, active-owner lookup, and name lookup keep BotEntry as the
  temporary backing store but no longer read the corresponding `bot`/`owner`
  identity fields directly in those paths.
- Runtime identity local extraction now enters through
  `AgentRuntimeIdentityRuntime`; BotManager normalization, formation,
  target snapshots, retreat selection, grind loot, tick shell, tick-failure
  handling, follow setup, local movement checks, and movement-only stepping keep
  BotEntry as the temporary backing store but no longer initialize local
  `Character bot`/`Character owner` variables directly from `entry.bot` or
  `entry.owner`.
- Runtime identity side-effect arguments now enter through
  `AgentRuntimeIdentityRuntime`; BotManager pickup auto-equip, potion-share
  mode checks, spawn party join, owner-gained-equip notifications, and
  compatibility reply delivery keep BotEntry as the temporary backing store but
  no longer pass `entry.bot` or `entry.owner` directly in those calls.
- Runtime identity map/position reads now enter through
  `AgentRuntimeIdentityRuntime`; BotManager group supply responder
  selection, pending-offer map checks, owner-inactive town checks, away
  safe-mode map checks, town cluster targeting, farm/patrol setup, script task
  completion, swim-map detection, and movement cleanup/stuck detection keep
  BotEntry as the temporary backing store but no longer read `entry.bot`
  map/position fields directly in those paths.
- Remaining BotManager runtime identity reads now enter through
  `AgentRuntimeIdentityRuntime`; test tick execution, leader refresh,
  farm/patrol guards, owner-follow reset, script item drop, follow-target
  resolution, queued script ticking, cheap script movement checks, and
  movement-only stepping keep BotEntry as the temporary backing store but no
  longer read `entry.bot` or `entry.owner` directly in production code.
- BotMovementManager runtime identity reads now enter through
  `AgentRuntimeIdentityRuntime`; movement profile refresh, state reset,
  climb/air/swim/ground movement, mob-avoidance region checks, ground step
  resolution, unstuck jumps, and movement broadcast keep BotEntry as the
  temporary backing store but no longer read `entry.bot` directly in production.
- Active navigation-edge state in BotNavigationManager now enters through
  `AgentNavigationDebugStateRuntime`; path planning, committed-edge reuse,
  climb-exit refresh, ground-edge refresh, and post-ground edge execution keep
  BotEntry as the temporary backing store but no longer read or write
  `navEdge` directly in production.
- Navigation movement-profile reads now enter through
  `AgentMovementStateRuntime`; BotNavigationManager graph resolution,
  warmup, jump/drop planning, tolerance checks, and platform-margin calculation
  keep BotEntry as the temporary backing store but no longer read
  `movementProfile` directly in production.
- Navigation movement-phase reads now enter through
  `AgentMovementStateRuntime` and `AgentClimbStateRuntime`;
  BotNavigationManager committed-edge guards, edge reuse, portal/jump/drop/climb
  execution, waypoint selection, and current-region resolution keep BotEntry as
  the temporary backing store but no longer read `inAir`, `climbing`,
  `downJumpPending`, or `climbRope` directly in production.
- BotNavigationManager runtime identity reads now enter through
  `AgentRuntimeIdentityRuntime`; graph lookup/warmup, committed-edge
  execution, warmup notification, edge usability checks, waypoint selection,
  current-region resolution, and follow-anchor handling keep BotEntry as the
  temporary backing store but no longer read `entry.bot` or `entry.owner`
  directly in production.
- Remaining BotNavigationManager state reads now enter through
  `AgentMovementPhysicsStateRuntime` and `AgentShopStateRuntime`;
  directional-drop live landing simulation and follow-rope region targeting keep
  BotEntry as the temporary backing store but no longer read `physX`, `hspeed`,
  `groundPhysicsCarryMs`, or `shopVisitPending` directly in production.
- BotInventoryManager runtime identity reads now enter through
  `AgentRuntimeIdentityRuntime`; passive pickup ownership, patrol-loot
  lookup, manual trade handling, transfer setup, auto-equip handoff,
  transferable item selection, recipient resolution, and slow-path logging keep
  BotEntry as the temporary backing store but no longer read `entry.bot` or
  `entry.owner` directly in production.
- BotInventoryManager patrol-loot graph lookup now reads movement profile
  through `AgentMovementStateRuntime`; BotEntry remains the temporary backing
  store but inventory patrol-loot selection no longer reads `movementProfile`
  directly in production.
- BotInventoryManager owner-given trade item state now enters through
  `AgentPendingTradeStateRuntime`; trade reset and owner-given equipment
  capture keep BotEntry as the temporary backing store but no longer mutate
  `ownerGivenItems` directly in production.
- Combat animation/cooldown and alert-reset state now enters through
  `AgentCombatCooldownStateRuntime`; BotCombatManager attack cooldown,
  movement-window, alerted-stance timeout, and alert-reset scheduling keep
  BotEntry as the temporary backing store but no longer read or write
  `attackCooldownMs`, `moveWindowMs`, `alertedUntilMs`, or
  `alertResetScheduled` directly in production.
- Ammo/no-ammo combat gate state now enters through
  `AgentAmmoStateRuntime`; BotCombatManager attack gating and ammo/potion
  warning checks keep BotEntry as the temporary backing store but no longer read
  or write `noAmmo` or `ammoWarnSent` directly in production.
- Mob-touch sweep checkpoint state now enters through
  `AgentMobTouchStateRuntime`; BotCombatManager touch-damage sweep checks
  keep BotEntry as the temporary backing store but no longer read or write
  `lastMobTouchCheckPos` or `lastMobTouchMapId` directly in production.
- Skill-buff debug decision state now enters through
  `AgentSkillBuffDebugStateRuntime`; BotCombatManager buff decision logging
  and debug-line generation keep BotEntry as the temporary backing store but no
  longer read or write `lastSkillBuffActionAtMs` or
  `lastSkillBuffActionSummary` directly in production.
- Combat movement-facing state reads now enter through
  `AgentMovementStateRuntime`; BotCombatManager fall knockback, mob
  knockback, support-heal movement gating, attack-plan gating, attack-facing,
  action-lock movement, and grind graph profile selection keep BotEntry as the
  temporary backing store but no longer read or write `facingDir`, `inAir`,
  `climbing`, `moveDir`, or `movementProfile` directly in production.
- Combat runtime identity reads now enter through
  `AgentRuntimeIdentityRuntime`; BotCombatManager jump-heal leader
  resolution and delayed alert-stance broadcasts keep BotEntry as the temporary
  backing store but no longer read `entry.owner` or `entry.bot` directly in
  production.
- Movement intent and down-jump gate reads now enter through
  `AgentMovementStateRuntime`; BotMovementManager air steering, ground-action
  movement intent, down-jump grace gating, and pending down-jump dispatch keep
  BotEntry as the temporary backing store but no longer read or write
  `moveDir`, `downJumpPending`, or `downJumpGracePeriodMS` directly in
  production.
- Movement profile state now enters through `AgentMovementStateRuntime`;
  BotMovementManager profile refresh, graph lookup/warmup, jump velocity,
  walk-step, mob-avoidance, and unstuck calculations keep BotEntry as the
  temporary backing store but no longer read or write `movementProfile` directly
  in production.
- Horizontal movement hysteresis state now enters through
  `AgentMovementStateRuntime`; BotMovementManager idle-on-ground checks and
  follow-step hysteresis keep BotEntry as the temporary backing store but no
  longer read or write `movementVelX` or `wasMovingX` directly in production.
- Climb and rope movement state now enters through
  `AgentClimbStateRuntime`; BotMovementManager climb target steering,
  climb direction intent, rope transfer, rope snap, rope-grab filtering, and
  rope-entry dispatch keep BotEntry as the temporary backing store but no
  longer read or write `climbRope`, `climbVerticalDir`, `climbing`,
  `climbUpIntent`, `blockedRopeGrab`, or `ropeEntryPending` directly in
  production.
- Swim movement intent state now enters through `AgentSwimStateRuntime`;
  BotMovementManager airborne/grounded swim-mode clearing and swim input
  calculation keep BotEntry as the temporary backing store but no longer read
  or write `swimming`, `swimMoveDir`, `swimVerticalHold`,
  `swimJumpRequested`, or `swimNextJumpAtMs` directly in production.
- Active navigation edge state now enters through
  `AgentNavigationDebugStateRuntime`; BotMovementManager navigation-state
  clearing, climb steering, air-steering gating, grind target adjustment,
  ground action planning, wall-block recovery, directional drop execution, and
  mob-avoidance checks keep BotEntry as the temporary backing store but no
  longer read or write `navEdge` directly in production.
- Shop visit lifecycle state now enters through `AgentShopStateRuntime`;
  AgentShopService resupply/sell-trash visit setup, approach delay, sequence
  activation, stuck fallback, sequence validation, scheduled-step guard, abort,
  and cleanup keep BotEntry as the temporary backing store but no longer read or
  write shop visit fields directly in production. Shop approach graph profile
  lookup now reads through `AgentMovementStateRuntime`, and delayed abort
  identity reads through `AgentRuntimeIdentityRuntime`.
- Offer request/proactive-offer state now enters through
  `AgentOfferStateRuntime`; AgentOfferService owner upgrade request memory,
  proactive shared-loot offer checks, accepted/declined offer callbacks, sibling
  recipient scans, and reserved-offer recipient resolution keep BotEntry as the
  temporary backing store but no longer read `requestedUpgradeItemIds`,
  `proactiveUpgradeOffers`, `owner`, or `bot` directly in production.
- Potion sharing and passive recovery gates now enter through
  `AgentRuntimeIdentityRuntime` and `AgentMovementStateRuntime`;
  AgentPotionService owner lookup, donor bot selection, delayed low-supply
  replies, transfer donor identity, and standing-still recovery checks keep
  BotEntry as the temporary backing store but no longer read `owner`, `bot`,
  `inAir`, `climbing`, or `moveDir` directly in production.
- Ammo sharing identity now enters through `AgentRuntimeIdentityRuntime`;
  AgentAmmoService low-ammo request owner lookup, owner-request sharing, sibling
  donor scans, and delayed transfer donor identity keep BotEntry as the
  temporary backing store but no longer read `owner` or `bot` directly in
  production.
- AP build assignment identity now enters through
  `AgentRuntimeIdentityRuntime`; AgentBuildService set-build confirmation and
  immediate AP assignment keep BotEntry as the temporary backing store but no
  longer read `bot` directly in production.
- Maker automation bot identity now enters through
  `AgentRuntimeIdentityRuntime`; AgentMakerService crystal creation,
  disassembly, batch start, and delayed batch step checks keep BotEntry as the
  temporary backing store but no longer read `bot` directly in production.
- Scroll reaction bot identity now enters through
  `AgentRuntimeIdentityRuntime`; BotScrollReactionManager range filtering,
  delayed reaction eligibility, and emote side effects keep BotEntry as the
  temporary backing store but no longer read `bot` directly in production.
- Fidget bot identity and movement profile reads now enter through
  `AgentRuntimeIdentityRuntime` and `AgentMovementStateRuntime`;
  AgentFidgetService tick eligibility, fidget origin capture, walk-step
  calculations, grounded execution, diagonal/sideways direction selection, and
  prone visual broadcast keep BotEntry as the temporary backing store but no
  longer read `bot` or `movementProfile` directly in production.
- Fidget movement/nav gate state now enters through
  `AgentMovementStateRuntime` and `AgentNavigationDebugStateRuntime`;
  AgentFidgetService social fidget eligibility, active fidget eligibility,
  airborne/climb dispatch, air-steer movement intent, grounded sideways
  movement intent, and prone visual facing keep BotEntry as the temporary
  backing store but no longer read or write `inAir`, `climbing`, `navEdge`,
  `downJumpPending`, `moveDir`, or `facingDir` directly in production.
- Fidget state-machine fields now enter through `AgentFidgetStateRuntime`;
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
- Airshow state now enters through `AgentAirshowStateRuntime`;
  BotAirshowManager active/trail timing, scripted frame physics fields, bot
  identity lookup, restore checks, and trail foothold reads keep BotEntry as the
  temporary backing store but no longer read or write BotEntry fields directly
  in production.
- Navigation debug overlay identity and active-edge reads now enter through
  `AgentRuntimeIdentityRuntime` and `AgentNavigationDebugStateRuntime`;
  BotNavigationDebugOverlay path rendering, path-log messages, multi-bot
  selection names, and committed-edge status keep BotEntry as the temporary
  backing store but no longer read `bot` or `navEdge` directly in production.
- Path logger identity, map, and movement-profile reads now enter through
  `AgentRuntimeIdentityRuntime` and `AgentMovementStateRuntime`;
  BotPathLogger tick capture, graph snapshot resolution, region resolution,
  movement graph summaries, walk-step calculation, and path query calls keep
  BotEntry as the temporary backing store but no longer read `bot` or
  `movementProfile` directly in production.
- Path logger movement-formatting state now enters through
  `AgentClimbStateRuntime`, `AgentMovementStateRuntime`,
  `AgentMovementPhysicsStateRuntime`, and
  `AgentNavigationDebugStateRuntime`; BotPathLogger physics-state and
  nav-edge summaries keep BotEntry as the temporary backing store but no longer
  read climb, airborne, crouch, down-jump, velocity, or active-edge fields
  directly in production.
- Bot command target-name resolution now enters through
  `AgentRuntimeIdentityRuntime` and the Agent-owned
  `AgentCommandTargetResolver`; targeted command matching behavior is unchanged, and
  the old bot-package command parser shim has been removed.
- Starter-kit job advancement now lives in `AgentStarterKitService` and reads
  identity through `AgentRuntimeIdentityRuntime`; job-change, starter-kit
  grant, auto-equip, and build-status ordering are unchanged.
- KPQ coupon-target loot eligibility now enters through `AgentPqRuntime`;
  AgentLootEligibility preserves coupon/pass/rice-cake and quest-item filtering
  behavior while no longer reading KPQ coupon target state directly from
  BotEntry in production.
- KPQ grind-requirement stage reads now enter through `AgentPqRuntime`;
  AgentPartyQuestHooks preserves stage-1 grind gating while no longer reading KPQ state
  directly from BotEntry in production.
- KPQ stage-5 reward-claim state now enters through `AgentPqRuntime`;
  AgentKpqStage5 preserves reward-claim and announcement behavior while no
  longer reading or writing stage-5 claimed state directly on BotEntry in
  production.
- LLM identity and reply-channel reads now enter through
  `AgentRuntimeIdentityRuntime` and `AgentReplyChannelStateRuntime`;
  BotLlmReplyManager, PromptBuilder, SituationBuilder, and SenderRelation
  preserve reply gating, prompt wording, memory keys, and relation resolution
  while no longer reading bot, owner, map, or reply-channel state directly from
  BotEntry in production.
- Script runtime state now enters through `AgentScriptTaskStateRuntime`;
  BotScriptContext and BotScriptRunner preserve script id reset, step entry,
  step advancement, script-local ints, wait timers, and queued-task behavior
  while no longer reading or writing `entry.script` directly in production.
- KPQ stage-1 state-machine fields now enter through `AgentPqRuntime`;
  AgentKpqStage1 preserves stage transitions, coupon target assignment, progress
  reporting, pass exchange, pass delivery, and reset behavior while no longer
  reading or writing KPQ or script runtime fields directly on BotEntry in
  production.
- Fallback movement identity, map, movement-profile, and movement-gate reads
  now enter through `AgentRuntimeIdentityRuntime`,
  `AgentMovementStateRuntime`, and `AgentMovementPhysicsStateRuntime`;
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
  through `AgentMovementPhysicsStateRuntime`; BotPhysicsEngine preserves
  landing, grounded travel, swim, airborne collision, climb-position, and reset
  behavior while no longer reading or writing `physX`, `physY`, `hspeed`, or
  `groundPhysicsCarryMs` directly in production.
- Combat grind-region sibling occupancy and sibling gear-offer targeting now
  enter through `AgentRuntimeIdentityRuntime` and `AgentModeStateRuntime`;
  BotCombatManager and AgentOfferService preserve sibling filtering, map matching,
  and recipient selection while no longer reading sibling bot/owner/grinding
  fields directly in those paths.
- BotManager sibling target discovery, bot-id lookup, replacement cancellation,
  removal matching, and transfer authorization identity now enter through
  `AgentRuntimeIdentityRuntime` and `AgentScheduledTaskRuntime`; the registry
  behavior, task cancellation timing, and authorization inputs remain unchanged
  while those paths stop reading BotEntry identity/task fields directly.
- BotManager first-bot lookup now enters through
  `AgentRuntimeIdentityRuntime`; public owner-to-bot lookup preserves the
  same first-entry behavior while no longer reading the BotEntry bot field
  directly.
- KPQ stage-1 script context access now enters through `BotScriptContext`
  accessors; the stage script preserves Cloto movement, coupon assignment,
  progress reporting, exchange, and pass delivery behavior while no longer
  reading script context runtime fields directly.
- Shop purchase sequencing now uses `AgentShopPurchaseSequence` and
  `AgentShopPurchaseAction`; AgentShopService preserves resupply, recharge,
  potion purchase, trash-sale, shortfall, announcement, and finish behavior
  while the active purchase context is owned by the Agent shop capability
  instead of a private bot runtime record.
- Shop purchase shortfall reporting now uses `AgentShopBuyReport` and
  `AgentShopShortfallReason`; AgentShopService preserves the same quantity,
  meso, space, and generic-failure reporting while the purchase report value
  object is owned by the Agent shop capability.
- Potion donor planning now uses `AgentPotionDonorPlan`; AgentPotionService
  preserves the same donor selection, qualification threshold, donation
  quantity, delay, and transfer behavior while the donor plan context is owned
  by the Agent supplies capability instead of a private bot runtime record.
- Ammo donor planning now uses `AgentAmmoDonorPlan`; AgentAmmoService
  preserves the same donor selection ordering, same-ammo preference, donation
  quantity, delay, and transfer behavior while the donor plan context is owned
  by the Agent supplies capability instead of a bot package record.
- Bot command target, transfer, targeted-command match, and bot-entry parser
  adapter now use `AgentNamedCommandTarget`, `AgentTransferCommand`,
  `AgentTargetedCommandMatch`, and `AgentCommandTargetResolver`; parsed command
  boundary data is owned by Agent commands while bot-entry adaptation remains
  in the Agent integration layer, preserving the same `AgentCommandParser`
  matching behavior.
- Targeted dialogue routing now shares the Agent commands
  `AgentTargetedCommandMatch`; the duplicate dialogue-local match record has
  been removed while preserving the same targeted chat behavior.
- Pending-offer response routing is now generic over Agent runtime handles.
  Pending-offer expiry, target checks, targeted command resolution, and response
  handling are supplied as hooks; the current BotEntry target check remains in
  the chat-route adapter, preserving the same pending-offer response behavior.
- Character and inventory report pass-through bridges have been removed.
  Report delivery now calls Agent dialogue reporters directly from the chat
  report runtime, preserving the same stats, build, meso, exp, inventory,
  slots, and scroll report text.
- The supply report pass-through bridge has been removed. Potion reports and
  autopot debug reports are delivered directly through Agent supply/dialogue
  services with the same text.
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
  `AgentCombatReportRuntime`; BotCombatManager keeps temporary compatibility
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
  `AgentSkillBuffDebugStateRuntime.rememberAction` directly from the
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
  `AgentCombatReportRuntime`.
- Support buff SPECIAL_MOVE dispatch now lives in
  `AgentSupportSpecialMoveExecutor`; `BotCombatManager` calls the Agent-owned
  executor while preserving the same packet builder, timestamp, packet handler
  lookup, validation, and dispatch flow.
- Attack-facing memory now lives in `AgentCombatFacingRuntime`;
  `BotCombatManager` calls the Agent-owned runtime while preserving the same
  attack-packet stance conversion, facing direction update, and character-state
  sync.
- Attack/move action-lock countdown routing now lives in
  `AgentCombatActionLockRuntime`; BotManager calls the Agent-owned runtime
  while preserving the same legacy movement tick-down cadence and attack-before-
  move-window priority.
- Combat alert stance timing and reset scheduling now live in
  `AgentCombatAlertRuntime`; BotCombatManager preserves the same damage,
  heal, attack, and support-buff alert triggers through the Agent-owned runtime.
- Mob-touch sweep bounds, lower-body touch checks, and last-check position
  memory now live in `AgentMobTouchRuntime`; BotCombatManager preserves the
  same hostile-living-monster filtering and damage application path while
  delegating touch detection to Agent runtime.
- Combat ammo/MP-potion shortage checks now live in
  `AgentCombatAmmoCheckRuntime`; potion and shop flows call the Agent-owned
  runtime while preserving the same ammo thresholds, no-ammo state, follow-owner
  fallback, and map-chat replies.
- Combat debug-stat report assembly now lives in
  `AgentCombatReportRuntime`; `BotCombatManager.describeDebugStats` is a
  temporary compatibility delegate while the Agent reporting runtime preserves
  the same target lookup, attack route, speed, cooldown, tick, and AI cadence
  output.
- Combat debug-stat target search and attack-plan lookup now call
  `AgentCombatTargetRuntime` and `AgentCombatPlanRuntime` directly from
  `AgentCombatReportRuntime`; the report path no longer calls back through
  `BotCombatManager.findGrindTarget` or `BotCombatManager.planAttack` while
  preserving the same shared combat config object and report output.
- Combat support-buff tick orchestration now lives in
  `AgentCombatBuffRuntime`; `BotCombatManager.tickBuffs` is a temporary
  compatibility delegate while the Agent runtime preserves the same buff-enable
  gates, living-mob preflight, party-support checks, SPECIAL_MOVE dispatch,
  cooldown windows, and debug summaries.
- Combat skill-cache rebuild orchestration now lives in
  `AgentCombatSkillCacheRuntime`; `BotCombatManager.rebuildSkillCacheIfNeeded`
  is a temporary compatibility delegate while the Agent runtime preserves the
  same skill signature guard, attack/AoE/heal/summon/support-buff bucket
  selection, best single-target skill ordering, and support-buff next-tick
  initialization.
- Combat support-heal orchestration now lives in
  `AgentCombatHealRuntime`; `BotCombatManager.tickSupportHealing` is a
  temporary compatibility delegate while the Agent runtime preserves the same
  heal-skill gate, HP/undead target decision, jump-heal movement, Heal attack
  packet construction, cooldown, move-window, alert, and movement-broadcast
  behavior.
- Combat action-state clearing now lives in
  `AgentCombatActionStateRuntime`; the damage/death path calls the Agent
  helper while preserving the same grind target, attack cooldown, move window,
  navigation state, and movement broadcast invalidation reset.
- Combat death-state entry now lives in `AgentCombatDeathRuntime`;
  `BotCombatManager.enterDeadState` is a temporary compatibility delegate while
  the Agent runtime preserves action-state clearing, physics dead stance sync,
  movement broadcast, dead-window timing, and optional death dialogue.
- Combat mob/fall damage application now lives in
  `AgentCombatDamageRuntime`; `BotCombatManager.applyMobHit` and
  `BotCombatManager.applyFallDamage` are temporary compatibility delegates while
  the Agent runtime preserves DAMAGE_PLAYER packet fields, HP/autopot mutation,
  mob-hit cooldown, alert stance, fatal-death routing, stance-buff knockback
  gating, and air/ground knockback physics dispatch.
- Combat mob-touch polling now lives in `AgentCombatDamageRuntime`;
  `BotCombatManager.tickMobDamage` is a temporary compatibility delegate while
  the Agent runtime preserves mob-hit cooldown countdown, hostile-only contact
  filtering, client-style touch sweep checks, first-hit return behavior, and
  last touch-check position memory.
- Combat attack execution now lives in `AgentCombatAttackRuntime`;
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
- Attack-plan orchestration now lives in `AgentCombatPlanRuntime`;
  `BotCombatManager.planAttack` is a temporary compatibility adapter while the
  Agent runtime preserves cached skill candidate ordering, basic attack fallback,
  best-plan scoring, and the existing `combat-plan` performance metric.
- Combat ground foothold lookup now lives in
  `server.agents.capabilities.combat.AgentCombatGroundRuntime`;
  BotCombatManager no longer owns the null guards or physics-engine bridge used
  by grind, patrol, follow, and local target scoring.
- AoE repositioning now lives in `AgentCombatAoeRepositionRuntime`;
  `BotCombatManager.aoeRepositionTarget` is a temporary compatibility delegate
  while Agent-owned logic preserves the enable gate, cluster geometry, shifted
  hitbox scoring, full-HP kill priority, DPS threshold, and debug log payload.
- Combat target search now lives in `AgentCombatTargetRuntime`;
  `BotCombatManager.findGrindTarget`, `findPatrolTarget`,
  `findFollowAttackTarget`, and `isReachableGrindTarget` are temporary
  compatibility delegates while Agent-owned logic preserves candidate ranges,
  local-vs-graph scoring, patrol-region expansion, follow-mode local targeting,
  immediate projectile reach, sibling occupancy penalty, graph fallback, and the
  existing `combat-target-search` performance metric.
- BotManager grind, local-opportunity, priority-ranged, and AoE reposition paths
  now call `AgentCombatPlanRuntime`, `AgentCombatAttackRuntime`,
  `AgentCombatTargetRuntime`, and `AgentCombatAoeRepositionRuntime`
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
  `AgentCombatDamageRuntime.applyFallDamage` directly. The same
  peak-to-landing fall distance, threshold behavior, packet side effects, and
  shared combat config are preserved.
- Skill-cache focused combat tests now call
  `AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded` directly instead
  of the temporary `BotCombatManager` compatibility delegate. The covered cache
  signature, attack/AOE/heal/support bucket, and rebuild behavior are unchanged.
- Combat planning, target-search, and AoE-reposition focused tests now call
  `AgentCombatPlanRuntime`, `AgentCombatTargetRuntime`, and
  `AgentCombatAoeRepositionRuntime` directly with `AgentAttackPlan` and
  `AgentCombatConfig.cfg`. The temporary `BotCombatManager.AttackPlan`,
  `planAttack`, `attackMonster`, target-search, reachable-target, and
  AoE-reposition compatibility delegates have been removed because production
  and focused test callers no longer need them.
- The remaining `BotCombatManager` compatibility facade has been removed after
  mob-hit tests moved to `AgentCombatDamageRuntime` and stale source/test
  comments stopped referring to the deleted class. Combat behavior remains
  owned by Agent combat runtimes; the legacy `BotCombatManagerTest` class name
  is retained only as the historical focused combat parity suite.
- The remaining `BotChatManager` compatibility facade has been removed.
  `BotManager` now calls `AgentChatRuntime` directly with
  `AgentChatOrchestratorContext`, preserving the same handled-state
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
  `AgentFidgetSideEffects`; the old bot-side fidget side-effect shim has
  been removed, while `AgentFidgetService` owns the legacy fidget behavior in
  the Agent movement capability.
- Relog and owner-bot session lifecycle bridge calls now enter through
  `AgentSessionLifecycleSideEffects`; the old bot-side session lifecycle
  shim has been removed while `BotManager` still performs the unchanged relog
  and owner-entry lookup side effects.
- Movement target snapshot capture now enters through
  `AgentMovementTargetSideEffects`; the old bot-side movement-target shim has
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
- Ground precise-navigation stop-distance and directional-drop edge detection
  now live in `AgentGroundMovementPolicy`; BotMovementManager preserves the same
  compatibility helper and ground action planning inputs.
- Ground mob-avoidance lane scanning and simulated landing-region checks now
  live in `AgentMobAvoidanceService`; BotMovementManager preserves the same
  follow/grind guards and fixed-arc jump decision point.
- Ground action planning and execution now enter through
  `AgentGroundActionPlanner` and `AgentGroundActionExecutor`; BotMovementManager
  routes grounded runtime through the Agent services while old private bot
  helpers remain temporary dead compatibility clutter until the ground body is
  fully cleaned up. `BotPhysicsEngine.GroundMotion` and the ground-wall check
  are public compatibility seams only until physics migrates.
- Grounded movement tick orchestration now lives in
  `AgentGroundMovementRuntimeService`; `BotMovementManager.tickGrounded` is a
  compatibility delegate. The same swim-state clear, motion timer tick, ground
  sync/detect, rope-entry/down-jump priority, graph-warmup fallback, target
  shaping, action planning, execution, and `move-ground` timing label are
  preserved. `BotPhysicsEngine.syncAndDetectGround`, `beginDownJump`, and
  `beginTopRopeEntry` are public compatibility seams only until physics migrates.
- Movement phase dispatch now calls `AgentGroundMovementRuntimeService`
  directly for grounded ticks; Agent production dispatch no longer imports
  `BotMovementManager` for live ground movement.
- Dead BotMovementManager ground planner/executor compatibility clutter was
  removed after grounded runtime moved to Agent services. The local climb
  `MoveAction` helper remains temporarily because climb cleanup is a separate
  slice.
- Dead BotMovementManager climb, airborne, and swim phase bodies were removed
  after those runtime paths moved to `AgentClimbMovementService`,
  `AgentAirborneMovementService`, and `AgentSwimMovementService`. The
  navigation-facing `jumpOffRope`, `jumpToRope`, `sameRope`,
  `shouldHoldClimbIdle`, and `shouldSnapToClimbTarget` compatibility shims
  remain until BotNavigationManager migrates.
- BotNavigationManager movement side effects now call Agent movement services
  directly for navigation-state clear/reset, jump initiation, rope jump,
  rope-transfer jump, jump-off-rope, same-rope comparison, and movement
  broadcast. Remaining BotMovementManager references in BotNavigationManager
  are navigation config constants and comments for later policy extraction.
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
  `AgentPendingTradeStateRuntime` directly; the old `BotInventoryManager`
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
  target-follow now dispatch through
  `server.agents.capabilities.movement.AgentMovementCommandRuntime`; BotManager no
  longer keeps those compatibility entry points. The same script task clearing,
  shop-visit cancellation, null guards, mode-state transitions,
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
- Lifecycle chat command wiring for recruit, transfer, and dismiss now lives in
  `server.agents.runtime.AgentLifecycleChatCommandRuntime`. BotManager keeps
  only compatibility lifecycle entry points and supplies their callbacks to the
  Agent runtime command boundary. The same command aliases, success/failure
  messages, yellow-message delivery, and fall-through behavior are preserved.
- Formation chat command hook construction now lives in
  `server.agents.runtime.AgentFormationCommandRuntime`. BotManager keeps only
  the temporary active-entry source plus legacy stagger/snap config values while
  Agent runtime owns formation state lookup/write, offset application, reply
  routing, and the legacy help/status text path.
- Live tick context hook construction now lives in
  `server.agents.runtime.AgentLiveTickContextRuntime`. BotManager keeps only
  temporary callbacks for follow-anchor resolution and target snapshot capture,
  while Agent runtime owns movement-profile refresh, leader motion observation,
  map-change cleanup, and follow action-window cleanup.
- Live tick gate hook construction now lives in
  `server.agents.runtime.AgentLiveTickGateRuntime`. BotManager keeps only
  temporary script-task, grind, and follow callbacks plus legacy teleport
  thresholds, while Agent runtime owns common-system, trade-window, idle,
  recovery, and tracked map-change gate wiring and performance labels.
- Live mode hook construction now lives in
  `server.agents.runtime.AgentLiveModeTickRuntime`. BotManager keeps only
  temporary callbacks for local opportunity attacks, movement-core stepping,
  anchored farm ticking, and grind-mode ticking while Agent runtime owns shop,
  follow opportunity, follow idle, scripted move combat, anchored farm, grind
  dispatch, final movement-tail wiring, and matching performance labels.
- Tick-core hook construction now lives in
  `server.agents.runtime.AgentTickCoreRuntime`. BotManager keeps only temporary
  callbacks for leader resolution, inactive-leader handling, dead-state checks,
  target snapshots, script tasks, command-mode transitions, and low-level
  movement/combat mode execution while Agent runtime owns preflight,
  ownerless ticking, live context preparation, live gate dispatch, live mode
  dispatch, timing, and config handoff.
- Script task tick callback wiring now lives in
  `server.agents.runtime.AgentScriptTaskRuntime`. BotManager keeps only
  compatibility queue methods and supplies the legacy movement stop-distance
  config while Agent runtime owns script task start/completion callback
  composition.
- Grind-mode hook construction now lives in
  `server.agents.runtime.AgentGrindModeRuntime`. BotManager keeps only the
  temporary movement-core callback and legacy loot-radius config while Agent
  runtime owns grind target search, no-target fallback, target commitment,
  ranged engagement, navigation tail, and combat/navigation side-effect hook
  wiring.
- Local opportunity attack live-mode adaptation now lives in
  `server.agents.runtime.AgentLocalOpportunityAttackRuntime`. BotManager no
  longer keeps a local result record or adapter method for converting local
  attack capability results into live-mode tick results.
- Inactive leader safety and town-return hook wiring now lives in
  `server.agents.runtime.AgentLeaderSafetyRuntime`. BotManager keeps only
  compatibility methods for away/town commands while Agent runtime owns active
  leader return cleanup, inactive safe-mode entry, town-scroll fallback,
  cluster target resolution, movement reset, and mode/script/shop cleanup
  composition.
- Chat ingress, targeted route, and untargeted route hook construction now
  lives in `server.agents.runtime.AgentChatRouteRuntime`. BotManager keeps only
  the public compatibility `handleChat` entry point and supplies lifecycle
  action callbacks plus the active Agent entry map. Agent runtime owns pending
  offer routing, lifecycle chat command routing, formation routing, targeted
  command routing, typo suggestion wiring, reply-channel state, Agent chat
  dispatch, owner-command recording, LLM fallback, group supply routing, and
  broadcast-to-all untargeted routing.
- Formation default state, state lookup, and state update/apply helpers now
  live in `server.agents.runtime.AgentFormationRuntime`. BotManager keeps only
  package-visible compatibility methods for the movement simulation harness and
  delegates target-snapshot/default formation needs to Agent runtime.
- The remaining BotManager grind-mode adapter method was removed. Tick-core
  wiring now passes `AgentGrindModeRuntime.tickGrindMode` directly with the
  temporary movement-core callback and legacy loot-radius value.
- Equipment reserve service routing now enters `AgentEquipmentReservePolicy`
  directly through `AgentEquipmentService`; owned-item and incoming-item
  reserve behavior is unchanged, but production service callers no longer
  traverse the temporary `BotEquipManager` optimizer shell for that decision.
- Equipment recommendation candidate eligibility now lives in
  `AgentEquipmentRecommendationPolicy`; immediate and future recommendation
  scopes preserve the same can-wear/stat-only gates through Agent-owned
  equipment services.
- Equipment unequip command execution now lives in
  `AgentEquipmentUnequipService`; unequip-all and unequip-slot reply strings,
  cash-item skipping, free-slot guards, slot ordering, and item move side
  effects are unchanged, while the service boundary no longer enters
  `BotEquipManager` for those commands.
- Equipment recommendation filtering and result construction now live in
  `AgentEquipmentRecommendationService`; trade recommendation, single-item
  offer checks, recommended-item collection, and summary formatting preserve the
  same filters and reply wording. Optimizer orchestration now enters
  Agent-owned equipment services.
- Auto-equip debug dump formatting for headers, item rows, requirement summaries,
  self-reserve markers, and safe map IDs now lives in
  `AgentEquipmentDebugReportFormatter`; the chat-visible debug flow and dump
  contents are unchanged, and auto-equip/debug execution now lives in
  `AgentEquipmentAutoEquipService`.
- The optimizer result returned to Agent recommendation code now lives in
  `AgentEquipmentOptimizerResult`; Agent-owned optimization services now own
  that DP execution entry.
- The optimizer stat snapshot used for non-mutating wearability checks now
  lives in `AgentEquipmentStatSnapshot`; swap math, derived accuracy, and the
  legacy INT-plus-MATK magic behavior are unchanged.
- Fixed-weapon equipment DP result and lexicographic score data now live in
  `AgentEquipmentDpResult` and `AgentEquipmentScore`; DP branch selection,
  score comparison, and pareto-cap reporting are unchanged while
  `BotEquipManager` no longer owns those nested model types.
- The equipment optimizer metadata/requirement hook interface now lives in
  `AgentEquipmentOptimizerHooks`; immediate and future requirement hook
  adapters preserve the same ItemInformationProvider calls and test stubbing
  surface while `BotEquipManager` no longer owns that boundary type.
- Weapon-branch debug score breakdown data now lives in
  `AgentWeaponScoreBreakdown`; raw max, pre-cycle damage, cycle timing, and
  normalized-damage reporting are unchanged.
- Equipment slot planning and labels now live in `AgentEquipmentSlotResolver`;
  ring slot detection, DP slot expansion/order, chat slot aliases, and
  equipment recommendation labels share the same Agent-owned source.
- Equipment tests and remaining optimizer internals now call Agent scoring,
  slot, and weapon compatibility policies directly instead of bot-owned wrapper
  methods for slot lookup, expected-damage scoring, useful-stat scoring, mage
  checks, or weapon compatibility.
- Ground horizontal step resolution now lives in
  `AgentGroundMovementService`; `BotMovementManager.resolveGroundStepX`,
  `calcStepX`, and `updateStepX` are temporary compatibility delegates. The
  same stop/follow hysteresis, graph-warmup local stop distance, movement
  profile scaling, and `wasMovingX` state updates are preserved.
- Navigation jump/rope probe calls now route through
  `AgentJumpProbeService`; `BotMovementManager` keeps temporary compatibility
  wrappers while the underlying `BotPhysicsEngine` implementation migrates in a
  later physics slice. Jump landing and rope reach results are unchanged.
- Ground and rope jump initiation now lives in `AgentJumpActionService`;
  fallback movement, fidget jumps, and support-heal jump positioning call the
  Agent-owned action service directly. `BotMovementManager.initiateJump` and
  `initiateRopeJump` remain temporary delegates with the same air-velocity and
  movement-broadcast timing.
- Movement phase dispatch now enters `AgentMovementPhaseDispatchService`; action-lock,
  idle physics, grind, fidget, and live movement phase runtimes no longer wire
  their hooks directly to `BotMovementManager` phase methods. The phase method
  bodies remain temporary bot delegates until climb/swim/air/ground are moved
  one at a time.
- Movement profile refresh now lives in `AgentMovementProfileService`; live
  tick context no longer asks `BotMovementManager` to refresh speed/jump bucket
  state or warm the matching navigation graph. The same unchanged-profile
  short-circuit, async graph warmup, profile write, and navigation-state clear
  behavior are preserved.
- Agent trade/inventory runtime adapters and script task tests now read
  movement tick/stop distances from `AgentMovementPhysicsConfig`; standalone
  move-target runtime uses `AgentMovementProfileService` for profile refresh.
  These paths no longer depend on `BotMovementManager` for config/profile
  access.
- Stuck recovery jump action now lives in `AgentMovementRecoveryService`; stuck
  detection runtime no longer imports `BotMovementManager`. The same random
  left/right ground jump, navigation-state clear, five-second cooldown, and
  movement broadcast are preserved.
- Swim phase runtime now enters `AgentSwimMovementService`; the Agent service
  owns swim timer ticking, swim intent calculation, swim physics dispatch, and
  movement broadcast. `BotMovementManager.tickSwimming` remains a temporary
  delegate, and the old private swim intent helper is dead compatibility
  clutter to remove in a later cleanup slice.
- Airborne phase runtime now enters `AgentAirborneMovementService`; the Agent
  service owns swim-mode clearing, motion timer ticks, air steering intent,
  rope-grab checks, airborne physics stepping, jump-cooldown clearing, movement
  broadcast, and performance timing. `BotMovementManager.tickAirborne` remains
  a temporary compatibility shell while old unreachable helper code is cleaned
  up in a later slice.
- Climb phase runtime now enters `AgentClimbMovementService`; the Agent service
  owns climb timer ticks, rope jump-off/transfer actions, idle/snap policy
  routing, climb hold/advance dispatch, movement broadcast, and performance
  timing. `BotMovementManager.tickClimbing` remains a temporary compatibility
  shell while old helper wrappers are cleaned up later.
- Ground grind target adjustment now lives in `AgentGroundTargetService`; the
  legacy `BotMovementManager.adjustGrindingTargetPosition` helper delegates to
  the Agent service. Same-region edge clamping, cross-region no-op behavior,
  graph warmup fallback, and grind-edge margin are unchanged.
- Movement config reads in remaining bot-named tests and movement simulation
  harnesses now use `AgentMovementPhysicsConfig` directly. The unused
  `BotMovementManager.configured*` compatibility accessors were removed; the
  only remaining direct `BotMovementManager.cfg` test reference verifies the
  temporary shared config object with `BotPhysicsEngine` until the physics
  slice migrates that seam.
- Packet-facing movement snapshot retrieval now enters through
  `AgentMovementSnapshotService` and `AgentMovementPacketSnapshot`; the service
  preserves the legacy `BotPhysicsEngine.movementSnapshot` behavior while
  isolating broadcast packet code from the bot physics snapshot type.
- Continuous walking ground-travel state now uses Agent-owned
  `AgentGroundTravelState`. `BotEntry`, the Agent movement physics adapter,
  and the physics integrator still preserve the exact legacy `physX`, `hspeed`,
  and carry-ms values.
- Queued down-jump and top-rope entry actions now enter through
  `AgentQueuedMovementActionService`. The service still delegates to the
  legacy physics implementation, but Agent movement/navigation callers no
  longer reach those queue/begin methods directly.
- Grounded physics entry points now route through `AgentGroundPhysicsService`,
  and movement timer countdowns route through `AgentMotionTimerService`.
  Agent movement callers no longer call legacy `applyGroundMotion`,
  `syncAndDetectGround`, `stopGroundMotion`, `velocityFromDeltaX`, or
  `tickMotionTimers` directly.
- Grounded collision and ledge queries now route through
  `AgentGroundCollisionService`. The service preserves legacy query results,
  including `isGroundFarBelow(null, position) == true`, while callers move away
  from direct `BotPhysicsEngine` access.
- Rope, ladder, and jump-launch action entry points now route through
  `AgentRopeMovementService`. The service preserves legacy attach/hold/advance
  and ground/rope jump behavior while removing direct physics action calls from
  Agent movement callers and old movement/navigation shims.
- Combat-driven knockback movement now routes through
  `AgentKnockbackMovementService`. Combat damage code still preserves the
  legacy facing-direction and airborne velocity behavior while no longer
  calling knockback physics directly.
- Airborne and swim integration entry points now route through
  `AgentAirbornePhysicsService` and `AgentSwimPhysicsService`. The Agent
  airborne phase uses `AgentAirborneStepResult` instead of the legacy bot enum,
  while preserving the same wall/ceiling/land/continue outcomes.
- Fallback movement jump and rope-reach probes now route through
  `AgentJumpProbeService` instead of calling the legacy physics probe methods
  directly.
- Navigation-facing physics helpers now route through
  `AgentNavigationPhysicsService`, including first-climbable rope Y and
  walkable endpoint-step policy. The wrapper preserves the legacy +1 Y offset
  for first climbable rope/ladder positions.
- Navigation graph build walk-region lookup lifecycle now also routes through
  `AgentNavigationPhysicsService`. The physics engine still owns the
  thread-local lookup implementation until the graph/physics coupling is split
  in a later slice.
- Movement capability ground-target and mob-avoidance region classification
  now enter through `AgentNavigationRegionService`. The service delegates to
  the existing navigation manager until the pathfinding internals migrate.
- Follow-target character region classification now also enters through
  `AgentNavigationRegionService`, preserving the existing rope/platform clamp
  behavior while removing another direct navigation-manager dependency.
- Path logger, script move-target cost checks, and navigation debug overlay now
  enter path search through `AgentNavigationPathService` and region
  classification through `AgentNavigationRegionService`.
- Shop approach candidate reachability now uses `AgentNavigationRegionService`
  and `AgentNavigationPathService`, preserving the same candidate filtering
  while removing another direct navigation-manager dependency.
- Navigation probe path and optimality measurement now enter through
  `AgentNavigationPathService`, including an Agent-owned `PathOptimality`
  record that mirrors the legacy diagnostic values.
- Production navigation path search entry points now run through
  `AgentNavigationPathService`. The Agent service owns `findPath`,
  target-score path search, optimality measurement, slow-path logging,
  path reconstruction, next-edge selection, and the copied search algorithm.
  `BotNavigationManager` keeps public/package-private compatibility delegates,
  and its `runSearch` compatibility method now wraps the Agent-owned
  `SearchOutcome` instead of retaining a duplicate search body.
- Dead `BotNavigationManager` path-search helper shims for intra-region cost,
  heuristic calculation, map-edge usability, slow-path logging, and path
  reconstruction were removed after the compatibility search method delegated
  to `AgentNavigationPathService`.
- Navigation graph warmup notification throttling now lives in
  `AgentNavigationWarmupService`. The same leader/map throttle, walkable
  foothold threshold, and visible fallback message text are preserved.
- Bot navigation target resolution now calls Agent-owned movement reset and
  movement target snapshot services directly instead of keeping local
  `clearNavigation` / `captureTargetSnapshot` forwarding helpers.
- Portal edge execution now lives in `AgentNavigationPortalService`. The same
  portal-open check, enter-portal result check, 250 ms cooldown, navigation
  clear, and entry movement reset ordering are preserved.
- Navigation climb-start side effects now live in
  `AgentNavigationClimbExecutionService`. Rope attachment and movement
  broadcast ordering are unchanged.
- Explicit drop edge execution now lives in
  `AgentNavigationDropExecutionService`. The same airborne/climbing/down-jump
  guards, walk-off-drop no-op, drop-readiness check, edge target state write,
  queued down-jump, and movement broadcast ordering are preserved.
- Jump edge execution now lives in `AgentNavigationJumpExecutionService`.
  The same in-air/climbing guards, selected jump readiness calculation,
  top-of-rope fallback attach, block reason, edge target state write, and
  jump action initiation behavior are preserved.
- Climb-entry edge execution now lives in
  `AgentNavigationClimbEntryExecutionService`. The same rope lookup,
  climb-entry readiness, block reasons, direct attach paths, queued top-rope
  entry, movement broadcast, and ground rope-jump behavior are preserved.
- Climb-exit edge execution now lives in
  `AgentNavigationClimbExitExecutionService`. Rope-to-rope jumps, top step-off
  no-op behavior, top-rope alignment, jump-off-rope behavior, and climb-exit
  readiness are preserved.
- Navigation edge execution dispatch now lives in
  `AgentNavigationEdgeExecutor`. The bot navigation shell only adapts the
  Agent-owned directive record back to the legacy `NavigationDirective` type.
- Live navigation target resolution and committed-edge continuation now live
  in `AgentNavigationTargetService`. `BotNavigationManager.resolveTarget` and
  `tryExecuteCommittedEdgeAfterGroundMovement` are compatibility delegates
  that adapt the Agent-owned directive result back to the legacy wrapper type.
  Directional-drop waypoint matching still reads the legacy walk-off physics
  result until the physics engine body moves in a later slice.
- Grind navigation and fallback target runtime hooks now call
  `AgentNavigationRegionService` and `AgentNavigationPathService` directly
  instead of passing through `BotNavigationManager` compatibility delegates.
- Dead private `BotNavigationManager` target-resolution helper bodies were
  removed after live target resolution moved to `AgentNavigationTargetService`.
  Remaining BotNavigationManager methods are compatibility/test-facing
  delegates or waypoint/readiness helpers still awaiting Agent test migration.
- Directional-drop waypoint selection now lives in
  `AgentNavigationWaypointService`. `AgentNavigationTargetService` and the
  `BotNavigationManager` compatibility shell both call the same Agent-owned
  method, preserving the walk-off landing match behavior.
- Entry-backed jump waypoint selection now also lives in
  `AgentNavigationWaypointService`; the bot navigation shell delegates its
  test-visible jump waypoint helpers to the Agent-owned overloads.
- Default climb waypoint selection and climb-exit readiness wiring now live in
  `AgentNavigationWaypointService`; `AgentNavigationTargetService` and the bot
  navigation compatibility shell call the same Agent-owned overload.
- Default committed-edge reuse wiring now lives in
  `AgentNavigationCommittedEdgeService`; the bot navigation shell no longer
  supplies local edge-usability or rope-entry callbacks.
- Dead private pathfinding and rope lookup helpers have been removed from
  `BotNavigationManager`; pathfinding and rope lookup ownership now stays in
  `AgentNavigationPathService`, `AgentNavigationTargetService`, and
  `AgentNavigationGraphService`.
- Directional drop waypoint tests now target `AgentNavigationWaypointService`
  directly, allowing the temporary `BotNavigationManager.selectDropWaypoint`
  compatibility delegate to be removed.
- Jump and climb waypoint tests now target `AgentNavigationWaypointService`
  directly, allowing the temporary `BotNavigationManager` jump/climb waypoint
  delegates to be removed.
- Path collapse, precise walk-target, jump/drop readiness, launch-window, and
  rope top-step tests now target their Agent navigation services directly,
  allowing the matching `BotNavigationManager` compatibility delegates to be
  removed.
- Path search, path optimality/search-result adapters, and region resolution
  tests now target `AgentNavigationPathService` and
  `AgentNavigationRegionService` directly; the matching
  `BotNavigationManager` compatibility delegates and record adapters have been
  removed.
- Committed-edge reuse and retention tests now target
  `AgentNavigationCommittedEdgeService` directly; the matching
  `BotNavigationManager` compatibility delegates have been removed.
- Target-resolution tests now target `AgentNavigationTargetService` directly;
  `BotNavigationManager` has no remaining production or test callers and has
  been deleted.
- Combat grind-target region scoring, path-cost checks, and sibling occupancy
  checks now use `AgentNavigationRegionService` and
  `AgentNavigationPathService`. Target scoring policy and path-cost behavior
  are unchanged.
- Navigation region-classification implementation now lives in
  `AgentNavigationRegionService`. `BotNavigationManager` retains only
  compatibility delegates for current/target/character/point region lookup.
- Movement tick target resolution and committed-edge continuation now enter
  through `AgentNavigationTargetService`. The service delegates to the current
  resolver body until the live path-following implementation moves.
- Path intra-region travel cost and heuristic calculations now live in
  `AgentNavigationPathService`; the remaining bot search loop delegates to the
  Agent-owned calculations.
- Leading no-op walk collapse and precise walk-target policy now live in
  `AgentNavigationPathService`; `BotNavigationManager` retains compatibility
  delegates for existing tests and remaining internal calls.
- Navigation edge usability policy now lives in `AgentNavigationPathService`.
  The rule is unchanged: walk/jump/drop/climb edges are always usable and portal
  edges require an existing open portal.
- Committed navigation edge equality and retention policy now live in
  `AgentNavigationCommittedEdgeService`; `BotNavigationManager` delegates while
  remaining committed-edge execution code migrates later.
- Committed ground-edge refresh/replacement policy now also lives in
  `AgentNavigationCommittedEdgeService`. `BotNavigationManager` supplies only
  the existing `findNextEdge` callback so path search ordering and edge choice
  remain unchanged while ownership moves into the Agent navigation capability.
- Committed-edge reuse/staleness policy now lives in
  `AgentNavigationCommittedEdgeService`. `BotNavigationManager` supplies only
  edge-usability and rope-entry callbacks while the Agent service owns the
  stored target update, completion checks, retarget checks, climb retention,
  and airborne arc retention rules.
- Pending climb-exit edge refresh policy now lives in
  `AgentNavigationCommittedEdgeService`. `BotNavigationManager` supplies the
  current climb-exit readiness callback and next-edge search callback so rope
  exit timing and replacement choice stay unchanged.
- Navigation edge execution target state now lives in
  `AgentNavigationEdgeExecutionStateService`. Jump and drop execution still
  happen in the temporary bot navigation shell, but the nav-waypoint state
  write is Agent-owned.
- Navigation edge readiness thresholds now live in
  `AgentNavigationEdgeReadinessService`; `BotNavigationManager` delegates while
  execution sequencing remains in the temporary bot navigation shell.
- Jump and straight-drop execution readiness predicates now live in
  `AgentNavigationEdgeReadinessService`; `BotNavigationManager` keeps
  package-visible compatibility delegates for existing navigation tests.
- Selected jump-launch execution readiness now lives in
  `AgentNavigationEdgeReadinessService`; the bot navigation shell still
  supplies the current selected launch X and movement-tolerance calculation.
- Rope/climb navigation entry and exit predicates now live in
  `AgentNavigationRopeEdgeService`; `BotNavigationManager` delegates while
  side-effecting climb attach/broadcast behavior remains unchanged.
- Climb-exit execution readiness now lives in
  `AgentNavigationRopeEdgeService`; `BotNavigationManager` supplies only the
  existing region-to-rope lookup so rope-to-rope, top step-off, and rope-jump
  exit timing stay unchanged.
- Grind-mode path target clamping now lives in
  `AgentNavigationGrindTargetService`; `BotNavigationManager` delegates with
  the same live grinding flag and raw target fallback behavior.
- Jump/drop navigation launch-window checks now live in
  `AgentNavigationLaunchWindowService`; `BotNavigationManager` delegates while
  preserving the null-graph straight-drop fallback.
- Directional-drop runway checks now also live in
  `AgentNavigationLaunchWindowService`; the physics-coupled directional landing
  match remains in `BotNavigationManager` until the walk-off landing seam moves.
- Dead `BotNavigationManager.landingRegionId` was removed after audit showed no
  remaining production or test callers.
- Stale `BotNavigationManager.NO_MOVEMENT_WALK_TOLERANCE` was removed after
  no-movement walk policy moved to `AgentNavigationPathService`.
- Pure jump and straight-drop waypoint selection now lives in
  `AgentNavigationWaypointService`; directional-drop physics matching remains in
  `BotNavigationManager`.
- Entry-backed cached/random jump launch X selection now lives in
  `AgentNavigationWaypointService`; `BotNavigationManager` delegates through a
  temporary helper while larger waypoint routing migrates.
- Climb waypoint selection now lives in `AgentNavigationWaypointService` with
  `BotNavigationManager` supplying the existing climb-exit readiness callback.
- Precise waypoint targeting policy now lives in
  `AgentNavigationPreciseTargetService`; `BotNavigationManager` supplies the
  existing jump/drop/climb readiness callbacks.

Current navigation correction:

- `BotNavigationManager` has been fully migrated into Agent navigation services
  and deleted. Older notes above are historical breadcrumbs for the slice that
  introduced each Agent seam; they no longer describe the current navigation
  shell state.

Current physics correction:

- Movement reset, teleport, and death-pose state clearing now live in
  `AgentMovementPoseService`. `BotPhysicsEngine` retains only temporary
  compatibility delegates for those entry points while the remaining collision,
  ground, air, rope, swim, and packet-visible physics runtime migrates.
- Idle and prone-on-ground pose state clearing now also lives in
  `AgentMovementPoseService`; the bot physics methods are compatibility
  delegates.
- Queued down-jump/top-rope state and top-rope entry validation now live in
  `AgentQueuedMovementActionService`. Down-jump launch still enters
  `BotPhysicsEngine` until the shared airborne launch primitive migrates.
- Air-knockback state application now lives in `AgentKnockbackMovementService`.
  Knockback launch still enters `BotPhysicsEngine` until the shared airborne
  launch primitive migrates.
- First-climbable rope Y and walkable endpoint-step navigation policy now live
  in `AgentNavigationPhysicsService`; bot physics delegates remain for
  compatibility.
- Foothold walk-across connectivity policy now also lives in
  `AgentNavigationPhysicsService`; navigation graph region merging calls the
  Agent service directly.
- Ground-to-rope reach checks now live in `AgentJumpProbeService`; bot physics
  delegates remain for compatibility while lower-level rope-grab simulation
  migrates later.
- Ground-motion stop state writes now live in `AgentGroundPhysicsService`;
  `BotPhysicsEngine.stopGroundMotion` remains a compatibility delegate.
- Movement jump-probe callers now use the Agent-owned `AgentJumpLanding` DTO.
  The temporary bot landing simulator still supplies the data until collision
  simulation moves.
- Navigation graph jump-cache callers now use the Agent-owned
  `AgentPostLandingJump` DTO. The temporary bot post-landing simulator still
  supplies the data through `AgentJumpProbeService`.
- Navigation graph fall, down-jump, and rope-jump landing callers now use the
  Agent-owned `AgentJumpLanding` DTO through `AgentJumpProbeService`. The
  bot physics simulator remains the temporary backing implementation until the
  remaining collision/integration body is reconstructed.
- Directional-drop waypoint validation now uses the Agent-owned
  `AgentWalkOffLanding` DTO through `AgentJumpProbeService`. The simulator
  still delegates to `BotPhysicsEngine` until walk-off ground/air integration
  is moved.
- Navigation graph physics probes now enter through `AgentJumpProbeService`
  and `AgentGroundCollisionService` for fall/rope-grab estimates, rope-grab
  simulations, and runway wall checks. `AgentNavigationGraphService` no longer
  imports `BotPhysicsEngine` directly.
- Navigation build walk-region lookup storage now lives in
  `AgentNavigationWalkRegionLookupService`; `BotPhysicsEngine` only resolves
  through that Agent-owned service while its ground-step preview body remains.
- Runtime hook bundles now use Agent-owned movement services for ground lookup,
  teleport, idle-ground pose, and jump-height calculations. The runtime package
  no longer imports `BotPhysicsEngine` directly.
- Fidget movement remains Agent-owned for jump, pose, collision, ground-motion,
  and broadcast work, and no longer carries a stale `BotPhysicsEngine` import.
- Combat knockback launch now lives in `AgentKnockbackMovementService`.
  `BotPhysicsEngine.beginKnockback` remains only as a compatibility delegate.
- Attach-to-rope state application now lives in `AgentRopeMovementService`.
  Hold/advance climb still enter `BotPhysicsEngine` until climb boundary and
  fall handling migrate.
- Airborne landing eligibility now lives in `AgentAirbornePhysicsService`;
  `BotPhysicsEngine.canLand` remains a package-level compatibility delegate.
- Pure movement kinematics formulas now live in `AgentMovementKinematicsService`.
  `BotPhysicsEngine` delegates its public formula entry points there while
  remaining integrators continue to use the same values.
- Airborne runtime stepping now lives in `AgentAirbornePhysicsService`.
  `BotPhysicsEngine.stepAirborne` is only a temporary compatibility delegate;
  the Agent service owns air steering, airborne position integration, air
  collision handling, landing transition cleanup, and fall-damage handoff.
- Swim runtime stepping now lives in `AgentSwimPhysicsService`.
  `BotPhysicsEngine.applySwimMotion` is only a temporary compatibility
  delegate; the Agent service owns swim rebasing, swim impulse/intent,
  drag/gravity/thrust, collision sweep handling, grounded landing handoff, and
  packet-visible swim velocity updates.
- Unreachable private air/swim helper bodies should be deleted from
  `BotPhysicsEngine` after their Agent-owned service migrations pass focused
  compatibility tests; only the public temporary delegate entry points remain.
- Historical bot-named movement regression tests should move to Agent packages
  once the tested behavior has Agent-owned APIs. The corner fall-through
  regression now exercises Agent knockback, airborne stepping, timers, pose,
  and grounding services directly.
- Bot-named test harnesses that remain during reconstruction should still call
  Agent-owned movement services when the tested behavior has moved. Recent
  movement tests now use Agent ground-collision, movement-config, and rope
  movement services directly.
- Navigation regression tests should call Agent rope/navigation physics seams
  for rope attach and first-climbable-Y behavior once those services own the
  behavior.
- Navigation graph tests should call Agent jump-probe DTOs/services and Agent
  ground-collision services directly once probe behavior has moved out of the
  bot physics shell.
- The final bot-physics compatibility shell has been removed. Physics
  regression coverage now lives under `server.agents.capabilities.movement` and
  calls Agent movement/navigation services directly. Production/test
  `src/main/java/server/bots/**` and `src/test/java/server/bots/**` have been
  removed; remaining `server.bots` references in reconstruction docs are
  historical baseline paths or migration map rows.
- Reply queue storage now lives in `AgentMessageQueueState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while the Agent integration
  adapter delegates queue locking, enqueue/poll/peek, snapshots, sending-state,
  and idle checks to the Agent command module.
- Scroll-reaction storage now lives in `AgentScrollReactionState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while load decay, cooldown,
  streak, and pruning adapters use Agent social state types instead of a
  BotEntry inner state class.
- Script task queue storage now lives in `AgentScriptTaskQueueState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while task queueing, active
  task selection, clearing, and activity epoch checks route through the Agent
  plan state.
- Map tracking storage now lives in `AgentMapTrackingState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while current-map id,
  foothold-index snapshots, null-index handling, and read-only index exposure
  route through the Agent runtime state.
- Leader activity storage now lives in `AgentLeaderActivityState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while AFK position/timing,
  offline-or-dead recovery timing, returned-to-town safe mode, and last matched
  leader command metadata route through the Agent runtime state.
- Airshow session storage now lives in `AgentAirshowState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while active/inactive state
  and trail timestamp reads/writes route through the Agent social airshow
  capability.
- Formation offset storage now lives in `AgentFormationOffsetState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while per-Agent follow offset
  reads/writes route through the Agent runtime state.
- Pending chat action storage now lives in `AgentPendingActionState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while pending
  action and pending drop-category reads/writes route through the Agent
  dialogue capability.
- Pending loot-offer storage now lives in `AgentPendingLootOfferState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while offered
  item, recipient id, expiry timestamp, and bot-requesting flag reads/writes
  route through the Agent trade capability.
- Inventory cooldown storage now lives in `AgentInventoryCooldownState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while loot inhibit
  and inventory-full warning cooldown reads/writes route through the Agent
  inventory capability.
- Potion supply storage now lives in `AgentPotionSupplyState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while potion-check timer, MP
  recovery timer, and HP/MP share-request flag reads/writes route through the
  Agent supplies capability.
- Ammo supply storage now lives in `AgentAmmoSupplyState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while ammo share-request,
  no-ammo, and ammo-warning flag reads/writes route through the Agent supplies
  capability.
- Build prompt/progression storage now lives in `AgentBuildState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while AP build selection, AP
  prompt flag, SP variant/prompt flag, job-prompt milestone, and
  last-known-level reads/writes route through the Agent build capability.
- Queued trade-retry storage now lives in `AgentTradeRetryState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while bot-initiated trade
  retry callback and retry-delay reads/writes route through the Agent trade
  capability.
- Portal cooldown storage now lives in `AgentPortalCooldownState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while portal-use cooldown
  deadline reads/writes and cooldown checks route through the Agent navigation
  capability.
- Combat cooldown storage now lives in `AgentCombatCooldownState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while attack cooldown,
  movement-only attack window, touch-damage cooldown, alert pose expiry, and
  alert reset scheduling route through the Agent combat capability.
- Mob-touch sweep storage now lives in `AgentMobTouchState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while last checked position
  and map id reads/writes route through the Agent combat capability.
- Death/respawn window storage now lives in `AgentDeathState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while death deadline,
  dead-state entry, and respawn-due checks route through Agent runtime.
- Shop visit lifecycle storage now lives in `AgentShopState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while visit/sequence flags,
  NPC and target positions, approach delay, sell-trash flag, timeout windows,
  and stuck-near-NPC fallback tracking route through the Agent shop capability.
- Buff automation/debug storage now lives in `AgentBuffState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while consumable-buff
  enablement, cheap/max mode, scan/action timing, consumable action summary,
  and skill-buff debug action summary route through the Agent combat
  capability.
- Upgrade-offer/proactive gear suggestion storage now lives in
  `AgentUpgradeOfferState`. `BotEntry` temporarily hosts the Agent-owned state
  object, while proactive-offer enablement, requested upgrade item tracking,
  pending gear-prompt reservation timing, spawn-upgrade check status, and next
  gear-suggestion timing route through the Agent trade capability.
- Navigation debug/path-log storage now lives in `AgentNavigationDebugState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while path logger,
  last navigation decision, last edge block reason, and graph-warmup fallback
  reads/writes route through the Agent navigation capability.
- Navigation target waypoint storage now lives in `AgentNavigationTargetState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while target
  position, target region id, and precise-target flag reads/writes route
  through the Agent navigation capability.
- Active navigation edge and cached jump-launch storage now live in
  `AgentNavigationEdgeState`. `BotEntry` temporarily hosts the Agent-owned
  state object, while committed edge presence, active edge lookup, and
  jump-launch edge/X cache reads/writes route through the Agent navigation
  capability.
- Manual trade invite timing/reference storage now lives in
  `AgentManualTradeState`. `BotEntry` temporarily hosts the Agent-owned state
  object, while pending trade reference, accept-delay timer, and timeout timer
  reads/writes route through the Agent trade capability.
- Leader/owner motion observation storage now lives in `AgentOwnerMotionState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while last
  observed owner position and per-tick owner step deltas route through Agent
  runtime state.
- Tick/heartbeat/follow-idle metadata now lives in `AgentTickState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while last tick
  type/time, heartbeat time, and next follow-idle movement check time route
  through Agent runtime state.
- Tick failure window metadata now lives in `AgentTickFailureState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while failure
  count and failure-window start time route through Agent runtime state.
- Stuck/unstuck movement metadata now lives in `AgentMovementStuckState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while stuck
  duration, unstuck cooldown, and stuck-check coordinates route through Agent
  runtime state.
- Movement broadcast suppression cache state now lives in
  `AgentMovementBroadcastState`. `BotEntry` temporarily hosts the Agent-owned
  state object, while last broadcast position, velocity, stance, and foothold
  route through Agent runtime state.
- Last-ground foothold cache state now lives in
  `AgentMovementPhysicsCacheState`. `BotEntry` temporarily hosts the
  Agent-owned state object, while grounded movement and airshow trail readers
  route through Agent runtime state.
- Pending trade sequence state now lives in `AgentPendingTradeSequenceState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while pending
  category, items, recipient, meso, timers, flags, share budget, and restore
  slot mapping route through the Agent trade capability.
- Owner-given trade item tracking now lives in `AgentOwnerGivenTradeItemState`.
  `BotEntry` temporarily hosts the Agent-owned identity set while trade
  completion and trade reset route through the Agent trade capability.
- Farm/sentry anchor state now lives in `AgentFarmAnchorState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while farm-anchor position
  and map id route through Agent runtime state.
- Patrol region/wander state now lives in `AgentPatrolState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while patrol region, map id,
  and wander target route through Agent runtime state.
- Active grind target and search cadence state now lives in
  `AgentGrindTargetState`. `BotEntry` temporarily hosts the Agent-owned state
  object, while combat target selection and search cooldowns route through
  Agent runtime state.
- Grind loot target and retry suppression state now lives in
  `AgentGrindLootState`. `BotEntry` temporarily hosts the Agent-owned state
  object, while loot targeting and retry gating route through the Agent
  looting capability.
- Standalone move-target storage now lives in `AgentMoveTargetState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while explicit
  move-here/script/fidget movement targets route through Agent runtime state.
- Grind no-target wander direction storage now lives in
  `AgentGrindWanderState`. `BotEntry` temporarily hosts the Agent-owned state
  object, while free-grind fallback direction reads/writes route through Agent
  runtime state.
- Ranged retreat hold storage now lives in `AgentRetreatHoldState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while anti-oscillation retreat
  position and expiry reads/writes route through Agent runtime state.
- Surround-breakout commitment storage now lives in `AgentBreakoutState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while escape
  direction and hard timeout reads/writes route through Agent runtime state.
- AoE reposition commitment storage now lives in `AgentAoeRepositionState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while sweet-spot
  anchor, bounded-chase deadline, and arrival/expiry checks route through Agent
  runtime state.
- Degenerate close-range attack latch storage now lives in
  `AgentDegenerateAttackState`. `BotEntry` temporarily hosts the Agent-owned
  state object, while ranged-grind spacing and cleanup paths route through
  Agent runtime state.
- Combat skill cache storage now lives in `AgentCombatSkillCacheState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while job/level
  signature matching, attack/AoE/heal skill ids, buff ids, and summon-skill
  buckets route through the Agent combat capability.
- Combat buff/support automation state now lives in `AgentCombatBuffState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while skill-buff
  toggles, support-heal toggles, rebuff cooldowns, and support-buff cooldowns
  route through the Agent combat capability.
- Combat buff/support and combat skill-cache state wrappers have been removed
  from `BotEntry`. Agent runtime code now reaches those state objects through
  `AgentCombatBuffStateRuntime` and `AgentCombatSkillCacheStateRuntime`,
  leaving `BotEntry` as a temporary state-object host only for these combat
  surfaces.
- Supply and combat positioning state wrappers have been removed from
  `BotEntry`. Ammo warning/no-ammo state, potion timer state, inventory warning
  cooldowns, mob-touch sweep cache, degenerate attack latch, retreat hold,
  breakout, and AoE reposition state now route through Agent-owned state
  adapters or direct Agent-owned state accessors.
- Pending trade sequence wrappers have been removed from `BotEntry`. Trade
  category, recipient, item batch, meso, timer, invite, completion, budget, and
  restore-slot state now route through `AgentPendingTradeStateRuntime` and
  the Agent-owned `AgentPendingTradeSequenceState`.
- Build prompt/progression wrappers have been removed from `BotEntry`. AP
  build selection, AP/SP prompt flags, SP variant, job prompt level, and last
  known level now route through `AgentBuildStateRuntime` and
  `AgentBuildState`.
- Follow/grind mode wrappers have been removed from `BotEntry`. Following,
  grinding, and follow-target id reads/writes now route through
  `AgentModeStateRuntime` and the Agent-owned `AgentModeState`.
- Potion/ammo share and loot-inhibit wrappers have been removed from
  `BotEntry`. These now route through `AgentPotionStateRuntime`,
  `AgentAmmoStateRuntime`, `AgentInventoryStateRuntime`, and their
  Agent-owned supply/inventory state objects.
- Scheduled-task and script-task queue wrappers have been removed from
  `BotEntry`. Scheduler handles route through `AgentScheduledTaskState` and
  `AgentScheduledTaskRuntime`; script task queue operations route through
  `AgentScriptTaskStateRuntime` and `AgentScriptTaskQueueState`.
- Airshow state wrappers have been removed from `BotEntry`. Airshow active
  state and trail timing now route through `AgentAirshowStateRuntime` and
  `AgentAirshowState`.
- Manual trade invite wrappers have been removed from `BotEntry`. Manual trade
  reference, accept delay, and timeout state now route through
  `AgentManualTradeStateRuntime` and `AgentManualTradeState`.
- Map tracking and formation offset wrappers have been removed from `BotEntry`.
  Last-map/foothold tracking routes through `AgentMapStateRuntime`; follow
  spacing routes through `AgentFormationStateRuntime`.
- Leader motion observation wrappers have been removed from `BotEntry`. Last
  leader position and observed step deltas now route through
  `AgentOwnerMotionStateRuntime` and `AgentOwnerMotionState`.
- Upgrade-offer and gear-suggestion wrappers have been removed from
  `BotEntry`. Proactive-offer toggles, requested upgrade item tracking,
  spawn-upgrade checks, next suggestion cooldowns, and pending gear prompts now
  route through `AgentOfferStateRuntime` and `AgentUpgradeOfferState`.
- Consumable-buff and skill-buff debug wrappers have been removed from
  `BotEntry`. Buff enablement, cheap mode, scan timestamps, action summaries,
  and skill-buff debug summaries now route through `AgentBuffStateRuntime`
  and Agent-owned buff state adapters.
- Scroll reaction wrappers have been removed from `BotEntry`. Cooldown, recent
  load, observation timestamp, streak map, and prune timing now route through
  `AgentScrollReactionStateRuntime` and `AgentScrollReactionState`.
- Reply-channel wrappers have been removed from `BotEntry`. Map/party/whisper
  routing and null-to-map normalization now route through
  `AgentReplyChannelStateRuntime` and the Agent-owned
  `AgentReplyChannelState`.
- Navigation debug, target, and active-edge wrappers have been removed from
  `BotEntry`. Path logging, last decision/block reason, graph-warmup fallback,
  waypoint target, precise-target flag, active edge, and jump-launch cache now
  route through `AgentNavigationDebugStateRuntime` over Agent-owned
  navigation state objects.
- Movement stuck, last-ground foothold, and movement-broadcast cache wrappers
  have been removed from `BotEntry`. Stuck/unstuck progress routes through
  `AgentMovementStuckStateRuntime`; broadcast suppression and last-ground
  foothold cache route through Agent movement state runtimes over Agent-owned
  state objects.
- Dead KPQ reset, owner-given item set, and pending gear-prompt compatibility
  wrappers were removed from `BotEntry`. KPQ stage-5 reset now routes through
  `AgentPqRuntime` directly to `AgentKpqState`; trade and gear-prompt
  callers already use Agent-owned state adapters.
- Tick cadence and leader activity wrappers have been removed from `BotEntry`.
  Skip-delay and AI accumulator setup now routes through
  `AgentTickCadenceStateRuntime`; AFK, inactive, returned-to-town, and last
  leader-command state route through `AgentActivityStateRuntime`.
- Tick metadata and tick-failure wrappers have been removed from `BotEntry`.
  Last-tick, heartbeat, follow-idle, failure-count, and failure-window state
  now route through `AgentTickStateRuntime` and
  `AgentTickFailureStateRuntime`.
- Movement scalar physics wrappers have been removed from `BotEntry`. Vertical
  velocity, fall peak, horizontal speed, physics position, ground carry, and
  jump cooldown reads/writes now route through
  `AgentMovementPhysicsStateRuntime` over `AgentMovementPhysicsState`.
- Chat reply queue and reply-channel state adapters have moved from
  `server.agents.integration` to `server.agents.runtime`. Queue size,
  enqueue/poll/sending-state, map/party/whisper routing state, and null-to-map
  normalization are pure `AgentRuntimeEntry` state operations; packet
  delivery remains in `AgentReplyRuntime` at the Cosmic integration boundary.
- Combat positioning state adapters for surround breakout, AoE reposition,
  and retreat hold have moved from `server.agents.integration` to
  `server.agents.capabilities.combat`. They remain pure `AgentRuntimeEntry`
  state accessors; movement execution, map access, and combat side effects
  remain at their existing runtime/integration seams.
- Combat cooldown, consumable-buff, support-buff, skill-buff debug, and
  skill-cache state adapters have moved from `server.agents.integration` to
  `server.agents.capabilities.combat`. These adapters remain pure
  `AgentRuntimeEntry` state accessors; actual attack execution, buff
  packet/application work, damage mutation, and alert scheduling remain at
  their existing integration/runtime seams.
- Grind-wander state has moved from `server.agents.integration` to
  `server.agents.capabilities.combat.AgentGrindWanderStateRuntime`. It
  remains a pure `AgentRuntimeEntry` state accessor for no-target grind
  fallback direction; target selection, movement target creation, and runtime
  grind tick ordering remain at their existing runtime/capability seams.
- Scroll-reaction state has moved from `server.agents.integration` to
  `server.agents.capabilities.social.AgentScrollReactionStateRuntime`. It
  remains a pure `AgentRuntimeEntry` state accessor for reaction cooldown,
  recent-load decay, and per-scroller streak tracking; emote/fidget/reply
  side effects remain at the existing social capability and integration seams.
- Ammo and potion supply state adapters have moved from
  `server.agents.integration` to `server.agents.capabilities.supplies`.
  They remain pure `AgentRuntimeEntry` state accessors; item counting,
  consumption, inventory mutation, share-trade execution, and scheduler work
  remain at their existing capability/integration seams.
- Build prompt/progression state has moved from `server.agents.integration`
  to `server.agents.capabilities.build.AgentBuildStateRuntime`. It remains a
  pure `AgentRuntimeEntry` state accessor for AP build choice, AP/SP prompt
  flags, SP variant, last known level, and job-prompt milestone markers;
  build command handling, delayed replies, job/AP/SP mutation, and live
  character access remain at their existing runtime/integration seams.
- Inventory cooldown state has moved from `server.agents.integration` to
  `server.agents.capabilities.inventory.AgentInventoryStateRuntime`. It
  remains a pure `AgentRuntimeEntry` state accessor for loot inhibit and
  inventory-full warning cooldowns; item pickup/drop/transfer mutation and
  movement timer tick-down scheduling remain at their existing capability/
  integration seams.
- Shop transition state has moved from `server.agents.integration` to
  `server.agents.capabilities.shop.AgentShopStateRuntime`. It remains a pure
  `AgentRuntimeEntry` state accessor for pending visits, target/NPC positions,
  sequence timing, sell-trash flags, and stuck-near-NPC fallback tracking;
  shop NPC/inventory mutation and scheduled shop steps remain at their
  existing capability/integration seams.
- Movement input and facing wrappers have been removed from `BotEntry`.
  Movement velocity, movement direction, and facing direction reads/writes now
  route through `AgentMovementStateRuntime` over `AgentMovementInputState`.
- Swim mode/input/cooldown state now lives in `AgentSwimIntentState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while swimming
  mode, horizontal swim intent, vertical hold, one-shot swim jump requests, and
  swim-jump cooldown route through `AgentSwimStateRuntime` and the Agent
  movement capability.
- Climb, rope-entry, blocked-rope, rope cooldown, and down-jump wrappers have
  been removed from `BotEntry`. Those reads/writes now route through
  `AgentClimbStateRuntime` and `AgentMovementStateRuntime` over the
  Agent-owned movement state objects.
- Airborne steering, horizontal air velocity, fixed-air-arc, crouch, and
  horizontal movement hysteresis wrappers have been removed from `BotEntry`.
  Those reads/writes now route through `AgentMovementPhysicsStateRuntime`
  and `AgentMovementStateRuntime`.
- Fidget state wrappers have been removed from `BotEntry`. Fidget mode,
  trigger, timing, movement directions, visual timing, idle roll scheduling, and
  active-state mutation now route through `AgentFidgetStateRuntime`.
- Grind target and grind retarget-search wrappers have been removed from
  `BotEntry`. Active target state now routes through
  `AgentGrindTargetStateRuntime`, and search cadence routes through
  `AgentGrindSearchStateRuntime`.
- Movement profile and in-air passthrough wrappers have been removed from
  `BotEntry`. Movement profile reads/writes and in-air state now route through
  `AgentMovementStateRuntime`.
- Legacy JavaBean identity aliases `BotEntry.getBot()` and
  `BotEntry.getOwner()` have been removed. Agent identity reads now route
  through `AgentRuntimeIdentityRuntime`.
- Down-jump pending/grace-period state now lives in `AgentDownJumpState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while crouch-then
  jump pending state and airborne grace-period timing route through the Agent
  movement capability.
- Airborne horizontal steering state now lives in `AgentAirborneSteeringState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while committed
  air X velocity, corrective air-steer velocity, and fixed-air-arc mode route
  through the Agent movement capability.
- Movement input and packet-pose state now lives in `AgentMovementInputState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while move
  direction, packet movement velocity, facing direction, crouch/prone state,
  and horizontal movement hysteresis route through the Agent movement
  capability.
- Climb/rope state now lives in `AgentClimbState`. `BotEntry` temporarily
  hosts the Agent-owned state object, while rope attachment, climb direction,
  climb-up intent, blocked rope grabs, rope-grab cooldowns, and queued rope
  entries route through the Agent movement capability.
- Movement physics scalar state now lives in `AgentMovementPhysicsState`.
  `BotEntry` temporarily hosts the Agent-owned state object, while in-air
  state, physics position, vertical/horizontal velocity, ground carry,
  fall-peak tracking, and jump cooldown route through the Agent movement
  capability.
- Fidget runtime state now lives in `AgentFidgetState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while fidget mode, trigger,
  action/jump/visual timers, idle-roll scheduling, movement directions, spam
  air-steer flag, action delay, and origin position route through the Agent
  fidget capability.
- Tick cadence state now lives in `AgentTickState`. `BotEntry` temporarily
  hosts the Agent-owned state object, while initial skip delay and AI tick
  accumulator state route through the Agent runtime tick capability.
- Reply-channel state now lives in `AgentReplyChannelState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while map/party/whisper reply
  routing and null-to-map normalization route through the Agent command state.
- KPQ runtime state now lives behind `AgentKpqState` methods. `BotEntry`
  temporarily hosts the Agent-owned state object, while stage, coupon target,
  wait timer, reported coupon count, and stage-5 claim state route through the
  Agent party-quest runtime adapter.
- Script runtime progress state now lives behind `AgentScriptRuntimeState`
  methods. `BotEntry` temporarily hosts the Agent-owned state object, while
  script id, step index, entered flag, wait timer, and integer scratch values
  route through the Agent script/task runtime adapter.
- High-level follow/grind mode state now lives in `AgentModeState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while following, grinding,
  and follow-target id reads/writes route through the Agent mode runtime
  adapter.
- Movement profile storage now lives in `AgentMovementProfileState`. `BotEntry`
  temporarily hosts the Agent-owned state object, while profile refresh,
  bucketing, and null-to-base normalization route through the Agent movement
  state runtime adapter.
- Chat status and leader integration now read/write live Agent identity through
  `AgentRuntimeIdentityRuntime` and `AgentLeaderStateRuntime` rather than
  direct `BotEntry.bot()`, `BotEntry.owner()`, or `BotEntry.setOwner(...)`
  calls. The temporary shell methods remain only until all legacy-shaped callers
  are migrated.
- Utility chat callbacks now resolve bot/leader identity through
  `AgentRuntimeIdentityRuntime` before invoking the same trade, shop, and
  maker side effects. This keeps legacy scheduling and reply behavior intact
  while removing another direct `BotEntry` identity dependency from Agent
  utility dialogue.
- Transfer chat command evaluation now resolves the live Agent character through
  `AgentRuntimeIdentityRuntime` before scheduling meso transfer, inventory
  transfer scans, and named-item queries. Legacy async timing, request ordering,
  and reply decisions are unchanged.
- Supply chat requests now resolve the current leader through
  `AgentRuntimeIdentityRuntime` before potion counting and ammo weapon checks.
  Potion share, ammo share, and reply routing behavior is unchanged.
- Report operation callbacks now resolve the live Agent through
  `AgentRuntimeIdentityRuntime` before invoking the existing report and
  request-upgrade side effects. Help, gear, skill, stat, inventory, meso, exp,
  scroll, potion, and debug reporting remain behavior-identical.
- Control-triggered buff debug reports now resolve the live Agent through
  `AgentRuntimeIdentityRuntime` before forwarding to the existing chat report
  adapter. Report content and delivery behavior are unchanged.
- Build chat callbacks now resolve the live Agent through
  `AgentRuntimeIdentityRuntime` before AP build resolution, AP prompt
  creation, and SP auto-assignment. Existing AP/SP/job advancement behavior and
  replies remain unchanged.
- Active-mode preparation now resolves Agent and leader identity through
  `AgentRuntimeIdentityRuntime` before auto-equip, autopot setup, pot-share
  checks, and sibling gear suggestions. The legacy mode-entry side effects and
  cooldown behavior remain unchanged.
- Build/status checks now resolve the current leader through
  `AgentRuntimeIdentityRuntime` before recommended gear prompts and spawn
  upgrade gating. Build prompt, AP/SP assignment, idle checks, and offer
  behavior remain unchanged.
- Equipment chat callbacks now resolve Agent and leader identity through
  `AgentRuntimeIdentityRuntime` before unequip, debug, and auto-equip side
  effects. Scheduling, movement-stop behavior, replies, and pending-offer input
  remain unchanged.
- Control chat callbacks now resolve the live Agent through
  `AgentRuntimeIdentityRuntime` before buff summaries and AP/SP respec
  commands. Toggle state writes, delays, and replies remain unchanged.
- Movement diagnostics now resolve Agent and leader identity through
  `AgentRuntimeIdentityRuntime` before creating movement snapshots and
  path-log sessions. Snapshot content, defensive point copies, and navigation
  debug state behavior remain unchanged.
- Pending chat-action callbacks now resolve the live Agent through
  `AgentRuntimeIdentityRuntime` before item-choice execution and skill-tree
  follow-up handling. Pending-action state changes, delays, and replies remain
  unchanged.
- Social fame and recommended-gear report delivery now resolve Agent/leader
  identity through `AgentRuntimeIdentityRuntime`. Fame target resolution,
  fame status checks, report delivery, and offer recommendation behavior remain
  unchanged.
- Chat orchestration and AFK-return status adapters now resolve live Agent
  identity through `AgentRuntimeIdentityRuntime` for job/level checks and
  face-expression side effects. Dialogue classification and welcome-back
  behavior remain unchanged.
- Movement chat callbacks now resolve Agent and leader identity through
  `AgentRuntimeIdentityRuntime` before farm/patrol/move-here targets,
  follow/grind supply checks, fidget expressions, and greeting status checks.
  Existing delays, commands, replies, and position-copy behavior remain
  unchanged.
- Session lifecycle callbacks now resolve Agent and leader identity through
  `AgentRuntimeIdentityRuntime` before relog/logout persistence,
  disconnects, relog metadata capture, and owner-away safe-mode fanout. Existing
  confirmation prompts, delay windows, and lifecycle side effects remain
  unchanged.
- Targeted and untargeted dialogue route capabilities now accept the
  Agent-owned `AgentRuntimeHandle` boundary instead of naming `BotEntry`
  directly. `AgentChatRouteRuntime` remains the temporary adapter that converts
  legacy `BotEntry` command resolution into `AgentTargetedCommandMatch`, so
  reply-channel, typo suggestion, follow routing, LLM fall-through, and owner
  command recording behavior stay unchanged.
- Top-level chat ingress now also accepts `AgentRuntimeHandle` entries, keeping
  pending-offer, recruit, transfer, formation, dismiss, targeted, and
  untargeted route ordering unchanged while removing another capability-level
  `BotEntry` signature.
- Whisper-to-Agent routing now splits into a handle-based
  `AgentWhisperCommandService` and a temporary `AgentWhisperCommandRuntime`
  adapter. Bot-client detection, same-leader lookup, whisper reply-channel
  marking, and Agent chat dispatch remain unchanged while the capability no
  longer imports `BotEntry`.
- LLM sender-relation classification now accepts already-resolved Agent,
  leader, and sender characters instead of `BotEntry`. `AgentLlmReplyService`
  remains the adapter that reads those identities from the temporary runtime
  shell, preserving owner/party/stranger behavior.
- LLM prompt formatting now accepts resolved Agent identity, bot display name,
  and situation text instead of `BotEntry`. The prompt wording, recent-memory
  rendering, and situation snapshot remain unchanged; `AgentLlmReplyService`
  still adapts from the temporary runtime shell.
- LLM situation formatting now accepts resolved Agent/map/activity values
  instead of `BotEntry`. `AgentLlmReplyService` still reads mode, farm-anchor,
  and last-command state through temporary runtime adapters, preserving the
  existing "grinding", "following owner", map, party, mob, level, and last
  command text.
- LLM multi-message delivery now uses an Agent-owned runtime handle and reply
  emitter callback. `AgentLlmReplyService` still supplies
  `AgentLlmRuntime.replyNow` at the temporary adapter edge, preserving
  immediate/follow-up reply timing and failure swallowing.
- LLM prompt assembly now flows through `AgentLlmPromptContext`, an Agent-owned
  snapshot of identity, map, mode, farm-anchor, and last-command values. The
  context is still populated by `AgentLlmReplyService` from temporary runtime
  adapters, preserving prompt content while creating the next seam for removing
  `BotEntry` from LLM orchestration.
- LLM reply orchestration now accepts `AgentLlmReplyRequest` and
  `AgentRuntimeHandle` instead of `BotEntry`. `AgentLlmReplyRuntime` is the
  temporary adapter that reads live identity/reply-channel/mode state from the
  legacy shell, builds the request, and supplies `AgentLlmRuntime.replyNow`.
  Existing enablement checks, stranger-whisper suppression, in-flight gates,
  async scheduling, memory behavior, and reply delivery timing are unchanged.
- Potion check requests now split into a handle-based
  `AgentPotionCheckRequestService` and temporary
  `AgentPotionCheckRequestRuntime` adapter. Character/autopot call sites still
  request the same soon retry window, while BotClient detection, active-leader
  lookup, BotEntry resolution, and potion timer writes remain at the runtime
  edge.
- Group supply responder selection now accepts Agent handles plus an explicit
  Agent-map-id reader instead of `BotEntry`. The policy still prefers an Agent
  in the leader's current map and falls back to the first entry; the chat route
  runtime supplies the temporary BotEntry map-id adapter.
- Ammo donor plan data now lives in `AgentAmmoDonorPlan` under the supplies
  capability and is generic over `AgentRuntimeHandle`. The ammo service still
  uses `BotEntry` at its temporary runtime boundary, but donor scoring,
  selection fields, and scheduled transfer behavior are unchanged.
- Skill report decision assembly now lives in
  `AgentSkillReportDecisionService` under the dialogue capability. The
  temporary chat report runtime only applies that Agent-owned decision through
  the pending-action adapter, preserving reply text and pending skill-tree
  prompt behavior.
- Range report assembly now lives in `AgentRangeReportService` under the
  dialogue capability. Chat report delivery and equipment debug dumps call the
  Agent-owned service directly, preserving the same damage/range text.
- Movement report snapshot formatting now lives in
  `AgentMovementDialogueReporter`. The temporary chat report runtime still
  obtains the movement kinematics snapshot from the integration edge, but the
  conversion from snapshot values to legacy movement report lines is
  dialogue-owned.
- Trade queued-retry, between-batch, and recipient resolution helpers now
  accept `AgentRuntimeEntry` instead of `BotEntry`. The trade capability still
  uses the same pending-trade and identity state adapters, preserving retry
  timing, batch advancement, and recipient lookup behavior while removing
  another direct bot-shell dependency from pure trade services.
- Trade all-items-added completion marking now accepts `AgentRuntimeEntry`
  instead of `BotEntry`. The service still reads the same pending-trade item
  index/list state, marks the same all-items-added flag, clears the same timer,
  and sends the same trade-window chat line.
- Reply delivery adapters now accept `AgentRuntimeEntry` at the
  `AgentReplyRuntime` and `AgentInventoryRuntime` boundaries. The same
  reply-channel, message-queue, identity, scheduler, whisper, party, and map
  broadcast paths are used; this only moves the adapter seam off the bot shell.
- Trade tick/control helpers now accept `AgentRuntimeEntry` for sequence start,
  tick dispatch, invite wait, confirmation wait, closed-window handling,
  cancellation, completion reactions, item-add ticks, item-add execution,
  command profiling, and peer-trade ticks. Existing callers still pass the
  temporary runtime shell where needed, but these Agent-owned trade services no
  longer require the bot shell type.
- Shop and Maker reply bridges now accept `AgentRuntimeEntry` for immediate
  replies. Delayed execution and map-say helpers are unchanged, preserving shop
  and Maker automation timing while moving the reply seam off `BotEntry`.
- LLM reply and scroll-reaction queue bridges now accept `AgentRuntimeEntry`.
  They still delegate to the same Agent reply queue/scheduler paths, preserving
  reply delivery and delayed scroll reaction behavior.
- Combat action-lock ticking now accepts `AgentRuntimeEntry`. It still uses the
  same attack-cooldown and move-window state with the same movement timer
  countdown policy.
- Movement packet snapshot and broadcast services now accept
  `AgentRuntimeEntry`. Packet construction, stance resolution, foothold
  z-layer cache behavior, duplicate-broadcast suppression, and performance
  monitoring are unchanged.
- Combat death-state entry and attack-facing memory wrappers now accept
  `AgentRuntimeEntry`. Dead pose marking, movement broadcast, death timer
  state, optional death dialogue, and attack-facing stance sync are unchanged.
- Movement motion-timer countdown now accepts `AgentRuntimeEntry`; down-jump
  grace-period countdown math is unchanged.
- Navigation edge execution target state now accepts `AgentRuntimeEntry`; it
  still writes the same navigation waypoint/debug state.
- Map environment swim-map detection now accepts `AgentRuntimeEntry`; it still
  resolves the Agent map through the same runtime identity adapter.
- Script task start dispatch now accepts `AgentRuntimeEntry`; MOVE_TO, follow,
  grind, stop, and drop-item hook dispatch remains unchanged.
- Live leader session refresh now accepts `AgentRuntimeEntry`; cached leader
  reuse, offline/mismatch refresh, and null caching behavior are unchanged.
- Script move-target cheapness checks now accept `AgentRuntimeEntry`; bot
  position, map, graph lookup, fallback range, and path-cost behavior are
  unchanged.
- Combat skill-cache rebuild and attack planning wrappers now accept
  `AgentRuntimeEntry`; cache signatures, skill classification, candidate
  planning, attack scoring, and performance recording are unchanged.
- Agent mode transitions now accept `AgentRuntimeEntry`; follow/grind/stop,
  move-to, farm, patrol, active-mode reset ordering, and navigation clear hooks
  are unchanged.
- Command-mode preparation now accepts `AgentRuntimeEntry`; null guard,
  can-start guard, script-task clearing, shop cancellation, and mode-start hook
  ordering are unchanged.
- Script task queue operations now accept `AgentRuntimeEntry`; null guards,
  activity-epoch bumps, queue ordering, move/drop/follow task construction, and
  queued-task checks are unchanged.
- Heartbeat ticking now accepts `AgentRuntimeEntry`; heartbeat due checks,
  timestamp marking, client last-packet updates, and movement broadcast side
  effects are unchanged.
- Script item drop execution now accepts `AgentRuntimeEntry`; runtime identity
  lookup, inventory lookup, quantity clamping, and the final
  `InventoryManipulator.drop` call are unchanged.
- Scheduled-task cancellation now accepts `AgentRuntimeEntry`; null guards,
  scheduled-task presence checks, and `ScheduledFuture.cancel(false)` behavior
  are unchanged.
- Action-lock physics dispatch now accepts `AgentRuntimeEntry`; attack-cooldown
  gating, swim/airborne/grounded branch selection, and movement-phase callbacks
  are unchanged.
- Target snapshot steering helpers now accept `AgentRuntimeEntry`; navigation
  waypoint override lookup and primary-target fallback behavior are unchanged.
- Final movement tail dispatch now accepts `AgentRuntimeEntry`; the live-mode
  wrapper still calls the same movement core with the same target and AI-tick
  flag.
- Idle-mode tick dispatch now accepts `AgentRuntimeEntry`; the live-gate
  wrapper still calls the same idle physics path with the same Agent character
  and performance flag.
- Movement phase dispatch now accepts `AgentRuntimeEntry`; climb, swim,
  airborne, and grounded branch ordering and callbacks are unchanged.
- Movement core tick orchestration now accepts `AgentRuntimeEntry`; navigation
  resolution, precise-target marking, fidget short-circuiting, phase dispatch,
  committed-edge execution, stuck detection, and move-target cleanup ordering
  are unchanged.
- Precise navigation target marking now accepts `AgentRuntimeEntry`; precise
  move-target checks and active-edge suppression are unchanged.
- Live tick gate ordering now accepts `AgentRuntimeEntry`; common, trade,
  idle, recovery, and tracked-map-change gate order and short-circuit behavior
  are unchanged. `AgentLiveTickGateRuntime` remains the temporary compatibility
  bridge to BotEntry-shaped downstream tick callbacks.
- Live tick context preparation now accepts `AgentRuntimeEntry`; movement
  profile refresh, follow-anchor resolution, target snapshot capture, observed
  leader motion updates, map-change cleanup, and follow-action cleanup ordering
  are unchanged.
- Tick core orchestration now accepts `AgentRuntimeEntry`; preflight,
  leader-resolution, inactive-leader, ownerless, death, live-context, live-gate,
  and live-mode ordering are unchanged. `AgentTickCoreRuntime` remains the
  temporary compatibility bridge to BotEntry-shaped downstream callbacks.
- Tick preflight now accepts `AgentRuntimeEntry`; null handling, airshow
  skip, movement-delay skip, missing-map cleanup, heartbeat, offer expiry, and
  AI tick preparation ordering are unchanged.
- Trade-window tick gating now accepts `AgentRuntimeEntry`; open-trade
  detection, physics-only tick dispatch, and consumed-tick behavior are
  unchanged.
- Ownerless tick handling now accepts `AgentRuntimeEntry`; follow-mode clearing,
  map-change grounding short-circuit, standalone move-target ticking, and idle
  fallback behavior are unchanged.
- Death tick and respawn-near-leader handling now accept `AgentRuntimeEntry`;
  dead-state entry checks, respawn timing, HP restore, map-change, grounding,
  teleport, reset, movement broadcast, map speech, and glare emote behavior are
  unchanged.
- Recovery tick handling now accepts `AgentRuntimeEntry`; shop-visit follow-sync
  suppression, follow-map sync, party recovery, target recovery ordering, and
  short-circuit behavior are unchanged. `AgentLiveTickGateRuntime` remains the
  temporary bridge to BotEntry-shaped recovery callbacks.
- Tracked map-change tick handling now accepts `AgentRuntimeEntry`; map-change
  handler dispatch and consumed/fall-through behavior are unchanged.
  `AgentLiveTickGateRuntime` remains the temporary bridge to BotEntry-shaped
  map-change callbacks.
- Map transition grounding and tracked-map-change handling now accept
  `AgentRuntimeEntry`; tracking checks, foothold index capture, grounding
  teleport, reset, graph warmup, movement broadcast, grind/follow/PQ dispatch,
  shop map-change, and status-check ordering are unchanged.
- Recovery teleport distance handling now accepts `AgentRuntimeEntry`; target
  distance checks, out-of-bounds checks, grind-party constraints, shop-visit
  suppression, multiplier math, grounding lookup, teleport/reset, and movement
  broadcast side effects are unchanged.
- Cross-map follow synchronization now accepts `AgentRuntimeEntry`; follow-mode
  gating, same-map/null-anchor skip behavior, grounded anchor spawn selection,
  idle-on-ground, map change, and movement reset side effects are unchanged.
- Follow-target command application now accepts `AgentRuntimeEntry` collections;
  target resolution, null/missing/self-target filtering, reply queuing, delay
  scheduling, auto-equip, potion sharing, and follow-start ordering are
  unchanged.
- Follow-target candidate selection now accepts Agent runtime sibling entries;
  leader inclusion, party-member filtering, sibling-agent filtering, and
  duplicate suppression are unchanged.
- Formation state lookup and offset application now accept Agent runtime
  entries; formation store access, leader/default resolution, and offset
  assignment patterns are unchanged.
- Formation command handling now accepts Agent runtime entries; formation
  command matching, help/status replies, snap range updates, formation writes,
  offset application, and first-entry/leader reply routing are unchanged.
- Common per-tick system ordering now accepts `AgentRuntimeEntry`; mob damage,
  death short-circuiting, monster release, passive loot/trade gating, potion and
  recovery ticks, build level-up checks, AFK/status checks, trade/manual-trade,
  PQ/script/NPC-lock gates, action-lock handling, AI-gated combat systems, and
  final action-lock return behavior are unchanged.
- Live-mode tick phase ordering now accepts `AgentRuntimeEntry`; shop-visit,
  follow-opportunity, follow-idle, scripted-move combat, anchored farm, grind
  dispatch, target propagation, consumed-tick short-circuits, and final movement
  tail behavior are unchanged.
- Shop-visit tick gating now accepts `AgentRuntimeEntry`; pending-shop checks,
  shop tick execution, active target lookup, approach-delay consumption, target
  movement stepping, and result propagation are unchanged.
- Standalone move-target ticking now accepts `AgentRuntimeEntry`; map-change
  grounding, movement-profile refresh, stored-target lookup, and movement-step
  dispatch are unchanged.
- Movement-only map-change handling now accepts `AgentRuntimeEntry`; map
  tracking, foothold index rebuild, ground-point resolution, teleport/reset,
  movement broadcast, shop map-change hook, and status check ordering are
  unchanged.
- Follow-opportunity ticking now accepts `AgentRuntimeEntry`; following/climbing
  gates, same-map and distance checks, local opportunity attack delegation, and
  target propagation are unchanged.
- Anchored-farm mode gating now accepts `AgentRuntimeEntry`; farm-anchor
  presence checks, anchored-farm tick delegation, and consumed-tick behavior are
  unchanged.
- Anchored-farm tick service now accepts `AgentRuntimeEntry`; anchor
  map-mismatch cleanup, local opportunity attack delegation, near-anchor idle,
  precise move-target setting, and movement-core dispatch are unchanged.
- Farm-anchor map-change maintenance now accepts `AgentRuntimeEntry`; same-map
  retention, map-change anchor clearing, and precise move-target cleanup are
  unchanged.
- Follow-idle fast-path evaluation now accepts `AgentRuntimeEntry`; eligibility
  gates, recheck timing, navigation decision marking, and stuck-progress reset
  behavior are unchanged.
- Action-lock physics service tests now exercise the existing
  `AgentRuntimeEntry` boundary directly; attack-cooldown gating and swim,
  airborne, grounded, and legacy climbing branches are unchanged.
- Action-lock physics runtime now accepts `AgentRuntimeEntry`; attack-lock
  physics gating and swim/airborne/grounded dispatch are unchanged.
- Ground grind-target adjustment now accepts `AgentRuntimeEntry`; grind-mode
  gates, active-navigation bypass, graph warmup, same-region detection,
  rope-region bypass, and safe edge-margin clamping are unchanged.
- Navigation warmup notification now accepts `AgentRuntimeEntry`; leader/map
  throttle checks, walkable-foothold threshold, and fallback notification text
  are unchanged.
- Portal navigation execution now accepts `AgentRuntimeEntry`; portal cooldown
  checks, portal-use success detection, cooldown update, navigation reset, and
  movement state reset are unchanged.
- Airborne launch now accepts `AgentRuntimeEntry`, with ground-motion stopping
  lifted to the same boundary; air-state initialization, climb intent,
  horizontal/vertical velocity setup, down-jump clearing, movement velocity
  projection, and character state sync are unchanged.
- Knockback movement now accepts `AgentRuntimeEntry`; facing preservation,
  blocked-rope clearing, airborne launch reuse, air knockback velocity setup,
  and character state sync are unchanged.
- Command-mode service tests now exercise the existing `AgentRuntimeEntry`
  boundary directly; null-entry skip behavior, guard evaluation, task clearing,
  shop cancellation, and mode-start ordering are unchanged.
- Final-movement tail service tests now exercise the existing
  `AgentRuntimeEntry` boundary directly; target and AI-tick flag delegation to
  movement-core hooks is unchanged.
- Live-tick gate service tests now exercise the existing `AgentRuntimeEntry`
  boundary directly; common, trade-window, idle, recovery, and tracked-map
  gate ordering and short-circuit behavior are unchanged.
- Tick-core service tests now exercise the existing `AgentRuntimeEntry`
  boundary directly; preflight short-circuiting, leader resolution, inactive,
  ownerless, dead, live-context, live-gate, and live-mode ordering are
  unchanged.
- Live-tick context service tests now exercise the existing
  `AgentRuntimeEntry` boundary directly; movement-profile refresh, follow-anchor
  resolution, target snapshot capture, leader observation, map-change cleanup,
  and move-window update ordering are unchanged.
- Map-environment service tests now exercise the existing `AgentRuntimeEntry`
  boundary directly; null-map handling and swim-map detection are unchanged.
- Heartbeat service tests now exercise the existing `AgentRuntimeEntry`
  boundary directly; interval gating, heartbeat timestamp update, last-packet
  update, and movement broadcast behavior are unchanged.
- Idle-physics service now accepts `AgentRuntimeEntry`; active-mode gating,
  swim/airborne/grounded physics dispatch, idle stance correction, and movement
  broadcast behavior are unchanged.
- Stuck-detection service now accepts `AgentRuntimeEntry`; cooldown ticking,
  active-navigation gates, stuck-position tracking, stuck timer accumulation,
  cooldown blocking, and unstuck trigger behavior are unchanged.
- Movement-phase service tests now exercise the existing `AgentRuntimeEntry`
  boundary directly; climb priority, swim-map airborne dispatch, non-swim
  airborne dispatch, and grounded dispatch are unchanged.
- Local attack move-window service now accepts `AgentRuntimeEntry`; null-position
  clearing, long/short/settled window timing, follow-mode gating, and settle
  band checks are unchanged.
- Grind-mode dispatch service now accepts `AgentRuntimeEntry`; non-grinding
  fall-through, grind tick delegation, run-AI flag propagation, and target
  result propagation are unchanged.
- Grind no-target fallback service now accepts `AgentRuntimeEntry`; target
  clearing, swim/airborne fall-through, wander-direction side effects,
  patrol/no-grind target resolution, and movement-step dispatch are unchanged.
- Grind target-search service and policy now accept `AgentRuntimeEntry`;
  AI-tick gating, retarget cooldown checks, patrol/grind target selection, AoE
  cluster switch hysteresis, and next-search scheduling are unchanged.
- Grind navigation-tail service now accepts `AgentRuntimeEntry`; cross-region
  retreat precedence, AoE reposition navigation, degenerate-attack latch
  clearing, patrol gating, and convenient-loot override behavior are unchanged.
- Grind target-commitment service now accepts `AgentRuntimeEntry`; target
  commit, wander/patrol cleanup, ranged-priority replacement, closer threat
  replacement, target-position propagation, and attack-plan invalidation are
  unchanged.
- Grind ranged-engagement service now accepts `AgentRuntimeEntry`;
  degenerate-attack gating, ranged retreat selection, AoE reposition checks,
  attack execution, cooldown comparison, jump initiation, idle-on-ground, and
  movement broadcast behavior are unchanged.
- Navigation precise-target service now accepts `AgentRuntimeEntry`; airborne
  suppression, walk precision policy, jump/drop/climb readiness gating, and
  portal readiness behavior are unchanged.
- Navigation waypoint service now accepts `AgentRuntimeEntry`; cached jump
  launch selection, drop/runway fallback, airborne handling, climb exit
  readiness, and rope-X targeting behavior are unchanged.
- Navigation committed-edge service now accepts `AgentRuntimeEntry`; pending
  climb-exit refresh, committed ground-edge replacement, active-edge reuse, and
  stale-edge rejection behavior are unchanged.
- Navigation edge-execution state tests now exercise `AgentRuntimeEntry`;
  non-precise end-point waypoint writes remain unchanged.
- Rope movement actions now accept `AgentRuntimeEntry`; rope attach, climb hold,
  climb advance, ground jump, climb-up jump, rope jump, rope-transfer jump,
  swim jump fallback, top landing, and fall-damage handoff behavior are
  unchanged.
- Navigation climb execution now accepts `AgentRuntimeEntry`; rope attach and
  movement-broadcast side effects are unchanged.
- Jump action initiation now accepts `AgentRuntimeEntry`; signed air velocity,
  ground jump, fixed-arc jump, rope jump, and movement broadcast behavior are
  unchanged.
- Queued movement actions now accept `AgentRuntimeEntry`; down-jump queueing,
  top-rope entry queueing, invalid down-jump clearing, valid down-jump launch,
  grace-period setting, and queued rope attach behavior are unchanged.
- Navigation drop execution now accepts `AgentRuntimeEntry`; airborne, climbing,
  pending down-jump, launch-step, readiness, edge-target, queued down-jump, and
  broadcast behavior are unchanged.
- Navigation climb-entry execution now accepts `AgentRuntimeEntry`; rope lookup,
  position gating, block reasons, direct attach, top-platform queueing, rope
  jump entry, and broadcast behavior are unchanged.
- Climb movement runtime now accepts `AgentRuntimeEntry`; climb ticking, idle
  hold, precise snap, rope jump-off, rope transfer, climb action selection, and
  performance recording behavior are unchanged.
- Navigation climb-exit execution now accepts `AgentRuntimeEntry`; exit
  readiness, rope-transfer exit, top-rope alignment, jump-off, and movement
  behavior are unchanged.
- Navigation jump execution now accepts `AgentRuntimeEntry`; in-air/climbing
  gates, selected-launch readiness, rope fallback attach, block reasons, edge
  target marking, and jump initiation behavior are unchanged.
- Navigation edge execution dispatch now accepts `AgentRuntimeEntry`; run-AI
  gating, jump/drop/climb/portal branch selection, consumed-tick directives,
  and climb entry/exit dispatch behavior are unchanged.
- Swim movement and swim physics now accept `AgentRuntimeEntry`; swim intent
  clearing, attack-cooldown gating, arrival/up/down intent selection, swim
  velocity integration, collision landing, and airborne landing handoff behavior
  are unchanged.
- Death tick and respawn runtime no longer cast through `BotEntry`; dead-state
  entry, respawn timing, HP restore, leader-map change, grounding, teleport,
  movement reset, movement broadcast, map speech, and glare emote behavior are
  unchanged.
- Formation command runtime now queues replies with `AgentRuntimeEntry`
  directly; formation parsing, leader fallback help, formation state writes,
  offset application, and visible reply text are unchanged.
- Follow-idle movement runtime now accepts `AgentRuntimeEntry`; configured
  follow/stop distance lookup, fast-path eligibility, recheck cadence, decision
  label, and stuck-progress reset behavior are unchanged.
- Tick state maintenance now clears reached move targets and patrol-on-map-change
  through `AgentRuntimeEntry`; arrival-distance handling, precise target
  preservation, patrol map matching, and live tick context ordering are
  unchanged.
- Movement-only tick ordering service now accepts `AgentRuntimeEntry`; no-agent
  and null-target guards, idle/follow-map/recovery/map-change/shop/follow-idle
  short-circuit order, and final movement-core dispatch behavior are unchanged.
- Tick cadence preparation now accepts `AgentRuntimeEntry`; AI-tick accumulator
  carry, last-tick timestamp, and last-tick-AI recording behavior are unchanged.
- Movement-only runtime boundary now accepts `AgentRuntimeEntry`; the remaining
  BotEntry casts are limited to downstream movement/shop/recovery adapters, and
  movement-only tick ordering plus config propagation are unchanged.
- Movement-only direct point-step runtime now accepts `AgentRuntimeEntry`;
  default config lookup, last-tick timestamp usage, follow-anchor resolver
  wiring, and movement-only runtime dispatch behavior are unchanged. The
  snapshot-taking overload remains BotEntry-backed until target snapshot runtime
  is migrated.
- Target snapshot service now accepts `AgentRuntimeEntry`; follow-anchor
  resolution, formation lookup, shop/move/farm/grind target precedence, primary
  target source labels, and copied point behavior are unchanged.
- Target snapshot runtime now accepts `AgentRuntimeEntry`; runtime-registry
  sibling lookup, formation map lookup, follow-anchor resolution, and target
  snapshot capture behavior are unchanged while registry storage remains a
  temporary BotEntry-backed compatibility seam.
- Runtime registry now exposes a read-only `AgentRuntimeEntry` view for
  leader-scoped entries. The mutable registry storage remains BotEntry-backed,
  while follow-anchor and target-snapshot read paths no longer request
  BotEntry-shaped lists.
- Owner item notification now reads leader entries through the Agent runtime
  registry view, and the gear-upgrade notification entry point accepts
  `AgentRuntimeEntry`; equip filtering, self-agent trade suppression, deferred
  offer scan scheduling, and upgrade recommendation behavior are unchanged.
- Loot eligibility now accepts `AgentRuntimeEntry`; drop presence checks,
  quest-item gating, inventory-full gating, KPQ pass/rice-cake exclusions,
  KPQ coupon target/skip behavior, and bot-inventory-drop age delays are
  unchanged.
- Loot target selection now accepts `AgentRuntimeEntry`; nearest grind-loot
  target selection, retry suppression callbacks, passive-radius exclusion,
  seek-range filtering, patrol-region adjacency checks, and inventory-full
  guards are unchanged.
- Passive loot capability and runtime callback boundaries now accept
  `AgentRuntimeEntry`; loot-inhibit/trade-sequence gates, cooldown ticks,
  inventory-full warnings, NX-card owner pickup routing, ghost-drop cleanup,
  auto-equip, and loot-offer prompt scheduling behavior are unchanged. The
  remaining BotEntry cast is isolated at the inventory integration adapter for
  the still BotEntry-shaped offer scheduler.
- KPQ Stage 5 reward tick now accepts `AgentRuntimeEntry`; map gating,
  already-claimed gating, event clear detection, reward grant, inventory
  snapshot comparison, claimed-state marking, and reward announcement behavior
  are unchanged.
- Party-quest runtime state bridge no longer imports `BotEntry`; KPQ state,
  coupon target, coupon progress, stage-5 claimed state, queued PQ dialogue,
  and stage-1 reset behavior now accept `AgentRuntimeEntry` while preserving
  the same state bag and script-reset side effect.
- Party-quest gate checks now accept `AgentRuntimeEntry`; NPC-lock, KPQ
  grind-required, and PQ follow-required decisions no longer require runtime
  casts in common tick or map-transition gates, and their map/state predicates
  are unchanged.
- KPQ Stage 5 map-change reset now routes through the Agent-entry PQ bridge
  directly, preserving the existing reset timing during tracked map changes.
- Offer runtime reply and gear-prompt helper methods now accept
  `AgentRuntimeEntry`; immediate replies, queued-say delay estimation,
  pending-prompt checks, prompt reservation, reserved-prompt checks, and
  prompt clearing still delegate to the same reply/state runtimes. The
  recommended-gear action callback remains BotEntry-shaped until the offer
  service itself is widened.
- Pending-offer state cleanup in `AgentOfferService` now accepts
  `AgentRuntimeEntry`; pending-offer checks, expiration, owner-request cleanup,
  pending drop-category clearing, pending-offer clearing, and gear-prompt
  clearing behavior are unchanged, and tick preflight no longer casts for
  offer expiration.
- Owner upgrade-request entry in `AgentOfferService` now accepts
  `AgentRuntimeEntry`; owner lookup, busy/no-upgrade replies, recommendation
  selection, requested-item memory, and upgrade-request prompt creation are
  unchanged.
- Offer reservation classification now accepts `AgentRuntimeEntry`; owner
  lookup, throwing-star recipient checks, equipment-only gating, future-reserve
  checks, and eligible bot-recipient filtering are unchanged.
- Chat status owner-activity and offline-return bridge methods now accept
  `AgentRuntimeEntry`; leader position lookup, status-state update, bot
  identity lookup, and offline-return announcement behavior are unchanged.
- Chat status AFK-check bridge methods now accept `AgentRuntimeEntry`;
  owner-position sampling, AFK timer state, delayed welcome-back scheduling,
  expression changes, and reply behavior are unchanged, and common tick no
  longer casts for this AFK status callback.
- Airshow-active status checks now accept `AgentRuntimeEntry`; tick preflight
  reads the same airshow state without a BotEntry cast, preserving the existing
  skip behavior while airshow movement itself remains a separate capability
  slice.
- Manager-status offline-return announcement now accepts `AgentRuntimeEntry`;
  inactive-leader recovery still invokes the same chat-status announcement path
  with unchanged delayed party message and expression behavior.
- Status-check, gear-suggestion, and recommended-gear report state adapters now
  accept `AgentRuntimeEntry`; spawn-upgrade flags and gear-suggestion cooldowns
  still read/write the same Agent upgrade-offer state.
- Manager and movement status-check bridge methods now accept
  `AgentRuntimeEntry`; spawn delayed status checks, tracked-map status checks,
  movement-only map-change status checks, and movement-triggered status checks
  still invoke the same build-status backend.
- Movement active-mode status preparation now accepts `AgentRuntimeEntry`;
  auto-equip, gear-suggestion cooldown reset, sibling gear suggestions,
  autopot setup, and mode-start potion sharing still route through the same
  active-mode backend.
- Session-control primary-session and away-town checks now accept
  `AgentRuntimeEntry`; primary entry detection, leader entry lookup, and town
  offer map checks are unchanged.
- Fidget leader-idle status checks now accept `AgentRuntimeEntry`; fidget
  eligibility still reads the same chat owner-idle state.
- Mob-touch sweep runtime now accepts `AgentRuntimeEntry`; previous-position
  lookup, sweep bounds, lower-half intersection, and remembered touch position
  behavior are unchanged.
- Social/fame chat callbacks now accept `AgentRuntimeEntry`; target resolution,
  self-fame handling, fame eligibility checks, fame mutation, and reply behavior
  are unchanged.
- Chat status facade no longer imports `BotEntry`; build-status and active-mode
  backend seams now accept `AgentRuntimeEntry` while preserving the same
  build, equipment, offer, autopot, and potion-share side effects.
- Movement target snapshot facade and side-effect bridge now accept
  `AgentRuntimeEntry`; target capture, sibling follow-anchor resolution,
  navigation waypoint steering override, and defensive snapshot copying are
  unchanged.
- Combat ammo-check runtime now accepts `AgentRuntimeEntry`; projectile low/out
  warnings, mage MP-pot detection, warning-state clearing, follow fallback, and
  map dialogue replies are unchanged. The movement command backend remains a
  temporary BotEntry seam beyond its widened follow-owner entrypoint.
- Combat support-heal runtime now accepts `AgentRuntimeEntry`; heal skill
  selection, party/self heal gating, undead target collection, jump-heal
  positioning, cooldowns, attack packet emission, and movement broadcast remain
  unchanged. Sibling-anchor lookup still uses the temporary session lifecycle
  bridge and is left as a later runtime-registry slice.
- Combat support-buff runtime now accepts `AgentRuntimeEntry`; skill-buff
  readiness gates, living-mob checks, party support rebuff detection, support
  skill execution, cooldown updates, alert marking, and legacy debug summaries
  remain unchanged while common tick no longer casts for this buff callback.
- Consumable buff-pot service now accepts `AgentRuntimeEntry`; enabled/cheap
  state, scan cadence, monster-presence gating, inventory selection, ACC
  need checks, use-item application, last-action notes, chat summaries, and
  debug formatting remain unchanged while common tick no longer casts for this
  buff-pot callback.
- Common tick now calls the already Agent-entry-based action-lock cooldown and
  combat skill-cache rebuild callbacks directly; cooldown decay, skill-cache
  signature checks, cache reset, attack/AOE/heal/summon/support bucket
  selection, and rebuff scheduling remain unchanged.
- Mob damage runtime now accepts `AgentRuntimeEntry`; touch-damage rolling,
  mob-touch sweep, damage broadcast, HP/MP mutation, mob-hit cooldowns, alert
  marking, death entry, stance/knockback gating, and movement broadcast remain
  unchanged. Common tick also calls the existing Agent-entry death-state/death
  entry callbacks without redundant casts.
- Combat target selection and combat debug reporting now accept
  `AgentRuntimeEntry`; grind/patrol/follow target search, graph scoring,
  immediate projectile target checks, region occupancy penalties, debug stats,
  consumable-buff debug lines, and skill-buff debug lines remain unchanged.
  Grind-mode target-search hooks now call the Agent-entry selectors directly.
- Ranged priority target selection now accepts `AgentRuntimeEntry`; no-ammo
  gating, ranged weapon checks, degenerate-target replacement, attack-plan
  routing, range checks, and grounded-use gating remain unchanged. Grind-mode
  priority-target hooks now call the Agent-entry selector directly.
- Grind navigation retreat target selection now accepts `AgentRuntimeEntry`;
  retreat holds, breakout direction selection, cross-region retreat scoring,
  projectile retreat probes, local retreat validation, path lookup, and region
  resolution remain unchanged. Grind-mode navigation hooks now call the
  Agent-entry selector directly.
- Potion-check request runtime now uses `AgentRuntimeEntry` for the generic
  potion-check request service hook; BotClient detection, active-leader lookup,
  character-id registry lookup, and retry-soon delay behavior are unchanged.
- Shop purchase sequencing now uses
  `AgentShopPurchaseSequence<E extends AgentRuntimeHandle>` and generic
  purchase actions. The current shop service still binds the sequence to the
  temporary `BotEntry` compatibility edge, preserving recharge, fixed-ammo,
  HP/MP potion purchase, shortfall reporting, sell-trash continuation, and
  delayed step scheduling behavior.
- Trade sequence orchestration now accepts `AgentRuntimeEntry`; recipient
  missing replies, sequence initialization, batch initialization, busy-recipient
  cancellation, trade invite side effects, invitation reply gating, and pending
  trade state behavior are unchanged while the runtime service remains the
  temporary `BotEntry` adapter.
- Trade transfer availability runtime now accepts `AgentRuntimeEntry`; named
  item counts, equipped-slot checks, recommended-item collection, owner lookup,
  and count-vs-availability behavior are unchanged while inventory tick/runtime
  adapters continue to pass the temporary compatibility entry.
- Script plan interfaces, script context, script runner, and party-quest script
  hooks now use `AgentRuntimeEntry`; KPQ Stage 1 applicability, script state
  reset, queued movement/combat/drop tasks, cheap-move checks, and Stage 5 tick
  dispatch behavior are unchanged while lower runtime adapters remain
  compatibility-bound where needed.
- Tick failure policy now accepts `AgentRuntimeEntry`; failure counting,
  volatile action cleanup, warning/idle/disable escalation, failure-context
  reporting, missing-entry logging, and reset behavior are unchanged while the
  runtime hook keeps the temporary `BotEntry` stop-command adapter.
- Tick orchestration now accepts `AgentRuntimeEntry`; guarded tick execution,
  failure reset, failure handler dispatch, and performance timing behavior are
  unchanged while `AgentTickRuntime` remains the temporary adapter for
  BotEntry-shaped movement command callbacks.
- Tick-core map-transition and standalone-move callbacks now pass
  `AgentRuntimeEntry` through the ownerless/tracked-map-change path; map-change
  grounding, PQ grind/follow mode switching, shop map-change handling,
  standalone move-target ticking, and legacy movement-command issue callbacks
  are unchanged while remaining physics/script/live-mode adapters still cast at
  their compatibility edges.
- Script-task ticking now uses `AgentRuntimeEntry` through the task runtime,
  tick loop, and execution start/complete boundary; queued task activation,
  completion checks, movement/follow/grind/stop/drop dispatch, and configured
  stop-distance behavior are unchanged while movement command execution remains
  the temporary BotEntry adapter.
- Dismiss lifecycle hooks now accept `AgentRuntimeEntry`; entry removal,
  scheduled-task cancellation, stop-command dispatch, delayed farewell timing,
  and farewell reply behavior are unchanged while spawn/register lifecycle
  hooks remain temporary BotEntry-backed boundaries.
- Idle physics runtime now accepts `AgentRuntimeEntry`; trade-window physics,
  idle-mode physics, ownerless idle physics, anchored-farm idle, and
  movement-only idle dispatch behavior are unchanged. Movement phase dispatch
  remains a later BotEntry-backed movement capability slice.
- Common-tick script task callbacks now pass `AgentRuntimeEntry` through
  tick-core and live-gate dispatch; script task cadence, common tick ordering,
  PQ lock handling, action-lock gating, and script task execution behavior are
  unchanged.
- Movement phase dispatch now accepts `AgentRuntimeEntry`; action-lock
  physics, idle physics, and grind no-target fallback callers no longer own
  BotEntry compatibility casts. Climb/swim dispatch remains directly
  Agent-entry based, while airborne/ground movement still adapt inside the
  movement capability until those lower movement services are reconstructed.
  Action-lock, idle, swim, airborne, grounded, and grind fallback behavior are
  unchanged.
- Movement phase runtime now exposes an `AgentRuntimeEntry` boundary above the
  phase service and dispatch hooks. The movement tick runtime may still supply
  the temporary compatibility entry, but phase selection no longer depends on
  the bot package type.
- Follow-map sync runtime now accepts `AgentRuntimeEntry`; cross-map follow
  grounding, idle stance, map-change, and movement-state reset behavior remain
  unchanged while movement-only and recovery live-gate callers no longer cast
  for this hook.
- Formation runtime now accepts `AgentRuntimeEntry` and
  `List<? extends AgentRuntimeEntry>`; formation lookup, stored leader
  formation state, and offset application behavior are unchanged.
- Fidget social/greeting entry points now accept `AgentRuntimeEntry`; the
  temporary fidget side-effect bridge no longer imports the bot package while
  social fidget eligibility, random mode selection, and start-state behavior
  remain unchanged.
- Equipment chat callbacks now accept `AgentRuntimeEntry`; unequip-slot,
  unequip-all, auto-equip debug, auto-equip, reply timing, and stop-before-
  unequip-all behavior remain unchanged through the movement command adapter.
- Chat report runtime now accepts `AgentRuntimeEntry`; report callback
  orchestration, help delivery, stats/range/build/inventory/meso/exp/slot/
  scroll/potion/debug reporting, skill report pending-action behavior, and
  recommended gear reporting remain unchanged while remaining BotEntry
  adaptation moves into supply, offer, and pending-action adapters.
- Targeted bot command parser resolution is now generic over
  `AgentRuntimeEntry`; existing BotEntry lists still resolve identically, while
  the parser itself no longer imports the bot package.
- Movement chat callbacks now accept `AgentRuntimeEntry`; farm-here, patrol,
  move-here, follow, grind, stop, fidget, greeting, delayed replies, active-mode
  preparation, and pot-share checks remain unchanged through Agent-entry
  overloads on the movement command and potion adapters.
- Follow-target runtime now accepts lists of `AgentRuntimeEntry`; target
  resolution, sibling/leader candidate lookup, queued follow replies, delayed
  auto-equip, pot-share checks, and follow command dispatch remain unchanged.
- Dismiss runtime and lifecycle chat command dismiss wiring now accept
  `AgentRuntimeEntry` stoppers; removal, scheduled-task cancellation, delayed
  farewell replies, and stop-command behavior remain unchanged.
- Leader session runtime now accepts `AgentRuntimeEntry`; cached leader refresh,
  world storage lookup, and tick-core leader resolution behavior remain
  unchanged while tick-core no longer casts for this resolver.
- Spawn placement service now accepts generic `AgentRuntimeHandle` hooks. The
  BotEntry-specific agent/leader identity lookup is isolated in
  `AgentSpawnPlacementRuntime`, while teleport, movement reset, death clear,
  map tracking, navigation warmup, cadence reset, broadcast invalidation, and
  party HP update order remain unchanged.
- Tick preflight runtime now accepts `AgentRuntimeEntry`; null-entry short
  circuiting, airshow skip, delay skip, missing-map cleanup, heartbeat, pending
  offer expiry, and AI tick preparation behavior remain unchanged.
- Live tick context runtime now accepts `AgentRuntimeEntry`; movement profile
  refresh, follow-anchor resolution, target snapshot capture, leader motion
  observation, owner-position memory, map-change cleanup, and follow action
  window cleanup behavior remain unchanged.
- Local attack move-window runtime now accepts `AgentRuntimeEntry` for its
  configured-distance wrappers; set and clear behavior still delegates to
  `AgentLocalAttackMoveWindowService` with the same movement config values.
- Standalone move-target runtime now stays on `AgentRuntimeEntry` through
  movement-core dispatch. The temporary cast is isolated in
  `AgentMovementTickRuntime` while map-change grounding, movement profile
  refresh, move-target lookup, unstuck config, and stop-distance behavior remain
  unchanged.
- Stuck detection runtime now accepts `AgentRuntimeEntry`; movement timers,
  stuck progress checks, cooldown updates, and recovery jump behavior remain
  unchanged while the temporary recovery cast is isolated in
  `AgentMovementRecoveryService`.
- Recovery teleport runtime now accepts `AgentRuntimeEntry`; ground lookup,
  teleport pose, reset-after-teleport, movement broadcast, teleport distance,
  out-of-bounds distance, and grind-party multiplier behavior remain unchanged.
- Movement-only map-change runtime now accepts `AgentRuntimeEntry`; foothold
  index rebuild, grounding, teleport pose, reset, broadcast, shop visit refresh,
  and manager status checks remain unchanged.
- Map transition runtime now accepts `AgentRuntimeEntry` across grounding and
  tracked map-change hooks; grind/follow reissue, KPQ reset, shop refresh, and
  manager status behavior remain unchanged.
- Movement-only runtime now uses Agent entry overloads for follow-anchor
  resolution, shop visit ticking, and movement-core dispatch. Movement-only tick
  ordering and target selection behavior remain unchanged.
- Movement-only step runtime now accepts `AgentRuntimeEntry` for ownerless
  movement tick preparation; bot existence checks, AI cadence, target snapshot
  capture, leader motion memory, and movement-only dispatch remain unchanged.
- Leader safety service now accepts `AgentRuntimeEntry` for inactive-leader
  timers, active-leader return cleanup, town-return eligibility, idle-safe-mode
  state, town cluster target resolution, and batch safe-mode issuance. The
  BotEntry dependency remains isolated in `AgentLeaderSafetyRuntime` while
  behavior stays unchanged.
- Trade lifecycle service now accepts `AgentRuntimeEntry` for trade sequence
  cancellation, manual-trade clearing, reset, completion reactions, and
  callback construction. `AgentTradeLifecycleRuntimeService` keeps the
  temporary BotEntry callback adaptation for existing trade runtime callers.
- Manual trade service now accepts `AgentRuntimeEntry` for trade-window timeout
  bookkeeping, greeting cleanup, state clearing, and delayed invite acceptance.
  Existing manual owner/peer trade flow still enters through
  `AgentManualTradeRuntimeService`, preserving behavior.
- Trade sequence runtime service now accepts `AgentRuntimeEntry` for starting
  trade sequences and opening trade batches. Recipient resolution, trade start,
  invite, cancel-unavailable handling, and first-batch invitation replies still
  flow through the same sequence callbacks.
- Supply share trade service now accepts `AgentRuntimeEntry` for potion/ammo
  share orchestration. Empty-item skips, retry queueing when a trade is active,
  share budget setup, trade sequence initialization, and trade invitation
  replies remain unchanged.
- Pending offer chat route service now accepts generic `AgentRuntimeEntry`
  groups instead of BotEntry groups. Offer expiry, targeted response parsing,
  same-map recipient filtering, confirmation handling, and speaker feedback
  remain unchanged; deeper offer/transfer methods still contain staged BotEntry
  compatibility points for later slices.
- Equipped slot trade service now accepts `AgentRuntimeEntry` for preparing
  equipped-slot trade items and restoring temporarily moved equipment. Cash
  filtering, bag-capacity checks, move ordering, restore-slot memory, and
  cleanup behavior remain unchanged.
- Inventory sell-trash service now accepts `AgentRuntimeEntry` while collecting
  sell-trash equipment. Safe item selection, self-upgrade reserve filtering,
  offer-reservation filtering, equip sorting, and trash classification remain
  unchanged.
- Inventory drop service now accepts `AgentRuntimeEntry` for category routing
  and drop helper replies. Drop-limit handling, safe-drop filtering,
  InventoryManipulator calls, named-item lookup, category ordering, and legacy
  reply strings remain unchanged.
- Inventory/trade facade routing now accepts `AgentRuntimeEntry` through
  `AgentInventoryTickRuntime`, `AgentInventoryTransferService`,
  `AgentManualTradeRuntimeService`, `AgentTradeTickRuntimeService`, and
  `AgentTradeLifecycleRuntimeService`. Passive loot, manual trade, transfer
  category routing, grouped trade batches, retry handling, invite waits,
  item-add ticks, trade confirmation, lifecycle reset, and completion reactions
  remain unchanged.
- Ammo share service now accepts `AgentRuntimeEntry` for low-ammo checks,
  request routing, donor selection, owner-share offers, and scheduled ammo
  share execution. Thresholds, cooldown/backoff behavior, donor scoring,
  same-ammo surplus limits, reply timing, and supply-share transfer behavior
  remain unchanged.
- Potion share service now accepts `AgentRuntimeEntry` for potion checks,
  passive recovery, low-pot share requests, owner-share offers, donor
  selection, donor plans, and scheduled pot share execution. Autopot setup,
  HP/MP thresholds, cooldown/backoff behavior, low-donor replies, passive
  recovery math, low-pot grind stop behavior, and supply-share transfer
  behavior remain unchanged.
- Offer service now accepts `AgentRuntimeEntry` for recommended gear offers,
  sibling offers, loot-offer scheduling, prompt auto-accept, recipient
  resolution, and gear-offer need checks. Offer reservations, idle/busy gates,
  same-map sibling filtering, prompt timing, confirmation handling, gear
  scoring, and throwing-star recipient behavior remain unchanged.
- Build service, starter-kit service, and build chat runtime now accept
  `AgentRuntimeEntry` for AP build selection, AP/SP auto-assignment, respec
  prompts, level-up checks, job advancement callbacks, starter-kit grants,
  build status refresh, and AP confirmation replies. Prompt text, stat
  allocation order, SP variant gating, job-prompt thresholds, starter-kit
  contents, auto-equip refresh, and reply timing remain unchanged.
- Maker service now accepts `AgentRuntimeEntry` for monster-crystal and
  trash-equip disassembly batches. Maker skill gating, active-batch guard,
  leftover scanning, trash-equip selection, client-lock retry behavior,
  activity-epoch interruption, step timing, abort reasons, and completion
  replies remain unchanged.
- Build status runtime now stays on `AgentRuntimeEntry` when creating
  status-check actions. Job/AP/SP prompt lookup, auto-assignment callbacks,
  gear suggestion gates, spawn-upgrade offering, pending-offer checks, and
  queued build replies remain unchanged.
- Utility, supply, and transfer chat runtimes now accept `AgentRuntimeEntry`.
  Trade-invite timing, sell-trash shop visit scheduling, Maker command delays,
  potion/ammo request replies, upgrade request routing, async transfer
  evaluation, request-id superseding, and transfer result decisions remain
  unchanged. `AgentShopService` now exposes an Agent-entry sell-trash overload
  while deeper shop internals remain staged.
- Active-mode runtime now stays on `AgentRuntimeEntry` for preparation
  callbacks. Auto-equip, gear suggestion cooldown reset, sibling gear
  suggestion, autopot setup, and mode-start potion-share checks remain
  unchanged.
- Grounded physics, grounded movement runtime, and fallback movement now accept
  `AgentRuntimeEntry`. Ground position sync, no-ground fall launch, graph-warmup
  fallback steering, rope/drop/swim/jump immediate actions, ground-action
  execution, packet-visible velocity writes, and performance timing remain
  unchanged.
- Local opportunity attack and combat attack execution now accept
  `AgentRuntimeEntry`. Follow-opportunity target selection, retreat decisions,
  jump-to-target gating, attack-plan readiness, damage packet construction,
  cooldown updates, facing memory, alert marking, and move-window writes remain
  unchanged.
- Grind-mode tick service and runtime entry points now accept
  `AgentRuntimeEntry`. Target seek, cached loot validation, no-target fallback,
  target commitment, ranged engagement, navigation-tail target resolution, and
  loot radius/seek range hook wiring remain unchanged. A few downstream grind
  callbacks still adapt to the temporary `BotEntry` compatibility shell.
- Grind combat helper callbacks now accept `AgentRuntimeEntry` for AoE
  reposition resolution and priority ranged target selection. Existing AoE
  anchor, target-priority, and ranged-threat selection behavior remains
  unchanged.
- Movement command runtime now exposes one `AgentRuntimeEntry` API for follow,
  stop, move-to, farm-here, patrol, and grind commands. Script-task clearing,
  shop-visit cancellation, navigation-state clearing, patrol-region lookup,
  missing-region reply text, and mode state transitions remain unchanged.
- Session lifecycle side-effect lookup methods now return `AgentRuntimeEntry`
  instead of exposing `BotEntry` lists. Relogin dispatch, active-leader lookup,
  inactive safe-mode issuing, and name/leader lookup behavior remain unchanged.
- Fidget runtime now accepts `AgentRuntimeEntry` throughout. Idle/social/speed
  mismatch fidget selection, prone/sideways/jump actions, return-to-origin
  behavior, movement broadcasts, and visual prone-attack packets remain
  unchanged.
- Navigation target resolution and movement-core ticking now accept
  `AgentRuntimeEntry`. Graph warmup fallback, swim fallback, committed-edge
  reuse/refresh, path logging, waypoint/precise-target selection, post-ground
  edge execution, fidget short-circuiting, phase dispatch, stuck detection, and
  move-target cleanup remain unchanged.
- Leader safety runtime now accepts `AgentRuntimeEntry`. Active-leader return
  cleanup, inactive town eligibility, safe-mode entry, town-scroll fallback,
  town-cluster formation target selection, map-change grounding, movement reset,
  and return announcements remain unchanged.
- Anchored farm runtime hooks now use `AgentRuntimeEntry` for local opportunity
  attacks, idle movement/broadcast, and movement-core stepping. Anchored farm
  target handling, idle/farm physics, and movement ordering remain unchanged.
- Script-task execution, tick-failure recovery, and interaction command wiring
  now call `AgentMovementCommandRuntime` through its `AgentRuntimeEntry`
  API instead of casting command callbacks back to `BotEntry`. Script start,
  failure idling, recruit/transfer/dismiss, follow, grind, and stop behavior
  remain unchanged.
- Movement recovery, movement phase dispatch, and ground-action planning now
  stay on `AgentRuntimeEntry` for unstuck jumps, airborne dispatch, ground-step
  planning, ledge fallback checks, and mob-avoidance jump checks. Random
  unstuck direction, navigation-state clearing, airborne motion, and ground
  action selection remain unchanged.
- Live-mode tick callback interfaces and grind-mode runtime hooks now use
  `AgentRuntimeEntry`. Shop visit tick routing, follow-opportunity attacks,
  follow-idle movement, scripted move/combat, anchored farm dispatch, grind
  dispatch, final movement tail, jump initiation, idle pose, and movement
  broadcasting keep the same ordering and behavior.
- Movement-triggered status facade ownership now sits in
  `server.agents.capabilities.movement.AgentMovementStatusRuntime`. The
  facade still delegates to the integration chat/status boundary for active-mode
  status checks and random fidget expressions, so movement callback behavior and
  reply timing are unchanged.
- Shop service runtime entry points and purchase/sell-trash sequence helpers
  now use `AgentRuntimeEntry`. Map-change resupply detection, sell-trash
  visits, shop approach, purchase/recharge steps, sell-trash steps, shortfall
  reporting, sequence validation, and scheduled shop-step guards remain
  unchanged.
- Tick runtime and tick-core runtime public seams now use `AgentRuntimeEntry`.
  Guarded tick execution, preflight, leader resolution, inactive-leader
  handling, ownerless movement, dead-tick handling, live-context preparation,
  live gates, live mode dispatch, and tick-failure handling remain in the same
  order with unchanged behavior.
- Lifecycle registration, spawn placement, interaction registration, and the
  runtime registry now store and return `AgentRuntimeEntry` directly instead
  of the temporary `BotEntry` compatibility shell. Registration scheduling,
  replacement/cancel behavior, spawn normalization, formation offsets, spawn
  status checks, dismiss/remove cleanup, active-leader lookup, and registry
  snapshots remain unchanged.
- Live leader/anchor state access now lives in
  `server.agents.runtime.AgentLeaderStateRuntime`. It still stores the same
  live `Character` reference on `AgentRuntimeEntry.identityState()` and keeps
  tick-leader refresh behavior unchanged.
- Map/foothold tracking state access now lives in
  `server.agents.runtime.AgentMapStateRuntime`. It still stores the same last
  map id and copied foothold index on `AgentRuntimeEntry.mapTrackingState()`;
  map-change, spawn placement, and movement-only refresh behavior are
  unchanged.
- Manual trade invite state access now lives in
  `server.agents.capabilities.trade.AgentManualTradeStateRuntime`. It still
  adapts the same `AgentRuntimeEntry.manualTradeState()` fields for trade
  reference, timeout, accept delay, and clear behavior; manual trade handling
  and inventory tick behavior are unchanged.
- Offer and upgrade prompt state access now lives in
  `server.agents.capabilities.trade.AgentOfferStateRuntime`. It still adapts
  the same pending loot-offer, gear-prompt, proactive-upgrade, and gear
  suggestion fields on `AgentRuntimeEntry`; offer scheduling, pending-offer
  chat routing, auto-equip checks, and status reporting are unchanged.
- Grind target state access now lives in
  `server.agents.capabilities.combat.AgentGrindTargetStateRuntime`. It still
  stores the same active `Monster` reference on `AgentRuntimeEntry`, including
  alive/map/seek-range validation; grind target snapshots, fallback clearing,
  commitment, and combat report behavior are unchanged.
- Combat skill-cache rebuild logic now lives in
  `server.agents.capabilities.combat.AgentCombatSkillCacheRuntime`. It still
  scans the same learned skills, classifies attack/AoE/summon/buff/heal
  buckets, and writes the same skill-cache state; common tick routing and
  combat parity behavior are unchanged.
- AoE reposition planning now lives in
  `server.agents.capabilities.combat.AgentCombatAoeRepositionRuntime`. It still
  uses the same cluster scoring, hitbox shift, DPS threshold, and debug logging
  behavior; reposition command dispatch and combat tests are unchanged.
- Attack-plan orchestration now lives in
  `server.agents.capabilities.combat.AgentCombatPlanRuntime`. It still builds
  the same cached-skill and basic-attack candidates, applies the same scoring,
  and records the same `combat-plan` performance metric; combat execution and
  reporting behavior are unchanged.
- Delayed Agent callback scheduling now lives in
  `server.agents.runtime.AgentSchedulerRuntime`. It still delegates to the same
  `TimerManager` schedule call and uses the same inclusive/exclusive random
  delay window; delayed dialogue/status/item behavior is unchanged.
- Session-control orchestration now lives in
  `server.agents.runtime.AgentSessionControlRuntime`. It still uses the same
  primary-session check and delegates owner-away safe-mode effects to the
  existing integration lifecycle boundary; away prompt behavior is unchanged.
- Combat reply/timing facade now lives in
  `server.agents.capabilities.combat.AgentCombatRuntime`. It still delegates
  map-chat delivery to `AgentReplyRuntime` and delayed callbacks to
  `AgentSchedulerRuntime`; combat alert and ammo/death reply behavior is
  unchanged.
- Ammo share reply/timing facade now lives in
  `server.agents.capabilities.supplies.AgentAmmoRuntime`. It still delegates
  map-chat delivery to `AgentReplyRuntime` and delayed callbacks/random delays
  to `AgentSchedulerRuntime`; ammo request/share behavior is unchanged.
- Potion share reply/timing facade now lives in
  `server.agents.capabilities.supplies.AgentPotionRuntime`. It still delegates
  map-chat delivery to `AgentReplyRuntime` and delayed callbacks/random delays
  to `AgentSchedulerRuntime`; potion request/share behavior is unchanged.
- Shop automation reply/timing facade now lives in
  `server.agents.capabilities.shop.AgentShopRuntime`. It still delegates
  visible replies to `AgentReplyRuntime` and delayed shop steps/random delays
  to `AgentSchedulerRuntime`; resupply and sell-trash behavior is unchanged.
- Build AP/SP/job and status orchestration now lives in
  `server.agents.capabilities.build.AgentBuildRuntime` and
  `server.agents.capabilities.build.AgentBuildStatusRuntime`. They still use
  the same reply, owner lookup, starter-kit, equipment, and offer services;
  build prompt, AP/SP assignment, job advancement, and gear suggestion behavior
  are unchanged.
- Equipment chat callback orchestration now lives in
  `server.agents.capabilities.equipment.AgentEquipmentRuntime`. It still uses
  the same delayed reply scheduling, movement stop, equipment service, and
  pending-offer lookup; unequip and auto-equip chat behavior is unchanged.
- Offer reply/timing/state orchestration now lives in
  `server.agents.capabilities.trade.AgentOfferRuntime`. It still delegates
  visible replies to `AgentReplyRuntime`, owner-idle checks to the status
  runtime, and delayed callbacks to `AgentSchedulerRuntime`; offer prompt,
  auto-accept, and recommended-gear behavior is unchanged.
- Supply request and upgrade-request orchestration now lives in
  `server.agents.capabilities.supplies.AgentSupplyRuntime`. It still delegates
  live leader lookup to `AgentRuntimeIdentityRuntime`, visible replies to
  `AgentReplyRuntime`, and delayed callbacks to `AgentSchedulerRuntime`; HP/MP
  potion, ammo, and upgrade-request behavior is unchanged.
- Transfer command and item-query orchestration now lives in
  `server.agents.capabilities.trade.AgentTransferRuntime`. It still delegates
  live Agent lookup to `AgentRuntimeIdentityRuntime`, visible replies to
  `AgentReplyRuntime`, delayed callbacks to `AgentSchedulerRuntime`, and
  inventory/trade mutation to `AgentInventoryTransferService`; transfer
  command behavior is unchanged.
- Utility chat command orchestration now lives in
  `server.agents.capabilities.dialogue.AgentUtilityRuntime`. It still delegates
  live Agent lookup to `AgentRuntimeIdentityRuntime`, visible replies to
  `AgentReplyRuntime`, delayed callbacks to `AgentSchedulerRuntime`, shop/maker
  work to their existing services, and direct server trade invitation mutation
  to `AgentTradeInviteGateway`; utility command behavior is unchanged.
- Toggle, buff-query, and respec chat callback orchestration now lives in
  `server.agents.capabilities.dialogue.AgentControlRuntime`. It still delegates
  live Agent lookup to `AgentRuntimeIdentityRuntime`, visible replies to
  `AgentReplyRuntime`, delayed callbacks to `AgentSchedulerRuntime`, and
  detailed buff/build reports to the existing report/build services; control
  command behavior is unchanged.
- Pending chat-action orchestration now lives in
  `server.agents.capabilities.dialogue.AgentPendingActionRuntime`. It still
  delegates live Agent lookup to `AgentRuntimeIdentityRuntime`, visible replies
  to `AgentReplyRuntime`, delayed callbacks to `AgentSchedulerRuntime`, session
  confirmations to `AgentSessionRuntime`, and inventory choice execution to
  `AgentInventoryTransferService`; pending-action behavior is unchanged.
- Chat status orchestration now lives in
  `server.agents.capabilities.dialogue.AgentChatStatusOrchestrator`, separate
  from the pure `AgentChatStatusRuntime` decision helpers. It still delegates
  live Agent/leader lookup to `AgentRuntimeIdentityRuntime`, active-mode action
  construction to `AgentActiveModeRuntime`, offline/AFK expression and reply
  effects to `AgentStatusRuntime`, and build status checks to
  `AgentBuildStatusRuntime`; status, fidget, AFK, and offline-return behavior
  is unchanged.
- Active-mode preparation orchestration now lives in
  `server.agents.capabilities.dialogue.AgentActiveModeRuntime`. It still
  delegates live Agent/leader lookup to `AgentRuntimeIdentityRuntime`,
  equipment setup to `AgentEquipmentService`, supply checks to
  `AgentPotionService`, and gear suggestions to `AgentOfferService`; follow/
  stop active-mode preparation behavior is unchanged.
- Manager status callback orchestration now lives in
  `server.agents.runtime.AgentManagerStatusRuntime`. It still delegates delayed
  spawn checks to `AgentSchedulerRuntime`, status behavior to
  `AgentChatStatusOrchestrator`, and airshow state reads to
  `AgentAirshowStateRuntime`; spawn status checks, offline-return notices, AFK
  checks, and airshow gating behavior are unchanged.
- Combat report orchestration now lives in
  `server.agents.capabilities.combat.AgentCombatReportRuntime`. It still
  delegates live target search to the temporary `AgentCombatTargetRuntime`
  integration seam, while debug-stat, crit, buff, and skill-buff report
  formatting stays unchanged.
- Combat alert timing/state orchestration now lives in
  `server.agents.capabilities.combat.AgentCombatAlertRuntime`. It still uses
  the same alert duration, reset scheduling, and cooldown state; packet-visible
  stance refresh is isolated behind `AgentCombatStanceGateway`.
- Movement kinematics snapshot construction now lives in
  `server.agents.capabilities.movement.AgentMovementKinematicsRuntime`. It
  still reads the same live character/map movement stats and field limits to
  build the same movement report snapshot; chat report formatting is unchanged.
- Movement state/profile/input/down-jump access now lives in
  `server.agents.capabilities.movement.AgentMovementStateRuntime`. It still
  reads and writes the same `AgentRuntimeEntry` state bags, and live Agent/
  leader position lookup remains isolated through the temporary
  `AgentRuntimeIdentityRuntime` integration seam; movement behavior is
  unchanged.
- Movement command orchestration now lives in
  `server.agents.capabilities.movement.AgentMovementCommandRuntime`. Follow,
  stop, move-to, farm-here, patrol, and grind commands still use the same
  prepared-mode ordering, script-task clearing, shop cancellation, navigation
  state clearing, and missing-patrol-region reply; live identity lookup and
  visible replies remain temporary integration seams.
- Movement chat callback orchestration now lives in
  `server.agents.capabilities.movement.AgentMovementRuntime`. Farm-here,
  patrol, move-here, follow, grind, stop, fidget, and greeting callbacks keep
  the same delays, replies, supply checks, active-mode preparation, command
  dispatch, and fidget side effects; live identity, scheduler, reply delivery,
  and fidget side effects remain explicit integration/runtime seams.
- Mob-touch sweep and checkpoint behavior now lives in
  `server.agents.capabilities.combat.AgentMobTouchRuntime`. It preserves the
  same previous-position sweep bounds, mob hitbox lookup, lower-half
  intersection policy, and per-map checkpoint memory.
- Combat support-buff orchestration now lives in
  `server.agents.capabilities.combat.AgentCombatBuffRuntime`. It preserves the
  same buff tick readiness, living-mob gate, party-support selection, skill
  cost checks, special-move dispatch, cooldown updates, alert marking, and
  legacy debug summaries.
- Combat ammo and mage MP-pot shortage checks now live in
  `server.agents.capabilities.supplies.AgentCombatAmmoCheckRuntime`. It
  preserves the same weapon/ammo classification, warning latch state,
  grind-mode follow fallback, and map dialogue side effects.
- Offline-return and AFK-return status action adapters now live in
  `server.agents.capabilities.dialogue.AgentStatusRuntime`. They preserve the
  same map-name lookup, random delay scheduling, face expression changes,
  party speech, and reply behavior while keeping live identity and reply
  delivery behind integration seams.
- Fame/social chat callback orchestration now lives in
  `server.agents.capabilities.social.AgentSocialRuntime`. It preserves the
  same delayed fame command handling, self-target resolution, same-map target
  lookup, fame eligibility checks, fame mutation, and replies while keeping
  live identity and reply delivery behind integration seams.
- Combat death-state entry now lives in
  `server.agents.capabilities.combat.AgentCombatDeathRuntime`. It preserves
  the same action-state clearing, dead pose marking, movement broadcast,
  dead-state timer entry, and optional map death dialogue.
- Support-heal combat orchestration now lives in
  `server.agents.capabilities.combat.AgentCombatHealRuntime`. It preserves the
  same heal readiness checks, party/undead target selection, jump-heal assist,
  skill-cost/application behavior, attack packet construction, cooldowns,
  alert marking, movement-window handling, and movement broadcast behavior;
  live leader/session lookup remains an explicit integration seam.
- Chat report operation wiring now lives in
  `server.agents.capabilities.dialogue.AgentChatReportOperationsRuntime`,
  separate from the pure `AgentChatReportRuntime` scheduler/formatting
  helpers. It preserves the same report callbacks, delayed scheduling,
  stat/range/movement/build/inventory/supply/combat report delivery, skill
  report pending-action behavior, recommended-gear actions, and queued replies;
  live identity and reply delivery remain integration seams.
- Combat target search and scoring now lives in
  `server.agents.capabilities.combat.AgentCombatTargetRuntime`. It preserves
  grind, patrol, and follow target selection, graph path cost scoring, immediate
  projectile targeting, local target scoring, patrol-region filtering, and
  sibling occupancy penalties; live leader/sibling session lookup remains an
  explicit integration seam.
- Combat attack execution orchestration now lives in
  `server.agents.capabilities.combat.AgentCombatAttackRuntime`. It preserves
  readiness checks, skill-cost checks, range checks, attack-info construction,
  damage profile/target construction, route application, cooldown updates,
  facing memory, and alert marking; packet-visible route application remains
  behind the existing attack execution provider seam.
- Combat damage, mob-touch damage, fall damage, death transition, and knockback
  orchestration now lives in
  `server.agents.capabilities.combat.AgentCombatDamageRuntime`. It preserves
  damage rolling, cooldowns, packet-visible damage broadcast, HP/autopot
  mutation, death entry, stance/knockback checks, knockback state, and movement
  broadcast behavior. The direct HP mutation and packet broadcast calls are
  intentionally documented as the next gateway split.
- Session request, relog/logout confirmation, and away-choice orchestration now
  lives in `server.agents.runtime.AgentSessionRuntime`. It preserves pending
  action state, stop-command dispatch, confirmation replies, owner-away town/
  stay/logout handling, save/disconnect timing, and relog scheduling. Direct
  save/disconnect/relogin side effects and reply delivery remain explicit
  integration seams.
- Reconstruction capability extraction audit: the only remaining
  `Agent*Runtime` classes under `server.agents.integration` are
  `AgentReplyRuntime` and `AgentRuntimeIdentityRuntime`. `AgentReplyRuntime`
  is the Cosmic chat/whisper/party packet delivery and reply-queue dispatch
  boundary. `AgentRuntimeIdentityRuntime` is the live Cosmic `Character` and
  `MapleMap` identity boundary for `AgentRuntimeEntry`. Capability, runtime,
  plan, and monitoring modules may depend on these two seams until the broader
  SPI/gateway phase replaces direct `Character`/packet access.
- Reconstruction capability extraction: removed `AgentFidgetSideEffects` from
  `server.agents.integration`. Movement chat callbacks now call
  `AgentFidgetService` directly because fidget behavior is Agent movement
  capability behavior, not a Cosmic/server boundary.
- Reconstruction capability extraction: removed
  `AgentMovementTargetSideEffects` from `server.agents.integration`. Movement
  target snapshot construction now lives in
  `server.agents.capabilities.movement.AgentMovementTargetRuntime`; only live
  `Character` identity access remains behind the documented
  `AgentRuntimeIdentityRuntime` boundary seam.
- Reconstruction capability extraction: moved `AgentCommandTargetResolver` from
  `server.agents.integration` to `server.agents.commands`. Targeted command
  parsing and transfer command matching are Agent command-domain behavior; the
  resolver still uses `AgentRuntimeIdentityRuntime` only for live Agent name
  lookup.
- Reconstruction capability extraction: moved `AgentSessionLifecycleSideEffects`
  to `server.agents.runtime.AgentSessionLifecycleRuntime`. Session lookup,
  relogin dispatch, leader safe mode, and active leader lookup are Agent runtime
  responsibilities rather than integration package side-effect wrappers.
- Reconstruction capability extraction boundary audit: remaining
  `server.agents.integration` files are intentionally boundary-only for this
  milestone:
  - `AgentCombatStanceGateway`: packet-visible stance refresh through live
    `Character`.
  - `AgentInventoryRuntimeAdapters`: callback wiring for Cosmic inventory,
    trade, item info, pickup, and server-config side effects.
  - `AgentReplyRuntime`: Cosmic chat, whisper, party, and reply packet delivery.
  - `AgentRuntimeIdentityRuntime`: live `Character`/`MapleMap` identity lookup.
  - `AgentTradeInviteGateway`: server-side trade creation and invite calls.
  - `AgentServerAdapter` plus `*Gateway` interfaces: SPI placeholders for the
    next gateway-decoupling phase.
- SPI/gateway extraction: `PacketGateway` now exposes movement packet broadcast
  and `server.agents.integration.cosmic.CosmicPacketGateway` owns
  `PacketCreator.movePlayer` plus `MapleMap.broadcastMessage` for Agent
  movement packets. `server.agents.integration.cosmic.CosmicAgentServerAdapter`
  exposes this concrete gateway through the `AgentServerAdapter` seam. Movement
  capability code delegates packet construction and map broadcast to this
  boundary.
- SPI/gateway extraction: `PacketGateway` also owns close-range attack packet
  broadcast for fidget visuals. `AgentFidgetService` now delegates
  `PacketCreator.closeRangeAttack` and map broadcast side effects to the Cosmic
  packet gateway while preserving the same prone visual packet values.
- SPI/gateway extraction: `AgentAirshowService` now reuses `PacketGateway` for
  airshow movement packets. Airshow monster trail spawn/kill packets remain a
  documented follow-up gateway slice.
- SPI/gateway extraction: navigation debug overlay mist removal now routes
  through `PacketGateway.sendRemoveMist`, moving `PacketCreator.removeMist` and
  direct client packet send out of the navigation capability.
- SPI/gateway extraction: loot ghost-drop cleanup now routes item removal
  packets through `PacketGateway.sendRemoveItemFromMap`, moving
  `PacketCreator.removeItemFromMap` out of the looting capability while keeping
  visibility and BotClient filtering in `AgentLootCleanupService`.
- SPI/gateway extraction: trade item-add packets now route through
  `PacketGateway.sendTradeItemAdd`, moving `PacketCreator.getTradeItemAdd` and
  direct recipient packet sends out of `AgentTradeItemAddService` while keeping
  trade item selection, inventory removal, and partner routing in the trade
  capability.
- SPI/gateway extraction: combat damage packets now route through
  `PacketGateway.broadcastDamagePlayer`, moving `PacketCreator.damagePlayer` and
  map broadcast side effects out of `AgentCombatDamageRuntime` while preserving
  HP, cooldown, death, and knockback behavior in the combat capability.
- SPI/gateway extraction: navigation debug overlay mist spawn packets now route
  through `PacketGateway.sendMistFakeSpawn`, moving direct client packet send out
  of the navigation capability. Overlay geometry and mist object allocation stay
  in `AgentNavigationDebugOverlay`.
- SPI/gateway extraction: airshow trail spawn/kill monster packets now route
  through `PacketGateway.broadcastSpawnMonster` and
  `PacketGateway.broadcastKillMonster`. Airshow still owns trail timing,
  object-id allocation, position, foothold, and stance setup.
- SPI/gateway extraction: support special-move dispatch now routes synthetic
  packet parsing and handler execution through `CombatGateway.dispatchSyntheticPacket`
  and `CosmicCombatGateway`. `AgentSupportSpecialMoveExecutor` still owns
  building the legacy special-move packet bytes and deciding when to dispatch.
- SPI/gateway extraction: ammo-share projectile attack lookup now routes
  through `InventoryGateway.getProjectileWeaponAttack` and
  `CosmicInventoryGateway`. `AgentAmmoService` still owns low-ammo detection,
  donor scoring, share quantity, dialogue timing, and trade scheduling; the
  Cosmic item-info lookup is isolated at the inventory gateway boundary.
- SPI/gateway extraction: autopot debug item-name lookup now routes through
  `InventoryGateway.getItemName`. `AgentPotionService` still owns potion
  counting, ranking, keybinding setup, low-pot sharing, passive recovery, and
  supply dialogue formatting; only the Cosmic item-name lookup moved to the
  inventory gateway boundary.
- SPI/gateway extraction: inventory safe-collection and drop safety now route
  quest-item checks through `InventoryGateway.isQuestItem`. Inventory
  collection/drop services still own bag iteration, filters, untradeable rules,
  drop-limit handling, and replies; the Cosmic quest-item metadata lookup is
  isolated behind the inventory gateway.
- SPI/gateway extraction: named-item inventory queries now route item-name and
  quest-item metadata through `InventoryGateway`. The normalized-name cache,
  query normalization, bag scanning, and safe-item filtering remain in
  `AgentInventoryNamedItemService`; the synchronized Cosmic item-name lookup is
  preserved inside `CosmicInventoryGateway`.
- SPI/gateway extraction: use-item trade grouping now routes quest-item
  metadata through `InventoryGateway.isQuestItem`. Trade collection still owns
  category expansion, priority ordering, equipped-slot fallback, ammo/use/equip
  grouping, and transfer counts; only the Cosmic quest-item check moved.
- SPI/gateway extraction: equipped-slot trade preparation now routes cash-item
  metadata through `InventoryGateway.isCashItem`. Equipped-slot counting,
  temporary unequip/move behavior, restore-slot tracking, and trade-preparation
  replies remain in `AgentEquippedSlotTradeService`.
- SPI/gateway extraction: use-item classification now routes item-effect lookup
  through `InventoryGateway.getItemEffect`. Recovery/buff classification still
  lives in `AgentUseItemClassificationPolicy`; the gateway still returns the
  existing `StatEffect` type as an interim compatibility seam until a later
  Agent-owned item-effect view is introduced.
- SPI/gateway extraction: inventory dialogue summaries and nearest-town return
  scroll use now route item-effect and quest-item metadata through
  `InventoryGateway`. Dialogue formatting, safe-to-mention rules, return-scroll
  item search, effect application, and inventory removal behavior are unchanged.
- SPI/gateway extraction: sell-trash equip protection now routes equip metadata
  through `InventoryGateway.getEquipStats` and `InventoryGateway.getEquipById`.
  Sell-trash filtering, protected stat thresholds, mage/weapon attack checks,
  self-upgrade exclusions, reserved-recipient exclusions, and sorting behavior
  are unchanged.
- SPI/gateway extraction: equipment unequip live hooks now route cash-item and
  item-name metadata through `InventoryGateway`. Unequip slot selection,
  free-slot checks, item moves, and reply text remain in
  `AgentEquipmentUnequipService`.
- SPI/gateway extraction: equipment recommendation summary formatting now
  routes item-name metadata through `InventoryGateway`. Candidate filtering,
  optimizer input, useful-item policy, and future/immediate recommendation
  behavior still use the existing equipment item-info path and remain a later
  equipment gateway slice.
- SPI/gateway extraction: scroll-reaction success-rate lookup now routes equip
  metadata through `InventoryGateway.getEquipStats`. Reaction radius checks,
  cooldowns, streak/load scaling, emotes, queued chat, and fidget behavior are
  unchanged.
- SPI/gateway extraction: shop resupply metadata now routes projectile attack,
  ammo slot-max, and item-name lookups through `InventoryGateway`. Shop visit
  sequencing, resupply/recharge decisions, purchase shortfall replies, and the
  existing test seams for projectile/slot-max behavior are unchanged.
- SPI/gateway extraction: trade-transfer ammo grouping now routes projectile
  attack and quest-item metadata through `InventoryGateway`. Trade command
  routing, item collection, ammo grouping, batching, and transfer sequencing are
  unchanged.
- SPI/gateway extraction: KPQ Stage 5 reward announcement item names now route
  through `InventoryGateway`. Reward claiming, inventory delta detection,
  claimed-state marking, and queued PQ chat remain unchanged.
- SPI/gateway extraction: common buff-pot active/available item names now route
  through `InventoryGateway`. Buff selection, cheap/best scoring, active-buff
  detection, item consumption, and dialogue formatting are unchanged.
- SPI/gateway extraction: combat attack execution now routes equipped-weapon
  type and two-handed metadata through `InventoryGateway`. Attack route
  selection, skill animation resolution, degenerate ranged handling, retreat
  checks, hitbox construction, and packet fields are unchanged.
- SPI/gateway extraction: inventory runtime trade callbacks now route projectile
  attack and quest-item metadata through `InventoryGateway` instead of direct
  item-info lambdas. Callback wiring, trade availability, category profiling,
  and transfer behavior are unchanged.
- SPI/gateway extraction: trade offer item names, cash-equip filtering, weapon
  compatibility metadata, and throwing-star attack values now route through
  `InventoryGateway`. Offer prompt wording, recipient selection, proactive
  upgrade logic, sibling/owner priority, and trade scheduling are unchanged.
  Deeper equipment reserve checks still use the existing item-info signatures
  until the equipment policy gateway slice.
- SPI/gateway extraction: Maker leftover-to-crystal metadata now routes through
  `InventoryGateway`. Maker skill checks, leftover counting, batch scheduling,
  MakerProcessor execution, disassembly filtering, and interruption behavior are
  unchanged.
- SPI/gateway extraction: equipment optimizer weapon-cycle scoring now routes
  weapon-type metadata through `InventoryGateway`. DP state generation,
  requirement validation, Pareto pruning, score comparison, and attack-cycle
  timing behavior are unchanged.
- SPI/gateway extraction: auto-equip debug dump item rows now route item name,
  text slot, equip stats, and level requirement metadata through
  `InventoryGateway`. Dump layout, self-reserve flags, optimizer inputs, and
  equipment selection behavior are unchanged.
- SPI/gateway extraction: default equipment self-reserve and potential
  self-upgrade collection now use gateway-backed reserve hooks for cash checks,
  text slot, weapon type, equip stats, level requirements, and requirement
  validation. The legacy item-info overloads remain for compatibility and
  focused tests; reserve selection behavior is unchanged.
- SPI/gateway extraction: equipment recommendation filtering now uses
  gateway-backed recommendation hooks for cash checks, text slot, weapon type,
  wearability, and requirement validation. Recommendation candidate filtering,
  future/immediate scope behavior, optimizer inputs, and summary formatting are
  unchanged.
- SPI/gateway extraction: trade offer future-reserve checks now call the
  gateway-backed equipment reserve service instead of passing
  `ItemInformationProvider` through `AgentOfferService`. Sibling/leader
  recipient priority, proactive-upgrade FUTURE classification, reserved-item
  trade filtering, and prompt behavior are unchanged.
- SPI/gateway extraction: live auto-equip infeasible-equipment cleanup now
  enters `AgentEquipmentPlanExecutor` through gateway-backed cash and
  wearability hooks. The legacy item-info overload remains as a compatibility
  seam, while cash-skip behavior, wearability checks, selected slots, and
  unequip execution order are unchanged.
- SPI/gateway cleanup: unused item-info compatibility overloads were removed
  from equipment recommendation filtering, debug row formatting, infeasible
  cleanup, and the public equipment service reserve facade. Live callers were
  already on gateway-backed hooks; recommendation decisions, debug formatting,
  infeasible cleanup, and reserve behavior are unchanged.
- SPI/gateway extraction: optimizer extra-item recommendation filtering now
  calls `AgentEquipmentRecommendationPolicy` with `InventoryGateway` hooks.
  The surrounding optimizer still contains staged item-info metadata seams, but
  extra-offer candidate acceptance/rejection behavior is unchanged.
- SPI/gateway extraction: auto-equip debug/report metadata now routes item
  names, cash checks, and weapon-type display lookups through
  `InventoryGateway`. Optimizer math, candidate pools, dump structure, branch
  ordering, and chat/debug text content are intended to remain unchanged.
- SPI/gateway extraction: trade lifecycle operations now have a
  `TradeGateway` exposed from `AgentServerAdapter`. Start/invite, no-response
  cancellation, completion, and invite-accept visit operations route through
  the Cosmic gateway while live `Trade` window objects remain in staged trade
  state/tick APIs. Trade sequencing, timeout replies, completion reactions,
  and manual invite behavior are unchanged.
- SPI/gateway extraction: equipment optimizer hook construction now has
  `InventoryGateway` factories for current and future-scope checks.
  `AgentEquipmentOptimizationService` uses those factories for optimizer DP
  metadata while legacy item-info factories remain only for compatibility
  overloads. Two-handed, weapon-type, overall-slot, requirement, and equip-stat
  behavior are unchanged.
- Trade-window reconstruction: category announcements, all-items-added chat,
  and pending-meso setup now use narrow chat/meso callbacks instead of directly
  owning `Trade` objects. `AgentTradeItemAddTickService` still supplies the
  live trade window callbacks, so chat text, timers, meso checks, and item-add
  tick ordering are unchanged.
- Trade-window reconstruction: confirmation-wait logic now consumes a narrow
  partner-confirmed callback instead of owning the live `Trade` object.
  `AgentTradeTickRuntimeService` still adapts the current trade window, so bot
  recipient auto-confirm, partner-confirmed completion, timeout replies, and
  no-response cancellation behavior are unchanged.
- Trade-window reconstruction: completion reactions now consume partner item
  and offer snapshots instead of owning the live `Trade` object.
  `AgentTradeLifecycleService` still adapts the current trade window, so
  owner-given equip tracking, trade completion, delayed thanks replies, and
  occasional freebie reactions are unchanged.
- SPI/gateway extraction: consumable buff selection, active-buff summaries,
  available-buff summaries, and debug lines now receive `InventoryGateway`
  from runtime callers instead of reaching for the Cosmic adapter inside
  `AgentBuffService`. Buff-pot scanning, cheap/max selection, item names,
  delayed use behavior, and chat/debug wording are unchanged.
- SPI/gateway extraction: autopot debug item-name lookup now receives
  `InventoryGateway` from the report runtime instead of reaching for the
  Cosmic adapter inside `AgentPotionService`. Potion counting, autopot choice
  ranking, keybind setup, and debug wording are unchanged.
- SPI/gateway extraction: inventory summary reporting now receives
  `InventoryGateway` from the report runtime instead of reaching for the
  Cosmic adapter inside `AgentInventoryDialogueReporter`. USE inventory scroll,
  recovery-pot, buff-pot, untradeable, and quest-item filtering behavior is
  unchanged.
- SPI/gateway extraction: Maker monster-crystal leftover metadata now receives
  `InventoryGateway` from the utility command runtime instead of reaching for
  the Cosmic adapter inside `AgentMakerService`. Maker skill gating, leftover
  scanning, batch timing, client-lock retry behavior, abort replies, and
  completion replies are unchanged.
- SPI/gateway extraction: named-item inventory collection/count/drop matching
  now receives `InventoryGateway` from runtime/caller boundaries instead of
  reaching for the Cosmic adapter inside `AgentInventoryNamedItemService`.
  Normalized query matching, cached item-name normalization, quest-item
  filtering, trade collection, drop-by-name behavior, and transfer counts are
  unchanged.
- SPI/gateway extraction: shared safe bag collection now receives
  `InventoryGateway`/explicit quest-item predicates from inventory/trade
  runtime callers instead of reaching for the Cosmic adapter inside
  `AgentInventoryCollectionService`. Slot-order traversal, untradeable rules,
  quest-item filtering, equip-bag grouping, scroll/potion/buff/etc collection,
  and trade classification behavior are unchanged.
- SPI/gateway extraction: KPQ Stage 5 reward announcement item-name lookup now
  receives `InventoryGateway` from common tick runtime through
  `AgentPartyQuestHooks` instead of reaching for the Cosmic adapter inside
  `AgentKpqStage5`. Stage-clear detection, reward claiming, claimed-state
  marking, item-delta selection, fallback item-id text, and queued PQ chat are
  unchanged.
- SPI/gateway extraction: scroll-reaction success-rate lookup now receives
  `InventoryGateway` from the scroll notification boundary instead of reaching
  for the Cosmic adapter inside `AgentScrollReactionService`. Nearby-Agent
  detection, per-Agent jitter, streak/load scaling, emote/chat/fidget chances,
  cooldowns, and reaction text selection are unchanged.
- SPI/gateway extraction: trade collection category expansion now receives
  `InventoryGateway` from trade/runtime callers instead of reaching for the
  Cosmic adapter inside `AgentInventoryTradeCollectionService`. Recommended,
  name, scroll, potion, buff, use, ammo, equip, trash, reserved-equip, and ETC
  trade collection behavior is unchanged.
- SPI/gateway extraction: sell-trash equip filtering now receives
  `InventoryGateway` from Maker/shop/drop-transfer callers instead of reaching
  for the Cosmic adapter inside `AgentInventorySellTrashService`. Safe equip
  collection, quest-item filtering, self-upgrade/reserved-recipient exclusions,
  protected-stat filtering, and sort order are unchanged.
- SPI/gateway extraction: inventory drop category handling now receives
  `InventoryGateway` from the transfer/drop caller instead of reaching for the
  Cosmic adapter inside `AgentInventoryDropService`. Drop-limit checks, safe
  item filtering, named-item matching, drop execution, and legacy reply text
  are unchanged.
- SPI/gateway extraction: equipped-slot trade preparation now receives
  `InventoryGateway` from transfer and runtime callers instead of reaching for
  the Cosmic adapter inside `AgentEquippedSlotTradeService`. Cash-item skipping,
  equipped-slot counting, temporary unequip ordering, restore-slot recording,
  full-bag errors, and restore behavior are unchanged.
- SPI/gateway extraction: ammo-share item collection now receives
  `InventoryGateway` from potion/supply/mode runtime boundaries instead of
  reaching for the Cosmic adapter inside `AgentAmmoService`. Low-ammo checks,
  owner/manual bypass behavior, donor selection, cooldown/backoff handling,
  dialogue timing, and transfer scheduling are unchanged.
- SPI/gateway extraction: runtime inventory lookups for common ticks,
  active-mode preparation, utility commands, movement follow setup, supply
  requests, and buff-control reporting now route through
  `AgentInventoryGatewayRuntime` instead of importing the Cosmic adapter
  directly at those runtime boundaries. Potion, ammo, buff, Maker, shop, and
  mode-start behavior is unchanged.
- SPI/gateway extraction: report operations, combat buff-debug reporting,
  scroll-reaction notification, and default use-item effect classification now
  obtain inventory metadata through `AgentInventoryGatewayRuntime`. Report
  text, scroll reaction timing, recovery/buff classification, and item-effect
  fallback behavior are unchanged.
- SPI/gateway extraction: equipment live hooks and combat attack metadata now
  obtain inventory metadata through `AgentInventoryGatewayRuntime` instead of
  importing the concrete Cosmic adapter. Auto-equip, optimizer hook creation,
  reserve checks, recommendation filtering, unequip behavior, infeasible-equip
  cleanup, and equipped-weapon attack metadata are unchanged; deeper
  `ItemInformationProvider` compatibility signatures remain staged for later.
- SPI/gateway extraction: movement broadcast, fidget visual attack, combat
  damage, loot ghost-drop cleanup, navigation debug overlay, and airshow visual
  packets now obtain packet emission through `AgentPacketGatewayRuntime` instead
  of importing the concrete Cosmic adapter. Packet payloads, broadcast targets,
  scheduling, and visibility filtering are unchanged.
- SPI/gateway extraction: support special-move synthetic packet dispatch now
  obtains combat gateway access through `AgentCombatGatewayRuntime` instead of
  importing the concrete Cosmic adapter. Packet bytes, dispatch timing, and
  handler behavior are unchanged.
- SPI/gateway extraction: remaining runtime inventory lookups for follow-target
  potion sharing, shop visit ticks, map-change shop transitions, movement-only
  shop ticks, and nearest-town return scroll metadata now route through
  `AgentInventoryGatewayRuntime`. Follow setup, shop sequencing, map-change
  handling, movement-only ticks, and return-scroll behavior are unchanged.
- SPI/gateway extraction: utility trade-invite creation now routes through
  `AgentTradeGatewayRuntime` instead of importing the concrete Cosmic adapter
  from `AgentTradeInviteGateway`. Trade start/invite ordering and utility chat
  behavior are unchanged.
- Trade-window reconstruction: manual trade invite state now stores the live
  trade window as an opaque identity object instead of owning the `server.Trade`
  type. Manual invite timeout, accept-delay, greeting, cancel, and accept
  behavior are unchanged; deeper trade tick/services still adapt live
  `Trade` windows until later slices.
- SPI/gateway extraction: immediate Agent map-chat and whisper reply packet
  creation now routes through `AgentPacketGatewayRuntime`/`PacketGateway`
  instead of importing `PacketCreator` in `AgentReplyRuntime`. Reply-channel
  selection, sanitization, party fallback, and visible chat behavior are
  unchanged.
- Trade-window reconstruction: manual trade timeout, peer-trade, owner-trade,
  callback, and greeting orchestration now use the Agent-owned
  `AgentTradeWindow` seam. The live `server.Trade` object is wrapped only at the
  runtime adapter boundary by `AgentServerTradeWindow`; accept timing, timeout,
  peer authorization, owner confirmation, greeting, completion, and refill
  behavior are unchanged.
- Trade-window reconstruction: pending trade tick routing and its callback
  factory now use `AgentTradeWindow` for current-window, accept-wait,
  item-add, and confirmation dispatch. `AgentTradeTickRuntimeService` still
  unwraps to live `server.Trade` only for deeper item-add and lifecycle
  services that have not moved yet; queued retry, closed-window, batch, add,
  confirmation, and completion ordering are unchanged.
- Trade-window reconstruction: item-add and item-add tick services now use
  `AgentTradeWindow` for item placement, meso placement, partner lookup, and
  trade chat. The live `server.Trade` item-add/meso operations stay behind
  `AgentServerTradeWindow`; inventory locking, restore-slot tracking,
  quantity caps, packet emission, category announcements, all-done chat, and
  meso cancellation behavior are unchanged.
- Trade-window reconstruction: trade lifecycle completion now reads partner
  items and received-offer state through `AgentTradeWindow`; the lifecycle
  service/runtime no longer import `server.Trade`. Completion reactions,
  equipment tracking for received equips, thanks/freebie reply timing, and
  reset/cancel/refill behavior are unchanged.
- Trade-window reconstruction: `AgentTradeTickRuntimeService.RuntimeCallbacks`
  now returns `AgentTradeWindow` instead of live `server.Trade`; the integration
  adapter wraps `Character.getTrade()` with `AgentServerTradeWindow`. Trade tick
  lookup ordering, queued retry behavior, closed-window handling, item-add
  dispatch, confirmation waits, and completion reactions are unchanged.
- Reconstruction audit: production `src/main/java/server/agents/**` no longer
  references `server.bots`; production `src/main/java/server/bots/**` contains
  only the deprecated empty `BotEntry` compatibility shell. Remaining `BotEntry`
  references are test-side compatibility fixtures or stale explanatory wording,
  not production Agent runtime dependencies.

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
   now enters `AgentChatRuntime` through `AgentChatOrchestratorContext`, and
   `AgentChatRuntimeParityTest` is the historical focused parity suite.
4. Movement and navigation.
5. Combat.
6. Loot and supplies.
7. Inventory, equipment, trade, shop, and build.
8. Quest, NPC, and PQ.
9. Dialogue, social, and LLM.
10. SPI/Cosmic gateway attachment.
11. Delete old bot-shaped runtime once all callers use Agent modules.
