# Bot To Agent Reconstruction Map

Status values:

- `MIGRATE_TO_AGENT`: move behavior into one Agent module.
- `SPLIT_TO_MULTIPLE_AGENT_MODULES`: split behavior across multiple bins.
- `COMPATIBILITY_ALIAS_TEMPORARY`: keep only as a temporary wrapper during migration.
- `DELETE_AFTER_MIGRATION`: remove once behavior/callers are migrated.
- `LEGACY_PROFILE`: preserve as legacy behavior profile until deliberately replaced.

This map tracks reconstruction from the source/master bot baseline into neutral Agent modules.

Recent map updates:

- Integration runtime adapter audit has started. Current classification:

  | Classification | Files | Notes |
  | --- | --- | --- |
  | `MOVE_TO_RUNTIME` | `AgentChatOrchestratorContext`, `AgentRuntimeIdentityRuntime`, `AgentSchedulerRuntime`, `AgentManagerStatusRuntime`, `AgentSessionControlRuntime`, `AgentSessionRuntime` | Runtime/session/tick/chat-flow orchestration rather than a Cosmic boundary. `AgentChatOrchestratorContext`, the delayed callback scheduler facade, session-control facade, and manager status callback orchestration have moved first; other files need one-slice moves with focused tests. |
  | `MOVE_TO_RUNTIME` | `AgentActivityStateRuntime`, `AgentDeathStateRuntime`, `AgentFarmAnchorStateRuntime`, `AgentLeaderStateRuntime`, `AgentMapStateRuntime`, `AgentMessageQueueStateRuntime`, `AgentModeStateRuntime`, `AgentOwnerMotionStateRuntime`, `AgentPatrolStateRuntime`, `AgentReplyChannelStateRuntime`, `AgentTickCadenceStateRuntime`, `AgentTickFailureStateRuntime`, `AgentTickStateRuntime` | Mutable runtime/session state adapters. Cross-capability runtime state remains here while capability-owned state adapters move into their capability packages one slice at a time. |
  | `MOVE_TO_CAPABILITY_dialogue` | `AgentAirshowStateRuntime`, `AgentChatReportRuntime`, `AgentChatStatusRuntime`, `AgentControlRuntime`, `AgentPendingActionRuntime`, `AgentPendingActionStateRuntime`, `AgentReplyRuntime`, `AgentSocialRuntime`, `AgentLlmRuntime`, `AgentScrollReactionRuntime`, `AgentScrollReactionStateRuntime` | Dialogue/report/status/social behavior should move toward dialogue/social/LLM capability modules. Pure scroll-reaction state, scroll-reaction reply/timing bridge access, airshow capability state, utility/control chat callback orchestration, pending-action chat orchestration, chat-status orchestration, active-mode preparation orchestration, and the LLM reply bridge have moved; leave queueing, scheduling, live identity lookup, expression changes, and packet side effects behind integration boundaries until gateways exist. |
  | `MOVE_TO_CAPABILITY_combat` | `AgentAmmoStateRuntime`, `AgentAoeRepositionStateRuntime`, `AgentBreakoutStateRuntime`, `AgentBuffStateRuntime`, `AgentCombatActionLockRuntime`, `AgentCombatActionStateRuntime`, `AgentCombatAlertRuntime`, `AgentCombatAmmoCheckRuntime`, `AgentCombatAoeRepositionRuntime`, `AgentCombatAttackRuntime`, `AgentCombatBuffRuntime`, `AgentCombatBuffStateRuntime`, `AgentCombatCooldownStateRuntime`, `AgentCombatDamageRuntime`, `AgentCombatDeathRuntime`, `AgentCombatFacingRuntime`, `AgentCombatGroundRuntime`, `AgentCombatHealRuntime`, `AgentCombatPlanRuntime`, `AgentCombatReportRuntime`, `AgentCombatRuntime`, `AgentCombatSkillCacheRuntime`, `AgentCombatSkillCacheStateRuntime`, `AgentCombatTargetRuntime`, `AgentDegenerateAttackStateRuntime`, `AgentGrindSearchStateRuntime`, `AgentGrindTargetStateRuntime`, `AgentGrindWanderStateRuntime`, `AgentMobTouchRuntime`, `AgentMobTouchStateRuntime`, `AgentRetreatHoldStateRuntime`, `AgentSkillBuffDebugStateRuntime` | Combat planning/state belongs in combat capability. The pure positioning, ground foothold lookup, combat reply/timing facade, cooldown, combat-alert timing/state orchestration, action-lock ticking, action-state clearing, attack-facing update facade, AoE reposition planning, attack-plan orchestration, consumable-buff, support-buff, combat report orchestration, skill-buff debug, degenerate ranged-hit latch, grind-target state access, grind-wander, grind-search cadence, mob-touch checkpoint, skill-cache rebuild logic, and skill-cache state adapters have moved; attack packets, damage mutation, live target map checks/search, stance broadcast packets, and mob-touch server writes stay integration until gateway seams are introduced. |
  | `MOVE_TO_CAPABILITY_movement_navigation` | `AgentClimbStateRuntime`, `AgentFidgetRuntime`, `AgentFidgetStateRuntime`, `AgentGrindWanderStateRuntime`, `AgentMovementBroadcastStateRuntime`, `AgentMovementCommandRuntime`, `AgentMovementKinematicsRuntime`, `AgentMovementPhysicsStateRuntime`, `AgentMovementRuntime`, `AgentMovementStateRuntime`, `AgentMovementStatusRuntime`, `AgentMovementStuckStateRuntime`, `AgentMovementTargetRuntime`, `AgentMoveTargetStateRuntime`, `AgentNavigationDebugStateRuntime`, `AgentSwimStateRuntime` | Movement/navigation state and decisions belong in movement/navigation capabilities. Pure move-target state, stuck/unstuck counter state, climb/rope state, swim intent state, movement broadcast cache state, movement physics state, movement kinematics snapshot construction, fidget active-state/timer state, fidget status bridge, movement status facade, movement target snapshot facade access, and navigation debug/target/edge state access have moved to `server.agents.capabilities.movement`, `server.agents.capabilities.movement.fidget`, or `server.agents.capabilities.navigation`; fidget visual side effects, packet broadcasts, map/foothold access, status reply side effects, and live target snapshot capture remain integration until gateway-backed. |
  | `MOVE_TO_CAPABILITY_looting` | `AgentGrindLootStateRuntime` | Loot targeting state belongs in the looting capability. Grind loot target storage and retry suppression now live in `server.agents.capabilities.looting`; live `MapItem` selection, pickup execution, and inventory/map mutation remain in existing callers until gateway-backed. |
  | `MOVE_TO_CAPABILITY_items` | `AgentAmmoRuntime`, `AgentAmmoStateRuntime`, `AgentBuildRuntime`, `AgentBuildStateRuntime`, `AgentBuildStatusRuntime`, `AgentEquipmentRuntime`, `AgentInventoryRuntime`, `AgentInventoryStateRuntime`, `AgentMakerRuntime`, `AgentManualTradeStateRuntime`, `AgentOfferRuntime`, `AgentOfferStateRuntime`, `AgentPendingTradeStateRuntime`, `AgentPotionRuntime`, `AgentPotionStateRuntime`, `AgentShopRuntime`, `AgentShopStateRuntime`, `AgentSupplyRuntime`, `AgentTransferRuntime`, `AgentUtilityRuntime` | Build/supply/inventory/equipment/trade/shop behavior should move into domain capabilities. Build AP/SP/job and status orchestration, equipment chat callback orchestration, offer reply/timing/state orchestration, supply request/upgrade orchestration, transfer command/item-query orchestration, utility chat command orchestration, pure build prompt/progression state, ammo/potion reply/delay facades, shop reply/delay facade, ammo/potion supply state, inventory cooldown state, shop transition state, pending trade sequence state, manual trade invite state, pending offer/upgrade prompt state, inventory reply/delay bridge access, and Maker reply/delay bridge access have moved; real inventory mutation, trade/shop APIs, timers, client locks, and trade invitation mutation stay integration until adapters are available. |
  | `MOVE_TO_CAPABILITY_plans_pq` | `AgentPqRuntime`, `AgentScriptMoveTargetRuntime`, `AgentScriptTaskStateRuntime` | PQ/script state belongs with plan/PQ capabilities. Script task queue/runtime state and script move-target cost adapter have moved to `server.agents.plans`; KPQ/PQ state plus queued-dialogue bridge access has moved to `server.agents.capabilities.partyquest`; server-side PQ/script hooks must stay integration until split. |
  | `KEEP_IN_INTEGRATION_BOUNDARY` | `CharacterGateway`, `CombatGateway`, `InventoryGateway`, `MapGateway`, `MovementGateway`, `NpcGateway`, `PacketGateway`, `PartyGateway`, `QuestGateway`, `AgentServerAdapter` | These are intentional Cosmic/server boundary contracts or adapter entry points. |
  | `SPLIT_LATER` | Any file above that mixes pure decisions with `Character`, `MapleMap`, timers, packets, inventory/shop/trade/NPC/database mutation, or other server internals | Split pure behavior into capability/runtime first and leave only side effects in integration. |

- Final compatibility shell removal is complete: `src/main/java/server/bots/**`
  and `src/test/java/server/bots/**` are removed. Runtime/session fixtures now
  use `AgentRuntimeEntry` directly, and source/test scans for `server.bots` and
  `BotEntry` are clean. Runtime behavior is unchanged.
- Remaining `AgentBot*` production names are intentionally retained for now as
  Agent-owned adapter/runtime names that preserve historical behavior while the
  semantic rename pass is planned separately. They do not depend on
  `server.bots` and should be renamed by capability in a future low-risk
  naming-only milestone.
- Semantic rename pass started with the foundational identity/scheduler/reply
  slice. The identity, scheduler, reply, reply-channel, and message-queue
  runtime adapters now use neutral Agent names. Behavior, public methods,
  scheduling, reply routing, and message queue state are unchanged.
- The state-adapter slice renamed `AgentBot*StateRuntime` classes to
  `Agent*StateRuntime` by capability/state. State storage, method signatures,
  and runtime behavior are unchanged.
- The movement/fidget semantic rename slice renamed movement command,
  movement callback, movement status, movement target, movement kinematics,
  and fidget runtime adapters from `AgentBot*` to neutral `Agent*` names.
  Movement command routing, target snapshot capture, fidget side effects, and
  runtime behavior are unchanged.
- The dialogue/control/status semantic rename slice renamed chat
  orchestrator, chat report, chat status, control, manager-status, pending
  action, and status runtime adapters from `AgentBot*` to neutral `Agent*`
  names. Chat command routing, report callbacks, AFK/offline status checks,
  toggle/respec callbacks, and pending-action behavior are unchanged.
- The item/build/supply semantic rename slice renamed active-mode, ammo,
  build, equipment, inventory, Maker, offer, potion, shop, supply, transfer,
  and utility runtime adapters from `AgentBot*` to neutral `Agent*` names.
  Build setup, item automation, shop/trade orchestration, upgrade offers,
  supply sharing, and utility command behavior are unchanged.
- The session/social/PQ semantic rename slice renamed LLM, party-quest,
  script move target, scroll reaction, session control, session lifecycle,
  session runtime, and social runtime adapters from `AgentBot*` to neutral
  `Agent*` names. LLM reply routing, PQ hooks, scroll/social reactions,
  relog/logout session flows, and scripted movement behavior are unchanged.
- The command resolver semantic rename slice renamed `AgentBotCommandParser` to
  `AgentCommandTargetResolver` and replaced bot-named parser entry points with
  Agent-named equivalents. Transfer command parsing and targeted Agent command
  matching are unchanged.
- The final combat semantic rename slice renamed combat and mob-touch runtime
  adapters from `AgentBot*` to neutral `Agent*` names. Attack planning, packet
  route selection, hitbox handling, ammo gates, damage execution, combat
  reports, support behavior, and mob-touch handling are unchanged.
- `AgentChatRouteRuntime` no longer imports `BotEntry`; its custom-entry path
  is generic over `AgentRuntimeEntry`. Pending-offer routing, lifecycle chat
  commands, formation commands, targeted/untargeted routing, typo suggestions,
  reply-channel updates, LLM fallback, and owner-command activity tracking are
  unchanged.
- `AgentLlmReplyRuntime` now accepts `AgentRuntimeEntry` and creates
  `AgentLlmReplyRequest<AgentRuntimeEntry>`. Sender relation, reply channel,
  prompt context, mode/farm-anchor flags, owner-command context, and reply
  delivery are unchanged.
- `AgentInventoryRuntimeAdapters` no longer imports `BotEntry`. Passive
  loot, manual trade, trade tick, lifecycle, transfer availability, and trade
  runtime callback wiring remain unchanged.
- `AgentOfferRuntime` no longer imports `BotEntry` for recommended-gear
  report actions. It now calls `AgentOfferService.offerBestRecommendedGear`
  with the existing `AgentRuntimeEntry`; owner checks, offer execution, and
  queued report replies remain unchanged.
- `AgentChatOrchestratorContext` now accepts `AgentRuntimeEntry` for the
  generic Agent dialogue context adapter. Pending action, session, supply,
  social, control, equipment, movement, build, utility, transfer, report, and
  job advancement callback wiring remains unchanged.
- `AgentChatOrchestratorContext` has moved from `server.agents.integration` to
  `server.agents.runtime`. It is runtime orchestration for the generic dialogue
  context, not a direct Cosmic boundary. Its callback wiring still points at
  existing integration runtime adapters until those slices are split safely.
- `AgentStatusStateRuntime` now owns the status/AFK/gear-suggestion state
  adapters under `server.agents.runtime`. `AgentStatusRuntime` remains in
  `server.agents.integration` only for the offline/AFK return actions that
  still schedule timers, emit replies, or change the live Agent character
  expression.
- `AgentPendingActionStateRuntime` has moved from `server.agents.integration`
  to `server.agents.runtime`. It only reads/writes pending action fields on
  `AgentRuntimeEntry`; pending-action decision handling and reply/transfer side
  effects remain in integration adapters for later slices.
- `AgentTickStateRuntime`, `AgentTickCadenceStateRuntime`, and
  `AgentTickFailureStateRuntime` have moved from `server.agents.integration`
  to `server.agents.runtime`. They only adapt `AgentRuntimeEntry` tick,
  cadence, heartbeat, and failure-window state; tick scheduling and failure
  side effects remain in runtime/integration callers unchanged.
- `AgentActivityStateRuntime` has moved from `server.agents.integration` to
  `server.agents.runtime`. It only adapts live leader activity/AFK/away command
  fields on `AgentRuntimeEntry`; status replies and delayed return actions stay
  in integration-facing adapters.
- Movement-mode state adapters `AgentModeStateRuntime`,
  `AgentFarmAnchorStateRuntime`, and `AgentPatrolStateRuntime` have moved from
  `server.agents.integration` to `server.agents.runtime`. They only adapt
  follow/grind/farm-anchor/patrol fields on `AgentRuntimeEntry`; combat,
  movement packets, map navigation, and recovery side effects remain unchanged.
- `AgentPendingActionRuntime` now accepts `AgentRuntimeEntry` for pending
  action state, pending action callbacks, and skill-tree choice handling.
  Item-choice execution/cancel paths, owner-away routing, relog/logout
  confirmations, skill-report decisions, and queued replies remain unchanged.
- `AgentSessionRuntime` now accepts `AgentRuntimeEntry` for session request
  callbacks, relog/logout confirmation, and owner-away choices. Relog/logout
  prompts, owner-away stay/town/logout decisions, delayed replies,
  save/disconnect scheduling, and lifecycle lookup behavior remain unchanged.
- `AgentControlRuntime` now accepts `AgentRuntimeEntry` for toggle, buff
  query, and respec callback wiring. Support/heal toggles, consumable buff
  toggles, proactive-offer toggles, buff debug reporting, and AP/SP respec
  behavior keep the same delayed scheduling and reply text.
- `AgentAirshowService` now uses `AgentRuntimeEntry` internally. The command
  still resolves through the temporary session lifecycle lookup, but timed
  flight frames, trail effects, restoration, movement packet emission, and
  state cleanup no longer require an Agent service-level `BotEntry` import.
- `AgentScrollReactionService` now accepts `AgentRuntimeEntry` on its Agent
  service API. Scroll-event range filtering, delayed reaction scheduling,
  streak/load tracking, emotes, chat queueing, fidget triggering, and cooldowns
  remain unchanged while the remaining registry source is still a temporary
  `BotEntry`-backed shell.
- `AgentActiveModeRuntime` now stays on `AgentRuntimeEntry` for active-mode
  preparation callbacks. Auto-equip, gear suggestion cooldown reset, sibling
  gear suggestion, autopot setup, and mode-start potion-share checks remain
  unchanged.
- `AgentUtilityRuntime`, `AgentSupplyRuntime`, and
  `AgentTransferRuntime` now accept `AgentRuntimeEntry` for chat utility,
  supply request, upgrade request, item query, and transfer command callbacks.
  Trade-invite timing, sell-trash shop visit scheduling, Maker command delays,
  potion/ammo request replies, upgrade request routing, async transfer
  evaluation, request-id superseding, and transfer result decisions remain
  unchanged. `AgentShopService` has an Agent-entry sell-trash overload while
  deeper shop internals remain a later staged migration.
- `AgentBuildStatusRuntime` now stays on `AgentRuntimeEntry` when building
  status-check actions. Job/AP/SP prompt lookup, auto-assignment callbacks,
  gear suggestion gates, spawn-upgrade offering, pending-offer checks, and
  queued build replies remain unchanged.
- `AgentMakerService` now accepts `AgentRuntimeEntry` for monster-crystal and
  trash-equip disassembly batches. Maker skill gating, active-batch guard,
  leftover scanning, trash-equip selection, client-lock retry behavior,
  activity-epoch interruption, step timing, abort reasons, and completion
  replies remain unchanged.
- `AgentBuildService`, `AgentStarterKitService`, and `AgentBuildRuntime`
  now accept `AgentRuntimeEntry` for AP build selection, AP/SP auto-assignment,
  respec prompts, level-up checks, job advancement callbacks, starter-kit
  grants, build status refresh, and AP confirmation replies. Build prompts,
  stat allocation order, SP variant gating, job-prompt thresholds, starter-kit
  contents, auto-equip refresh, and reply timing remain unchanged.
- `AgentOfferService` now accepts `AgentRuntimeEntry` for recommended gear
  offers, sibling offers, loot-offer scheduling, prompt auto-accept, recipient
  resolution, and gear-offer need checks. Offer reservations, owner-idle gates,
  pending-action/trade gates, sibling same-map filtering, prompt timing,
  positive/negative response handling, gear scoring, and throwing-star
  recipient behavior remain unchanged.
- `AgentPotionService` now accepts `AgentRuntimeEntry` for potion checks,
  passive recovery, low-pot share requests, owner-share offers, donor
  selection, donor plans, and scheduled pot share execution. Autopot setup,
  HP/MP thresholds, cooldown/backoff behavior, donor selection, low-donor
  replies, passive recovery math, stop-grind low-pot behavior, and
  supply-share transfer behavior remain unchanged.
- `AgentAmmoService` now accepts `AgentRuntimeEntry` across ammo-share checks,
  request routing, donor selection, owner-share offering, donor plans, and
  scheduled share execution. Low-ammo thresholds, cooldown/backoff keys, donor
  scoring, same-ammo surplus donation, random delays, offer dialogue, and
  supply-share transfer behavior remain unchanged.
- `AgentShopService` now receives `InventoryGateway` from runtime callers for
  map-change resupply checks, sell-trash shop visits, shop tick purchase
  sequences, recharge metadata, and shortfall item names. Runtime boundaries
  still provide the Cosmic inventory gateway, while the shop capability no
  longer performs direct Cosmic adapter lookup for those metadata reads.
  Shop approach timing, purchase/recharge order, sell-trash sequencing, and
  dialogue output remain unchanged.
- `AgentInventoryGatewayRuntime` now owns the live inventory gateway lookup in
  `server.agents.integration`. `AgentOfferService` and
  `AgentInventoryRuntimeAdapters` use this integration boundary instead of
  importing the Cosmic adapter directly for legacy metadata. This is a boundary
  cleanup only; offer prompts, gear reservation decisions, throwing-star
  comparisons, and inventory transfer routing are unchanged.
- `AgentTradeGatewayRuntime` now owns the live trade gateway lookup in
  `server.agents.integration`. `AgentInventoryTransferService` uses
  `AgentInventoryGatewayRuntime` and `AgentTradeGatewayRuntime` for its legacy
  metadata and trade mutation seams instead of importing the Cosmic adapter
  directly. Drop-vs-trade choice handling, transfer availability checks, item
  collection, equip/ammo classification, batch opening, invitation replies, and
  trade start/invite ordering are unchanged.
- Manual trade, supply-share trade, trade sequence runtime, cancellation,
  invite-wait, confirm-wait, and completion services now call trade lifecycle
  operations through `AgentTradeGatewayRuntime`. This removes direct Cosmic
  adapter imports from those simple trade lifecycle services while preserving
  manual-trade timeout behavior, invite acceptance, supply-share retries,
  sequence callbacks, timeout replies, cancel/reset order, completion reactions,
  and visible reply timing.
- `AgentPacketGatewayRuntime` now owns the live packet gateway lookup in
  `server.agents.integration`. `AgentTradeItemAddService` uses that boundary
  for trade item-add packet emission instead of importing the Cosmic adapter
  directly. Inventory removal, copied trade item slot/quantity assignment,
  partner packet mirroring, and restore-slot bookkeeping are unchanged.
- `AgentInventoryTickRuntime`, `AgentInventoryTransferService`,
  `AgentManualTradeRuntimeService`, `AgentTradeTickRuntimeService`, and
  `AgentTradeLifecycleRuntimeService` now accept `AgentRuntimeEntry` across
  inventory/drop/trade facade routing. Passive-loot dispatch, manual-trade
  timeout handling, transfer category routing, trade batching, closed-window
  handling, invite waits, item-add ticks, confirmation waits, lifecycle reset,
  and trade reaction behavior remain unchanged.
- `AgentInventoryDropService` now accepts `AgentRuntimeEntry` for drop-category
  routing and focused drop helpers. Drop-limit checks, category dispatch,
  safe-to-drop filtering, named-item lookup, inventory drop calls, and legacy
  reply text remain unchanged.
- `AgentInventorySellTrashService` now accepts `AgentRuntimeEntry` when
  collecting sell-trash equipment. Safe-item collection, self-upgrade reserves,
  other-recipient offer reservations, equip sorting, and trash classification
  remain unchanged.
- `AgentEquippedSlotTradeService` now accepts `AgentRuntimeEntry` for equipped
  slot trade preparation and temporary restore-slot cleanup. Counting,
  cash-item filtering, bag-slot checks, item move order, restore-slot memory,
  and restore behavior remain unchanged.
- `AgentPendingOfferChatRouteService` now routes pending offer responses over
  generic `AgentRuntimeEntry` groups instead of `BotEntry` groups. The targeted
  response resolver, same-map recipient check, positive/negative confirmation
  behavior, and direct item transfer callback remain behavior-compatible.
- `AgentSupplyShareTradeService` now accepts `AgentRuntimeEntry` for potion
  and ammo share transfer orchestration. Empty-transfer skips, active-trade
  retry queueing, share budget setup, trade sequence initialization, and
  invitation reply behavior remain unchanged.
- `AgentTradeSequenceRuntimeService` now accepts `AgentRuntimeEntry` for trade
  sequence start/open-batch orchestration. It delegates to the already
  Agent-owned sequence/batch services while preserving recipient resolution,
  invite-start order, and first-batch invitation reply behavior.
- `AgentManualTradeService` now accepts `AgentRuntimeEntry` for manual-trade
  timeout tracking, greeting cleanup, state clearing, and delayed invite
  acceptance. `AgentManualTradeRuntimeService` remains the temporary BotEntry
  runtime caller while manual trade behavior stays unchanged.
- `AgentTradeLifecycleService` now accepts `AgentRuntimeEntry` for trade
  cancellation, manual-trade clearing, reset, completion reactions, and
  lifecycle callbacks. `AgentTradeLifecycleRuntimeService` remains the
  BotEntry compatibility edge for current inventory/trade runtime callers.
- `AgentLeaderSafetyService` now owns inactive-leader safe-mode logic over
  `AgentRuntimeEntry` instead of `BotEntry`. The outer
  `AgentLeaderSafetyRuntime` remains the temporary BotEntry compatibility edge,
  preserving inactive timer handling, town-return decisions, cluster formation
  targets, return-scroll fallback, and active-leader return announcements.
- `AgentMovementOnlyStepRuntime` now accepts `AgentRuntimeEntry` for
  ownerless movement-only tick preparation, removing another BotEntry runtime
  wrapper while preserving AI cadence preparation and target snapshot capture.
- `AgentMovementOnlyRuntime` now uses Agent entry overloads for follow-anchor
  resolution, shop visit ticks, and movement-core dispatch, removing its direct
  BotEntry dependency while preserving movement-only tick ordering.
- `AgentMapTransitionRuntime` now stays on `AgentRuntimeEntry` for grounding
  and tracked map-change hooks. Movement pose/reset/broadcast and shop
  map-change side effects use Agent entry overloads without BotEntry casts.
- `AgentMovementOnlyMapChangeRuntime` now accepts `AgentRuntimeEntry` and uses
  Agent movement pose/reset/broadcast hooks directly. `AgentShopService`
  exposes an Agent entry map-change overload so movement-only map changes no
  longer cast for shop visit refresh.
- `AgentRecoveryTeleportRuntime` now accepts `AgentRuntimeEntry` and delegates
  directly to Agent movement pose/reset/broadcast services. Live tick gates and
  movement-only recovery call it without BotEntry casts, preserving the same
  teleport-distance recovery thresholds.
- `AgentStuckDetectionRuntime` now accepts `AgentRuntimeEntry` and uses the
  Agent entry overload on `AgentMovementRecoveryService`; the temporary recovery
  cast is isolated inside movement recovery while stuck timers, cooldowns, and
  recovery jumps remain unchanged.
- `AgentStandaloneMoveTargetRuntime` now uses AgentRuntimeEntry all the way to
  movement-core dispatch. `AgentMovementTickRuntime` gained an Agent entry
  overload that isolates the temporary BotEntry cast while lower movement
  services continue their staged reconstruction.
- `AgentLocalAttackMoveWindowRuntime` now exposes AgentRuntimeEntry-based
  configured-distance wrappers around `AgentLocalAttackMoveWindowService`;
  legacy BotEntry callers still pass through by inheritance while the wrapper
  no longer imports `server.bots`.
- `AgentLiveTickContextRuntime` now accepts `AgentRuntimeEntry` directly and
  uses Agent-owned movement profile and follow-action-window services without
  BotEntry casts. Follow-anchor resolution, target snapshot capture, leader
  motion tracking, and map-change cleanup behavior remain unchanged.
- `AgentTickPreflightRuntime` now accepts `AgentRuntimeEntry` directly and
  calls tick preparation without a BotEntry cast. The preflight service tests
  also use Agent runtime entries while heartbeat, offer expiry, map-missing
  cleanup, and AI tick cadence behavior remain unchanged.
- `AgentSpawnPlacementService` now uses generic `AgentRuntimeHandle` hooks for
  spawn placement and normalization. The BotEntry identity lookup remains only
  in `AgentSpawnPlacementRuntime`, preserving the current spawn reset order.
- `AgentMovementPhaseDispatchService` now exposes `AgentRuntimeEntry` methods.
  Action-lock physics, idle physics, and grind no-target fallback use this
  Agent-owned movement boundary directly; the temporary BotEntry cast is
  isolated inside movement dispatch only for lower airborne/ground services
  that have not yet been reconstructed.
- `AgentMovementPhaseRuntime` now accepts `AgentRuntimeEntry` above the
  movement phase service. Phase selection and hook wiring no longer import the
  legacy bot entry type; lower movement implementation slices remain to be
  reconstructed.
- `AgentFollowMapSyncRuntime` now accepts `AgentRuntimeEntry`; movement-only
  and recovery live-gate paths call the Agent follow-map sync boundary without
  BotEntry casts, preserving the existing cross-map follow synchronization
  behavior.
- `AgentFormationRuntime` now accepts Agent runtime entries and generic Agent
  entry lists, preserving formation state lookup and offset application while
  removing another runtime dependency on the legacy bot entry type.
- `AgentFidgetService` social/greeting start helpers and
  `AgentFidgetSideEffects` now use `AgentRuntimeEntry`, keeping deeper
  active fidget movement execution for a later movement reconstruction slice.
- `AgentEquipmentRuntime` now exposes equipment chat callbacks over
  `AgentRuntimeEntry`; the temporary movement-command adapter supplies the
  legacy stop command where unequip-all still needs it.
- `AgentChatReportRuntime` now exposes report callbacks and direct report
  methods over `AgentRuntimeEntry`; lower supply, offer, and pending-action
  adapters keep the remaining compatibility casts for their own BotEntry-shaped
  side effects.
- `AgentCommandTargetResolver.resolveTargetedAgent` is now generic over
  `AgentRuntimeEntry`, preserving current targeted command matching while
  removing its direct dependency on the legacy bot entry type.
- `AgentMovementRuntime` movement chat callbacks now use
  `AgentRuntimeEntry`; lower movement command and potion adapters keep the
  temporary BotEntry compatibility casts for their own side effects.
- `AgentFollowTargetRuntime` now accepts Agent runtime entry lists and no
  longer owns BotEntry casts; lower reply, potion, and movement-command
  adapters keep their compatibility edges.
- `AgentDismissRuntime` and lifecycle chat dismiss wiring now use
  `AgentRuntimeEntry` stopper callbacks; the outer interaction runtime remains
  the compatibility edge for the current stop command.
- `AgentLeaderSessionRuntime` now resolves tick leaders from
  `AgentRuntimeEntry`; tick-core invokes it without a BotEntry cast while other
  tick-core hooks remain staged for later reconstruction.
- Targeted and untargeted chat route services now depend on the Agent-owned
  `AgentRuntimeHandle` boundary and `AgentTargetedCommandMatch`. The legacy
  `AgentCommandTargetResolver` match is adapted in `AgentChatRouteRuntime`, leaving
  command routing behavior unchanged while reducing `BotEntry` type leakage
  from dialogue capabilities.
- `AgentChatIngressService` now uses the same `AgentRuntimeHandle` boundary for
  entry lookup and route callbacks. `AgentChatRouteRuntime` still supplies the
  temporary `BotEntry` lists from the legacy runtime registry, preserving route
  ordering and behavior.
- `AgentWhisperCommandService` now uses `AgentRuntimeHandle` hooks for entry
  resolution, reply-channel marking, and chat dispatch. The new
  `AgentWhisperCommandRuntime` keeps the temporary `BotEntry` registry lookup
  and `AgentChatOrchestratorContext` construction at the runtime adapter
  edge.
- `AgentSenderRelation` no longer imports `BotEntry`; it classifies
  owner/party/stranger from resolved Agent, leader, and sender characters while
  `AgentLlmReplyService` keeps the temporary identity adapter call.
- `AgentPromptBuilder` no longer imports `BotEntry`; prompt text is built from
  resolved Agent identity/name and situation text supplied by
  `AgentLlmReplyService`.
- `AgentSituationBuilder` no longer imports `BotEntry`; situation text is built
  from resolved Agent/map/activity snapshot values supplied by
  `AgentLlmReplyService`.
- LLM follow-up reply delivery now accepts `AgentRuntimeHandle` plus a reply
  emitter callback; the current `BotEntry` delivery path remains supplied by
  `AgentLlmReplyService` through `AgentLlmRuntime.replyNow`.
- `AgentLlmPromptContext` now carries prompt/situation snapshot values without
  importing `BotEntry`; `AgentLlmReplyService` remains the temporary adapter
  that populates it from runtime state.
- `AgentLlmReplyService` no longer imports `BotEntry`; it now accepts
  `AgentLlmReplyRequest` plus an Agent handle reply emitter. The new
  `AgentLlmReplyRuntime` owns the temporary `BotEntry` adaptation for identity,
  reply-channel, prompt-context, and reply delivery wiring.
- `AgentPotionCheckRequestService` no longer imports `BotEntry`; it accepts an
  Agent handle resolver and potion-check requester. The temporary
  `AgentPotionCheckRequestRuntime` owns BotClient detection, active leader
  lookup, BotEntry lookup, and `AgentPotionStateRuntime` wiring.
- `AgentGroupSupplyResponderSelector` no longer imports `BotEntry`; it selects
  over Agent handles using a supplied map-id reader. `AgentChatRouteRuntime`
  keeps the temporary map-id adapter.
- `AgentBotAmmoDonorPlan` moved out of integration as
  `AgentAmmoDonorPlan<E extends AgentRuntimeHandle>` in the supplies
  capability. Existing ammo donor selection and tests now use the Agent-owned
  value object while `AgentAmmoService` still adapts with `BotEntry`.
- `AgentBotPotionDonorPlan` moved out of integration as
  `AgentPotionDonorPlan<E extends AgentRuntimeHandle>` in the supplies
  capability. Existing potion donor selection and tests now use the Agent-owned
  value object while `AgentPotionService` still adapts with `BotEntry`.
- `AgentCombatHealRuntime.tickSupportHealing` now accepts
  `AgentRuntimeEntry`. Support-heal readiness, heal/undead target selection,
  jump-heal positioning, cooldown application, attack packet emission, and
  movement broadcast are unchanged while sibling lookup remains behind the
  temporary session lifecycle bridge.
- `AgentCombatBuffRuntime.tickBuffs` now accepts `AgentRuntimeEntry`.
  Skill-buff readiness, living-mob checks, support rebuff detection, support
  skill execution, cooldown updates, alert marking, and legacy debug summaries
  are unchanged while common tick no longer casts for this buff callback.
- `AgentBuffService` now accepts `AgentRuntimeEntry` for ticking and debug
  state. Consumable buff enablement, cheap-mode filtering, scan cadence,
  inventory selection, ACC-hit checks, use-item application, last-action notes,
  chat summaries, and debug formatting are unchanged while common tick no
  longer casts for this buff-pot callback.
- `AgentCommonTickRuntime` now invokes the already Agent-entry-based
  action-lock and combat skill-cache callbacks directly. Cooldown decay,
  skill-cache signature checks, cache reset, attack/AOE/heal/summon/support
  bucket selection, and rebuff scheduling are unchanged.
- `AgentCombatDamageRuntime` now accepts `AgentRuntimeEntry` for mob-hit and
  mob-damage ticking. Touch-damage rolling, sweep detection, damage broadcast,
  HP/MP mutation, mob-hit cooldowns, alert marking, death entry, knockback
  gating, and movement broadcast are unchanged; common tick also invokes the
  existing Agent-entry death callbacks directly.
- `AgentCombatTargetRuntime` and `AgentCombatReportRuntime` now accept
  `AgentRuntimeEntry`. Grind, patrol, follow-attack, and reachable-target
  selection, graph scoring, region occupancy penalties, debug stats, consumable
  buff debug lines, and skill-buff debug lines are unchanged. Grind-mode target
  search now uses these Agent-entry selectors without redundant casts.
- `BotManager.java#anchored-farm-runtime` is migrated to
  `server.agents.runtime.AgentAnchoredFarmRuntime`. Anchored farm runtime hooks
  now use `AgentRuntimeEntry` for local opportunity attacks, idle movement and
  broadcast, and movement-core stepping while preserving anchored-farm behavior.
- `BotManager.java#movement-command-callbacks` now routes script-task,
  tick-failure, and interaction command callbacks through the
  `AgentRuntimeEntry`-based `AgentMovementCommandRuntime` API. Temporary
  `BotEntry` casts were removed from script execution and tick failure; the
  interaction facade still returns `BotEntry` until registration/lifecycle
  return types are migrated.
- `BotMovementManager.java#movement-helper-dispatch` is migrated through
  `AgentMovementRecoveryService`, `AgentMovementPhaseDispatchService`, and
  `AgentGroundActionPlanner`. Unstuck recovery, airborne dispatch, ground-step
  planning, ledge fallback, and mob-avoidance decisions now use
  `AgentRuntimeEntry` directly with unchanged movement behavior.
- `BotManager.java#live-mode-tick-callbacks` is migrated through
  `AgentLiveModeTickRuntime` and `AgentGrindModeRuntime`. Local opportunity,
  movement-core, anchored-farm, and grind-mode callbacks now use
  `AgentRuntimeEntry`; shop visits, scripted movement/combat, follow-idle,
  anchored farm, grind, and final movement-tail behavior remain unchanged.
- `BotShopManager.java#shop-service-runtime` is migrated through
  `server.agents.capabilities.shop.AgentShopService`. Shop map-change
  detection, sell-trash requests, shop visits, purchase sequences, recharge
  sequences, sell-trash sequences, and scheduled shop-step guards now use
  `AgentRuntimeEntry` while preserving existing shop behavior.
- `BotManager.java#tick-core-runtime` is migrated through
  `AgentTickRuntime` and `AgentTickCoreRuntime`. Guarded tick dispatch,
  preflight, leader resolution, inactive-leader handling, ownerless movement,
  live-context preparation, live gates, live mode dispatch, and failure
  handling now use `AgentRuntimeEntry` at the runtime seam.
- `BotEntry` runtime storage is migrated to `AgentRuntimeEntry` through
  `AgentRuntimeRegistry`, `AgentLifecycleService`, `AgentRegistrationRuntime`,
  `AgentSpawnRuntime`, `AgentSpawnPlacementRuntime`, and
  `AgentInteractionRuntime`. Agent registration now constructs the Agent
  runtime entry directly; the deprecated `server.bots.BotEntry` shell is no
  longer used by production Agent modules.
- Final production dependency scan for this reconstruction slice shows no
  `server.bots` references under `src/main/java/server/agents/**`. The only
  production file remaining under `src/main/java/server/bots/**` is the
  deprecated empty `BotEntry` compatibility shell, with no runtime logic.
- `AgentRangedPriorityTargetSelector` now accepts `AgentRuntimeEntry`. No-ammo
  gating, ranged weapon checks, degenerate-target replacement, attack-plan
  routing, attack range checks, and grounded-use gating are unchanged while
  grind-mode priority-target hooks no longer cast for this selector.
- `AgentGrindNavigationTargetSelector` and `AgentGrindNavigationRuntime` now
  accept `AgentRuntimeEntry`. Retreat holds, breakout direction selection,
  cross-region retreat scoring, projectile retreat probes, local retreat
  validation, path lookup, and region resolution are unchanged while grind-mode
  navigation hooks no longer cast for this selector.
- `AgentPotionCheckRequestRuntime` now uses `AgentRuntimeEntry` for the generic
  potion-check request hook. BotClient detection, active-leader lookup,
  character-id registry lookup, and retry-soon delay behavior are unchanged.
- `AgentShopPurchaseSequence` and `AgentShopPurchaseAction` now use the
  Agent-owned `AgentRuntimeHandle` boundary. `AgentShopService` remains the
  temporary `BotEntry` adapter for the current shop purchase/sell-trash flow,
  with recharge, ammo, potion, shortfall, bought-summary, and delayed-step
  behavior unchanged.
- `AgentTradeSequenceOrchestrator` now accepts `AgentRuntimeEntry`; the
  current `AgentTradeSequenceRuntimeService` still supplies the temporary
  `BotEntry` shell. Missing-recipient replies, sequence/batch state setup,
  busy-recipient cancellation, trade start/invite callbacks, and invitation
  announcement behavior are unchanged.
- `AgentTradeTransferAvailabilityRuntimeService` now accepts
  `AgentRuntimeEntry`; named-fragment counting, equipped-slot counting,
  owner-based recommended-item collection, and item-count behavior are
  unchanged while inventory runtime adapters keep the temporary compatibility
  entry.
- `AgentScript`, `AgentScriptContext`, `AgentScriptRunner`,
  `AgentPartyQuestHooks`, and KPQ Stage 1 script applicability now use
  `AgentRuntimeEntry`; script queueing, wait/int state, cheap-target checks,
  item-drop actions, KPQ Stage 1 reset/applicability, and KPQ Stage 5 dispatch
  behavior are unchanged.
- `AgentTickFailurePolicy` now accepts `AgentRuntimeEntry`; failure-window
  counting, volatile action cleanup, forced-idle escalation, disable escalation,
  and warning/disable context reporting are unchanged. `AgentTickFailureRuntime`
  remains the temporary adapter for the legacy stop-command callback.
- `AgentTickOrchestrator` now accepts `AgentRuntimeEntry`; guarded tick
  execution, failure reset, failure handler dispatch, and performance timing
  behavior are unchanged. `AgentTickRuntime` keeps the temporary BotEntry
  adapter for movement command callbacks and tick-core entrypoints.
- Tick-core map-transition and standalone-move callbacks now carry
  `AgentRuntimeEntry` through `AgentMapTransitionRuntime`,
  `AgentStandaloneMoveTargetRuntime`, `AgentLiveTickGateRuntime`, and the
  issue-grind/follow callback path. Existing grounding, PQ mode switching,
  shop map-change hooks, tracked-map-change issue callbacks, and standalone
  move-target behavior are unchanged; script/physics/live-mode callback
  adapters remain as later slices.
- `AgentScriptTaskRuntime`, `AgentScriptTaskTickService`, and the public
  start/complete boundary of `AgentScriptTaskExecutionService` now accept
  `AgentRuntimeEntry`; task activation, task completion, configured stop
  distance, and move/follow/grind/stop/drop dispatch behavior are unchanged.
  The execution service keeps the temporary BotEntry adapter for legacy
  movement commands.
- `AgentLifecycleService.DismissHooks` and the dismiss entry speaker now accept
  `AgentRuntimeEntry`; dismiss lookup/removal, scheduled-task cancellation,
  stop dispatch, farewell delay, and farewell reply behavior are unchanged.
  Spawn/register/relogin lifecycle hooks remain separate BotEntry-backed
  migration targets.
- `AgentIdlePhysicsRuntime` now accepts `AgentRuntimeEntry`; live trade-window
  physics, idle-mode physics, ownerless idle physics, anchored-farm idle, and
  movement-only idle call sites no longer cast for this boundary. The runtime
  still adapts to BotEntry for lower movement phase dispatch services.
- Common-tick script task callbacks now use `AgentRuntimeEntry` through
  `AgentCommonTickRuntime`, `AgentLiveTickGateRuntime`, and
  `AgentTickCoreRuntime`; common tick order, PQ locking, action-lock behavior,
  and script task execution are unchanged.
- `AgentStandaloneMoveTargetTickService` now accepts `AgentRuntimeEntry`.
  Map-change grounding, movement-profile refresh, stored-target lookup, and
  movement-step dispatch remain unchanged while the temporary `BotEntry`
  adapter edge shrinks.
- `AgentMovementOnlyMapChangeService` now accepts `AgentRuntimeEntry`. Map
  tracking, foothold index rebuild, ground-point resolution, teleport/reset,
  movement broadcast, shop map-change hook, and status check ordering remain
  unchanged while the temporary `BotEntry` adapter edge stays in
  `AgentMovementOnlyMapChangeRuntime`.
- `AgentFollowOpportunityTickService` now accepts `AgentRuntimeEntry`.
  Following/climbing gates, same-map and distance checks, local opportunity
  attack delegation, and target propagation remain unchanged while
  `AgentLiveModeTickRuntime` keeps the temporary `BotEntry` callback adapter.
- `AgentAnchoredFarmModeTickService` now accepts `AgentRuntimeEntry`.
  Farm-anchor presence checks, anchored-farm tick delegation, and consumed-tick
  behavior remain unchanged while `AgentLiveModeTickRuntime` keeps the
  temporary `BotEntry` callback adapter.
- `AgentAnchoredFarmTickService` now accepts `AgentRuntimeEntry`. Anchor
  map-mismatch cleanup, local opportunity attack delegation, near-anchor idle,
  precise move-target setting, and movement-core dispatch remain unchanged
  while `AgentAnchoredFarmRuntime` keeps the temporary `BotEntry` callback
  adapter.
- `AgentMovementTargetMaintenanceService.clearFarmAnchorOnMapChange` now accepts
  `AgentRuntimeEntry`, preserving same-map retention, map-change anchor
  clearing, and precise move-target cleanup.
- `AgentFollowIdleMovementService` now accepts `AgentRuntimeEntry`.
  Eligibility gates, recheck timing, navigation decision marking, and
  stuck-progress reset behavior remain unchanged while runtime wrappers keep
  temporary `BotEntry` public signatures.
- `AgentActionLockPhysicsServiceTest` now exercises the existing
  Agent-runtime-entry service boundary directly. Attack-cooldown gating and
  swim, airborne, grounded, and legacy climbing branches remain unchanged.
- `AgentActionLockPhysicsRuntime` now accepts `AgentRuntimeEntry`.
  Attack-lock physics gating and swim/airborne/grounded dispatch remain
  unchanged while the runtime wrapper keeps the temporary `BotEntry` movement
  phase adapter.
- `AgentGroundTargetService` now accepts `AgentRuntimeEntry`. Grind-mode gates,
  active-navigation bypass, graph warmup, same-region detection, rope-region
  bypass, and safe edge-margin clamping remain unchanged.
- `AgentNavigationWarmupService` now accepts `AgentRuntimeEntry`. Leader/map
  throttle checks, walkable-foothold threshold, and fallback notification text
  remain unchanged.
- `AgentNavigationPortalService` now accepts `AgentRuntimeEntry`. Portal
  cooldown checks, portal-use success detection, cooldown update, navigation
  reset, and movement state reset remain unchanged.
- `AgentAirborneLaunchService` now accepts `AgentRuntimeEntry`, with
  `AgentGroundPhysicsService.stopGroundMotion` lifted to the same boundary.
  Air-state initialization, climb intent, horizontal/vertical velocity setup,
  down-jump clearing, movement velocity projection, and character state sync
  remain unchanged.
- `AgentKnockbackMovementService` now accepts `AgentRuntimeEntry`. Facing
  preservation, blocked-rope clearing, airborne launch reuse, air knockback
  velocity setup, and character state sync remain unchanged.
- `AgentCommandModeServiceTest` now exercises the existing Agent-runtime-entry
  service boundary directly. Null-entry skip behavior, guard evaluation, task
  clearing, shop cancellation, and mode-start ordering remain unchanged.
- `AgentFinalMovementTailServiceTest` now exercises the existing
  Agent-runtime-entry service boundary directly. Target and AI-tick flag
  delegation to movement-core hooks remains unchanged.
- `AgentLiveTickGateServiceTest` now exercises the existing Agent-runtime-entry
  service boundary directly. Common, trade-window, idle, recovery, and
  tracked-map gate ordering and short-circuit behavior remain unchanged.
- `AgentTickCoreServiceTest` now exercises the existing Agent-runtime-entry
  service boundary directly. Preflight short-circuiting, leader resolution,
  inactive, ownerless, dead, live-context, live-gate, and live-mode ordering
  remain unchanged.
- `AgentLiveTickContextServiceTest` now exercises the existing
  Agent-runtime-entry service boundary directly. Movement-profile refresh,
  follow-anchor resolution, target snapshot capture, leader observation,
  map-change cleanup, and move-window update ordering remain unchanged.
- `AgentMapEnvironmentServiceTest` now exercises the existing
  Agent-runtime-entry service boundary directly. Null-map handling and swim-map
  detection remain unchanged.
- `AgentHeartbeatServiceTest` now exercises the existing Agent-runtime-entry
  service boundary directly. Interval gating, heartbeat timestamp update,
  last-packet update, and movement broadcast behavior remain unchanged.
- `AgentIdlePhysicsService` now accepts `AgentRuntimeEntry`. Active-mode
  gating, swim/airborne/grounded physics dispatch, idle stance correction, and
  movement broadcast behavior remain unchanged while `AgentIdlePhysicsRuntime`
  keeps the temporary `BotEntry` adapter edge.
- `AgentStuckDetectionService` now accepts `AgentRuntimeEntry`. Cooldown
  ticking, active-navigation gates, stuck-position tracking, stuck timer
  accumulation, cooldown blocking, and unstuck trigger behavior remain unchanged
  while `AgentStuckDetectionRuntime` keeps the temporary `BotEntry` adapter
  edge.
- `AgentMovementPhaseServiceTest` now exercises the existing Agent-runtime-entry
  service boundary directly. Climb priority, swim-map airborne dispatch,
  non-swim airborne dispatch, and grounded dispatch remain unchanged.
- `AgentLocalAttackMoveWindowService` now accepts `AgentRuntimeEntry`.
  Null-position clearing, long/short/settled window timing, follow-mode gating,
  and settle-band checks remain unchanged.
- `AgentGrindModeDispatchService` now accepts `AgentRuntimeEntry`.
  Non-grinding fall-through, grind tick delegation, run-AI flag propagation, and
  target result propagation remain unchanged while `AgentLiveModeTickRuntime`
  keeps the temporary `BotEntry` callback adapter.
- `AgentGrindNoTargetFallbackService` now accepts `AgentRuntimeEntry`.
  Target clearing, swim/airborne fall-through, wander-direction side effects,
  patrol/no-grind target resolution, and movement-step dispatch remain
  unchanged while `AgentGrindModeRuntime` keeps the temporary `BotEntry`
  callback adapter.
- `AgentGrindTargetSearchService` and `AgentGrindTargetSearchPolicy` now accept
  `AgentRuntimeEntry`. AI-tick gating, retarget cooldown checks, patrol/grind
  target selection, AoE cluster switch hysteresis, and next-search scheduling
  remain unchanged while `AgentGrindModeRuntime` keeps the temporary `BotEntry`
  target-finder adapter.
- `AgentGrindNavigationTailService` now accepts `AgentRuntimeEntry`.
  Cross-region retreat precedence, AoE reposition navigation, degenerate-attack
  latch clearing, patrol gating, and convenient-loot override behavior remain
  unchanged while `AgentGrindModeRuntime` keeps the temporary `BotEntry`
  selector adapter.
- `AgentGrindTargetCommitmentService` now accepts `AgentRuntimeEntry`.
  Target commit, wander/patrol cleanup, ranged-priority replacement, closer
  threat replacement, target-position propagation, and attack-plan invalidation
  remain unchanged while `AgentGrindModeRuntime` keeps the temporary `BotEntry`
  ranged-priority adapter.
- `AgentGrindRangedEngagementService` now accepts `AgentRuntimeEntry`.
  Degenerate-attack gating, ranged retreat selection, AoE reposition checks,
  attack execution, cooldown comparison, jump initiation, idle-on-ground, and
  movement broadcast behavior remain unchanged while `AgentGrindModeRuntime`
  keeps the temporary `BotEntry` callback adapter.
- Combat reply and scheduler pass-through bridges were removed. Combat
  warning/status delivery now calls the existing Agent reply and scheduler
  runtimes directly through `AgentCombatRuntime`.
- Shop reply and scheduler pass-through bridges were removed. Shop owner/map
  replies and delayed shop callbacks now call the existing Agent reply and
  scheduler runtimes directly through `AgentShopRuntime`.
- Ammo reply and scheduler pass-through bridges were removed. Ammo map replies,
  delayed callbacks, and delay sampling now call the existing Agent reply and
  scheduler runtimes directly through `AgentAmmoRuntime`.
- Potion reply and scheduler pass-through bridges were removed. Potion map
  replies, delayed callbacks, and delay sampling now call the existing Agent
  reply and scheduler runtimes directly through `AgentPotionRuntime`.
- Scroll-reaction reply and scheduler pass-through bridges were removed.
  Queued scroll-reaction dialogue and delayed callbacks now call the existing
  Agent reply and scheduler runtimes directly through
  `AgentScrollReactionRuntime`.
- Maker reply and scheduler pass-through bridges were removed. Maker owner
  replies and delayed batch callbacks now call the existing Agent reply and
  scheduler runtimes directly through `AgentMakerRuntime`.
- Build reply and scheduler pass-through bridges were removed. Build/AP/SP/job
  replies, build-status queued replies, and delayed job advancement callbacks
  now call the existing Agent reply and scheduler runtimes directly.
- Offer reply and scheduler pass-through bridges were removed. Gear/loot offer
  replies, queued/map/channel dialogue, queued-say delay estimation, and delayed
  callbacks now call the existing Agent reply and scheduler runtimes directly.
- PQ reply pass-through bridge was removed. Queued party-quest dialogue now
  calls the existing Agent reply runtime directly through `AgentPqRuntime`.
- LLM reply pass-through bridge was removed. LLM reply delivery now calls the
  existing Agent reply runtime directly through `AgentLlmRuntime`.
- Equipment reply and scheduler pass-through bridges were removed. Equipment
  visible replies, unequip, auto-equip debug, and auto-equip callbacks now call
  the existing Agent reply and scheduler runtimes directly.
- Inventory reply and scheduler pass-through bridges were removed. Inventory,
  trade, drop, and meso reply/timing bridge methods now call the existing Agent
  reply and scheduler runtimes directly.
- Manager reply pass-through bridge was removed. Runtime command/error,
  formation, transfer, relogin, respawn, and tick-failure replies now call the
  existing Agent reply runtime directly.
- Movement reply and scheduler pass-through bridges were removed.
  Movement/follow/grind/stop/fidget/greeting replies and random-delay callbacks
  now call the existing Agent reply and scheduler runtimes directly.
- Manager scheduler pass-through bridge was removed. Delayed callbacks now call
  the existing Agent scheduler runtime directly, while scheduled-task
  cancellation state moved to `AgentScheduledTaskRuntime`.
- Shop purchase sequencing and shortfall value objects moved out of integration
  as `AgentShopPurchaseSequence`, `AgentShopPurchaseAction`,
  `AgentShopBuyReport`, and `AgentShopShortfallReason` in the shop capability.
  Existing shop purchase flow and tests now use the Agent-owned value objects
  while the sequence still carries `BotEntry` until the shop runtime boundary is
  replaced.
- The pure transfer-command wrapper moved out of integration as
  `AgentTransferCommand` in the commands package. The temporary
  `AgentCommandTargetResolver` still adapts legacy bot-targeted command matching
  while transfer command data is Agent-owned.
- The BotEntry-specific targeted-command match wrapper moved out of
  integration as generic `AgentTargetedCommandMatch<E extends
  AgentRuntimeHandle>` in the commands package. `AgentCommandTargetResolver` still
  supplies the temporary `BotEntry` adapter until targeted command resolution
  stops depending on the bot shell.
- The BotEntry-specific command target wrapper moved out of integration as
  `AgentNamedCommandTarget<E extends AgentRuntimeHandle>` in the commands
  package. `AgentCommandTargetResolver` now supplies names from the temporary
  identity adapter without owning a Bot-specific target DTO.
- Pending-offer response routing now operates on generic Agent runtime handles.
  `AgentPendingOfferResponseService` owns the routing algorithm without
  importing `BotEntry`; the temporary `AgentPendingOfferChatRouteService`
  adapter keeps the BotEntry-specific pending-offer target check.
- Character and inventory report pass-through bridges were removed.
  `AgentChatReportRuntime` now calls `AgentCharacterDialogueReporter` and
  `AgentInventoryDialogueReporter` directly; the existing dialogue reporter
  tests cover the unchanged formatting behavior.
- The supply report pass-through bridge was removed. Potion count and autopot
  debug report delivery now calls `AgentSupplyDialogueReporter` and
  `AgentPotionService` directly from the chat report runtime, preserving the
  same report text.
- The skill report decision bridge was removed. Skill report decision assembly
  now lives in `AgentSkillReportDecisionService` in the dialogue capability,
  while the temporary chat report runtime still applies the decision through
  `AgentPendingActionRuntime` until pending-action side effects are fully
  reconstructed.
- The range report bridge was removed. Range report assembly now lives in
  `AgentRangeReportService` in the dialogue capability, and equipment debug
  plus chat report callers use it directly while keeping the existing range
  text unchanged.
- The movement report bridge was removed. Movement kinematics snapshot
  formatting now lives in `AgentMovementDialogueReporter`, with the temporary
  chat report runtime only supplying the current snapshot from the integration
  edge.
- The report reply pass-through bridge was removed. Report delivery now queues
  through the existing reply runtime directly, preserving the same owner-directed
  queued reply behavior while reducing one temporary BotEntry adapter.
- The report scheduler pass-through bridge was removed. Report callback wiring
  now supplies the existing random-delay scheduler directly while preserving the
  same report delay behavior.
- The control report pass-through bridge was removed. Control buff-debug and
  skill-buff-debug callbacks now call the existing chat-report facade directly
  from the same delayed control action.
- Control reply and scheduler pass-through bridges were removed. Control
  toggles, buff queries, and respec callbacks now call the existing reply and
  scheduler runtimes directly with the same 500-700 ms delay behavior.
- Status reply and scheduler pass-through bridges were removed. AFK-return and
  offline-return callbacks now call the existing reply and scheduler runtimes
  directly with the same status side effects and delay windows.
- Session reply and scheduler pass-through bridges were removed. Relog,
  logout, and owner-away session callbacks now call the existing reply and
  scheduler runtimes directly with the same prompts, confirmations, and delays.
- Pending-action reply and scheduler pass-through bridges were removed. Item
  choices, cancel replies, and skill-tree reply queueing now call the existing
  reply and scheduler runtimes directly with the same pending-action behavior.
- Utility reply and scheduler pass-through bridges were removed. Trade-invite,
  sell-trash, crystal-making, and disassemble-trash utility callbacks now call
  the existing reply and scheduler runtimes directly with the same timings.
- Transfer reply and scheduler pass-through bridges were removed. Immediate
  transfer replies, fixed-delay callbacks, random-delay callbacks, and delay
  sampling now call the existing reply and scheduler runtimes directly.
- Supply reply and scheduler pass-through bridges were removed. Potion/ammo
  request replies and delayed supply callbacks now call the existing reply and
  scheduler runtimes directly.
- Social reply and scheduler pass-through bridges were removed. Fame replies
  and delayed social callbacks now call the existing reply and scheduler
  runtimes directly.
- The report operations bridge was folded into `AgentChatReportRuntime`.
  Report callback construction still dispatches to the same help, upgrade,
  gear, skill, stat, movement, range, inventory, potion, and debug report
  methods.
- The report delivery bridge was folded into `AgentChatReportRuntime`.
  Help, line, multi-line, and recommended-gear report delivery now use the
  existing reply and offer runtimes directly from the chat-report facade.
- Dialogue targeted chat routing now reuses the commands-package
  `AgentTargetedCommandMatch`, removing the duplicate dialogue-local match
  record without changing targeted reply, typo-suggestion, LLM fallback, or
  owner-command recording behavior.
- Agent integration chat, status, utility, transfer, supply, report, control,
  build, equipment, movement diagnostics, pending-action, social/report
  delivery, movement-command, and session lifecycle adapters now resolve live
  Agent/leader identity through `AgentRuntimeIdentityRuntime` instead of
  direct `BotEntry.bot()`, `BotEntry.owner()`, or `BotEntry.setOwner(...)`
  calls. `BotEntry` still hosts the Agent-owned identity state temporarily
  while broader `BotEntry` signature migration continues.
- The `BotEntry` runtime/session implementation moved to
  `server.agents.runtime.AgentRuntimeEntry`. `server.bots.BotEntry` is now a
  deprecated constructor-compatible shell only; remaining imports should move to
  `AgentRuntimeEntry` or narrower Agent runtime handles before `server.bots` is
  removed.
- Ammo and potion state runtime adapters now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. Existing `BotEntry` call
  sites remain source-compatible through the temporary shell while the supplies
  state boundary no longer imports `server.bots`.
- Build and consumable-buff state runtime adapters now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. AP/SP build state, job
  prompt state, consumable buff toggles, scan cadence, and last-action summaries
  remain backed by the same Agent-owned state objects.
- Activity, mode, tick, and tick-failure state runtime adapters now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. Follow/grind flags,
  leader activity metadata, heartbeat timing, and failure windows keep identical
  storage and behavior while dropping direct `server.bots` imports at this
  boundary.
- Movement target, farm anchor, patrol, and stuck-state runtime adapters now
  accept `server.agents.runtime.AgentRuntimeEntry` directly. Explicit move
  targets, sentry/farm anchors, patrol regions, and stuck recovery counters keep
  the same Agent-owned state backing.
- Reply-channel, message-queue, and pending-action state runtime adapters now
  accept `server.agents.runtime.AgentRuntimeEntry` directly. Whisper routing,
  queued reply delivery, pending confirmations, and pending drop categories keep
  identical state and behavior.
- Inventory cooldown and manual-trade state runtime adapters now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. Loot inhibit timers,
  inventory-full warning cooldowns, manual trade references, accept delays, and
  timeout state are unchanged.
- Death, map tracking, live leader, and formation state runtime adapters now
  accept `server.agents.runtime.AgentRuntimeEntry` directly. Respawn windows,
  foothold index tracking, live leader references, and follow offsets remain
  backed by the same Agent runtime state.
- Combat cooldown, combat skill cache, and combat support-buff state adapters
  now accept `server.agents.runtime.AgentRuntimeEntry` directly. Attack windows,
  mob-hit cooldowns, cached attack/support skills, and buff timing retain the
  same Agent-owned state.
- Grind target, grind search, grind wander, grind loot, retreat hold, breakout,
  and degenerate ranged-attack state adapters now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. Target references, search
  cadence, wander direction, loot retry suppression, retreat holds, breakout
  commitments, and degenerate-hit markers keep the same runtime state.
- Climb, swim, movement physics, movement broadcast, and mob-touch state
  adapters now accept `server.agents.runtime.AgentRuntimeEntry` directly. Rope
  state, swim input, physics integration fields, movement broadcast cache, and
  mob-touch sweep memory keep the same runtime state.
- Airshow, owner-motion, tick-cadence, scroll-reaction, and skill-buff debug
  state adapters now accept `server.agents.runtime.AgentRuntimeEntry` directly.
  Airshow trail timing, observed leader movement, tick accumulator/skip delay,
  social reaction cooldown/streak tracking, and skill-buff debug summaries keep
  the same runtime state.
- AoE reposition, shop transition, and script-task state adapters now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. AoE reposition anchors,
  shop visit/sequence transition state, and scripted task queues/runtime step
  state keep the same behavior and storage.
- Live Agent identity runtime now accepts `server.agents.runtime.AgentRuntimeEntry`
  directly. Bot/leader character lookup, ids, map lookup, and copied position
  snapshots keep the same behavior while removing another `server.bots`
  boundary import.
- Movement state facade and fidget state adapter now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. Movement snapshots,
  profile/input/down-jump state, fidget mode/timing/input state, and crouch
  checks keep the same runtime behavior.
- Offer and pending-trade state adapters now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. Gear prompt reservations,
  pending loot offers, proactive upgrade flags, trade sequence category/timers,
  item/meso batch state, restore slots, and queued retries keep the same
  behavior and storage.
- Script task completion checks now accept `server.agents.runtime.AgentRuntimeEntry`
  directly, and an unused `server.bots.BotEntry` import was removed from the
  transfer runtime. Script movement/follow completion behavior is unchanged.
- Navigation debug/path logging, movement state reset, follow-anchor resolution,
  navigation region classification, and combat action state reset now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. Path-log output,
  navigation edge/debug state, transient movement cleanup, follow-anchor
  selection, and combat action cleanup behavior are unchanged.
- Movement pose reset/sync helpers and the fidget clear helper now accept
  `server.agents.runtime.AgentRuntimeEntry` directly so navigation reset can
  clear transient movement state without re-entering the `server.bots` shell.
- Pure trade state services now accept `server.agents.runtime.AgentRuntimeEntry`
  directly for sequence initialization, batch setup, meso insertion, category
  announcement, and reset behavior. Trade state storage and trade-window side
  effects are unchanged.
- Movement profile refresh now accepts `server.agents.runtime.AgentRuntimeEntry`
  directly. Profile comparison, graph warmup, profile storage, and navigation
  state reset behavior are unchanged.
- AoE combat reposition service/runtime now accept
  `server.agents.runtime.AgentRuntimeEntry` directly. AoE reposition eligibility,
  target clustering, scoring, debug logging, and anchor commitment behavior are
  unchanged.
- `BotEntry` combat cooldown wrapper methods were removed. Attack cooldown,
  local move window, mob-hit cooldown, and alert timing behavior enters through
  `AgentCombatCooldownStateRuntime`.
- `BotEntry` grind wander and grind-loot wrapper methods were removed. Grind
  fallback and loot targeting behavior enters through Agent runtime adapters
  backed by `AgentGrindWanderState` and `AgentGrindLootState`.
- `BotEntry` move-target, farm-anchor, and patrol wrapper methods were
  removed. Movement mode behavior enters through Agent runtime adapters backed
  by `AgentMoveTargetState`, `AgentFarmAnchorState`, and `AgentPatrolState`.
- `BotEntry` death-window and portal-cooldown wrappers were removed. Death and
  navigation callers use Agent runtime adapters backed by `AgentDeathState` and
  `AgentPortalCooldownState`.
- `BotEntry` shop transition wrapper methods were removed after callers moved
  to `server.agents.capabilities.shop.AgentShopStateRuntime`. The temporary
  shell only hosts the Agent-owned `AgentShopState`.
- `BotEntry` pending loot-offer and trade-retry wrapper methods were removed.
  Offer and retry behavior enters through `AgentOfferStateRuntime` and
  `AgentPendingTradeStateRuntime`, backed by Agent-owned trade state.
- `BotEntry` message queue wrapper methods were removed after callers moved to
  `server.agents.runtime.AgentMessageQueueStateRuntime`. The queue
  remains owned by `server.agents.commands.AgentMessageQueueState`.
- `BotEntry` pending chat action wrapper methods were removed after callers
  moved to `server.agents.integration.AgentPendingActionStateRuntime`.
  `AgentPendingActionState` remains the Agent-owned mutable state object.
- `BotEntry` live character identity ownership moved to
  `server.agents.runtime.AgentRuntimeIdentityState`. The temporary shell keeps
  compatibility accessors while Agent identity adapters now read the
  Agent-owned state directly.
- `BotEntry` scheduled task handle ownership moved to
  `server.agents.runtime.AgentScheduledTaskState`. The temporary `BotEntry`
  shell delegates scheduled tick presence/cancellation to Agent runtime state,
  and scheduler/lifecycle adapters now use that Agent-owned state.
- Fixed-weapon DP optimizer entry points moved from direct
  `BotEquipManager.solveForWeapon` test/production usage to
  `AgentEquipmentOptimizer.solveForWeapon`. Branch score comparison,
  requirement-dimension scanning, branch snapshots, and weapon score breakdown
  calls in `BotEquipManager` now delegate to `AgentEquipmentOptimizer`; the
  stale private bot-side DP helper copy was deleted. The production
  `BotEquipManager` file has since been removed.
- Equipment plan application moved from `BotEquipManager` to
  `AgentEquipmentPlanExecutor`, including the live equip move loop and
  post-plan infeasible-equipment sweep.
- `BotEquipManager.runOptimizerWithExtras` behavior moved to
  `AgentEquipmentOptimizationService`; Agent recommendation code now calls the
  Agent optimizer orchestration directly, and the bot methods remain only as
  compatibility delegates.
- Dead `BotEquipManager` recommendation wrapper methods were deleted after
  callers moved to Agent recommendation APIs.
- Auto-equip execution and debug branch reporting moved to
  `AgentEquipmentAutoEquipService`. Production `AgentEquipmentService` calls
  the Agent implementation directly.
- Production callers of `server.bots.BotEquipManager` now call
  `server.agents.capabilities.equipment.AgentEquipmentService`; the old
  production bot class was deleted after focused equipment tests passed.
- `AgentEquipmentService.isMageJob` and `isWeaponCompatible` now call
  `AgentWeaponCompatibilityPolicy` directly, removing those production
  compatibility reads from the legacy bot optimizer surface.
- Equipment slot alias resolution moved from `BotEquipManager.slotsFromName`
  to `AgentEquipmentSlotResolver`; the bot shell has since been deleted.
- Useful-stat scoring and expected damage after monster defense moved from
  `BotEquipManager` to `AgentEquipmentScoringPolicy`.
- Auto-equip throttle state moved from `BotEquipManager` to
  `AgentAutoEquipThrottle`.
- Owned and incoming equipment reserve service entry points now call
  `AgentEquipmentReservePolicy` directly through `AgentEquipmentService`,
  removing another production compatibility hop through `BotEquipManager`.
- Equipment recommendation immediate/future candidate eligibility moved to
  `AgentEquipmentRecommendationPolicy`.
- Equipment unequip command execution moved to `AgentEquipmentUnequipService`;
  `AgentEquipmentService` now handles unequip requests without entering the
  temporary `BotEquipManager` shell.
- Equipment recommendation filtering, recommendation result construction,
  single-item recommendation checks, recommended-item collection, and
  recommendation summary formatting moved to `AgentEquipmentRecommendationService`.
  Its optimizer orchestration now enters Agent-owned equipment services.
- Auto-equip debug dump header/item-row/map-id formatting moved to
  `AgentEquipmentDebugReportFormatter`; auto-equip/debug execution now lives in
  `AgentEquipmentAutoEquipService`.
- The equipment optimizer external result type moved to
  `AgentEquipmentOptimizerResult` and is used by Agent-owned optimization
  services.
- `server.bots.BotInventoryManager` inventory/trade tick entry points moved to
  `server.agents.capabilities.inventory.AgentInventoryTickRuntime`.
  Production common ticks and legacy parity tests now call Agent inventory
  runtime directly; the old bot class has been deleted.
- BotManager runtime/lifecycle/tick milestone audit: production source no
  longer imports `server.bots.BotManager` or calls `BotManager.getInstance()`.
  The production `BotManager` compatibility shell has been deleted after
  focused runtime/config/supply/combat parity tests passed.
- Movement profile physics baseline constants moved to
  `server.agents.capabilities.movement.AgentMovementPhysicsConfig`.
  `AgentMovementProfile` now reads Agent-owned walk, horizontal-force, jump,
  and rope-jump baselines directly; the remaining bot movement/physics files
  read the same constants while their runtime bodies are reconstructed.
- Rope-grab, snap-drop, and slope-up threshold constants also moved to
  `AgentMovementPhysicsConfig`. Agent fallback movement and navigation graph
  generation now consume those Agent-owned thresholds directly.
- Movement tick duration reads moved to `AgentMovementPhysicsConfig` for Agent
  runtime registration/preflight/movement-only/stuck-detection, combat
  knockback/reporting, fidget delay, and navigation edge timing call sites.
- Movement command distance/config reads moved to `AgentMovementPhysicsConfig`
  for Agent runtime, fallback movement, fidget, target snapshot, and navigation
  graph call sites. `BotMovementManager` compatibility accessors now delegate
  stop/follow/teleport/jump-threshold/grind-margin/follow-Y-cap values to the
  Agent-owned source.
- Navigation-facing movement config reads moved to `AgentMovementPhysicsConfig`.
  `BotNavigationManager` now reads jump-Y, stop-distance, rope-grab,
  grind-margin, snap-drop, and climb-speed values from the Agent-owned source
  instead of `BotMovementManager.cfg` / `BotPhysicsEngine.cfg`.
- External climb-step calculation reads moved to
  `AgentMovementKinematicsService.climbStepPerTick()`. Agent navigation,
  climb movement, movement report snapshots, and remaining bot compatibility
  code use the Agent-owned calculation; the physics engine retains only its
  own internal use until the simulation body is migrated.
- External swim steering threshold reads moved to `AgentMovementPhysicsConfig`.
  Agent swim and airborne movement no longer depend on `BotPhysicsEngine`
  compatibility config accessors for swim arrival radius, swim jump cooldown,
  swim vertical bands, swim jump trigger, or rope-grab tolerance.
- External jump/rope kinematics range reads moved to
  `AgentMovementKinematicsService`. Agent navigation graph generation and
  movement report snapshots no longer read those calculations directly from
  `BotPhysicsEngine`; the Agent service remains a temporary delegation seam
  until the physics simulation body is migrated.
- Remaining external walk-step calculation reads moved to
  `AgentMovementKinematicsService.walkStep(...)`. Fidget, fallback movement,
  path logging, and the temporary navigation shim now use the Agent movement
  kinematics seam instead of calling `BotPhysicsEngine.walkStep(...)`
  directly.
- Pose and stance side-effect entry points moved to
  `AgentMovementPoseService`. Agent runtime/capability callers now use the
  Agent movement seam for idle, prone, reset, teleport, dead, stance resolve,
  standing-check, and character-state sync operations; the seam remains a
  compatibility delegate until the physics body moves.
- Ground lookup entry points moved to `AgentGroundingService`. Agent runtime,
  combat, movement, fidget, broadcast, and navigation graph callers no longer
  call `BotPhysicsEngine.findGroundPoint` or `findGroundFoothold` directly.
- Movement countdown helper ownership moved to `AgentMovementTimers`; Agent
  combat, common tick, inventory/trade, shop, supplies, and stuck-detection
  callback bundles no longer depend on `BotMovementManager` for tick-down or
  delay-after-current-tick helpers.
- Packet-visible movement broadcast logic moved to
  `AgentMovementBroadcastService`; Agent callers no longer depend on
  `BotMovementManager.broadcastMovement`, while the bot method remains a
  temporary compatibility delegate.
- Movement reset and transient-state cleanup moved to
  `AgentMovementStateResetService`; Agent callers no longer depend on
  `BotMovementManager.resetEntryState`, `resetEntryStateAfterTeleport`, or
  `clearNavigationState`.
- Foothold-index construction moved to `AgentFootholdIndexService`; Agent
  spawn-placement and map-change runtimes no longer depend on
  `BotMovementManager.buildFhIndex`.
- Walk-step kinematics moved to `AgentMovementKinematicsService`; Agent
  navigation graph/probe and movement report runtime callers no longer depend
  on `BotMovementManager.walkStep`, and the old bot compatibility delegates
  have been removed.
- Dead `BotMovementManager` movement-timer and jump-probe compatibility
  wrappers were removed after their callers moved to Agent-owned services.
- `BotMovementManager` profile-refresh, movement-state reset, navigation-clear,
  and foothold-index wrappers were removed after test and harness callers moved
  to the Agent movement services directly.
- `BotMovementManager` movement-policy wrappers for grind target adjustment,
  climb hold/snap, precise navigation stop distance, and ground step
  calculation were removed after tests moved to the Agent movement services.
- `BotMovementManager` movement execution wrappers were removed after tests
  moved to Agent movement services. The remaining class is a temporary
  config-binding shell for `BotPhysicsEngine`.
- `BotMovementManager` has been deleted. The final config-shell assertion now
  verifies `BotPhysicsEngine` against `AgentMovementPhysicsConfig` values.
- `BotPhysicsEngine` pose and packet snapshot callers in tests/harnesses now
  enter through `AgentMovementPoseService` and
  `AgentMovementSnapshotService`; the legacy physics body remains the
  implementation seam for later migration.
- `BotPhysicsEngine` kinematics/report test callers now enter through
  `AgentMovementKinematicsService`; the legacy physics body remains the
  implementation seam until the calculation internals are moved.
- `BotPhysicsEngine` ground lookup implementation for `findGroundFoothold`
  and `findGroundPoint` moved to `AgentGroundingService`; bot methods remain
  temporary delegates for internal physics callers.
- Navigation graph fall, down-jump, and rope-jump landing callers now use the
  Agent-owned `AgentJumpLanding` DTO through `AgentJumpProbeService`. The
  temporary bot physics simulator still supplies the raw landing data until the
  collision/integration body moves.
- Directional-drop waypoint validation now uses the Agent-owned
  `AgentWalkOffLanding` DTO through `AgentJumpProbeService`; the underlying
  walk-off simulation remains temporarily bot-backed.
- Navigation graph physics probes for fall/rope-grab estimates, ground-jump
  rope grabs, down-jump rope grabs, rope-jump grabs, rope-jump landing costs,
  and runway wall checks now enter through Agent movement seams. The graph
  builder no longer imports `BotPhysicsEngine` directly.
- Navigation build walk-region lookup storage and foothold-id caching now live
  in `AgentNavigationWalkRegionLookupService`. The remaining bot physics
  ground-step preview code resolves through the Agent service while that
  preview body is migrated later.
- Runtime hook bundles no longer import `BotPhysicsEngine` for ground lookup,
  teleport, idle-ground pose, or max-jump-height callbacks. They now use
  `AgentGroundingService`, `AgentMovementPoseService`, and
  `AgentMovementKinematicsService` directly.
- Fidget movement no longer imports `BotPhysicsEngine`; it already routes
  jump, pose, ground-collision, ground-motion, and broadcast work through
  Agent movement services.
- Combat knockback launch state mutation now lives in
  `AgentKnockbackMovementService`; `BotPhysicsEngine.beginKnockback` is a
  compatibility delegate.
- Shared airborne launch state setup now lives in
  `AgentAirborneLaunchService`. Combat knockback and down-jump launch use the
  same Agent-owned velocity/airborne-state setup while preserving the legacy
  movement velocity conversion.
- `BotPhysicsEngine` simple down-jump eligibility and far-ground detection
  moved to `AgentGroundCollisionService`; ground-step preview and wall
  collision remain later slices.
- Ground-step collision queries, wall-blocked step detection, and ground-runway
  wall checks moved to `AgentGroundCollisionService`. The Agent service now owns
  the public ground collision query behavior used by navigation and movement;
  `BotPhysicsEngine` keeps compatibility delegates and its private integrator
  preview until the full ground physics body migrates.
- Ground physics entry points moved to `AgentGroundPhysicsService`, including
  ground-position synchronization, no-ground fall launch, horizontal ground
  force/friction stepping, blocked-step handling, walk-off fall launch, and
  packet-visible ground movement velocity. `BotPhysicsEngine.syncAndDetectGround`
  and `applyGroundMotion` are compatibility delegates.
- Jump/rope/fall probe simulation ownership moved to `AgentJumpProbeService`.
  Agent movement now owns jump landing, rope-jump landing, fall landing,
  down-jump landing, walk-off landing, rope-grab simulation, and probe time
  estimates. `BotPhysicsEngine` keeps compatibility DTO adapters for legacy
  callers and tests.
- Down-jump launch ownership moved to `AgentQueuedMovementActionService`.
  `BotPhysicsEngine.beginDownJump` is now a compatibility delegate while the
  Agent service applies the same down-jump kick and grace-period values from
  `AgentMovementPhysicsConfig`.
- Ground jump, climb-up jump, jump-off-rope, and rope-transfer jump launch
  ownership moved to `AgentRopeMovementService`. The swim-map ground-jump
  special case preserves its legacy swim impulse, cooldown, and packet velocity
  behavior; the `BotPhysicsEngine` entry points are compatibility delegates.
- Rope/ladder climb hold, advance, top landing, and bottom fall boundary
  handling moved to `AgentRopeMovementService`. The Agent service now owns the
  packet-visible rope position, zero-velocity hold, top foothold snap, and
  fall launch behavior; the `BotPhysicsEngine` methods are compatibility
  delegates.
- Airborne runtime stepping moved to `AgentAirbornePhysicsService`. The Agent
  service now owns air steering, airborne integration, wall/ceiling collision
  responses, landing state transition, fall-damage handoff, and packet-visible
  airborne velocity updates. `BotPhysicsEngine.stepAirborne` is now a
  temporary compatibility delegate that converts the Agent result enum back to
  the legacy result type.
- Swim runtime stepping moved to `AgentSwimPhysicsService`. The Agent service
  now owns swim integrator rebasing, swim-jump impulse, horizontal/vertical
  swim intent, drag/gravity/thrust, wall/floor sweep collision, grounded
  landing handoff, and packet-visible swim velocity updates.
  `BotPhysicsEngine.applySwimMotion` is now a temporary compatibility
  delegate.
- Dead private air/swim helper bodies were removed from `BotPhysicsEngine`
  after airborne and swim runtime stepping moved into Agent movement services.
- The corner fall-through regression test moved from
  `src/test/java/server/bots/BotCornerFallThroughTest` to
  `server.agents.capabilities.movement.AgentCornerFallThroughTest`. It now
  verifies the same map-edge knockback behavior through Agent knockback,
  airborne, timer, pose, and grounding services instead of the
  `BotPhysicsEngine` compatibility API.
- Remaining movement-manager and simulation-lab test/harness references for
  ground-step checks, movement tick duration, and rope attachment now use
  `AgentGroundCollisionService`, `AgentMovementPhysicsConfig`, and
  `AgentRopeMovementService` directly instead of `BotPhysicsEngine`.
- Navigation-manager rope hold regression coverage now uses
  `AgentRopeMovementService` and `AgentNavigationPhysicsService` directly for
  rope attachment and first-climbable-Y lookup instead of `BotPhysicsEngine`.
- Navigation graph regression coverage now uses `AgentJumpProbeService`,
  `AgentJumpLanding`, `AgentPostLandingJump`, and
  `AgentGroundCollisionService` directly for jump landing, post-landing, and
  ground-step probes instead of the `BotPhysicsEngine` compatibility DTO/API.
- `BotPhysicsEngine.tickMotionTimers` countdown implementation moved to
  `AgentMotionTimerService`; the bot method remains a temporary delegate.
- `BotPhysicsEngine` stance resolution, stance sync, and packet movement
  snapshot construction moved to `AgentMovementPoseService` and
  `AgentMovementSnapshotService`; bot methods remain temporary delegates.
- Remaining `BotPhysicsEngine` compatibility callers in the physics regression
  suite now enter Agent movement/navigation services directly, including jump
  probes, rope reach/grab simulation, down-jump launch/probes, rope/climb
  motion, ground motion, ground collision, walk-region lookup, airborne step
  results, and navigation walk-connectivity checks. `BotPhysicsEngine` has no
  remaining production or test callers and the production bot file has been
  deleted. The remaining production `server.bots` file is `BotEntry`.
- Reply message queue state moved into `AgentMessageQueueState`. `BotEntry`
  still hosts the state object temporarily, but queue ownership, sending-state
  mutation, snapshots, and idle checks now live in `server.agents.commands`.
- Reply-channel state moved into `AgentReplyChannelState`, and the direct
  `BotEntry` reply-channel wrappers were removed. Reply routing now enters
  through `server.agents.runtime.AgentReplyChannelStateRuntime` while the
  temporary shell only hosts the Agent-owned state object.
- Scroll-reaction load/cooldown/streak state moved into
  `AgentScrollReactionState`. `BotEntry` still hosts the state object
  temporarily, while the social reaction adapter uses Agent-owned streak data.
- Script task queue, active task, and activity epoch state moved into
  `AgentScriptTaskQueueState`. `BotEntry` still hosts the state object
  temporarily, while script-task adapters use the Agent plan state directly.
- Map id and foothold-index tracking moved into `AgentMapTrackingState`.
  `BotEntry` still hosts the state object temporarily, while map-transition
  adapters and legacy movement harnesses use the Agent runtime state boundary.
- Leader activity state moved into `AgentLeaderActivityState`; AFK position,
  AFK timing, was-AFK flags, offline/dead recovery timing, returned-to-town
  safe-mode flags, and last matched leader command metadata now live in Agent
  runtime state. `BotEntry` still hosts the state object temporarily, while
  activity adapters delegate through the Agent state boundary.
- Airshow session state moved into `AgentAirshowState`; active/inactive state
  and trail timestamp storage now live in the Agent social airshow capability.
  `BotEntry` still hosts the state object temporarily, while airshow adapters
  delegate through the Agent state boundary.
- Formation offset state moved into `AgentFormationOffsetState`; per-Agent
  follow offset storage now lives in Agent runtime state. `BotEntry` still
  hosts the state object temporarily, while formation adapters delegate through
  the Agent state boundary.
- Pending chat action state moved into `AgentPendingActionState`; pending
  action and pending drop-category storage now live in the Agent dialogue
  capability. `BotEntry` still hosts the state object temporarily, while
  pending-action adapters delegate through the Agent state boundary.
- Pending loot-offer state moved into `AgentPendingLootOfferState`; offered
  item, recipient id, expiry timestamp, and bot-requesting flag storage now
  live in the Agent trade capability. `BotEntry` still hosts the state object
  temporarily, while offer adapters delegate through the Agent state boundary.
- Inventory cooldown state moved into `AgentInventoryCooldownState`; loot
  inhibit and inventory-full warning cooldown storage now live in the Agent
  inventory capability. `BotEntry` still hosts the state object temporarily,
  while inventory adapters delegate through the Agent state boundary.
- Potion supply state moved into `AgentPotionSupplyState`; potion-check timer,
  MP recovery timer, and HP/MP share-request flag storage now live in the Agent
  supplies capability. `BotEntry` still hosts the state object temporarily,
  while potion adapters delegate through the Agent state boundary.
- Ammo supply state moved into `AgentAmmoSupplyState`; ammo share-request,
  no-ammo, and ammo-warning flag storage now live in the Agent supplies
  capability. `BotEntry` still hosts the state object temporarily, while ammo
  adapters delegate through the Agent state boundary.
- Build prompt/progression state moved into `AgentBuildState`; AP build
  selection, AP prompt flag, SP variant/prompt flag, job-prompt milestone, and
  last-known-level storage now live in the Agent build capability. `BotEntry`
  still hosts the state object temporarily, while build adapters delegate
  through the Agent state boundary.
- Queued trade-retry state moved into `AgentTradeRetryState`; bot-initiated
  trade retry callback and retry-delay storage now live in the Agent trade
  capability. `BotEntry` still hosts the state object temporarily, while trade
  retry adapters delegate through the Agent state boundary.
- Portal cooldown state moved into `AgentPortalCooldownState`; portal-use
  cooldown deadline storage and cooldown checks now live in the Agent
  navigation capability. `BotEntry` still hosts the state object temporarily,
  while navigation adapters delegate through the Agent state boundary.
- Combat cooldown state moved into `AgentCombatCooldownState`; attack
  cooldown, movement-only attack window, touch-damage cooldown, alert pose
  expiry, and alert reset scheduling now live in the Agent combat capability.
  `BotEntry` still hosts the state object temporarily, while combat adapters
  delegate through the Agent state boundary.
- Mob-touch sweep state moved into `AgentMobTouchState`; last checked position
  and map id storage now live in the Agent combat capability. `BotEntry` still
  hosts the state object temporarily, while mob-touch adapters delegate through
  the Agent state boundary.
- Death/respawn window state moved into `AgentDeathState`; death deadline,
  dead-state entry, and respawn-due checks now live in the Agent runtime
  module. `BotEntry` still hosts the state object temporarily, while death tick
  adapters delegate through the Agent state boundary.
- Shop visit lifecycle state moved into `AgentShopState`; pending visit flag,
  NPC/target positions, approach delay, sequence timestamps, sell-trash flag,
  and stuck-near-NPC fallback tracking now live in the Agent shop capability.
  `BotEntry` still hosts the state object temporarily, while shop adapters and
  tests delegate through the Agent state boundary.
- Buff automation/debug state moved into `AgentBuffState`; consumable-buff
  enablement, cheap/max mode, scan/action timing, consumable action summary,
  and skill-buff debug action summary now live in the Agent combat capability.
  `BotEntry` still hosts the state object temporarily, while buff adapters
  delegate through the Agent state boundary.
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
  `AgentMovementCommandRuntime` directly.
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
  and `AgentScriptMoveTargetRuntime` to
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
  `server.agents.capabilities.movement.AgentFormationRuntime`. BotManager now delegates
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
  `server.agents.capabilities.follow.AgentFollowTargetCandidateService`. BotManager now
  supplies only a temporary sibling-entry lookup hook while Agent runtime owns
  the leader/party/sibling ordering and duplicate filtering.
- Follow-target command application moved from BotManager to
  `server.agents.capabilities.follow.AgentFollowTargetCommandService`. BotManager now
  supplies temporary hooks for target resolution, reply queueing, delay
  scheduling, auto-equip, potion-share checks, and follow-mode entry while
  Agent runtime owns the per-entry command application order.
- Follow-target name resolution moved from BotManager to
  `server.agents.capabilities.follow.AgentFollowTargetResolutionService`. BotManager now
  supplies temporary candidate-list assembly and follow-mode application while
  Agent runtime owns the exact/prefix/ambiguous/missing-target rules.
- Transfer chat command routing moved from BotManager to
  `server.agents.capabilities.trade.AgentTransferCommandService`. BotManager now supplies
  temporary hooks for transfer lifecycle delegation and leader yellow-message
  delivery while Agent runtime owns the command routing response behavior.
- Recruit chat command parsing moved from BotManager to
  `server.agents.commands.AgentRecruitCommandService`. BotManager now supplies
  temporary hooks for ownerless-Agent recruitment and leader yellow-message
  delivery while the Agent runtime owns the legacy aliases and response text.
- Dismiss chat command parsing moved from BotManager to
  `server.agents.commands.AgentDismissCommandService`. BotManager now supplies
  temporary hooks for dismiss lifecycle delegation and leader yellow-message
  delivery while the Agent runtime owns the legacy aliases and response text.
- Formation chat command parsing and formation-state mutation moved from
  BotManager to `server.agents.capabilities.movement.AgentFormationCommandService`.
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
  `server.agents.capabilities.trade.AgentTransferService`. BotManager now supplies
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
  `server.agents.capabilities.movement.AgentMovementOnlyMapChangeService`. BotManager now
  supplies temporary foothold, grounding, teleport, reset, broadcast, shop, and
  status hooks.
- Grind-mode dispatch moved from BotManager to
  `server.agents.capabilities.combat.AgentGrindModeDispatchService`. BotManager now supplies
  only the temporary grind tick hook and performance timing; the grind decision
  pipeline itself remains in `AgentGrindModeTickService`.
- Final movement-tail dispatch moved from BotManager to
  `server.agents.capabilities.movement.AgentFinalMovementTailService`. BotManager now
  supplies only the temporary movement-core hook and performance timing.
- Tracked map-change tick dispatch moved from BotManager to
  `server.agents.runtime.AgentTrackedMapChangeTickService`. BotManager now
  supplies the temporary tracked-map-change handler and performance timing.
- Follow map-sync and teleport recovery dispatch moved from BotManager to
  `server.agents.capabilities.recovery.AgentRecoveryTickService`. BotManager now supplies
  temporary hooks for follow map sync, grind-party recovery teleport, and
  target-distance recovery teleport.
- Anchored-farm mode dispatch moved from BotManager to
  `server.agents.capabilities.combat.AgentAnchoredFarmModeTickService`. BotManager now
  supplies only the temporary anchored-farm tick hook and performance timing.
- Scripted move local-combat tick dispatch moved from BotManager to
  `server.agents.capabilities.combat.AgentScriptedMoveCombatTickService`. BotManager now
  supplies temporary hooks for action-window cleanup, local-opportunity attack,
  movement-core stepping, and performance timing.
- Follow-mode local opportunity attack dispatch moved from BotManager to
  `server.agents.capabilities.follow.AgentFollowOpportunityTickService`. BotManager now
  supplies the temporary local-opportunity attack hook and performance timing.
- Idle-mode consumed-tick dispatch moved from BotManager to
  `server.agents.runtime.AgentIdleModeTickService`. BotManager now supplies the
  temporary idle physics/mode hook and performance timing.
- Shop-visit tick dispatch moved from BotManager to
  `server.agents.capabilities.shop.AgentShopVisitTickService`. BotManager now supplies
  temporary hooks for the existing shop visit tick body and movement-core
  stepping while Agent runtime owns the pending/delay/target consumed-tick flow.
- Trade-window tick dispatch moved from BotManager to
  `server.agents.capabilities.trade.AgentTradeWindowTickService`. BotManager now supplies
  only the temporary physics-only tick hook while the Agent runtime owns the
  trade-open consumed-tick decision.
- Tick preflight sequencing moved from BotManager to
  `server.agents.runtime.AgentTickPreflightService`. BotManager now supplies
  temporary hooks for airshow skip, skip-delay consumption, removed-map cleanup,
  heartbeat, pending-offer expiry, and AI cadence preparation.
- Inactive leader tick gating moved from BotManager to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService.handleInactiveLeaderTick`.
  BotManager now supplies temporary hooks for active-leader return cleanup,
  town-warp eligibility, and inactive safe-mode entry side effects.
- Standalone move-target tick sequencing moved from BotManager to
  `server.agents.capabilities.movement.AgentStandaloneMoveTargetTickService`. BotManager now
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
  `server.agents.capabilities.combat.AgentMonsterControlService`; common tick now delegates
  to the Agent-owned service.
- Death respawn recovery moved from BotManager to
  `server.agents.capabilities.combat.AgentDeathTickService.respawnNearLeader`. BotManager
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
  `AgentSessionLifecycleSideEffects` remain temporary compatibility
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
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`. BotManager now references
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
  `server.agents.runtime.AgentRuntimeRegistry`. `AgentEquipHandler` no longer
  imports BotManager for those lookups, while BotManager keeps compatibility
  accessors for older callers.
- BotManager bot-only autopot cleanup moved to
  `server.agents.capabilities.supplies.AgentAutopotCleanupService`. BotManager
  `cleanupBotRuntimeState` remains the temporary lifecycle wrapper that removes
  the entry and then calls the Agent-owned cleanup helper.
- BotManager spawn-position resolution moved to
  `server.agents.runtime.AgentSpawnPositionService`. BotManager keeps
  `resolveSpawnPosition` as a compatibility delegate while offline load,
  online placement, and spawn normalization continue to use the same
  BotPhysicsEngine ground lookup through the Agent runtime service.
- BotManager post-spawn party lifecycle side effects moved to
  `server.agents.capabilities.party.AgentPartyLifecycleService`. BotManager keeps
  `joinBotToOwnerParty` as a temporary compatibility delegate, and Agent spawn
  command plus Messenger respawn paths now use the Agent runtime service
  directly.
- BotManager inactive-leader town-warp eligibility moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager still owns the
  temporary offline/dead leader side effects, return-scroll execution, and town
  cluster target wiring.
- BotManager inactive-leader idle preparation sequence moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager only supplies
  temporary callbacks for script-task clearing, shop cancellation, and mode
  clearing.
- BotManager active-leader return cleanup and single-representative welcome-back
  rule moved to `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager
  only supplies temporary callbacks for move-target clearing, town-cluster anchor
  removal, and party-visible return announcement.
- BotManager inactive-leader timer gate moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager still performs
  the temporary safe-mode entry side effects once the Agent-owned gate says the
  delay has elapsed.
- BotManager non-town inactive safe-mode idle sequence moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager only supplies
  temporary physics and movement-broadcast callbacks.
- BotManager inactive-town cluster target calculation moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager only supplies
  temporary leader-entry snapshots, formation state, edge-inset config, and
  ground-point lookup.
- BotManager inactive-town return completion state sequencing moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager only supplies
  temporary movement reset and precise move-start callbacks.
- BotManager inactive safe-mode entry branching moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager only supplies
  temporary prepare, town-scroll, and in-place idle callbacks.
- BotManager inactive town-scroll orchestration moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager only supplies
  temporary Cosmic callbacks for current map, return-scroll use, map changing,
  map-change grounding, town-cluster anchor storage, target resolution, movement
  reset, and precise move start.
- BotManager first-entry/representative lookup rule moved to
  `server.agents.runtime.AgentRuntimeRegistry`; BotManager only supplies the
  temporary runtime map.
- BotManager leader away-safe-mode entry loop moved to
  `server.agents.capabilities.recovery.AgentLeaderSafetyService`; BotManager only supplies
  temporary entry snapshots, map-presence checks, town eligibility, and
  safe-mode entry callbacks.
- BotManager script task completion rules moved to
  `server.agents.plans.AgentScriptTaskCompletionService`; BotManager only
  supplies temporary follow-target resolution and movement distance config.
- BotManager script task start dispatch moved to
  `server.agents.plans.AgentScriptTaskStartService`; BotManager only supplies
  temporary callbacks for move, follow, grind, stop, and drop side effects.
- BotManager script task tick loop moved to
  `server.agents.plans.AgentScriptTaskTickService`; BotManager only supplies
  temporary callbacks for task start and completion checks.
- BotManager script task queue helpers moved to
  `server.agents.plans.AgentScriptTaskQueueService`; BotManager remains a
  temporary compatibility facade for clear, queue, move, move-then-drop,
  follow-then-drop, and has-queued checks.
- Agent script context and runner task queue operations now call
  `server.agents.plans.AgentScriptTaskQueueService` directly instead of
  routing queue/clear checks through BotManager; BotManager remains only for the
  temporary cheap-move helper used by script context.
- Agent script context no longer stores `BotManager`; it receives a narrow
  cheap-move callback and drop-item callback from `AgentScriptRunner`, reducing
  the script subsystem's direct BotManager dependency while preserving the same
  cheap-move and drop side-effect results.
- BotManager local near-distance helper moved to
  `server.agents.capabilities.movement.AgentPositionService`; BotManager movement/combat tick
  paths now use the Agent-owned geometry helper.
- BotManager move/farm/patrol/follow/grind/stop command-mode preparation moved to
  `server.agents.commands.AgentCommandModeService`; BotManager only supplies
  temporary guards and callbacks for script-task clearing, shop cancellation,
  and mode start side effects.
- Agent movement command facade now routes follow-owner, stop, move-to,
  farm-here, grind, and patrol through Agent runtime mode/queue services
  directly, including patrol graph-region validation and visible failure
  replies.
- Build level-up, potion stop, and combat ammo-stop paths now request
  follow-owner through `AgentMovementCommandRuntime` instead of calling
  `BotManager.issueFollowOwner` directly.
- Session relog/logout/away prompts and equipment unequip-all now request stop
  through `AgentMovementCommandRuntime` instead of calling
  `BotManager.issueStop` directly.
- Patrol command graph-region validation, visible failure reply, and active
  patrol mode transition moved into `AgentMovementCommandRuntime`; the
  former `BotManager.issuePatrol` compatibility delegate has been removed.
- Session first-agent checks, away-town offer checks, and away-safe command
  routing moved behind `AgentSessionControlRuntime`; BotManager remains only
  the temporary side-effect bridge for away-safe state changes.
- Support-heal jump-anchor resolution now uses `AgentFollowAnchorService` plus
  `AgentSessionLifecycleSideEffects.getBotEntries`, removing the direct
  `BotManager.resolveFollowAnchor` call from Agent combat heal runtime.
- Combat grind region-occupancy scoring now reads sibling Agents through
  `AgentSessionLifecycleSideEffects.getBotEntries`, removing another direct
  `BotManager.getBotEntries` call from Agent combat target runtime.
- Ammo-share, potion-share, and sibling gear offer scans now read sibling
  Agents through `AgentSessionLifecycleSideEffects.getBotEntries`, removing
  direct BotManager entry-list calls from those capability donor scans.
- Bot-inventory-drop loot delay detection now reads active Agent ownership via
  `AgentSessionLifecycleSideEffects.activeLeaderByAgentCharacterId`, removing
  the direct BotManager lookup from `AgentLootEligibility`.
- Airshow named-Agent lookup now reads through
  `AgentSessionLifecycleSideEffects.getBotEntry`, removing the direct
  BotManager lookup from `AgentAirshowService`.
- Manual trade greeting selection moved to `AgentTradeDialogueService` backed
  by `AgentDialogueCatalog`; inventory runtime adapters no longer call
  `BotManager.getInstance().manualTradeGreeting`.
- Navigation debug overlay bot selection now reads Agent entries through
  `AgentSessionLifecycleSideEffects`, removing direct BotManager lookup from
  `AgentNavigationDebugOverlay`.
- BotNavigationManager follow-anchor region resolution now reads sibling Agent
  entries through `AgentSessionLifecycleSideEffects` and resolves anchors
  through `server.agents.capabilities.follow.AgentFollowAnchorService`, removing its direct
  `BotManager.resolveFollowAnchor` call while preserving follow target
  behavior.
- BotManager follow-owner, grind, and stop entry points were removed. Callers
  now use `AgentMovementCommandRuntime` directly while preserving command
  setup behavior.
- BotManager inactive-town return-scroll item use moved to
  `server.agents.capabilities.supplies.AgentReturnScrollService`; BotManager remains only the
  leader-safety callback site for this action.
- BotManager swim-map helper moved to
  `server.agents.capabilities.movement.AgentMapEnvironmentService`; BotManager no longer owns
  that map-environment predicate for movement/tick physics routing.
- BotManager grind-loot retry suppression predicate was removed; Agent loot
  targeting now consumes `AgentGrindLootStateRuntime::isRetrySuppressed`
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
  `server.agents.capabilities.movement.AgentFormationService`; BotManager still contains the
  temporary command parsing and snapshot wiring around that Agent-owned store.
- BotManager follow-target position resolver moved to
  `server.agents.capabilities.follow.AgentFollowTargetPositionService`; the Agent movement
  target gateway now captures snapshots without calling BotManager.
- BotManager live entry map moved to
  `server.agents.runtime.AgentRuntimeRegistry`; BotManager keeps a compatibility
  alias while Agent session lookup bridges read the Agent-owned registry.
- BotManager scripted follow-target resolution moved to
  `server.agents.capabilities.follow.AgentFollowAnchorService`; BotManager only supplies
  the temporary sibling entry list.
- BotManager dead-state tick handling moved to
  `server.agents.capabilities.combat.AgentDeathTickService`; BotManager only supplies
  temporary combat death-entry and respawn callbacks.
- BotManager attack-lock physics dispatch moved to
  `server.agents.capabilities.movement.AgentActionLockPhysicsService`; BotManager only
  supplies temporary swim-map and movement physics callbacks.
- BotManager map-change grounding moved to
  `server.agents.capabilities.movement.AgentMapTransitionService`; BotManager only supplies
  temporary foothold/physics/navigation callbacks.
- BotManager idle/trade physics mode selection moved to
  `server.agents.capabilities.movement.AgentIdlePhysicsService`; BotManager only supplies
  temporary movement/physics callbacks.
- BotManager tick heartbeat ownership moved to
  `server.agents.runtime.AgentHeartbeatService`; BotManager only supplies
  temporary packet freshness and movement broadcast callbacks.
- BotManager mode transition state ownership moved to
  `server.agents.runtime.AgentModeService`; BotManager remains the temporary
  command/script side-effect wrapper for follow, grind, stop, move-to,
  farm-here, and patrol entry points.
- BotManager target snapshot composition moved further into
  `server.agents.capabilities.movement.AgentTargetSnapshotService`; BotManager now only wires
  temporary sibling/formation storage and the follow-target resolver callback.
- BotManager formation state lookup moved to
  `server.agents.capabilities.movement.AgentFormationService`; BotManager still stores the
  temporary per-leader formation map for command compatibility.
- BotManager tick leader/session refresh moved to
  `server.agents.runtime.AgentLeaderSessionService`; BotManager remains a
  compatibility wrapper that supplies the current Cosmic player-storage lookup.
- BotManager follow-anchor resolution moved to
  `server.agents.capabilities.follow.AgentFollowAnchorService`; BotManager remains a
  compatibility wrapper that supplies the temporary sibling list until runtime
  registry ownership is fully moved.
- BotManager target snapshot assembly moved to
  `server.agents.capabilities.movement.AgentTargetSnapshotService`; BotManager still supplies
  temporary follow-anchor and follow-target-position callbacks for this slice.
- BotManager target snapshot record moved to
  `server.agents.capabilities.movement.AgentTargetSnapshot`; movement/navigation callers and
  tests consume the Agent-owned record.
- BotManager formation type/state and offset calculation moved to
  `server.agents.capabilities.movement.AgentFormationService`; command parsing and the
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
  `AgentInventoryRuntimeAdapters`; `BotInventoryManager` is now a thin
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
  sit on the report path. `AgentCombatReportRuntime` now calls
  `AgentCombatTargetRuntime` and `AgentCombatPlanRuntime` directly, while
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
  `AgentCombatDamageRuntime.applyFallDamage` directly instead of the
  temporary `BotCombatManager` facade.
- Combat skill-cache tests now exercise
  `AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded` directly, reducing
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
  now calls `AgentChatRuntime` with `AgentChatOrchestratorContext` directly;
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
  `server.agents.integration.AgentCommandTargetResolver`. Bot-entry target
  adaptation, transfer command wrapping, and targeted-command feedback are
  unchanged; `AgentCommandParser` remains the shared parser core.
- `server.bots.BotFidgetSideEffects` has moved to
  `server.agents.integration.AgentFidgetSideEffects`. Greeting/social
  fidget dispatch still delegates to the unchanged legacy fidget runtime, but
  the Agent movement callback no longer imports the bot-side shim.
- `server.bots.BotSessionLifecycleSideEffects` has moved to
  `server.agents.integration.AgentSessionLifecycleSideEffects`. Relog and
  owner-entry lookup behavior still delegates to `BotManager`, but session
  orchestration no longer imports a bot-side lifecycle shim.
- `server.bots.BotMovementTargetSideEffects` has moved to
  `server.agents.integration.AgentMovementTargetSideEffects`. Snapshot
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
| `src/main/java/server/bots/BotChatManager.java` | `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.dialogue.AgentChatRuntime`, `server.agents.capabilities.dialogue.AgentChatCommandClassifier`, `server.agents.capabilities.dialogue.AgentTradeDialogueClassifier`, `server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier`, `server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier`, `server.agents.capabilities.dialogue.AgentSocialDialogueClassifier`, `server.agents.capabilities.dialogue.AgentBuildDialogueClassifier`, `server.agents.capabilities.dialogue.AgentDialogueReportFormatter`, `server.agents.commands.AgentReplyQueue`, `server.agents.events` | `MIGRATED_TO_AGENT`; source file deleted after named random reply pools, reply queue, movement/follow/fidget, supply-request/direct supply, query/toggle, support/heal/buff toggle, logout/relog/away session request and confirmation normalization, report/debug, trade/drop/item and pending drop-choice, trade-invite/shop/maker utility, equipment/autoequip, greeting/fame, build/job/AP/SP classification, skill-tree choice resolution, job advancement resolution, report/AP-build/job-display/skill-tree/learned-skill formatting, upgrade-request classification, handled-state, and top-level chat orchestration moved to Agent-owned modules. Owner-activity, offline-return, AFK-check, airshow-active, manager-status offline-return, manager/movement status checks, movement active-mode preparation, social/fame callbacks, status-check, gear-suggestion, and recommended-gear report state bridge methods now accept `AgentRuntimeEntry`; the chat-status facade no longer imports `BotEntry`, while build/status active-mode backends retain temporary casts until build/offer/supply calls are widened |
| `src/main/java/server/bots/BotCombatManager.java` | `server.agents.capabilities.combat`, `server.agents.capabilities.dialogue`, and integration combat gateways | `MIGRATED_TO_AGENT`; source file deleted after config, combat planning, target search, AoE reposition, damage/death, support, skill-cache, debug-stat, packet execution, and combat reply behavior moved to Agent-owned modules. Combat action-lock ticking, death-state entry, attack-facing memory, skill-cache rebuild, attack planning, combat reporting, and ammo-check runtime now accept `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotCommandParser.java` | `server.agents.integration.AgentCommandTargetResolver` and `server.agents.commands.AgentCommandParser` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotEntry.java` | `server.agents.runtime.AgentSession` and capability state objects | `SPLIT_TO_MULTIPLE_AGENT_MODULES`; message queue state now lives in `AgentMessageQueueState`, scroll-reaction state now lives in `AgentScrollReactionState`, airshow session state now lives in `AgentAirshowState`, formation offset state now lives in `AgentFormationOffsetState`, pending chat action state now lives in `AgentPendingActionState`, pending loot-offer state now lives in `AgentPendingLootOfferState`, trade retry state now lives in `AgentTradeRetryState`, manual trade invite timing/reference state now lives in `AgentManualTradeState`, pending trade sequence state now lives in `AgentPendingTradeSequenceState` with direct `BotEntry` wrappers removed, owner-given trade item tracking now lives in `AgentOwnerGivenTradeItemState`, farm/sentry anchor state now lives in `AgentFarmAnchorState`, patrol region/wander state now lives in `AgentPatrolState`, active grind target/search cadence state now lives in `AgentGrindTargetState`, grind loot target/retry suppression state now lives in `AgentGrindLootState`, explicit move-here/script/fidget movement target state now lives in `AgentMoveTargetState`, grind no-target wander direction state now lives in `AgentGrindWanderState`, ranged-retreat hold state now lives in `AgentRetreatHoldState` with direct `BotEntry` wrappers removed, surround-breakout commitment state now lives in `AgentBreakoutState` with direct `BotEntry` wrappers removed, AoE reposition commitment state now lives in `AgentAoeRepositionState` with direct `BotEntry` wrappers removed, degenerate close-range attack latch state now lives in `AgentDegenerateAttackState` with direct `BotEntry` wrappers removed, combat skill cache state now lives in `AgentCombatSkillCacheState` with direct `BotEntry` wrappers removed, combat buff/support automation state now lives in `AgentCombatBuffState` with direct `BotEntry` wrappers removed, upgrade-offer/proactive gear suggestion and pending gear-prompt reservation state now lives in `AgentUpgradeOfferState`, inventory cooldown state now lives in `AgentInventoryCooldownState` with direct warning-cooldown wrappers removed, potion supply state now lives in `AgentPotionSupplyState` with direct timer wrappers removed, ammo supply state now lives in `AgentAmmoSupplyState` with direct warning/no-ammo wrappers removed, build prompt/progression state now lives in `AgentBuildState`, portal cooldown state now lives in `AgentPortalCooldownState`, combat cooldown state now lives in `AgentCombatCooldownState`, mob-touch sweep state now lives in `AgentMobTouchState` with direct `BotEntry` wrappers removed, death/respawn window state now lives in `AgentDeathState`, shop visit lifecycle state now lives in `AgentShopState`, buff automation/debug state now lives in `AgentBuffState`, navigation debug/path-log state now lives in `AgentNavigationDebugState`, navigation target waypoint state now lives in `AgentNavigationTargetState`, active navigation edge/jump-launch cache state now lives in `AgentNavigationEdgeState`, leader/owner motion observation state now lives in `AgentOwnerMotionState`, tick/heartbeat/follow-idle metadata now lives in `AgentTickState`, tick failure window metadata now lives in `AgentTickFailureState`, stuck/unstuck movement metadata now lives in `AgentMovementStuckState`, movement broadcast suppression cache state now lives in `AgentMovementBroadcastState`, last-ground foothold cache state now lives in `AgentMovementPhysicsCacheState`, script task queue state now lives in `AgentScriptTaskQueueState`, map tracking state now lives in `AgentMapTrackingState`, leader activity/safety metadata now lives in `AgentLeaderActivityState`, queued message type is Agent-owned, continuous ground-travel snapshots now use `AgentGroundTravelState`, swim mode/input/cooldown state now lives in `AgentSwimIntentState`, down-jump pending/grace-period state now lives in `AgentDownJumpState`, airborne horizontal steering state now lives in `AgentAirborneSteeringState`, movement input/packet-pose state now lives in `AgentMovementInputState`, climb/rope attachment, intent, cooldown, blocked-grab, and queued-entry state now lives in `AgentClimbState`, movement physics scalar state now lives in `AgentMovementPhysicsState`, fidget runtime state now lives in `AgentFidgetState`, tick cadence state now lives in `AgentTickState`, reply-channel state now lives in `AgentReplyChannelState`, KPQ runtime state now lives in `AgentKpqState`, script runtime progress state now lives in `AgentScriptRuntimeState`, follow/grind mode state now lives in `AgentModeState`, and movement profile storage now lives in `AgentMovementProfileState` |
| `src/main/java/server/bots/BotEquipManager.java` | `server.agents.capabilities.equipment.AgentEquipmentService` and equipment capability classes | `MIGRATED_TO_AGENT`; production callers now enter through `AgentEquipmentService`. Map-damage benchmark snapshot/selection lives in `AgentMapDamageProfile`, production weapon/job compatibility plus self-reserve weapon track labels live in `AgentWeaponCompatibilityPolicy`, slot alias resolution, ring slot detection, DP slot ordering, and display labels live in `AgentEquipmentSlotResolver`, useful-stat and defense-adjusted damage scoring lives in `AgentEquipmentScoringPolicy`, auto-equip duplicate-trigger state lives in `AgentAutoEquipThrottle`, auto-equip execution and debug branch reporting live in `AgentEquipmentAutoEquipService`, auto-equip debug report formatting lives in `AgentEquipmentDebugReportFormatter`, recommendation result data uses `AgentEquipRecommendation`, optimizer result data uses `AgentEquipmentOptimizerResult`, fixed-weapon DP result/score data uses `AgentEquipmentDpResult` and `AgentEquipmentScore`, optimizer stat snapshot data uses `AgentEquipmentStatSnapshot`, optimizer metadata/requirement hooks use `AgentEquipmentOptimizerHooks`, weapon-branch debug score breakdown data uses `AgentWeaponScoreBreakdown`, recommendation candidate eligibility lives in `AgentEquipmentRecommendationPolicy`, recommendation filtering/result construction/summary formatting lives in `AgentEquipmentRecommendationService`, unequip command execution lives in `AgentEquipmentUnequipService`, and owned/incoming equipment reserve, requirement-gate, requirement-comparison, and future-track policy lives in `AgentEquipmentReservePolicy`; production bot file deleted after compile and focused equipment tests passed |
| `src/main/java/server/bots/BotFallbackMovementManager.java` | `server.agents.capabilities.movement.AgentFallbackMovementService` | `MIGRATED_TO_AGENT`; fallback steering, rope/drop/swim/jump immediate actions, and ledge targeting moved unchanged |
| `src/main/java/server/bots/BotFidgetManager.java` | `server.agents.capabilities.movement.fidget.AgentFidgetService` | `MIGRATED_TO_AGENT`; active fidget state machine and social/greeting fidget start behavior moved unchanged, fidget leader-idle status checks now accept `AgentRuntimeEntry`, and movement/physics helpers remain temporary backing seams |
| `src/main/java/server/bots/BotFidgetSideEffects.java` | `server.agents.integration.AgentFidgetSideEffects` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotInventoryManager.java` | `server.agents.capabilities.inventory.AgentInventoryTickRuntime`, `looting`, `trade`, `server.agents.capabilities.dialogue.AgentItemQueryNormalizer`, `server.agents.capabilities.dialogue.AgentDialogueCatalog`, `server.agents.capabilities.supplies.AgentAmmoService`, `server.agents.capabilities.supplies.AgentPotionService`, `server.agents.capabilities.supplies.AgentPotionSharePolicy`, `server.agents.capabilities.trade.AgentSupplyShareTradeService`, `server.agents.capabilities.trade.AgentTradeCommandProfiler`, `server.agents.capabilities.trade.AgentInventoryTransferService`, `server.agents.capabilities.trade.AgentManualTradeService`, `server.agents.capabilities.trade.AgentManualPeerTradeService`, `server.agents.capabilities.trade.AgentGroupedTradeTransferService`, `server.agents.capabilities.trade.AgentReservedEquipTradeTransferService`, `server.agents.capabilities.trade.AgentPreparedTradeTransferService`, `server.agents.capabilities.trade.AgentTradeTransferRouter`, `server.agents.capabilities.trade.AgentTradeRecipientService`, `server.agents.capabilities.trade.AgentMesoTradeService`, `server.agents.capabilities.trade.AgentDirectItemTradeService`, `server.agents.capabilities.trade.AgentTradeStateService`, `server.agents.capabilities.trade.AgentTradeBatchService`, `server.agents.capabilities.trade.AgentTradeCancellationService`, `server.agents.capabilities.trade.AgentTradeCompletionService`, `server.agents.capabilities.trade.AgentTradeSequenceService`, `server.agents.capabilities.trade.AgentTradeResetService`, `server.agents.capabilities.trade.AgentTradeMesoAddService`, `server.agents.capabilities.trade.AgentTradeItemAddService`, `server.agents.capabilities.trade.AgentTradeAllItemsAddedService`, `server.agents.capabilities.trade.AgentTradeCategoryAnnouncementService`, `server.agents.capabilities.trade.AgentTradeInviteWaitService`, `server.agents.capabilities.trade.AgentTradeConfirmWaitService`, `server.agents.capabilities.trade.AgentTradeClosedWindowService`, `server.agents.capabilities.trade.AgentTradeTransferStartGuard`, `server.agents.capabilities.trade.AgentTradeQueuedRetryService`, `server.agents.capabilities.trade.AgentTradeBetweenBatchService`, `server.agents.capabilities.trade.AgentTradeItemAddTickService`, `server.agents.capabilities.trade.AgentTradeTickService`, `server.agents.capabilities.trade.AgentTradeSequenceOrchestrator` | `MIGRATED_TO_AGENT`; source file deleted after item query normalization, USE-item classification, passive loot, transfer availability/count, trade tick, manual trade, trade state, trade sequencing, drop behavior, supply sharing, and reply/result pools moved to Agent-owned modules. Passive loot capability/runtime callbacks, trade retry, between-batch advancement, recipient resolution, all-items-added completion marking, sequence start, tick dispatch, invite/confirmation waits, closed-window handling, cancellation, completion reactions, item-add execution, command profiling, and peer-trade ticking now accept `AgentRuntimeEntry` directly while preserving the same state-adapter behavior |
| `src/main/java/server/bots/BotLootEligibility.java` | `server.agents.capabilities.looting.AgentLootEligibility` | `MIGRATED_TO_AGENT`; loot eligibility and loot target selection now accept `AgentRuntimeEntry` while preserving target age, quest-item, inventory-full, retry suppression, seek-range, and patrol-region behavior |
| `src/main/java/server/bots/BotMakerManager.java` | `server.agents.capabilities.build.AgentMakerService` | `MIGRATED_TO_AGENT`; Maker crystal and trash-disassembly batch orchestration moved unchanged. Maker reply bridge now accepts `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotManager.java` | `server.agents.runtime`, `commands`, `events`, capability orchestrators | `MIGRATED_TO_AGENT`; runtime/lifecycle/tick/chat/command/config/test callers moved to Agent runtime, registry, lifecycle, notification, cleanup, movement, combat, supply, and dialogue services; production bot file deleted after compile and focused parity tests passed |
| `src/main/java/server/bots/BotManager.java#map-environment` | `server.agents.capabilities.movement.AgentMapEnvironmentService`, `server.agents.integration.MapGateway` | `MIGRATED_TO_AGENT`; swim-map detection accepts `AgentRuntimeEntry` and routes the Cosmic map query through the map gateway while preserving null and map-flag behavior |
| `src/main/java/server/bots/BotManager.java#leader-session` | `server.agents.runtime.AgentLeaderSessionService` | `MIGRATED_TO_AGENT`; live leader refresh now accepts `AgentRuntimeEntry` while preserving cached/refresh behavior |
| `src/main/java/server/bots/BotManager.java#mode-service` | `server.agents.runtime.AgentModeService` | `MIGRATED_TO_AGENT`; mode transitions now accept `AgentRuntimeEntry` while preserving follow/grind/stop/move/farm/patrol state changes |
| `src/main/java/server/bots/BotManager.java#command-mode` | `server.agents.commands.AgentCommandModeService` | `MIGRATED_TO_AGENT`; command-mode preparation now accepts `AgentRuntimeEntry` while preserving guard and hook order |
| `src/main/java/server/bots/BotManager.java#script-task-queue` | `server.agents.plans.AgentScriptTaskQueueService` | `MIGRATED_TO_AGENT`; script task queue operations now accept `AgentRuntimeEntry` while preserving null guards, activity-epoch bumps, queue ordering, move/drop/follow task construction, and queued-task checks |
| `src/main/java/server/bots/BotManager.java#heartbeat` | `server.agents.runtime.AgentHeartbeatService` | `MIGRATED_TO_AGENT`; heartbeat ticking now accepts `AgentRuntimeEntry` while preserving due checks, timestamp marking, client last-packet updates, and movement broadcast side effects |
| `src/main/java/server/bots/BotManager.java#scheduled-task` | `server.agents.runtime.AgentScheduledTaskRuntime` | `MIGRATED_TO_AGENT`; scheduled-task cancellation now accepts `AgentRuntimeEntry` while preserving null guards, scheduled-task presence checks, and `ScheduledFuture.cancel(false)` behavior |
| `src/main/java/server/bots/BotManager.java#action-lock-physics` | `server.agents.capabilities.movement.AgentActionLockPhysicsService` | `MIGRATED_TO_AGENT`; action-lock physics dispatch now accepts `AgentRuntimeEntry` while preserving attack-cooldown gating, swim/airborne/grounded branch selection, and movement-phase callbacks |
| `src/main/java/server/bots/BotManager.java#target-snapshot` | `server.agents.capabilities.movement.AgentTargetSnapshot` | `MIGRATED_TO_AGENT`; target snapshot steering helpers now accept `AgentRuntimeEntry` while preserving navigation waypoint override lookup and primary-target fallback behavior |
| `src/main/java/server/bots/BotManager.java#final-movement-tail` | `server.agents.capabilities.movement.AgentFinalMovementTailService` | `MIGRATED_TO_AGENT`; final movement tail dispatch now accepts `AgentRuntimeEntry` while preserving movement-core target and AI-tick arguments |
| `src/main/java/server/bots/BotManager.java#idle-mode-tick` | `server.agents.runtime.AgentIdleModeTickService` | `MIGRATED_TO_AGENT`; idle-mode tick dispatch now accepts `AgentRuntimeEntry` while preserving idle physics callback behavior |
| `src/main/java/server/bots/BotManager.java#movement-phase` | `server.agents.capabilities.movement.AgentMovementPhaseService` | `MIGRATED_TO_AGENT`; movement phase dispatch now accepts `AgentRuntimeEntry` while preserving climb/swim/airborne/grounded branch ordering and callbacks |
| `src/main/java/server/bots/BotManager.java#movement-core-tick` | `server.agents.capabilities.movement.AgentMovementTickService` | `MIGRATED_TO_AGENT`; movement core tick orchestration and precise navigation target marking now accept `AgentRuntimeEntry` while preserving navigation resolution, precise-target marking, fidget short-circuiting, phase dispatch, committed-edge execution, stuck detection, and move-target cleanup ordering |
| `src/main/java/server/bots/BotManager.java#live-tick-gates` | `server.agents.runtime.AgentLiveTickGateService` | `MIGRATED_TO_AGENT`; live tick gate ordering now accepts `AgentRuntimeEntry` while preserving common/trade/idle/recovery/map-change order and short-circuit behavior. `AgentLiveTickGateRuntime` remains the temporary compatibility bridge to BotEntry-shaped downstream callbacks |
| `src/main/java/server/bots/BotManager.java#live-tick-context` | `server.agents.runtime.AgentLiveTickContextService` | `MIGRATED_TO_AGENT`; live tick context preparation and observed leader motion updates now accept `AgentRuntimeEntry` while preserving movement profile refresh, follow-anchor resolution, target snapshot capture, map-change cleanup, and follow-action cleanup ordering |
| `src/main/java/server/bots/BotManager.java#tick-core` | `server.agents.runtime.AgentTickCoreService` | `MIGRATED_TO_AGENT`; tick core orchestration now accepts `AgentRuntimeEntry` while preserving preflight, leader-resolution, inactive-leader, ownerless, death, live-context, live-gate, and live-mode ordering. `AgentTickCoreRuntime` remains the temporary compatibility bridge to BotEntry-shaped downstream callbacks |
| `src/main/java/server/bots/BotManager.java#tick-preflight` | `server.agents.runtime.AgentTickPreflightService` | `MIGRATED_TO_AGENT`; tick preflight now accepts `AgentRuntimeEntry` while preserving null handling, airshow skip, movement-delay skip, missing-map cleanup, heartbeat, offer expiry, and AI tick preparation ordering |
| `src/main/java/server/bots/BotManager.java#trade-window-tick` | `server.agents.capabilities.trade.AgentTradeWindowTickService` | `MIGRATED_TO_AGENT`; trade-window tick gating now accepts `AgentRuntimeEntry` while preserving open-trade detection, physics-only tick dispatch, and consumed-tick behavior |
| `src/main/java/server/bots/BotManager.java#ownerless-tick` | `server.agents.capabilities.movement.AgentOwnerlessTickService` | `MIGRATED_TO_AGENT`; ownerless tick handling now accepts `AgentRuntimeEntry` while preserving follow-mode clearing, map-change grounding short-circuit, standalone move-target ticking, and idle fallback behavior |
| `src/main/java/server/bots/BotManager.java#death-tick` | `server.agents.capabilities.combat.AgentDeathTickService`, `server.agents.runtime.AgentRespawnRuntime` | `MIGRATED_TO_AGENT`; death tick and respawn-near-leader handling now accept `AgentRuntimeEntry` while preserving dead-state entry checks, respawn timing, HP restore, map-change, grounding, teleport, reset, movement broadcast, map speech, and glare emote behavior |
| `src/main/java/server/bots/BotManager.java#leader-safety-runtime` | `server.agents.runtime.AgentLeaderSafetyRuntime` | `MIGRATED_TO_AGENT`; inactive-leader runtime now accepts `AgentRuntimeEntry` while preserving active-return cleanup, town eligibility, safe-mode entry, town-scroll fallback, formation target selection, map-change grounding, movement reset, and return announcements |
| `src/main/java/server/bots/BotManager.java#recovery-tick` | `server.agents.capabilities.recovery.AgentRecoveryTickService` | `MIGRATED_TO_AGENT`; recovery tick handling now accepts `AgentRuntimeEntry` while preserving shop-visit follow-sync suppression, follow-map sync, party recovery, target recovery ordering, and short-circuit behavior |
| `src/main/java/server/bots/BotManager.java#tracked-map-change-tick` | `server.agents.runtime.AgentTrackedMapChangeTickService` | `MIGRATED_TO_AGENT`; tracked map-change tick handling now accepts `AgentRuntimeEntry` while preserving handler dispatch and consumed/fall-through behavior |
| `src/main/java/server/bots/BotManager.java#map-transition` | `server.agents.capabilities.movement.AgentMapTransitionService` | `MIGRATED_TO_AGENT`; map transition grounding and tracked-map-change handling now accept `AgentRuntimeEntry` while preserving tracking checks, foothold index capture, grounding teleport, reset, graph warmup, movement broadcast, grind/follow/PQ dispatch, shop map-change, and status-check ordering |
| `src/main/java/server/bots/BotManager.java#recovery-teleport` | `server.agents.capabilities.recovery.AgentRecoveryTeleportService` | `MIGRATED_TO_AGENT`; recovery teleport distance handling now accepts `AgentRuntimeEntry` while preserving target distance checks, out-of-bounds checks, grind-party constraints, shop-visit suppression, multiplier math, grounding lookup, teleport/reset, and movement broadcast side effects |
| `src/main/java/server/bots/BotManager.java#follow-map-sync` | `server.agents.capabilities.follow.AgentFollowMapSyncService` | `MIGRATED_TO_AGENT`; cross-map follow synchronization now accepts `AgentRuntimeEntry` while preserving follow-mode gating, same-map/null-anchor skip behavior, grounded anchor spawn selection, idle-on-ground, map change, and movement reset side effects |
| `src/main/java/server/bots/BotManager.java#follow-target-command` | `server.agents.capabilities.follow.AgentFollowTargetCommandService` | `MIGRATED_TO_AGENT`; follow-target command application now accepts `AgentRuntimeEntry` collections while preserving target resolution, null/missing/self-target filtering, reply queuing, delay scheduling, auto-equip, potion sharing, and follow-start ordering |
| `src/main/java/server/bots/BotManager.java#follow-target-candidates` | `server.agents.capabilities.follow.AgentFollowTargetCandidateService` | `MIGRATED_TO_AGENT`; follow-target candidate selection now accepts Agent runtime sibling entries while preserving leader inclusion, party-member filtering, sibling-agent filtering, and duplicate suppression |
| `src/main/java/server/bots/BotManager.java#movement-command-runtime` | `server.agents.capabilities.movement.AgentMovementCommandRuntime` | `MIGRATED_TO_AGENT`; follow, stop, move-to, farm-here, patrol, and grind command entry points now accept `AgentRuntimeEntry` while preserving prepared-mode ordering, script-task clearing, shop cancellation, patrol-region lookup, missing-region reply text, navigation-state clearing, and mode transitions |
| `src/main/java/server/bots/BotFidgetManager.java` | `server.agents.capabilities.movement.fidget.AgentFidgetService` | `MIGRATED_TO_AGENT`; fidget runtime now accepts `AgentRuntimeEntry` while preserving idle/social/speed-mismatch selection, prone/sideways/jump behavior, return-to-origin, movement broadcasts, and visual prone-attack packets |
| `src/main/java/server/bots/BotManager.java#formation-state` | `server.agents.capabilities.movement.AgentFormationService` | `MIGRATED_TO_AGENT`; formation state lookup and offset application now accept Agent runtime entries while preserving formation store access, leader/default resolution, and offset assignment patterns |
| `src/main/java/server/bots/BotManager.java#formation-command` | `server.agents.capabilities.movement.AgentFormationCommandService` | `MIGRATED_TO_AGENT`; formation command handling now accepts Agent runtime entries while preserving command matching, help/status replies, snap range updates, formation writes, offset application, and first-entry/leader reply routing |
| `src/main/java/server/bots/BotManager.java#common-tick-systems` | `server.agents.runtime.AgentCommonTickService` | `MIGRATED_TO_AGENT`; common per-tick system ordering now accepts `AgentRuntimeEntry` while preserving mob damage, death short-circuiting, monster release, passive loot/trade gating, potion and recovery ticks, build level-up checks, AFK/status checks, trade/manual-trade, PQ/script/NPC-lock gates, action-lock handling, AI-gated combat systems, and final action-lock return behavior. Mob-touch sweep runtime now accepts `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotManager.java#live-mode-tick` | `server.agents.runtime.AgentLiveModeTickService` | `MIGRATED_TO_AGENT`; live-mode tick phase ordering now accepts `AgentRuntimeEntry` while preserving shop-visit, follow-opportunity, follow-idle, scripted-move combat, anchored farm, grind dispatch, target propagation, consumed-tick short-circuits, and final movement tail behavior |
| `src/main/java/server/bots/BotManager.java#grind-mode-tick` | `server.agents.runtime.AgentGrindModeRuntime`, `server.agents.capabilities.combat.AgentGrindModeTickService` | `MIGRATED_TO_AGENT`; grind-mode tick entry points now accept `AgentRuntimeEntry` while preserving target seek, loot validation/refresh, no-target fallback, commitment, ranged engagement, navigation-tail resolution, seek range, and loot radius behavior. Some downstream grind callbacks still adapt to the temporary BotEntry shell |
| `src/main/java/server/bots/BotManager.java#shop-visit-tick` | `server.agents.capabilities.shop.AgentShopVisitTickService` | `MIGRATED_TO_AGENT`; shop-visit tick gating now accepts `AgentRuntimeEntry` while preserving pending-shop checks, shop tick execution, active target lookup, approach-delay consumption, target movement stepping, and result propagation |
| `src/main/java/server/bots/BotMovementManager.java` | `server.agents.capabilities.movement` | `MIGRATED_TO_AGENT`; cooldown/delay countdown math, packet-visible movement broadcast, movement reset/transient cleanup, foothold-index construction, walk-step kinematics, movement profile refresh, stuck recovery, swim/airborne/climb/grounded phase runtime, ground grind target adjustment, mob avoidance, ground action planning/execution, jump/rope probe seams, fallback jump/rope routing, jump action initiation, queued movement actions, rope/climb launch routing, grounded physics entry-point routing, movement phase dispatch, ground-step resolution/update state, climb idle/snap/rope identity policy, ground horizontal step policy, precise-stop/drop-edge policy, and movement command distance/config reads are Agent-owned; production bot file deleted after compile and focused movement tests passed. Movement snapshot/broadcast, motion-timer services, rope movement actions, jump action initiation, queued movement actions, climb movement runtime, swim movement runtime, swim physics, grounded physics, grounded runtime dispatch, and fallback movement now accept `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotMovementTargetSideEffects.java` | `server.agents.integration.AgentMovementTargetSideEffects` | `MIGRATED_TO_AGENT`; movement target snapshot facade and side-effect bridge now accept `AgentRuntimeEntry` while preserving target capture and steering target behavior |
| `src/main/java/server/bots/BotMovementProfile.java` | `server.agents.capabilities.movement.AgentMovementProfile` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationDebugOverlay.java` | `server.agents.capabilities.navigation.AgentNavigationDebugOverlay` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationGraph.java` | `server.agents.capabilities.navigation.AgentNavigationGraph` | `MIGRATED_TO_AGENT`; graph model, region/edge/segment data, cache serialization shape, and lookup helpers moved unchanged |
| `src/main/java/server/bots/BotNavigationGraphProvider.java` | `server.agents.capabilities.navigation.AgentNavigationGraphService` | `MIGRATED_TO_AGENT`; graph build/warmup/cache/report/collidable helper behavior moved unchanged, with BotPhysicsEngine/BotMovementManager as temporary explicit seams |
| `src/main/java/server/bots/BotNavigationManager.java` | `server.agents.capabilities.navigation` | `MIGRATED_TO_AGENT`; navigation-facing physics helper calls for first-climbable rope Y, walkable endpoint-step policy, and graph build walk-region lookup lifecycle route through `AgentNavigationPhysicsService`; movement capability ground-target, mob-avoidance, follow-target, path logger, script move-target, navigation debug overlay, shop approach, and combat grind-target region classification route through `AgentNavigationRegionService`; region-classification implementation now lives in `AgentNavigationRegionService` with bot region compatibility delegates removed; grind navigation and fallback target runtime hooks now call Agent region/path services directly; path logger, script move-target, navigation debug overlay, shop approach, navigation probe path/optimality, combat grind target-score path searches, path travel-cost, path heuristic calculations, leading no-op walk collapse, precise walk-target policy, edge usability policy, no-movement walk tolerance, production path search entry points, target-score path search, optimality measurement, slow-path logging, path reconstruction, next-edge selection, copied search algorithm, and search-result adapters route through `AgentNavigationPathService`, with bot path/search compatibility wrappers removed; graph warmup notification throttling routes through `AgentNavigationWarmupService`; navigation reset and target snapshot forwarding helpers were removed in favor of direct Agent movement reset/snapshot calls; target-resolution tests now call `AgentNavigationTargetService` directly and the bot navigation shell has been deleted; portal edge execution routes through `AgentNavigationPortalService`; climb-start side effects route through `AgentNavigationClimbExecutionService`, whose entry-facing API now accepts `AgentRuntimeEntry`; explicit drop edge execution routes through `AgentNavigationDropExecutionService`, whose entry-facing API now accepts `AgentRuntimeEntry`; jump edge execution routes through `AgentNavigationJumpExecutionService`, whose entry-facing API now accepts `AgentRuntimeEntry`; climb-entry edge execution routes through `AgentNavigationClimbEntryExecutionService`, whose entry-facing API now accepts `AgentRuntimeEntry`; climb-exit edge execution routes through `AgentNavigationClimbExitExecutionService`, whose entry-facing API now accepts `AgentRuntimeEntry`; edge execution dispatch routes through `AgentNavigationEdgeExecutor`, whose entry-facing API now accepts `AgentRuntimeEntry`; live navigation target resolution and committed-edge continuation now live in `AgentNavigationTargetService`, with the bot methods reduced to compatibility delegates and stale private target-resolution helper bodies removed; committed-edge equality, retention, committed ground-edge refresh/replacement, pending climb-exit refresh, and committed-edge reuse/staleness policy route through `AgentNavigationCommittedEdgeService`, whose entry-facing APIs now accept `AgentRuntimeEntry` and whose default overload owns edge-usability and rope-entry callback wiring, with bot committed-edge compatibility delegates removed; edge execution target state routes through `AgentNavigationEdgeExecutionStateService`; edge execution readiness thresholds plus jump/drop and selected-jump execution readiness predicates route through `AgentNavigationEdgeReadinessService`, with bot readiness test wrappers removed; rope/climb entry, exit predicates, and climb-exit execution readiness route through `AgentNavigationRopeEdgeService`, with the bot top-step wrapper removed; grind-mode path target clamping routes through `AgentNavigationGrindTargetService`; jump/drop launch-window and directional-drop runway checks route through `AgentNavigationLaunchWindowService`; pure jump, entry-backed cached jump-launch, default climb, straight-drop, and directional-drop waypoint selection route through `AgentNavigationWaypointService`, whose entry-backed waypoint methods and climb readiness seam now accept `AgentRuntimeEntry`, with directional-drop, jump, and climb bot compatibility delegates removed; precise waypoint targeting policy routes through `AgentNavigationPreciseTargetService`, whose policy and readiness seam now accept `AgentRuntimeEntry`; dead landing-region, private pathfinding, and private rope lookup helpers removed. Edge execution target state, live target resolution, and post-ground committed-edge execution now accept `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotNavigationMapLoader.java` | `server.agents.capabilities.navigation.AgentNavigationMapLoader` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotNavigationProbe.java` | `server.agents.capabilities.navigation.AgentNavigationProbe` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotOfferManager.java` | `server.agents.capabilities.trade.AgentOfferService`, `equipment`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; owner/sibling gear offer orchestration, pending offer responses, loot-offer prompts, reservation checks, and best-upgrade request routing now live in Agent trade. Offer runtime reply, gear-prompt helper methods, pending-offer state cleanup, owner upgrade-request entry, and reservation classification now accept `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotOwnershipService.java` | `server.agents.auth.AgentOwnershipService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPathLogger.java` | `server.agents.monitoring.AgentPathLogger` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPerformanceMonitor.java` | `server.agents.runtime.AgentPerformanceMonitor` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotPhysicsEngine.java` | `server.agents.capabilities.movement`, `server.agents.capabilities.navigation` | `MIGRATED_TO_AGENT`; movement config, kinematics, pose/snapshot, timers, ground lookup/collision/walk-region lookup, ground motion, queued down-jump, rope/climb motion, jump/rope/fall probes, airborne stepping, swim stepping, navigation walk-connectivity, and focused physics regression coverage now call Agent-owned services directly; production bot file deleted after compile and focused physics tests passed |
| `src/main/java/server/bots/BotPotionManager.java` | `server.agents.capabilities.supplies.AgentPotionService`, `server.agents.capabilities.supplies.AgentAutopotPolicy`, `server.agents.capabilities.supplies.AgentPotionInventoryPolicy`, `server.agents.capabilities.supplies.AgentPassiveRecoveryPolicy`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; potion tick orchestration, autopot setup/debug reporting, low-pot supply sharing, donor selection, passive recovery, and grind-start supply reporting now live in Agent supplies |
| `src/main/java/server/bots/BotScript.java` | `server.agents.plans.AgentScript` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptContext.java` | `server.agents.plans.AgentScriptContext` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScriptRunner.java` | `server.agents.plans.AgentScriptRunner` | `MIGRATED_TO_AGENT`; script item drop execution now accepts `AgentRuntimeEntry` while preserving runtime identity lookup, inventory lookup, quantity clamping, and the final drop call |
| `src/main/java/server/bots/BotScriptRuntime.java` | `server.agents.plans.AgentScriptRuntimeState` | `MIGRATED_TO_AGENT`; script move-target cheapness checks now accept `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotScriptStep.java` | `server.agents.plans.AgentScriptStep` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotScrollReactionManager.java` | `server.agents.capabilities.social.AgentScrollReactionService` | `MIGRATED_TO_AGENT`; scroll-reaction reply queue bridge now accepts `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotSessionLifecycleSideEffects.java` | `server.agents.integration.AgentSessionLifecycleSideEffects` | `MIGRATED_TO_AGENT`; session-control primary-session and away-town checks now accept `AgentRuntimeEntry` while preserving the same lifecycle side-effect backend |
| `src/main/java/server/bots/BotSessionLifecycleSideEffects.java#lookup` | `server.agents.integration.AgentSessionLifecycleSideEffects` | `MIGRATED_TO_AGENT`; session side-effect lookups now return `AgentRuntimeEntry` lists/entries while preserving relogin dispatch, active-leader lookup, inactive safe-mode, and name/leader lookup behavior |
| `src/main/java/server/bots/BotShopManager.java` | `server.agents.capabilities.shop.AgentShopService`, `server.agents.capabilities.dialogue.AgentDialogueCatalog` | `MIGRATED_TO_AGENT`; shop visit orchestration, sell-trash visit routing, resupply/recharge purchases, shop approach selection, timeout handling, and purchase sequence callbacks now live in Agent shop. Shop reply bridge now accepts `AgentRuntimeEntry` |
| `src/main/java/server/bots/BotStarterKitManager.java` | `server.agents.capabilities.build.AgentStarterKitService` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/BotTask.java` | `server.agents.plans.AgentTask` | `MIGRATED_TO_AGENT`; script task start dispatch now accepts `AgentRuntimeEntry` |
| `src/main/java/server/bots/Emote.java` | `server.agents.capabilities.dialogue.AgentEmote` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/ReplyChannel.java` | `server.agents.commands.AgentReplyChannel` | `COMPATIBILITY_ALIAS_TEMPORARY`; reply delivery adapters now accept `AgentRuntimeEntry` while preserving the same queue/channel dispatch behavior |
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
| `src/main/java/server/bots/BotManager.java#local-opportunity-attack` | `server.agents.runtime.AgentLocalOpportunityAttackRuntime`, `server.agents.capabilities.combat.AgentLocalOpportunityAttackService`, `server.agents.capabilities.combat.AgentCombatAttackRuntime` | `MIGRATED_TO_AGENT`; follow-opportunity local attack and attack execution now accept `AgentRuntimeEntry` while preserving target selection, retreat, jump, attack readiness, damage packet construction, cooldown, facing, alert, and move-window behavior |
| `src/main/java/server/bots/BotManager.java#grind-combat-helpers` | `server.agents.runtime.AgentGrindCombatRuntime` | `MIGRATED_TO_AGENT`; AoE reposition and priority ranged target callbacks now accept `AgentRuntimeEntry` while preserving AoE anchor and ranged-threat selection behavior |
| `src/main/java/server/bots/llm/BotLlmConfig.java` | `server.agents.capabilities.dialogue.llm.AgentLlmConfig` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/BotLlmReplyManager.java` | `server.agents.capabilities.dialogue.llm.AgentLlmReplyService` | `MIGRATED_TO_AGENT`; LLM reply bridge now accepts `AgentRuntimeEntry` |
| `src/main/java/server/bots/llm/BotMemoryStore.java` | `server.agents.capabilities.dialogue.llm.AgentMemoryStore` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/CommandTypoSuggester.java` | `server.agents.commands.AgentCommandTypoSuggester` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/OllamaClient.java` | `server.agents.capabilities.dialogue.llm.OllamaClient` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/PromptBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentPromptBuilder` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/SenderRelation.java` | `server.agents.capabilities.dialogue.llm.AgentSenderRelation` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/llm/SituationBuilder.java` | `server.agents.capabilities.dialogue.llm.AgentSituationBuilder` | `MIGRATED_TO_AGENT` |
| `src/main/java/server/bots/pq/BotKpqStage1.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage1` | `MIGRATED_TO_AGENT`; KPQ stage-1 scripted movement, coupon target, grind, exchange, and pass delivery behavior moved unchanged |
| `src/main/java/server/bots/pq/BotKpqStage5.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqStage5` | `MIGRATED_TO_AGENT`; KPQ stage-5 reward claim and announcement behavior moved unchanged, and the stage-5 tick/state bridge now accepts `AgentRuntimeEntry` |
| `src/main/java/server/bots/pq/BotKpqState.java` | `server.agents.capabilities.partyquest.kpq.AgentKpqState` | `MIGRATED_TO_AGENT`; temporary BotEntry-backed KPQ state bag now uses Agent-owned type |
| `src/main/java/server/bots/pq/BotPqHooks.java` | `server.agents.capabilities.partyquest.AgentPartyQuestHooks` | `MIGRATED_TO_AGENT`; PQ tick, NPC lock, grind/follow defaults, and coupon-loot gating moved unchanged. PQ state bridge methods and PQ gate checks now accept `AgentRuntimeEntry` |
| `src/main/resources/db/tables/025-bot-ownership.sql` | `server.agents.legacy` documentation initially; later external registry or deletion | `LEGACY_PROFILE` |

Recent capability extraction notes:

- `AgentMovementStateRuntime` now lives in
  `server.agents.capabilities.movement`. It remains behavior-preserving over
  the same `AgentRuntimeEntry` movement state bags; the only remaining
  integration seam is live Agent/leader position lookup through
  `AgentRuntimeIdentityRuntime`.
- `AgentMovementCommandRuntime` now lives in
  `server.agents.capabilities.movement`. It preserves the existing follow,
  stop, move-to, farm-here, patrol, and grind mode-transition behavior while
  keeping live identity lookup and visible replies behind integration seams.
- `AgentMovementRuntime` now lives in `server.agents.capabilities.movement`.
  It preserves the same movement chat callback timings, replies, supply checks,
  active-mode preparation, movement command dispatch, and fidget side effects;
  live identity, scheduling, reply delivery, and fidget side effects remain
  explicit seams.
- `AgentMobTouchRuntime` now lives in `server.agents.capabilities.combat`. It
  preserves the same touch-sweep bounds, hitbox lookup, lower-half
  intersection policy, and checkpoint state behavior.
- `AgentCombatBuffRuntime` now lives in `server.agents.capabilities.combat`.
  It preserves the same support-buff readiness, party-support selection,
  special-move dispatch, cooldown, alert, and legacy debug behavior.
- `AgentCombatAmmoCheckRuntime` now lives in
  `server.agents.capabilities.supplies`. It preserves the same ammo/MP-pot
  shortage decisions, warning state transitions, grind follow fallback, and map
  dialogue side effects.
- `AgentStatusRuntime` now lives in `server.agents.capabilities.dialogue`. It
  preserves the same offline-return and AFK-return status action adapters while
  keeping live identity and reply delivery as integration seams.
- `AgentSocialRuntime` now lives in `server.agents.capabilities.social`. It
  preserves the same delayed fame command handling, target lookup, fame
  eligibility/mutation, and reply behavior while keeping live identity and
  reply delivery as integration seams.
- `AgentCombatDeathRuntime` now lives in
  `server.agents.capabilities.combat`. It preserves action-state clearing,
  dead pose marking, movement broadcast, dead-state timing, and optional map
  death dialogue.
- `AgentCombatHealRuntime` now lives in
  `server.agents.capabilities.combat`. It preserves support-heal readiness,
  target selection, jump-heal assist, skill application, attack packet
  construction, cooldown/alert updates, and movement broadcast behavior while
  leaving live leader/session lookup as integration seams.
- `AgentChatReportRuntime` integration facade has been renamed and moved to
  `server.agents.capabilities.dialogue.AgentChatReportOperationsRuntime`. It
  preserves report callback wiring and delivery while leaving live identity and
  reply delivery as integration seams.
- `AgentCombatTargetRuntime` now lives in
  `server.agents.capabilities.combat`. It preserves target search/scoring,
  graph-cost reachability, patrol filtering, immediate projectile targeting,
  and sibling occupancy penalties while leaving live leader/session lookup as
  integration seams.
- `AgentCombatAttackRuntime` now lives in
  `server.agents.capabilities.combat`. It preserves attack readiness, packet
  attack-info construction, damage target construction, route application,
  cooldown, facing, and alert behavior while leaving route application behind
  the existing execution provider seam.
- `AgentCombatDamageRuntime` now lives in
  `server.agents.capabilities.combat`. It preserves mob/fall damage,
  HP/autopot mutation, packet-visible damage broadcast, death entry, knockback,
  cooldown, alert, and movement broadcast behavior; direct HP mutation and
  packet broadcast calls remain the next explicit gateway split.
- `AgentSessionRuntime` now lives in `server.agents.runtime`. It preserves
  session request callbacks, relog/logout confirmation timing, owner-away
  handling, stop-command dispatch, save/disconnect/relogin scheduling, and
  replies while leaving save/disconnect/relogin and reply delivery as
  integration seams.
- Remaining `server.agents.integration` `Agent*Runtime` classes are intentional
  boundary seams: `AgentReplyRuntime` owns Cosmic chat/whisper/party packet
  delivery and reply-queue dispatch; `AgentRuntimeIdentityRuntime` owns live
  Cosmic `Character`/`MapleMap` identity access for `AgentRuntimeEntry`. These
  are retained until the broader SPI/gateway extraction replaces direct Cosmic
  types.
- Removed `AgentFidgetSideEffects`; fidget behavior is now called through
  `server.agents.capabilities.movement.fidget.AgentFidgetService` from movement
  callbacks instead of an integration wrapper.
- Removed `AgentMovementTargetSideEffects`; target snapshot conversion and
  capture now live in `server.agents.capabilities.movement.AgentMovementTargetRuntime`.
  The capability still uses `AgentRuntimeIdentityRuntime` as the live
  `Character`/map boundary until the broader SPI/gateway phase.
- Moved `AgentCommandTargetResolver` to `server.agents.commands` so transfer
  command matching and targeted Agent command parsing are no longer modeled as
  integration code. Live Agent name lookup remains behind
  `AgentRuntimeIdentityRuntime`.
- Moved `AgentSessionLifecycleSideEffects` to
  `server.agents.runtime.AgentSessionLifecycleRuntime`; session registry lookup,
  relogin dispatch, leader safe mode, and active leader lookup now sit with
  Agent runtime ownership instead of an integration wrapper.
- Remaining `server.agents.integration` files after this extraction milestone
  are boundary adapters or SPI placeholders: stance broadcast, inventory/trade
  callback wiring around Cosmic item/trade APIs, reply delivery, live identity
  lookup, trade invite calls, and empty `*Gateway` interfaces for the later
  gateway implementation phase.
- SPI/gateway extraction: `PacketGateway` and
  `server.agents.integration.cosmic.CosmicPacketGateway` now own movement packet
  construction/broadcast for Agent movement. This removes direct
  `PacketCreator.movePlayer` and map broadcast calls from
  `AgentMovementBroadcastService`;
  `server.agents.integration.cosmic.CosmicAgentServerAdapter` exposes the packet
  gateway through `AgentServerAdapter`.
- SPI/gateway extraction: fidget close-range visual attack packets now route
  through the same packet gateway, removing direct `PacketCreator.closeRangeAttack`
  and map broadcast calls from `AgentFidgetService`.
- SPI/gateway extraction: airshow movement packets now route through
  `PacketGateway`; airshow trail spawn/kill packet calls remain direct until the
  airshow visual gateway slice.
- SPI/gateway extraction: navigation debug overlay cleanup now routes mist
  removal through `PacketGateway.sendRemoveMist` instead of calling
  `PacketCreator.removeMist` directly from the navigation capability.
- SPI/gateway extraction: loot cleanup now routes stale drop removal packets
  through `PacketGateway.sendRemoveItemFromMap`; looting still owns the
  visible-player and BotClient filtering behavior.
- SPI/gateway extraction: trade item-add packet delivery now routes through
  `PacketGateway.sendTradeItemAdd`; `AgentTradeItemAddService` still owns item
  selection, inventory removal, and recipient/partner routing.
- SPI/gateway extraction: combat damage packet broadcast now routes through
  `PacketGateway.broadcastDamagePlayer`; `AgentCombatDamageRuntime` still owns
  HP mutation, cooldowns, death transition, and knockback orchestration.
- SPI/gateway extraction: navigation debug overlay mist spawn packets now route
  through `PacketGateway.sendMistFakeSpawn`; navigation still owns overlay
  geometry, effect selection, and object id allocation.
- SPI/gateway extraction: airshow trail spawn/kill packets now route through
  `PacketGateway`; `AgentAirshowService` still owns trail timing and monster
  visual setup.
- SPI/gateway extraction: support special-move packet parsing and handler
  dispatch now route through `CombatGateway`/`CosmicCombatGateway`; combat still
  owns the support special-move packet byte builder and invocation decision.
- SPI/gateway extraction: ammo-share projectile weapon-attack lookup now routes
  through `InventoryGateway`/`CosmicInventoryGateway`; supplies still owns
  ammo-share behavior, donor selection, and transfer scheduling, while the
  direct `ItemInformationProvider.getWatkForProjectile` call is isolated in the
  Cosmic inventory gateway.
- SPI/gateway extraction: autopot debug item-name lookup now routes through
  `InventoryGateway`/`CosmicInventoryGateway`; supplies still owns potion
  counting, ranking, keybinding setup, share requests, and passive recovery
  while Cosmic item metadata access continues to shrink into the inventory
  gateway.
- SPI/gateway extraction: inventory safe-collection and drop safety now use
  `InventoryGateway.isQuestItem`; inventory still owns bag traversal, safe-item
  policy application, category filters, drop execution, and replies while the
  Cosmic quest-item metadata check sits behind the inventory gateway.
- SPI/gateway extraction: named-item inventory collection now uses
  `InventoryGateway` for item-name and quest-item metadata. Inventory still
  owns normalized query matching, cached normalized names, safe-item filtering,
  and count/collection behavior.
- SPI/gateway extraction: use-item trade grouping now uses
  `InventoryGateway.isQuestItem`; inventory/trade collection still owns category
  expansion, equipped-slot fallback, priority ordering, grouping, and transfer
  count behavior.
- SPI/gateway extraction: equipped-slot trade preparation now uses
  `InventoryGateway.isCashItem`; inventory still owns equipped-slot matching,
  temporary unequip, restore tracking, and trade item preparation behavior.
- SPI/gateway extraction: use-item classification now uses
  `InventoryGateway.getItemEffect`; inventory still owns recovery/buff
  classification. The gateway returns `StatEffect` temporarily to preserve
  behavior before a later Agent-owned item-effect view is extracted.
- SPI/gateway extraction: inventory dialogue reporting and nearest-town return
  scroll use now use `InventoryGateway` for item-effect and quest-item metadata;
  dialogue formatting, safe mention filtering, effect application, and item
  removal behavior stay in their existing Agent modules.
- SPI/gateway extraction: sell-trash equip protection now uses
  `InventoryGateway` for equip stats, base equip lookup, and quest-item checks;
  inventory still owns sell-trash filtering, protected-stat policy, self-upgrade
  exclusion, reserved-recipient exclusion, and sort behavior.
- SPI/gateway extraction: equipment unequip live hooks now use
  `InventoryGateway` for cash-item and item-name metadata; equipment still owns
  slot selection, free-slot validation, item moves, and reply formatting.
- SPI/gateway extraction: equipment recommendation summary formatting now uses
  `InventoryGateway` for item names; recommendation candidate filtering,
  optimizer input, useful-item policy, and future/immediate recommendation
  behavior remain unchanged for a later equipment gateway slice.
- SPI/gateway extraction: scroll-reaction success-rate lookup now uses
  `InventoryGateway.getEquipStats`; social reaction timing, chance scaling,
  streak state, emotes, queued chat, and fidget behavior are unchanged.
- SPI/gateway extraction: shop resupply metadata now uses `InventoryGateway`
  for projectile attack, ammo slot-max, and item names; shop sequencing,
  resupply/recharge decisions, purchase shortfall replies, and test seams remain
  behavior-preserving.
- SPI/gateway extraction: trade-transfer ammo grouping now uses
  `InventoryGateway` for projectile attack and quest-item metadata; trade
  routing, collection, grouping, batching, and sequence behavior stay unchanged.
- SPI/gateway extraction: KPQ Stage 5 reward announcement item names now use
  `InventoryGateway`; reward claiming, inventory delta detection, claimed-state
  marking, and queued PQ chat behavior stay unchanged.
- SPI/gateway extraction: common buff-pot active/available item names now use
  `InventoryGateway`; buff eligibility, cheap/best selection, active-buff
  detection, item consumption, and report text stay unchanged.
- SPI/gateway extraction: combat attack execution now uses `InventoryGateway`
  for equipped-weapon type and two-handed metadata; basic/skill route
  selection, animation action sampling, hitbox calculation, retreat behavior,
  and packet fields stay unchanged.
- SPI/gateway extraction: inventory runtime trade callbacks now use
  `InventoryGateway` for projectile attack and quest-item metadata; callback
  construction, trade availability, category profiling, and transfer behavior
  stay unchanged.
- SPI/gateway extraction: trade offer item names, cash-equip checks,
  weapon-type compatibility, and throwing-star attack values now use
  `InventoryGateway`; offer prompts, recipient priority, proactive upgrade
  checks, sibling/owner routing, and transfer scheduling stay unchanged. The
  remaining item-info calls in this class are tied to equipment reserve-policy
  signatures and remain for the equipment gateway slice.
- SPI/gateway extraction: Maker leftover-to-crystal metadata now uses
  `InventoryGateway`; Maker skill gating, leftover counting, batch scheduling,
  MakerProcessor execution, disassembly filtering, and interruption behavior
  stay unchanged.
- SPI/gateway extraction: equipment optimizer weapon-cycle scoring now uses
  `InventoryGateway` for weapon-type metadata; DP state generation, requirement
  validation, Pareto pruning, score comparison, and attack-cycle timing stay
  unchanged.
- SPI/gateway extraction: auto-equip debug dump item rows now use
  `InventoryGateway` for item name, text slot, equip stats, and level
  requirement metadata; dump layout, self-reserve flags, optimizer inputs, and
  equipment selection behavior stay unchanged.
- SPI/gateway extraction: default equipment self-reserve and potential
  self-upgrade collection now use gateway-backed reserve hooks for cash checks,
  text slot, weapon type, equip stats, level requirements, and requirement
  validation. Legacy item-info overloads remain for compatibility and focused
  tests; reserve selection behavior stays unchanged.
- SPI/gateway extraction: equipment recommendation filtering now uses
  gateway-backed recommendation hooks for cash checks, text slot, weapon type,
  wearability, and requirement validation; recommendation candidate filtering,
  future/immediate scope behavior, optimizer inputs, and summary formatting stay
  unchanged.
- SPI/gateway extraction: trade offer future-reserve checks now use the
  gateway-backed `AgentEquipmentService.wouldReserveIncomingItem(Character,
  Equip)` path. `AgentOfferService` no longer passes `ItemInformationProvider`
  into reserve checks; recipient priority, proactive-upgrade FUTURE routing,
  reserved-item filtering, and offer prompts stay unchanged.
- SPI/gateway extraction: live auto-equip infeasible-equipment cleanup now uses
  gateway-backed cash and wearability hooks in `AgentEquipmentPlanExecutor`.
  The compatibility item-info overload remains for staged callers/tests; cash
  skipping, wearability decisions, and eventual unequip execution stay
  unchanged.
- SPI/gateway cleanup: unused item-info overloads were removed from
  `AgentEquipmentRecommendationPolicy`, `AgentEquipmentDebugReportFormatter`,
  `AgentEquipmentPlanExecutor`, and `AgentEquipmentService`. Remaining
  item-info signatures are active staged seams in the auto-equip/reserve/
  optimizer cluster rather than dead public facades.
- SPI/gateway extraction: `AgentEquipmentOptimizationService` now uses
  `InventoryGateway` recommendation hooks when checking extra/offered
  equipment candidates. Candidate gates and optimizer behavior stay unchanged
  while the larger optimizer metadata cluster remains a later extraction slice.
- SPI/gateway extraction: `AgentEquipmentAutoEquipService` debug/report paths
  now use `InventoryGateway` for item names, cash checks, and weapon-type
  lookups. The auto-equip optimizer still owns the same candidate pools,
  branch ordering, dump shape, and debug reply behavior.
- SPI/gateway extraction: `TradeGateway` now owns static Cosmic trade
  lifecycle calls for start/invite, no-response cancellation, completion, and
  invite-accept visit operations. Trade capability code still carries live
  `Trade` window references as state/tick inputs until the later trade-window
  abstraction slice, but direct static lifecycle calls are gateway-backed.
- SPI/gateway extraction: `AgentEquipmentOptimizerHooks` now exposes
  `InventoryGateway` factories and `AgentEquipmentOptimizationService` uses
  them for optimizer DP metadata. Existing item-info hook factories remain as
  staged compatibility for older optimizer overloads.
- Trade-window reconstruction: `AgentTradeCategoryAnnouncementService`,
  `AgentTradeAllItemsAddedService`, and `AgentTradeMesoAddService` no longer
  import `server.Trade`; they receive narrow chat/meso callbacks from
  `AgentTradeItemAddTickService`. Live trade-window ownership remains in the
  item-add tick layer until the broader trade-window abstraction slice.
- Trade-window reconstruction: `AgentTradeConfirmWaitService` no longer
  imports `server.Trade`; it receives a partner-confirmed callback from
  `AgentTradeTickRuntimeService`. Confirmation timing, bot-recipient auto-done,
  timeout reply, and cancellation behavior remain unchanged.
- Trade-window reconstruction: `AgentTradeCompletionService` no longer imports
  `server.Trade`; it receives partner item and offer snapshots from
  `AgentTradeLifecycleService`. Owner-given equip tracking, completion through
  the trade gateway, delayed thanks replies, and freebie reactions remain
  unchanged.
- SPI/gateway extraction: `AgentBuffService` no longer reaches directly for
  `CosmicAgentServerAdapter`; common-tick, control-chat, and combat-report
  runtime callers pass `InventoryGateway` into buff selection and reporting.
  Buff consumable classification, cheap/max ordering, active/available
  summaries, and debug output remain unchanged.
- SPI/gateway extraction: `AgentPotionService.autopotDebugReport` now receives
  `InventoryGateway` from `AgentChatReportOperationsRuntime`; autopot item-name
  lookup moved to the caller boundary while potion counts, chosen-slot ranking,
  and report text stay unchanged.
- SPI/gateway extraction: `AgentInventoryDialogueReporter.inventorySummary`
  now receives `InventoryGateway` from `AgentChatReportOperationsRuntime`.
  Recovery/buff consumable classification and quest-item filtering no longer
  reach directly for the Cosmic adapter inside the dialogue reporter; inventory
  summary text and category counts stay unchanged.
- SPI/gateway extraction: `AgentMakerService.handleMakeCrystals` now receives
  `InventoryGateway` from `AgentUtilityRuntime`; leftover-to-monster-crystal
  metadata no longer reaches directly for the Cosmic adapter inside the Maker
  service. Maker batch behavior, timings, and replies stay unchanged.
- SPI/gateway extraction: `AgentInventoryNamedItemService` now receives
  `InventoryGateway` from inventory/trade/drop/runtime callers for item names
  and quest-item metadata. Normalized query matching, cached normalized names,
  safe-item filtering, named trade collection, drop-by-name matching, and
  transfer count behavior stay unchanged.
- SPI/gateway extraction: `AgentInventoryCollectionService` now receives
  `InventoryGateway` or explicit quest-item predicates from callers for safe
  bag collection. Inventory/trade/equip-group callers still own category
  selection, priority ordering, grouping, and callback wiring; safe-item
  filtering and slot-order traversal stay unchanged.
- SPI/gateway extraction: `AgentKpqStage5` now receives `InventoryGateway`
  through `AgentPartyQuestHooks` for reward item-name formatting. Stage 5 clear
  detection, reward claiming, claimed-state tracking, reward delta detection,
  fallback item-id wording, and queued reward chat stay unchanged.
- SPI/gateway extraction: `AgentScrollReactionService` now receives
  `InventoryGateway` through `AgentScrollReactionNotificationService` for
  scroll success-rate metadata. Reaction eligibility, delay scheduling,
  streak/load chance scaling, emote/chat/fidget side effects, and cooldown
  behavior stay unchanged.
- SPI/gateway extraction: `AgentInventoryTradeCollectionService` now receives
  `InventoryGateway` through trade collection/runtime callers. Category
  expansion, safe bag collection, named-item fallback, equipped-slot fallback,
  use/ammo/equip grouping, reserved-equip pages, and priority ordering stay
  unchanged while Cosmic inventory metadata lookup remains at the caller
  boundary.
- SPI/gateway extraction: `AgentInventorySellTrashService` now receives
  `InventoryGateway` from Maker/shop/drop-transfer callers. Sell-trash equip
  candidate collection, quest-item safety, self-upgrade and reserved-recipient
  exclusions, protected-stat checks, and item ordering stay unchanged while the
  service no longer reaches directly for the Cosmic adapter.
- SPI/gateway extraction: `AgentInventoryDropService` now receives
  `InventoryGateway` from the transfer/drop boundary. Drop category routing,
  safe-item checks, quest-item filtering, named-item matching, item dropping,
  and legacy reply text stay unchanged while drop behavior no longer reaches
  directly for the Cosmic adapter.
- SPI/gateway extraction: `AgentEquippedSlotTradeService` now receives
  `InventoryGateway` from transfer/runtime callers. Cash-equipped item
  filtering, equipped-slot counts, temporary unequip movement, restore-slot
  tracking, full-bag failure replies, and restoration behavior stay unchanged
  while equipped-slot trade preparation no longer reaches directly for the
  Cosmic adapter.
- SPI/gateway extraction: `AgentAmmoService` now receives `InventoryGateway`
  from potion/supply/mode runtime callers for ammo-share projectile metadata.
  Low-ammo request checks, manual owner request bypasses, donor scoring,
  cooldown/backoff state, map chat, random delays, and supply-share trade
  scheduling stay unchanged while ammo sharing no longer reaches directly for
  the Cosmic adapter.
- SPI/gateway extraction: common tick, active-mode, utility, movement, supply,
  and control-report runtime boundaries now obtain live inventory metadata
  through `AgentInventoryGatewayRuntime` instead of importing
  `CosmicAgentServerAdapter` directly. This keeps the Cosmic inventory adapter
  behind the integration seam while preserving potion, ammo, buff, Maker, shop,
  and mode-start behavior.
- SPI/gateway extraction: dialogue report operations, combat buff-debug
  reporting, scroll-reaction notification, and the default use-item
  classification path now use `AgentInventoryGatewayRuntime` for live inventory
  metadata. Inventory report text, autopot debug text, buff debug lines,
  scroll-reaction decisions, and recovery/buff consumable classification remain
  behavior-preserving.
- SPI/gateway extraction: equipment optimizer/recommendation/reserve/unequip
  live hooks and combat attack metadata now use `AgentInventoryGatewayRuntime`
  for live inventory metadata instead of importing the concrete Cosmic adapter.
  Existing item-info compatibility method signatures remain as staged seams,
  while auto-equip, recommendation, reserve, unequip, infeasible-cleanup, and
  attack route metadata behavior remain unchanged.
- SPI/gateway extraction: packet-emitting capability code for movement
  broadcasts, fidget visuals, combat damage, loot cleanup, navigation debug
  overlays, and airshow visuals now uses `AgentPacketGatewayRuntime` instead of
  importing the concrete Cosmic adapter. Existing packet gateway methods still
  build the same packets and target the same clients/maps, preserving visible
  behavior.
- SPI/gateway extraction: support special-move dispatch now uses
  `AgentCombatGatewayRuntime` for synthetic packet handling. The combat
  capability still builds the same legacy packet bytes, while gateway dispatch
  remains isolated behind the Agent integration boundary.
- SPI/gateway extraction: runtime follow/shop/map-change/movement-only/return
  scroll inventory metadata lookups now use `AgentInventoryGatewayRuntime`.
  This removes the remaining direct concrete inventory adapter imports from
  runtime/capability code while preserving follow setup, shop visit/map-change
  sequencing, movement-only shop ticks, and nearest-town return scroll use.
- SPI/gateway extraction: `AgentTradeInviteGateway` now delegates trade
  start/invite operations to `AgentTradeGatewayRuntime` rather than importing
  the concrete Cosmic adapter. Utility trade invite ordering and chat-command
  behavior stay unchanged.
- Trade-window reconstruction: `AgentManualTradeState` and
  `AgentManualTradeStateRuntime` no longer import `server.Trade`; they keep the
  pending manual trade window as an opaque identity reference. The surrounding
  manual trade services still receive live `Trade` windows for ticking and
  callbacks until the broader trade-window abstraction slice.
- SPI/gateway extraction: `AgentReplyRuntime` no longer imports `PacketCreator`
  for immediate map-chat and whisper replies. Those packets are built and sent
  through `AgentPacketGatewayRuntime`/`PacketGateway`; reply-channel selection,
  sanitization, party fallback, and visible chat behavior stay unchanged.
- SPI/gateway extraction: `MapGateway`/`CosmicMapGateway` now expose the first
  live map-change and ground-point operations through `AgentMapGatewayRuntime`.
  `AgentRespawnRuntime` uses this gateway for respawn-near-leader map changes
  and point-below lookup; respawn ordering, placement fallback, movement reset,
  broadcast, map reply, and face-expression behavior stay unchanged.
- SPI/gateway extraction: `AgentSpawnRuntime` now passes lifecycle spawn
  map-change hooks through `AgentMapGatewayRuntime` instead of a direct
  `forceChangeMap` lambda. Spawn lookup, registration, online placement,
  follow-start callback wiring, and failure logging stay unchanged.
- SPI/gateway extraction: `AgentFollowMapSyncRuntime` now routes its
  cross-map follow `changeMap(map, position)` call through
  `AgentMapGatewayRuntime`. Follow eligibility checks, spawn-position fallback,
  idle-on-ground, and reset ordering stay unchanged.
- SPI/gateway extraction: `AgentOfflineLoadRuntime` now routes map resolution
  and channel/world/map player registration through `AgentMapGatewayRuntime`.
  Offline character loading, disease restore, spawn positioning, rate setup,
  client binding, map visit, and disease task startup stay unchanged.
- SPI/gateway extraction: `CharacterGateway`/`CosmicCharacterGateway` now expose
  live world-player lookup through `AgentCharacterGatewayRuntime`.
  `AgentLeaderSessionRuntime` and `AgentReloginRuntime` use this boundary for
  leader refresh/relogin resolution while preserving the same player-storage
  lookup behavior.
- SPI/gateway extraction: `CombatGateway` now exposes the current server
  timestamp for support special-move packet construction. `AgentSupportSpecialMoveExecutor`
  no longer calls `Server.getInstance()` directly; packet bytes and synthetic
  dispatch behavior stay unchanged.
- SPI/gateway extraction: `CharacterGateway` now exposes stored disease
  restoration for offline Agent loading. `AgentOfflineLoadRuntime` no longer
  reaches directly into server player buff storage; disease restore behavior
  and offline-load ordering stay unchanged.
- SPI/gateway extraction: `CharacterGateway` now exposes live world-character
  lookup by name. `AgentRuntimeRegistry.findUnclaimedOnlineAgentByName` uses
  this boundary while preserving BotClient detection, active-leader exclusion,
  and null behavior.
- SPI/gateway extraction: `AgentOwnershipService` now uses
  `AgentCharacterGatewayRuntime` for online character-by-id/name resolution.
  Ownership checks, automatic same-account registration, DB fallback, and
  denial/registration behavior stay unchanged.
- Trade-window reconstruction: the live `server.Trade` adapter has moved to
  `server.agents.integration.cosmic.CosmicAgentTradeWindow`. Manual trade capability
  code now accepts current-window lookup from its runtime adapter and stays on
  the `AgentTradeWindow` abstraction for invite acceptance, preserving the
  existing visit-trade, full-trade check, greeting, timeout, and completion
  behavior.
- Trade-window reconstruction: manual trade timeout, peer-trade, owner-trade,
  callback, and greeting services now depend on `AgentTradeWindow` instead of
  `server.Trade`. `CosmicAgentTradeWindow` is the live Cosmic adapter for the
  existing `Trade` window while deeper trade lifecycle/item-add services are
  reconstructed in later slices.
- Trade-window reconstruction: `AgentTradeTickService` and
  `AgentTradeTickCallbackService` now dispatch current-window, accept-wait,
  item-add, and confirmation ticks through `AgentTradeWindow`. Runtime still
  unwraps for the deeper item-add/lifecycle services until those slices move.
- Trade-window reconstruction: `AgentTradeItemAddService` and
  `AgentTradeItemAddTickService` now use `AgentTradeWindow` for item add,
  meso add, partner packet recipient lookup, and trade chat. The concrete
  `server.Trade` item/meso calls are isolated in `CosmicAgentTradeWindow`.
- Trade-window reconstruction: `AgentTradeLifecycleService` and
  `AgentTradeLifecycleRuntimeService` now use `AgentTradeWindow` for partner
  item/offer inspection during completion reactions. Live `server.Trade`
  coupling remains only in the server-window adapter and current-trade runtime
  lookup.
- Trade-window reconstruction: `AgentTradeTickRuntimeService.RuntimeCallbacks`
  now supplies `AgentTradeWindow`; `AgentInventoryRuntimeAdapters` owns the live
  `Character.getTrade()` wrapping. The trade capability's production
  `server.Trade` coupling is now isolated to `CosmicAgentTradeWindow`.
- SPI/gateway extraction: `AgentScriptItemActionService` now drops script
  items through `InventoryGateway.dropItem`; direct `InventoryManipulator`
  usage for this plan action is isolated to `CosmicInventoryGateway`. Script
  item lookup, quantity clamping, and slot choice remain unchanged.
- SPI/gateway extraction: `AgentReturnScrollService` now consumes nearest-town
  return scrolls through `InventoryGateway.removeFromSlot`; direct
  `InventoryManipulator` removal is isolated to `CosmicInventoryGateway`.
  Effect lookup, apply-to-Agent behavior, and failure cases remain unchanged.
- SPI/gateway extraction: `AgentTradeItemAddService` now removes items from
  inventory through `AgentInventoryGatewayRuntime` while keeping its existing
  remover test seam. Trade item copies, quantity caps, restore slots, and
  packet sends remain unchanged.
- SPI/gateway extraction: `AgentInventoryDropService` now executes category
  and named-item drops through `InventoryGateway.dropItem`; direct drop
  mutation stays in `CosmicInventoryGateway`. Safe item filtering, quest-item
  protection, matching, and reply wording remain unchanged.
- SPI/gateway extraction: `AgentEquipmentUnequipService` now executes live
  equip moves through `InventoryGateway.moveItem`; direct item-move mutation
  stays in `CosmicInventoryGateway`. Slot selection, cash filtering, and
  unequip messages remain unchanged.
- SPI/gateway extraction: `AgentEquipmentPlanExecutor` now applies optimizer
  equip moves through `InventoryGateway.moveItem`. Equipment order, target
  selection, bag-position validation, and skip behavior remain unchanged.
- SPI/gateway extraction: `AgentEquippedSlotTradeService` now prepares and
  restores equipped-slot trade items through `InventoryGateway.moveItem`.
  Equipped-slot filtering, restore-slot state, failure handling, and restore
  ordering remain unchanged.
- SPI/gateway extraction: `AgentStarterKitService` now grants first-job starter
  kit items through `InventoryGateway.addItem`. Eligibility checks, inventory
  capacity guards, grant order, and post-job-advance follow-up remain
  unchanged.
- SPI/gateway extraction: `AgentKpqStage1` now exchanges KPQ coupons for passes
  through `InventoryGateway.removeById` and `InventoryGateway.addItem`. Coupon
  thresholds, pass grant behavior, event-grid reset, and delivery flow remain
  unchanged.
- SPI/gateway extraction: `LifeGateway`/`CosmicLifeGateway` now own live
  `LifeFactory` monster creation. `AgentAirshowService` uses
  `AgentLifeGatewayRuntime` for trail monster creation while visual trail
  behavior remains unchanged.
- SPI/gateway extraction: `AgentMapDamageProfile` now uses
  `AgentLifeGatewayRuntime` for spawn-template monster stats. Damage/avoid
  profile selection, live mob inclusion, and spawn fallback behavior remain
  unchanged.
- SPI/gateway extraction: `SkillGateway`/`CosmicSkillGateway` now own live
  `SkillFactory` access. `AgentCombatDialogueReporter` uses
  `AgentSkillGatewayRuntime` for combat skill labels while fallback wording
  remains unchanged.
- SPI/gateway extraction: `AgentSkillDialogueReporter` now uses
  `AgentSkillGatewayRuntime` for skill names and beginner-skill lookups.
  Learned-skill tree grouping, beginner skill reporting, beginner-SP math, and
  fallback labels remain unchanged.
- SPI/gateway extraction: `AgentCombatSkillUsePolicy` now uses
  `AgentSkillGatewayRuntime` for skill-effect lookup. Skill-cost affordability
  behavior remains unchanged.
- SPI/gateway extraction: `AgentCombatImmediateTargetPolicy` now uses
  `AgentSkillGatewayRuntime` for cached projectile skill lookup. Immediate
  target eligibility behavior remains unchanged.
- SPI/gateway extraction: `AgentSkillAttackPlanRuntime` now uses
  `AgentSkillGatewayRuntime` for attack skill lookup. Skill attack plan
  readiness, hitbox, target collection, Dragon Roar support gating, and packet
  timing behavior remain unchanged.
- SPI/gateway extraction: `AgentProjectileHitbox` now uses
  `AgentSkillGatewayRuntime` for passive projectile range skill fallback lookup.
  Learned-skill preference, range bonus math, and client hitbox geometry remain
  unchanged.
- SPI/gateway extraction: `AgentPassiveRecoveryPolicy` now uses
  `AgentSkillGatewayRuntime` for passive HP/MP recovery skill-effect lookup.
  Standing-still gating and legacy recovery math remain unchanged.
- SPI/gateway extraction: `AgentNavigationDebugOverlay` now uses
  `AgentSkillGatewayRuntime` for debug-overlay mist-effect lookup. Overlay
  effect fallback order and rendering behavior remain unchanged.
- SPI/gateway extraction: `AgentBuildService` and the Warrior/Bowman/Thief/Mage
  build profiles now use `AgentSkillGatewayRuntime`/`SkillGateway` for build
  skill lookup and max-level lookup. SP assignment, build ordering, respec, and
  AP behavior remain unchanged.
- SPI/gateway extraction: `AgentCombatBuffRuntime` now uses
  `AgentSkillGatewayRuntime`/`SkillGateway` for support-buff skill lookup.
  Party-support and self-buff timing/classification behavior remains unchanged.
- SPI/gateway extraction: `AgentCombatHealRuntime` now uses
  `AgentSkillGatewayRuntime`/`SkillGateway` for cached support-heal skill
  lookup. Heal selection, jump-heal, cooldown, and attack-packet behavior remain
  unchanged.
- SPI/gateway extraction: `AgentMakerService` now uses
  `AgentMakerGatewayRuntime`/`MakerGateway`; live `MakerProcessor` calls are
  isolated in `CosmicMakerGateway`. Crystal creation and trash disassembly
  behavior remain unchanged.
- SPI/gateway extraction: `AgentPartyQuestSyncService` now uses
  `AgentQuestSyncGatewayRuntime`/`AgentQuestSyncGateway` and
  `AgentQuestSyncHandle`; live `server.quest.Quest` lookup and force-action
  calls are isolated in `CosmicQuestSyncGateway`. Party quest start/progress/
  completion sync behavior remains unchanged.
- SPI/gateway extraction: `AgentNavigationPortalService` now uses
  `AgentMapGatewayRuntime`/`MapGateway.enterPortal`; live portal status lookup,
  `Portal.enterPortal(agent.getClient())`, and map/position transition detection
  are isolated in `CosmicMapGateway`. Portal cooldown and navigation-state reset
  behavior remain unchanged.
- SPI/gateway extraction: `AgentTickPreflightRuntime` now uses
  `AgentCharacterGatewayRuntime`/`CharacterGateway.markClientHeartbeat`; live
  `agent.getClient().updateLastPacket()` mutation is isolated in
  `CosmicCharacterGateway`. Heartbeat cadence and preflight behavior remain
  unchanged.
- SPI/gateway extraction: `AgentSessionRuntime` now uses
  `AgentCharacterGatewayRuntime`/`CharacterGateway.disconnect`; live
  `agent.getClient().disconnect(...)` mutation is isolated in
  `CosmicCharacterGateway`. Relog/logout save ordering, delays, and owner-away
  batch logout behavior remain unchanged.
- SPI/gateway extraction: `AgentAttackExecutionProvider` now uses
  `AgentCombatGatewayRuntime`/`CombatGateway.applyAttackEffects`; live
  close-range, ranged, and magic damage-handler dispatch is isolated in
  `CosmicCombatGateway`. Attack planning, route choice, packet-field generation,
  and cooldown behavior remain unchanged.
- SPI/gateway extraction: `AgentShopService` now uses
  `AgentShopGatewayRuntime`/`ShopGateway.sell`; live `Shop.sell` client dispatch
  is isolated in `CosmicShopGateway`. Sell-trash shop selection, item order,
  delays, slot and quantity values, failure detection, and replies remain
  unchanged.
- SPI/gateway extraction: `AgentShopService` now routes rechargeable-ammo
  transactions through `ShopGateway.recharge`; live `Shop.rechargeDirect`
  dispatch is isolated in `CosmicShopGateway`. Stack ranking, recharge cap,
  transaction-result handling, shortfall reports, and timings remain unchanged.
- SPI/gateway extraction: `AgentShopService` now routes fixed-ammo and potion
  transactions through `ShopGateway.buy`; live `Shop.buyDirect` dispatch is
  isolated in `CosmicShopGateway`. Batch and affordable-partial purchase logic,
  transaction-result handling, shortfall reports, and timings remain unchanged.
- SPI/gateway extraction: `AgentPartyLifecycleService` now routes party
  departure through `PartyGateway.leaveCurrentParty`; live `Party.leaveParty`
  client dispatch is isolated in `CosmicPartyGateway`. Same-party checks and
  leave-before-join ordering remain unchanged.
- SPI/gateway extraction: `AgentPartyLifecycleService` now routes leader-party
  creation through `PartyGateway.createAgentParty`; live `Party.createParty`
  dispatch is isolated in `CosmicPartyGateway`. Creation gating, Agent-party
  flag, failure behavior, and leader-party refresh remain unchanged.
- SPI/gateway extraction: `AgentPartyLifecycleService` now routes Agent party
  joining through `PartyGateway.joinAgentParty`; live `Party.joinParty`
  dispatch is isolated in `CosmicPartyGateway`. Leader-party lookup, Agent-party
  flag, success handling, and HP refresh remain unchanged.
- SPI/gateway extraction: `AgentPartyLifecycleService` now routes same-party
  online-state publication through `PartyGateway.publishAgentOnline`; live
  `PartyCharacter` construction and `LOG_ONOFF` dispatch are isolated in
  `CosmicPartyGateway`. Identity checks, channel/map values, and HP refresh
  remain unchanged.
- SPI/gateway extraction: the concrete `server.Trade` wrapper is now
  `integration.cosmic.CosmicAgentTradeWindow`. Production callers obtain
  `AgentTradeWindow` through `TradeGateway.currentWindow`, leaving the generic
  integration package free of `server.Trade` while preserving all trade-window
  behavior and timing.
- SPI/gateway extraction: `AgentReplyRuntime` now routes party-channel delivery
  through `PartyGateway.sendPartyChat`; live party/world-server dispatch is
  isolated in `CosmicPartyGateway`. Sanitization, availability checks, speaker
  identity, and map-chat fallback remain unchanged.
- SPI/gateway extraction: lifecycle and dialogue/LLM party reads now consume
  `AgentPartySnapshot` and `AgentPartyMemberSnapshot` from
  `PartyGateway.snapshot`. Live `Party`/`PartyCharacter` traversal is isolated
  in `CosmicPartyGateway`; identity checks, member order, relation resolution,
  situation text, and lifecycle refresh behavior remain unchanged.
- SPI/gateway extraction: follow targeting, trade recipient resolution, and
  party-quest sync now use `PartyGateway.hasParty`/`onlineMembers`. Live online
  party-member collection is isolated in `CosmicPartyGateway`; ordering,
  filtering, duplicate handling, and BotClient detection remain unchanged.
- SPI/gateway extraction: repeated live Agent-character classification now uses
  `CharacterGateway.isAgentCharacter`. The concrete `BotClient` type check is
  isolated in `CosmicCharacterGateway`; registry, loot, quest, dialogue, trade,
  and runtime filtering behavior remains unchanged.
- SPI/gateway extraction: spawn and offline-load paths now obtain headless
  clients and create/load backing characters through `AgentClientGateway`.
  Concrete `BotClient`, `BotCreator`, and `Character.loadCharFromDB` calls are
  isolated in `CosmicAgentClientGateway`; account binding, disease restore,
  rates, registration, map placement, and startup ordering remain unchanged.
- SPI/gateway extraction: `AgentMakerService` now uses `AgentClientGateway` for
  client presence/locking and passes Agent characters to `MakerGateway`.
  Concrete client access remains in the Cosmic client/Maker gateways; no-client
  abort, contention retry, lock release, operation status, and timing remain
  unchanged.
- SPI/gateway extraction: lifecycle/session world and channel lookup plus
  support-move client presence now route through `AgentClientGateway`. Concrete
  `Character.getClient()` access remains in `CosmicAgentClientGateway`; spawn,
  relog, and combat-dispatch behavior remains unchanged.
- Capability ownership: `AgentPartyLifecycleService` moved from generic runtime
  to `capabilities.party`. Spawn, placement, and messenger callers retain the
  same join entry point and party behavior; only package ownership changed.
- Capability ownership: `AgentFollowAnchorService` moved from generic runtime
  to `capabilities.follow`. Combat, navigation, target snapshot, and plan
  execution callers retain the same resolver methods and fallback behavior.
- Capability ownership: `AgentFollowTargetCandidateService` moved from generic
  runtime to `capabilities.follow`. Candidate ordering, party/sibling filters,
  and duplicate suppression remain unchanged.
- Capability ownership: `AgentFollowTargetResolutionService` moved from generic
  runtime to `capabilities.follow`. Exact/prefix matching, ambiguity behavior,
  and all leader-facing messages remain unchanged.
- Capability ownership: `AgentFollowTargetCommandService` moved from generic
  runtime to `capabilities.follow`. Entry filtering, reply timing, auto-equip,
  potion-share, and follow-start ordering remain unchanged.
- Capability ownership: `AgentFollowMapSyncService` moved from generic runtime
  to `capabilities.follow`; the runtime hook adapter remains in place. Cross-map
  gating, spawn selection, map-change ordering, and reset behavior are unchanged.
- Capability ownership: `AgentFollowTargetPositionService` moved from generic
  runtime to `capabilities.follow`. Ground/rope/swim target selection, region
  clamping, foothold fallback, and thresholds remain unchanged.
- Capability ownership: `AgentFollowIdleMovementService` moved from generic
  runtime to `capabilities.follow`. Fast-path gates, recheck cadence, debug and
  stuck-state updates, and distance behavior remain unchanged.
- Capability ownership: `AgentFollowOpportunityTickService` moved from generic
  runtime to `capabilities.follow`. Mode, AI, climb, map, and range gates plus
  target fallback and combat callback behavior remain unchanged.
- Capability ownership: `AgentShopVisitTickService` moved from generic runtime
  to `capabilities.shop`. Visit gating, delay consumption, target movement, and
  result behavior remain unchanged.
- Capability ownership: `AgentTransferCommandService` moved from generic runtime
  to `capabilities.trade`. Parsing, callback arguments, error handling, and
  leader-facing success text remain unchanged.
- Capability ownership: `AgentTransferService` moved from generic runtime to
  `capabilities.trade`. Validation, authorization, registry handoff, lifecycle
  ordering, delayed greeting, and return behavior remain unchanged.
- Capability ownership: `AgentReturnScrollService` moved from generic runtime
  to `capabilities.supplies`. Item selection, effect application, failure cases,
  and one-scroll consumption remain unchanged.
- Capability ownership/SPI: `AgentTradeWindowTickService` moved from generic
  runtime to `capabilities.trade` and reads the abstract window through
  `TradeGateway.currentWindow`. Trade gating and physics-only tick behavior
  remain unchanged.
- Capability ownership/naming: autopot cleanup moved from
  `AgentAutopotRuntimeCleanupService` to
  `capabilities.supplies.AgentAutopotCleanupService`. Alert reset and keybinding
  normalization behavior remain unchanged.
- Capability ownership: `AgentMonsterControlService` moved from generic runtime
  to `capabilities.combat`. Controlled-monster release behavior and common-tick
  invocation remain unchanged.
- Capability ownership: `AgentDeathTickService` moved from generic runtime to
  `capabilities.combat`. Dead-state timing and respawn map/HP/position/reset/
  broadcast/dialogue/emote ordering remain unchanged.
- Capability ownership: `AgentRecoveryTickService` moved from generic runtime
  to `capabilities.recovery`. Shop suppression, follow/party/target ordering,
  and short-circuit behavior remain unchanged.
- Capability ownership: `AgentFormationService` moved from generic runtime to
  `capabilities.movement`. Formation state storage, leader/default resolution,
  every offset formula and random range, and live offset application remain
  unchanged.
- Capability ownership: `AgentFormationCommandService` moved from generic
  runtime to `capabilities.movement`. Command matching, legacy aliases,
  help/status text, snap handling, formation mutation, offset application, and
  reply routing remain unchanged.
- Capability ownership: `AgentFormationRuntime` moved from generic runtime to
  `capabilities.movement`. Default formation construction, state lookup/write,
  null-leader behavior, and offset application remain unchanged.
- Capability state ownership: `AgentFormationOffsetState` and
  `AgentFormationStateRuntime` moved from generic runtime to
  `capabilities.movement`. Offset defaults, reads/writes, runtime-entry field
  ownership, target snapshot offsets, and path logging remain unchanged.
- Capability ownership: `AgentFinalMovementTailService` moved from generic
  runtime to `capabilities.movement`. Final movement target and AI-tick
  forwarding remain unchanged.
- Capability ownership: `AgentActionLockPhysicsService` moved from generic
  runtime to `capabilities.movement`. Cooldown gating and movement-phase branch
  selection remain unchanged.
- Capability ownership: `AgentIdlePhysicsService` moved from generic runtime to
  `capabilities.movement`. Active-mode gates, movement-phase selection, stance
  correction, and broadcast conditions remain unchanged.
- Capability ownership: `AgentMovementPhaseService` moved from generic runtime
  to `capabilities.movement`. Climb/swim/air/ground branch ordering and callback
  arguments remain unchanged.
- Capability ownership: `AgentMovementTickService` moved from generic runtime
  to `capabilities.movement`. The movement-core phase order and every callback
  argument remain unchanged.
- Capability ownership: `AgentStuckDetectionService` moved from generic runtime
  to `capabilities.movement`. Performance monitoring, cooldown and progress
  state updates, reset gates, thresholds, and unstuck dispatch remain
  unchanged.
- Capability ownership: `AgentPositionService` moved from generic runtime to
  `capabilities.movement`. Null handling and inclusive per-axis proximity checks
  remain unchanged.
- Capability ownership/SPI: `AgentMapEnvironmentService` moved from generic
  runtime to `capabilities.movement`; swim-map reads now pass through
  `MapGateway.isSwimMap` and `CosmicMapGateway`, preserving null handling and the
  exact Cosmic map flag.
- Capability ownership: `AgentMovementOnlyTickService` moved from generic
  runtime to `capabilities.movement`. Its complete idle/follow/recovery/map/
  shop/movement short-circuit order and callback arguments remain unchanged.
- Capability ownership: `AgentMovementOnlyMapChangeService` moved from generic
  runtime to `capabilities.movement`. Map tracking, grounding, teleport/reset,
  broadcast, shop, and status callback ordering remain unchanged.
- Capability ownership: `AgentMapTransitionService` moved from generic runtime
  to `capabilities.movement`. Grounding and post-transition dispatch ordering,
  state mutations, and callback arguments remain unchanged.
- Capability ownership: `AgentGrindNoTargetFallbackService` moved from generic
  runtime to `capabilities.combat`. State clearing, air-movement branches,
  wander/patrol target selection, movement dispatch, and result semantics
  remain unchanged.
- Capability ownership: `AgentGrindTargetPositionService` moved from generic
  runtime to `capabilities.combat`. Loot validation and convenience scoring,
  retry suppression, region/patrol wander selection, random bounds, and
  fallback target behavior remain unchanged.
- Capability ownership: `AgentStandaloneMoveTargetTickService` moved from
  generic runtime to `capabilities.movement`. Grounding, profile refresh,
  target lookup, and movement dispatch ordering remain unchanged.
- Capability ownership: `AgentScriptedMoveCombatTickService` moved from generic
  runtime to `capabilities.combat`. Script/AI gating, action-window cleanup,
  attack short-circuit, movement fallback, and result semantics remain
  unchanged.
- Capability ownership: `AgentAnchoredFarmTickService` moved from generic
  runtime to `capabilities.combat`. Anchor validation, attack/idle branches,
  target-state updates, precise movement, and callback ordering remain
  unchanged.
- Capability ownership: `AgentGrindModeDispatchService` moved from generic
  runtime to `capabilities.combat`. Mode gating, fall-through target retention,
  and grind callback result propagation remain unchanged.
- Capability ownership: `AgentAnchoredFarmModeTickService` moved from generic
  runtime to `capabilities.combat`. Anchor gating, callback arguments, and
  consumed/fall-through semantics remain unchanged.
- Capability ownership: `AgentLocalAttackMoveWindowService` moved from generic
  runtime to `capabilities.combat`. Move-window timing bands and settle/clear
  policy remain unchanged; runtime still supplies movement-config values.
- Capability ownership: `AgentRecoveryTeleportService` moved from generic
  runtime to `capabilities.recovery`. Recovery gates, distance/multiplier math,
  grounding fallback, and teleport/reset/broadcast ordering remain unchanged.
- Capability ownership: `AgentLeaderSafetyService` moved from generic runtime
  to `capabilities.recovery`. Inactive-leader timing and safe-mode policy,
  town-return and clustering behavior, active-leader restoration, and callback
  ordering remain unchanged.
- Capability ownership: `AgentOwnerlessTickService` moved from generic runtime
  to `capabilities.movement`. Follow clearing, grounding, standalone movement,
  and idle branch order remain unchanged.
- Command ownership: `AgentCommandModeService` moved from generic runtime to
  `commands`. Null/guard handling and preparation callback ordering remain
  unchanged.
- Command ownership: recruit and dismiss command parsing moved from generic
  runtime into `commands`. Aliases, matching, lifecycle callback arguments,
  replies, and handled semantics remain unchanged; lifecycle execution remains
  in runtime.
- Plan ownership: `AgentScriptTaskQueueService` moved from generic runtime to
  `plans`. Queue state mutation, epoch behavior, task construction/order, and
  query behavior remain unchanged.
- Plan ownership: `AgentScriptTaskStartService` moved from generic runtime to
  `plans`. Every task-type dispatch and callback argument remains unchanged.
- Plan ownership: `AgentScriptTaskCompletionService` moved from generic runtime
  to `plans`. Completion predicates, map/distance checks, and immediate task
  completion behavior remain unchanged.
- Plan ownership: `AgentScriptTaskExecutionService` moved from generic runtime
  to `plans`. Task start/completion composition, target resolution, capability
  routing, and item-drop delegation remain unchanged.
- Plan ownership: `AgentScriptTaskTickService` moved from generic runtime to
  `plans`. Activation/start/completion/clear loop ordering and early returns
  remain unchanged.
- Capability read-model ownership: `AgentTargetSnapshot` and its capture service
  moved from generic runtime to `capabilities.movement`. All target sources,
  point-copy behavior, precedence, labels, and fallback semantics remain
  unchanged.
- Capability ownership: tick-time leader-motion observation moved from the
  mixed runtime maintenance service to
  `capabilities.follow.AgentFollowMotionObservationService`; null guards and
  owner-step delta state updates remain unchanged.
- Capability ownership: precise navigation-target marker maintenance moved from
  generic runtime into `capabilities.navigation.AgentNavigationPreciseTargetService`;
  precise-move and active-edge predicates remain unchanged.
- Capability ownership: remaining target/farm/patrol maintenance moved from the
  mixed runtime service into
  `capabilities.movement.AgentMovementTargetMaintenanceService`. Same-map,
  arrival-distance, precise-target, and patrol cleanup behavior remain
  unchanged; the mixed runtime service is removed.
- Capability state ownership: the concrete `AgentAoeRepositionState` moved from
  generic runtime to `capabilities.combat`; point-copy, deadline, clear, and
  expired/arrived behavior remain unchanged.
- Capability state ownership: the concrete `AgentBreakoutState` moved from
  generic runtime to `capabilities.combat`; direction, deadline, inclusive
  expiry, and clear behavior remain unchanged.
- Capability state ownership: the concrete `AgentDegenerateAttackState` moved
  from generic runtime to `capabilities.combat`; latch defaults and mark/clear
  behavior remain unchanged.
- Capability state ownership: the concrete `AgentRetreatHoldState` moved from
  generic runtime to `capabilities.combat`; point copies, active deadline,
  null-reset, and clear behavior remain unchanged.
- Capability state ownership: the concrete `AgentGrindWanderState` moved from
  generic runtime to `capabilities.combat`; zero state, sign normalization, and
  clear behavior remain unchanged.
- Capability state ownership: the concrete `AgentGrindTargetState` moved from
  generic runtime to `capabilities.combat`; target and next-search fields,
  defaults, setters, and independent clears remain unchanged.
