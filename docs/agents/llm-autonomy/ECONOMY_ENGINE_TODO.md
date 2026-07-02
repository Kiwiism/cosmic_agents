# Economy Engine TODO

Future implementation backlog for the portable economy engine.

The economy engine should be separate from the Cosmic server runtime and accessed
through validated capabilities. The LLM may propose economy actions, but it must
not directly mutate inventory, mesos, shops, trades, or prices.

## Current State

Available preparation:

- Static item catalog.
- Static drop source catalog.
- Static NPC shop catalog.
- Derived item source index.
- Derived resupply catalog.
- Economy schema notes.
- Plan card system design.

Missing runtime pieces are listed below.

## 1. Runtime Market Model

- [ ] Implement `MarketItemState`.
  - item id/name/category.
  - NPC base price.
  - observed lowest/median/highest prices.
  - recent trade/listing volume.
  - observed supply count.
  - observed demand score.
  - price confidence.
  - last observed timestamp.
- [ ] Maintain one market state per `worldId + itemId`.
- [ ] Merge static catalog facts with live observations.
- [ ] Mark confidence levels:
  - `unknown`
  - `few-samples`
  - `observed`
  - `stable`
  - `volatile`
  - `manual`
- [ ] Expose read-only query API:
  - `getMarketItemState(itemId, worldId)`
  - `getMarketSummary(itemId, worldId)`
  - `findKnownMarketItems(filter)`

## 2. Dynamic Price Tracking

- [ ] Implement `PriceObservation`.
  - item id.
  - source type: `npc_shop`, `fm_shop`, `trade`, `merchant`, `manual`.
  - observed price.
  - quantity.
  - seller/shop identity where safe.
  - map/channel/FM room.
  - observed by agent id.
  - timestamp.
- [ ] Store observations in an append-only log or DB table.
- [ ] Build compacted summaries:
  - min/median/max over 1h, 24h, 7d.
  - moving average.
  - volatility.
  - trend.
  - confidence.
- [ ] Add retention/compaction policy for 30-day uptime.
- [ ] Reject or downweight suspicious outliers.

## 3. Agent Buy/Sell/Hold Decision Engine

- [ ] Implement `EconomyDecision`.
  - action: `buy`, `sell`, `hold`, `farm`, `trade`, `vendor`, `stash`.
  - item id.
  - quantity.
  - reason.
  - max buy price.
  - min sell price.
  - urgency.
  - confidence.
- [ ] Inputs:
  - agent profile.
  - current plan cards.
  - current/future quest requirements.
  - inventory.
  - mesos.
  - market price.
  - NPC price.
  - drop difficulty.
  - item rarity.
  - party/owner needs.
- [ ] First policy rules:
  - Hold item if needed for current quest.
  - Hold configured reserve for future quest/material items.
  - Vendor low-value junk if inventory pressure is high.
  - Sell marketable items only above minimum policy price.
  - Buy consumables only while keeping minimum meso reserve.
  - Farm instead of buy if market price exceeds farm threshold.
- [ ] Expose explainable decision output for logs and LLM summaries.

## 4. Free Market / Player Shop Search

- [ ] Implement FM scan plan card.
- [ ] Add capability to navigate to FM.
- [ ] Add capability to enumerate rooms/shops.
- [ ] Add capability to inspect selected shops and return structured listings.
- [ ] Record price observations from listings.
- [ ] Compare listing price against market model and agent policy.
- [ ] Rate-limit scanning per agent and globally.
- [ ] Randomize scan order to avoid synchronized behavior.
- [ ] Cache observations to avoid every agent scanning the same shop.
- [ ] Add policy guard against buying overpriced bait.
- [ ] Add policy guard against draining rare player listings too aggressively.

## 5. Autonomous Merchant Setup

- [ ] Implement merchant/shop listing plan card.
- [ ] Implement `MerchantPlan`.
  - agent id.
  - map/channel/FM room.
  - duration.
  - listings.
  - restock policy.
  - price policy.
  - exit criteria.
- [ ] Implement `ListingDecision`.
  - item id.
  - quantity.
  - unit price.
  - min/max price.
  - reason.
- [ ] Add listing selection policy.
- [ ] Add FM room placement policy.
- [ ] Add undercut policy.
- [ ] Add listing count and update rate limits.
- [ ] Add close/restock/expire behavior.
- [ ] Add global controls so agents do not overcrowd FM.

## 6. Supply/Demand Simulation

- [ ] Build demand score inputs:
  - class population growth.
  - active class distribution.
  - event reward effects.
  - event map/activity participation.
  - quest requirements.
  - job advancement requirements.
  - potion consumption.
  - ammo consumption.
  - popular training locations.
  - maker/crafting ingredients.
  - scroll/equipment upgrade demand.
  - active agent plan demand.
  - observed player demand.
- [ ] Build supply score inputs:
  - NPC shop availability.
  - drop availability.
  - market supply observations.
  - agent inventory supply.
  - farm activity.
- [ ] Compute:

```text
demandScore =
  questDemand
+ buildDemand
+ consumableDemand
+ upgradeDemand
+ planDemand
+ observedPlayerDemand
```

```text
supplyScore =
  npcAvailability
+ dropAvailability
+ observedMarketSupply
+ agentInventorySupply
```

```text
scarcity = demandScore / max(supplyScore, 1)
```

- [ ] Use scarcity to influence farm/buy/sell/hold decisions.
- [ ] Treat population and event signals as leading indicators, not only price
  history.
- [ ] Example population rule: if assassin population rises, increase expected
  demand for throwing stars, claws, LUK gear, relevant scrolls, and ammo
  resupply.
- [ ] Example event rule: if an event rewards higher-slot gloves, increase
  expected demand for glove scrolls and glove enhancement materials.

## 7. Price History Storage

- [ ] Choose storage mode:
  - portable SQLite.
  - server DB tables.
  - JSONL snapshots for early prototype.
- [ ] Define tables/files:
  - `economy_price_observations`.
  - `economy_item_daily_summary`.
  - `economy_agent_trade_events`.
  - `economy_market_snapshots`.
- [ ] Add startup load.
- [ ] Add periodic compaction.
- [ ] Add backup/export path.
- [ ] Add max-size guard.

## 8. Inventory Valuation Model

- [ ] Implement `ItemValuation`.
  - NPC sell value.
  - market value.
  - quest value.
  - future use value.
  - upgrade value.
  - rarity value.
  - liquidity score.
  - disposal policy.
- [ ] Disposal policies:
  - `keep`
  - `sell_market`
  - `sell_npc`
  - `trade_to_owner`
  - `trade_to_party`
  - `stash`
  - `drop_if_emergency`
- [ ] Prevent vending/trading protected items.
- [ ] Add inventory-pressure behavior.
- [ ] Add per-agent reserve rules.

## 9. Anti-Abuse Economy Policy

- [ ] Add max market share per item.
- [ ] Add max purchases per item per hour.
- [ ] Add max listings per agent/account.
- [ ] Add price floor/ceiling guards.
- [ ] Prevent coordinated mass undercutting.
- [ ] Prevent infinite meso loops.
- [ ] Prevent agents from buying from own agents to fake volume.
- [ ] Prevent draining rare player listings.
- [ ] Restrict auto-trading with suspicious players.
- [ ] Require policy approval for high-value transfers.
- [ ] Ensure LLM cannot bypass validators.

## 10. LLM Economy Tools

- [ ] Add read-only tools:
  - `economy.find_item_sources(itemId)`
  - `economy.get_price_summary(itemId)`
  - `economy.compare_farm_locations(itemId)`
  - `economy.find_resupply_shop(agentId, itemType)`
  - `economy.recommend_sell_items(agentId)`
  - `economy.recommend_buy_items(agentId)`
  - `economy.evaluate_trade(agentId, offer)`
- [ ] Add proposal-only tools:
  - `economy.propose_merchant_plan(agentId)`
  - `economy.propose_farm_for_sale_plan(agentId, itemId)`
  - `economy.propose_resupply_plan(agentId)`
- [ ] Route all executable actions through plan/capability validators.
- [ ] Do not expose direct mutation tools to LLM:
  - no direct item give.
  - no direct meso set.
  - no direct buy/sell/trade mutation.

## 11. Automated Economy Plan Execution

- [ ] Add plan cards:
  - Restock HP/MP potions.
  - Restock ammo.
  - Sell trash.
  - Farm item for quest.
  - Farm item for sale.
  - FM price scan.
  - Buy item from FM.
  - Merchant listing.
  - Trade item to owner/party.
  - Stash/hold future-use items.
- [ ] Each plan card needs:
  - preconditions.
  - budget.
  - risk limit.
  - exit criteria.
  - rollback behavior.
  - priority.
  - focus/sidetrack policy.
- [ ] Add explainable plan result:
  - completed.
  - blocked.
  - postponed.
  - policy rejected.
  - interrupted.
- [ ] Implement sell-trash as a policy objective, not a fixed item list:
  - sell ETC items that are not quest, future-quest, crafting, requested, or
    marketable items.
  - sell common below-average equips when not useful for crafting, transfer, or
    market listing.
  - prefer NPC sell for low-value low-liquidity items.
  - prefer market listing or stash for items with known buyer demand.
  - vary behavior by profile, such as crafter, minimalist, hoarder, or
    market-aware.
- [ ] Ensure economy plans only create validated plan objectives. They must not
  directly mutate inventory, mesos, trades, or shops.

## 12. Market Discovery Memory

- [ ] Add global economy memory.
- [ ] Add per-agent economy memory.
- [ ] Add party/shared economy memory.
- [ ] Remember:
  - observed prices.
  - known cheap sellers.
  - bad trades.
  - profitable farm items.
  - preferred markets.
  - items party/owner needs.
- [ ] Make memory decay over time.
- [ ] Keep memory separate from authoritative market state.

## 13. Economy Event Stream

- [ ] Emit events:
  - `ItemLooted`
  - `ItemSoldToNpc`
  - `ItemListedInShop`
  - `ItemBoughtFromShop`
  - `ItemTraded`
  - `PriceObserved`
  - `MarketPlanStarted`
  - `MarketPlanCompleted`
  - `MarketPolicyRejected`
- [ ] Add compact event logging.
- [ ] Add metrics counters from event stream.
- [ ] Use events to update price history and agent memory.

## 14. Economy Risk Scoring

- [ ] Implement risk score:
  - `low`
  - `medium`
  - `high`
  - `blocked`
- [ ] Risk factors:
  - high-value item.
  - price far above/below median.
  - unknown counterparty.
  - agent low on mesos.
  - item needed for current/future plan.
  - rare/one-of-kind item.
  - policy cap exceeded.
  - suspicious trading pattern.
- [ ] Output:
  - risk level.
  - reason codes.
  - requires human approval flag.
- [ ] Block high-risk automated execution.

## 15. Agent Economic Personality

- [ ] Add economy profile fields:
  - frugalness.
  - generosity.
  - risk tolerance.
  - hoarding.
  - market curiosity.
  - shop patience.
  - price sensitivity.
  - helpfulness.
  - speculation.
  - loyalty to owner.
  - party support.
- [ ] Use personality to vary:
  - sell/hold threshold.
  - willingness to share.
  - FM scan frequency.
  - buy urgency.
  - preferred items.
  - reaction to trade offers.
- [ ] Keep personality bounded by global anti-abuse policy.

## 16. Economy Signal Engine

- [ ] Implement `EconomySignalEngine`.
- [ ] Maintain world-level and channel-level signals:
  - class population distribution.
  - new character/job growth rate.
  - active training maps.
  - active questlines.
  - active party quests.
  - event participation.
  - event reward catalog.
  - known player buy/sell requests.
  - agent plan demand.
  - recent consumption patterns.
- [ ] Convert signals into item demand components:
  - `classDemand`.
  - `questDemand`.
  - `eventDemand`.
  - `craftingDemand`.
  - `upgradeDemand`.
  - `consumableDemand`.
  - `socialDemand`.
  - `speculativeDemand`.
- [ ] Output explainable demand forecasts:
  - item id.
  - demand score.
  - confidence.
  - reason codes.
  - expected duration.
  - affected plans/classes/events.
- [ ] Feed forecasts into buy/sell/hold, farming, merchant listing, and LLM
  economy summaries.

## 17. Inventory Valuation Expansion

- [ ] Expand `ItemValuation` beyond raw market price:
  - liquidity.
  - opportunity cost of keeping the slot.
  - expected time-to-sell.
  - future quest value.
  - future crafting value.
  - class/build demand.
  - event demand.
  - substitution value.
  - price confidence.
- [ ] Add reserve policy:
  - per-agent reserve.
  - party reserve.
  - owner reserve.
  - future-plan reserve.
- [ ] Add disposal reason codes so decisions are auditable:
  - `npc-junk`.
  - `quest-protected`.
  - `future-use`.
  - `crafting-reserve`.
  - `marketable`.
  - `low-liquidity`.
  - `inventory-pressure`.
  - `requested-by-player`.

## 18. Market Decision Modules

- [ ] Implement separate modules so the economy system remains portable:
  - `EconomySignalEngine`: predicts demand/supply pressure.
  - `InventoryValuationEngine`: values and protects items.
  - `MarketDecisionEngine`: proposes buy/sell/hold/farm/stash actions.
  - `PriceObservationEngine`: records and summarizes observed prices.
  - `RiskPolicyEngine`: blocks unsafe or abusive actions.
  - `PlanGenerationEngine`: converts approved proposals into plan cards.
  - `MerchantStrategyEngine`: chooses listing, pricing, and restock behavior.
  - `PopulationDemandModel`: maps class/job growth to item demand.
  - `EventDemandModel`: maps event rewards and participation to item demand.
- [ ] Keep modules independent from Cosmic server classes.
- [ ] Integrate through catalog query APIs, economy event streams, and capability
  validators.

## 19. Advanced Economy Concepts

- [ ] Add liquidity scoring so agents know whether an item is easy to sell.
- [ ] Add opportunity-cost scoring for inventory slots and mesos.
- [ ] Add time-to-sell estimates for market listings.
- [ ] Add price-confidence scoring to avoid overreacting to sparse data.
- [ ] Add market manipulation guards:
  - max agent market share per item.
  - max agent purchase rate per item.
  - max coordinated undercut pressure.
  - outlier rejection.
  - self-trade/fake-volume rejection.
- [ ] Add speculation policy:
  - allowed speculation categories.
  - max meso exposure.
  - max holding duration.
  - confidence threshold.
- [ ] Add substitution model:
  - similar equipment.
  - equivalent potions.
  - alternative crafting materials.
  - farm-vs-buy alternatives.
- [ ] Add social demand model:
  - player requests.
  - party requests.
  - owner requests.
  - guild/friend demand if supported later.

## 20. Plan Card Integration Contract

- [ ] Economy engine produces proposals, not final mutations.
- [ ] Plan engine converts accepted proposals into plan cards or child
  objectives.
- [ ] Capability validators enforce:
  - item protection.
  - meso budget.
  - trade/listing limits.
  - risk policy.
  - server state validity.
  - anti-abuse rules.
- [ ] LLM receives economy summaries and may request proposals, but cannot bypass
  the plan/capability execution path.
- [ ] Every executed economy action should record:
  - proposal id.
  - plan id.
  - objective id.
  - decision reason.
  - validator result.
  - resulting event.

## Recommended Build Order

1. Inventory valuation model.
2. Item source and shop query API.
3. Protected-item and reserve policy.
4. Basic buy/sell/hold policy.
5. Sell-trash policy objective.
6. Resupply plan card.
7. Price observation storage.
8. FM scan read-only behavior.
9. Dynamic price summaries.
10. Economy signal engine.
11. Population and event demand models.
12. Merchant listing policy.
13. LLM economy tools.
14. Anti-abuse policy expansion.
15. Full autonomous economy plans.
