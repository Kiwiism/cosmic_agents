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

Initial reconstruction order:

1. Runtime shell and registry.
2. Command parser/router boundaries. Initial parser and GM command bridge completed; old bot runtime remains underneath.
3. Chat/reply/dialogue boundaries. Reply queue primitive has moved to Agent commands; named random dialogue pools and away/logout/support/heal/buff/proactive/SP-variant/help/equipment-recommendation/fame/skill-report/trade-choice/pending-action-cancel/movement-stats-unavailable/supply-shortage/no-items/grind-start fixed prompt lines and buff-consumable mode labels have moved to Agent dialogue catalog; movement/follow/fidget, supply-request/direct supply, query/toggle, support/heal/buff toggle/replies, logout/relog/away session request and confirmation normalization, respec, upgrade-request, report/debug, trade/drop/item and pending drop-choice, trade-invite/shop/maker utility, equipment/autoequip, greeting/fame/self-target, build/job/AP/SP classification/replies, SP variant labels, pending chat action labels, trade category labels, skill-tree choice resolution, AP-build choice resolution, and job advancement resolution have moved to Agent dialogue classifiers/resolvers; session request routing/reply selection, pending chat-action branching/replies, away prompt/choice routing/replies, AFK welcome-back routing/reply selection, chat toggle routing/replies, buff query routing, respec routing, equipment chat routing/replies, recommended-gear reply selection, supply request routing/outcome replies, social/fame command routing, fame outcome routing, movement/greeting chat routing/reply selection, utility chat routing/reply selection, SP/AP build selection routing/replies, help/report-query routing, transfer command/item-query routing/result routing, job-advancement routing/reply selection, and skill-report/skill-tree-choice routing have moved to Agent dialogue flow orchestration; stats/range/build/crit/EXP/supply/meso/scroll/movement report formatting and basic stats/build/range/crit/EXP/meso/skill/movement/inventory-slot/inventory-summary/potion-count/buff-summary/autopot-debug/skill-buff-debug/combat-debug/grind-start report builders, range stat labels, potion type labels, fame reply formatting, no-items category formatting, buff debug-state formatting, skill-buff age/status formatting, combat debug stat formatting, grind-start low-supply formatting, build prompt wording, drop-or-trade prompt formatting, offline welcome-back map fallback formatting, selected catalog-template formatting, AP-build profile labels/job-display/skill-tree/learned-skill formatting, and item query normalization have moved to Agent dialogue; thin bot-side classifier and basic report/drop-or-trade/basic reply-pool pass-through wrappers have been removed; `BotChatManager` now primarily retains top-level chat-flow routing and bot-runtime side-effect callbacks.
4. Movement and navigation.
5. Combat.
6. Loot and supplies.
7. Inventory, equipment, trade, shop, and build.
8. Quest, NPC, and PQ.
9. Dialogue, social, and LLM.
10. SPI/Cosmic gateway attachment.
11. Delete old bot-shaped runtime once all callers use Agent modules.
