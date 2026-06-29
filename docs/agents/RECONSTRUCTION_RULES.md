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
  `BotChatRuntime` remains only as a temporary adapter from `BotEntry` to the
  Agent chat orchestrator context.
- Top-level chat orchestrator context ownership now lives in
  `AgentBotChatOrchestratorContext`; the old bot-side context class has been
  removed, and `BotChatRuntime` only creates the temporary Agent integration
  adapter.
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
- `BotChatRuntime` has been removed; `BotChatManager` is now the only bot-side
  chat compatibility facade and delegates directly to `AgentChatRuntime` with
  `AgentBotChatOrchestratorContext`.
- Immediate Agent integration reply delivery now routes through
  `AgentBotReplyRuntime.replyNow`, `visibleSayNow`, and `sayPartyNow`; scattered
  Agent integration facades no longer call `BotManager.botReply`/visible
  delivery directly. `AgentBotReplyRuntime` remains the temporary adapter to the
  legacy BotManager packet-delivery methods.
- Loot/gear offer owner-directed replies, queued offer prompts, estimated prompt
  delay reads, and delayed offer actions now enter through
  `AgentBotOfferRuntime`; `BotOfferManager` no longer reaches directly into the
  lower-level reply or scheduler runtime for offer-owned flows. The remaining
  bot-side map-only `botSay(Character, ...)` branch is intentionally unchanged
  until map-only visible delivery has an exact Agent adapter.
- AP build confirmation replies now enter through `AgentBotBuildRuntime`; the
  build manager no longer reaches directly into the lower-level reply runtime
  for AP-build selection confirmation, but it still owns the legacy AP
  assignment behavior for this reconstruction stage.
- Maker batch command replies and delayed batch steps now enter through
  `AgentBotMakerRuntime`; `BotMakerManager` no longer reaches directly into the
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
  enter through `AgentBotShopRuntime`; `BotShopManager` no longer reaches
  directly into the lower-level reply or scheduler runtime for shop-owned
  flows. Map-only resupply/shop chatter remains on the legacy visible-say path
  until exact map-visible delivery has an Agent adapter.
- Ammo-share donor selection delays and delayed transfer callbacks now enter
  through `AgentBotAmmoRuntime`; `BotAmmoManager` no longer reaches directly
  into the lower-level scheduler runtime for ammo-owned timing. Visible ammo
  request/offer chat remains unchanged on the legacy map-visible say path.
- Potion-share donor selection delays, low-supply fallback delay, and delayed
  transfer callbacks now enter through `AgentBotPotionRuntime`;
  `BotPotionManager` no longer reaches directly into the lower-level scheduler
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
  `AgentBotBuildStatusRuntime.checkBuildStatus`; `BotBuildManager` and
  `BotStarterKitManager` no longer call the broad chat-status facade directly
  for job/level build status prompts.
- Gear-offer idle gating now enters through
  `AgentBotOfferRuntime.isOwnerIdleForOffer`; `BotOfferManager` no longer
  reaches directly into the broad chat-status facade for offer prompt checks.
- Fidget idle gating now enters through
  `AgentBotFidgetRuntime.isLeaderIdleForFidget`; `BotFidgetManager` no longer
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
  `AgentBotOfferRuntime`; `BotOfferManager` no longer reads or clears the
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
  replies now enter through `AgentBotOfferRuntime`; `BotOfferManager` no longer
  calls `BotManager.botSay` directly for offer-owned reply delivery, while the
  same reply channel, random reply pool, and delay behavior remain intact.
- Ammo low-supply request and ammo-donor offer visible replies now enter
  through `AgentBotAmmoRuntime`; `BotAmmoManager` no longer calls
  `BotManager.botSay` directly for ammo-owned reply delivery, while the same
  random reply pools and transfer timing remain intact.
- Potion grind-stop warnings, low-supply requests, no-qualified-donor
  deflections, and donor offer visible replies now enter through
  `AgentBotPotionRuntime`; `BotPotionManager` no longer calls
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
  `BotShopManager` no longer calls `BotManager.botSay` directly for shop-owned
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
  BotFidgetManager social fidget gates, BotCombatManager buff/heal/ammo gates,
  BotPotionManager share/low-pot gates, BotNavigationManager follow/grind
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
  BotOfferManager auto-accept replies keep BotEntry as the temporary backing
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
  active-task activation/completion, scripted local-combat checks, BotMakerManager
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
  `AgentBotOfferStateRuntime`; BotManager offer-recipient checks, BotOfferManager
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
  BotShopManager resupply/sell-trash visit setup, approach delay, sequence
  activation, stuck fallback, sequence validation, scheduled-step guard, abort,
  and cleanup keep BotEntry as the temporary backing store but no longer read or
  write shop visit fields directly in production. Shop approach graph profile
  lookup now reads through `AgentBotMovementStateRuntime`, and delayed abort
  identity reads through `AgentBotRuntimeIdentityRuntime`.
- Offer request/proactive-offer state now enters through
  `AgentBotOfferStateRuntime`; BotOfferManager owner upgrade request memory,
  proactive shared-loot offer checks, accepted/declined offer callbacks, sibling
  recipient scans, and reserved-offer recipient resolution keep BotEntry as the
  temporary backing store but no longer read `requestedUpgradeItemIds`,
  `proactiveUpgradeOffers`, `owner`, or `bot` directly in production.
- Potion sharing and passive recovery gates now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotMovementStateRuntime`;
  BotPotionManager owner lookup, donor bot selection, delayed low-supply
  replies, transfer donor identity, and standing-still recovery checks keep
  BotEntry as the temporary backing store but no longer read `owner`, `bot`,
  `inAir`, `climbing`, or `moveDir` directly in production.
- Ammo sharing identity now enters through `AgentBotRuntimeIdentityRuntime`;
  BotAmmoManager low-ammo request owner lookup, owner-request sharing, sibling
  donor scans, and delayed transfer donor identity keep BotEntry as the
  temporary backing store but no longer read `owner` or `bot` directly in
  production.
- AP build assignment identity now enters through
  `AgentBotRuntimeIdentityRuntime`; BotBuildManager set-build confirmation and
  immediate AP assignment keep BotEntry as the temporary backing store but no
  longer read `bot` directly in production.
- Maker automation bot identity now enters through
  `AgentBotRuntimeIdentityRuntime`; BotMakerManager crystal creation,
  disassembly, batch start, and delayed batch step checks keep BotEntry as the
  temporary backing store but no longer read `bot` directly in production.
- Scroll reaction bot identity now enters through
  `AgentBotRuntimeIdentityRuntime`; BotScrollReactionManager range filtering,
  delayed reaction eligibility, and emote side effects keep BotEntry as the
  temporary backing store but no longer read `bot` directly in production.
- Fidget bot identity and movement profile reads now enter through
  `AgentBotRuntimeIdentityRuntime` and `AgentBotMovementStateRuntime`;
  BotFidgetManager tick eligibility, fidget origin capture, walk-step
  calculations, grounded execution, diagonal/sideways direction selection, and
  prone visual broadcast keep BotEntry as the temporary backing store but no
  longer read `bot` or `movementProfile` directly in production.
- Fidget movement/nav gate state now enters through
  `AgentBotMovementStateRuntime` and `AgentBotNavigationDebugStateRuntime`;
  BotFidgetManager social fidget eligibility, active fidget eligibility,
  airborne/climb dispatch, air-steer movement intent, grounded sideways
  movement intent, and prone visual facing keep BotEntry as the temporary
  backing store but no longer read or write `inAir`, `climbing`, `navEdge`,
  `downJumpPending`, `moveDir`, or `facingDir` directly in production.
- Fidget state-machine fields now enter through `AgentBotFidgetStateRuntime`;
  BotFidgetManager fidget mode/trigger, timers, origin position, spam-air-steer,
  jump/sideways direction, crouch checks, visual cooldown, and idle/speed-roll
  scheduling keep BotEntry as the temporary backing store but no longer read or
  write fidget fields directly in production. `BotFidgetMode` and
  `BotFidgetTrigger` were moved to public enum files with the same values so
  the Agent adapter can keep a typed boundary without changing behavior.
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
  `AgentBotRuntimeIdentityRuntime`; BotCommandParser keeps the same targeted
  command matching behavior but no longer reads the bot character directly from
  BotEntry in production.
- Starter-kit job advancement identity now enters through
  `AgentBotRuntimeIdentityRuntime`; BotStarterKitManager preserves job-change,
  starter-kit grant, auto-equip, and build-status ordering while no longer
  reading bot or leader characters directly from BotEntry in production.
- KPQ coupon-target loot eligibility now enters through `AgentBotPqRuntime`;
  BotLootEligibility preserves coupon/pass/rice-cake and quest-item filtering
  behavior while no longer reading KPQ coupon target state directly from
  BotEntry in production.
- KPQ grind-requirement stage reads now enter through `AgentBotPqRuntime`;
  BotPqHooks preserves stage-1 grind gating while no longer reading KPQ state
  directly from BotEntry in production.
- KPQ stage-5 reward-claim state now enters through `AgentBotPqRuntime`;
  BotKpqStage5 preserves reward-claim and announcement behavior while no
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
  BotKpqStage1 preserves stage transitions, coupon target assignment, progress
  reporting, pass exchange, pass delivery, and reset behavior while no longer
  reading or writing KPQ or script runtime fields directly on BotEntry in
  production.
- Fallback movement identity, map, movement-profile, and movement-gate reads
  now enter through `AgentBotRuntimeIdentityRuntime`,
  `AgentBotMovementStateRuntime`, and `AgentBotMovementPhysicsStateRuntime`;
  BotFallbackMovementManager preserves rope attach/jump, swim jump-up,
  down-jump, ledge walk-off, and fallback jump behavior while no longer reading
  BotEntry runtime fields directly in production.

Initial reconstruction order:

1. Runtime shell and registry.
2. Command parser/router boundaries. Initial parser and GM command bridge completed; old bot runtime remains underneath.
3. Chat/reply/dialogue boundaries. Reply queue primitive has moved to Agent commands; named random dialogue pools and away/logout/support/heal/buff/proactive/SP-variant/help/equipment-recommendation/fame/skill-report/trade-choice/pending-action-cancel/movement-stats-unavailable/supply-shortage/no-items/grind-start fixed prompt lines and buff-consumable mode labels have moved to Agent dialogue catalog; movement/follow/fidget, supply-request/direct supply, query/toggle, support/heal/buff toggle/replies, logout/relog/away session request and confirmation normalization, respec, upgrade-request, report/debug, trade/drop/item and pending drop-choice, trade-invite/shop/maker utility, equipment/autoequip, greeting/fame/self-target, build/job/AP/SP classification/replies, SP variant labels, pending chat action labels, trade category labels, skill-tree choice resolution, AP-build choice resolution, and job advancement resolution have moved to Agent dialogue classifiers/resolvers; session request routing/reply selection, pending chat-action branching/replies, away prompt/choice routing/replies, AFK welcome-back routing/reply selection, chat toggle routing/replies, buff query routing, respec routing, equipment chat routing/replies, recommended-gear reply selection, supply request routing/outcome reply selection, social/fame command routing, fame outcome routing/reply selection, movement/greeting chat routing/reply selection, utility chat routing/reply selection, SP/AP build selection routing/replies, help/report-query routing/help-line ownership, transfer command/item-query/weird-transfer routing/result routing, job-advancement routing/reply selection, skill-report/skill-tree-choice routing/model ownership, top-level chat route ordering, reply runtime ownership, Agent-owned bot reply adapter ownership, Agent-owned bot scheduler adapter ownership, Agent-owned bot status-state adapter ownership, Agent-owned recommended-gear cooldown state adapter ownership, Agent-owned AFK/offline return side-effect adapter ownership, Agent-owned gear-suggestion action adapter ownership, Agent-owned report offer action adapter ownership, Agent-owned supply report data adapter ownership, Agent-owned character report data adapter ownership, Agent-owned inventory report data adapter ownership, Agent-owned skill report data adapter ownership, Agent-owned build/status check adapter ownership, Agent-owned active-mode side-effect adapter ownership, Agent-owned range report data adapter ownership, Agent-owned movement report data adapter ownership, Agent-owned combat/buff report data adapter ownership, Agent status-state runtime ownership, Agent status-check, active-mode preparation, gear-suggestion cooldown, offline-return announcement, AFK-return announcement, and AFK tick callback orchestration ownership, Agent report scheduling runtime ownership, Agent status/report scheduling bridge ownership, Agent report action construction ownership, Agent-owned report operation adapter ownership, Agent-owned report delivery adapter ownership, direct report callback-operation wiring ownership, help report queueing ownership, single-line and multi-line report queueing ownership, skill-report decision orchestration ownership, recommended-gear report orchestration and reply selection ownership, report bridge ownership, report callback scheduling ownership, pending-action state/callback bridge ownership, session/relog/logout/away side-effect bridge ownership, manual supply/request-upgrade bridge ownership, async transfer/item-query bridge ownership, social/fame bridge ownership, toggle/buff/respec control bridge ownership, Agent-owned control callback adapter ownership, equipment chat bridge ownership, Agent-owned equipment callback adapter ownership, utility chat bridge ownership, build/AP/SP/job-advance bridge ownership, status/welcome-back bridge ownership, movement/greeting bridge ownership, same-package reply/status runtime wiring, PQ reply-runtime wiring, handled-state runtime bridge ownership, and direct orchestrator-context adapter wiring have moved to Agent dialogue flow orchestration; stats/range/build/crit/EXP/supply/meso/scroll/movement report formatting and basic stats/build/range/crit/EXP/meso/skill/movement/inventory-slot/inventory-summary/potion-count/buff-summary/autopot-debug/skill-buff-debug/combat-debug/grind-start report builders, range stat labels, potion type labels, fame reply formatting, no-items category formatting, buff debug-state formatting, skill-buff age/status formatting, combat debug stat formatting, grind-start low-supply formatting, build prompt wording, drop-or-trade prompt formatting, offline welcome-back map fallback formatting, selected catalog-template formatting, AP-build profile labels/job-display/skill-tree/learned-skill formatting, and item query normalization have moved to Agent dialogue; thin bot-side classifier, basic report/drop-or-trade/basic reply-pool pass-through wrappers, report helper shims, BotChatManager queue/status shims, and `BotChatReplyRuntime` have been removed; `AgentBotReplyRuntime`, `AgentBotSchedulerRuntime`, `AgentBotStatusRuntime`, `AgentChatStatusRuntime`, `AgentChatReportRuntime`, `BotChatRuntime`, `BotChatOrchestratorContext`, `BotChatReportRuntime`, `BotChatPendingActionRuntime`, `BotChatSessionRuntime`, `BotChatSupplyRuntime`, `BotChatTransferRuntime`, `BotChatSocialRuntime`, `BotChatControlRuntime`, `BotChatEquipmentRuntime`, `BotChatUtilityRuntime`, `BotChatBuildRuntime`, `BotChatStatusRuntime`, and `BotChatMovementRuntime` are temporary adapters from Agent chat orchestration/reply/report/scheduling/status-state/pending-action/session/supply/transfer/social/control/equipment/utility/build/status/movement runtime to bot runtime side effects; `BotChatManager` is now only a legacy compatibility facade over `BotChatRuntime`.
4. Movement and navigation.
5. Combat.
6. Loot and supplies.
7. Inventory, equipment, trade, shop, and build.
8. Quest, NPC, and PQ.
9. Dialogue, social, and LLM.
10. SPI/Cosmic gateway attachment.
11. Delete old bot-shaped runtime once all callers use Agent modules.
