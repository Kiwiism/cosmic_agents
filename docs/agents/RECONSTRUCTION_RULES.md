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
