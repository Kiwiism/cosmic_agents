# Adaptive Economy System Plan

This plan defines the portable economy engine for Agents and LLM-assisted
planning. The economy engine is separate from the Cosmic server runtime. It
reads catalogs and live observations, proposes actions, and records learning.
It never mutates inventory, mesos, shops, trades, quests, or market state
directly. Execution always goes through validated Agent capabilities.

## Goals

- Keep mesos useful and relatively valuable.
- Let market prices evolve through supply, demand, inflation, and behavior.
- Let Agents have imperfect individual beliefs instead of one shared perfect
  price.
- Support realistic volatility without allowing Agents or players to easily
  manipulate the market.
- Price stackable items, scrolls, rare use items, and stat-bearing equips with
  different valuation models.
- Record why each economic decision happened.
- Let the LLM query and propose, but never bypass validators.

## High-Level Loop

```text
static catalogs
  items, drops, shops, NPC shop inventory, quests, maps, mobs, recipes, events
        |
        v
live observations
  FM listings, confirmed sales, unsold listings, trades, agent inventory flows
        |
        v
market learning layer
  price summaries, confidence, demand, supply, inflation, manipulation risk
        |
        v
agent belief layer
  personal price memory, role, risk, wealth, urgency, relationship trust
        |
        v
economy decision engine
  buy, sell, hold, farm, vendor, stash, craft, trade, merchant-list
        |
        v
plan cards and capabilities
  navigate, scan shop, inspect listing, buy, sell, trade, list, resupply
        |
        v
runtime validators
  budget, cooldown, inventory, protected items, price limits, anti-abuse
        |
        v
execution result and decision journal
        |
        v
feedback into observations and memory
```

## Layers

### 1. Static Catalog Layer

Purpose: provide non-live intrinsic facts.

Inputs:

- item catalog.
- drop source catalog.
- NPC shop catalog.
- NPC shop inventory catalog.
- quest objective catalog.
- resupply catalog.
- map/mob spawn catalog.
- future maker/crafting catalog.
- event reward catalog.
- scroll/equipment metadata.

Outputs:

- `ItemStaticFacts`.
- `ItemSourceFacts`.
- `NpcShopPriceFacts`.
- `QuestDemandFacts`.
- `CraftingDemandFacts`.
- `EquipStatFacts`.
- `ScrollEffectFacts`.

This layer answers:

- where can this item come from?
- is it sold by NPC?
- is it consumed by quests or crafting?
- which jobs/builds want it?
- how hard is it to farm?
- is it fixed-price, drop-only, crafted, rare, or stat-bearing?

### 2. Observation Layer

Purpose: record what is seen in the live world.

Observation event types:

- `listing_seen`.
- `listing_price_changed`.
- `listing_quantity_changed`.
- `listing_cancelled`.
- `listing_expired`.
- `listing_sold_partial`.
- `listing_sold_full`.
- `trade_proposed`.
- `trade_completed`.
- `trade_cancelled`.
- `npc_shop_seen`.
- `agent_vendor_sale`.
- `agent_item_drop`.
- `agent_item_loot`.
- `agent_item_consumed`.

Important fields:

```json
{
  "eventType": "listing_sold_full",
  "worldId": 0,
  "channelId": 1,
  "mapId": 910000001,
  "fmRoom": 1,
  "itemId": 2040804,
  "quantity": 1,
  "unitPrice": 1200000,
  "listingAgeMs": 840000,
  "sellerKind": "player",
  "buyerKind": "player",
  "observedByAgentId": 51,
  "timestampMs": 123456789
}
```

Listings and sales are different. A listing shows asking price. A confirmed
sale shows accepted price. Unsold listing age is also valuable.

### 3. Market State Layer

Purpose: convert raw observations into current market belief.

One state per:

```text
worldId + itemValuationKey
```

For simple stackables, `itemValuationKey = itemId`.

For equips, the key includes item id and valuation bucket:

```text
itemId + statBucket + slotBucket + scrollBucket + qualityTier
```

Core model:

```json
{
  "itemValuationKey": "2040804",
  "worldId": 0,
  "sampleCount": 42,
  "listingCount": 12,
  "confirmedSaleCount": 9,
  "medianListingPrice": 1300000,
  "medianSalePrice": 1180000,
  "timeToSaleMedianMs": 900000,
  "unsoldAgeMedianMs": 7200000,
  "confidence": "observed",
  "volatility": 0.22,
  "liquidity": "medium",
  "manipulationRisk": "low",
  "demandScore": 1.35,
  "supplyScore": 0.82,
  "scarcityScore": 1.64,
  "inflationAdjustedFairValue": 1220000
}
```

Confidence levels:

- `unknown`: no useful live data.
- `few-samples`: some observations, not enough trust.
- `observed`: enough independent listings/sales.
- `stable`: enough volume and low manipulation risk.
- `volatile`: fast price/supply/demand movement.
- `manual`: operator/static override.

### 4. Global Inflation And Meso Stability Layer

Purpose: keep meso as the main unit of account.

Track:

- total meso supply.
- meso created by drops, quests, NPC sell, events.
- meso destroyed by NPC shops, travel, taxes, crafting, merchant fees.
- trade volume.
- item-for-item barter share.
- price index for common market baskets.
- agent share of total market activity.

Inflation pressure:

```text
mesoInflationPressure =
  (mesoCreated - mesoDestroyed) / max(totalMesoSupply, 1)
```

Item price pressure:

```text
pricePressure =
  globalInflationPressure
  + demandPressure
  - supplyPressure
  + speculationPressure
```

Trade tax starts at `1%`.

Tax can later adjust using:

```text
tax =
  baseTax
  + inflationSurcharge
  + speculationSurcharge
  + manipulationSurcharge
  - liquidityRelief
  - newPlayerAccessibilityDiscount
```

Do not make tax highly dynamic early. First implementation should use fixed
`1%`, record what would have changed, and only later enable dynamic modifiers.

Meso protection rules:

- all engine valuations are denominated in mesos.
- agent trade decisions use meso fair value first.
- NPC services and important sinks remain meso-denominated.
- item-for-item trades are allowed but tracked as barter.
- agent-to-agent trades do not define market price confidence.
- high barter share triggers monitoring or friction.

### 5. Demand And Supply Forecast Layer

Purpose: predict future pressure, not only react to current listings.

Demand inputs:

- job/class population growth.
- active plan-card demand.
- quest requirements.
- crafting/maker requirements.
- scroll and upgrade demand.
- event reward effects.
- agent profile/build targets.
- observed buy behavior.
- social/party demand.

Supply inputs:

- NPC shop availability.
- drop sources and drop rates.
- mob/map farming efficiency.
- event supply.
- crafting output.
- observed listing count.
- agent inventories.
- hoarding/stash behavior.

Demand score:

```text
demandScore =
  questDemand
  + buildDemand
  + classPopulationDemand
  + eventDemand
  + observedSalesVelocity
  + agentPlanDemand
  + speculationDemand
```

Supply score:

```text
supplyScore =
  npcAvailability
  + dropAvailability
  + observedListingSupply
  + agentInventorySupply
  + craftingSupply
```

Scarcity:

```text
scarcity = demandScore / max(supplyScore, 1)
```

### 6. Item Valuation Layer

Purpose: estimate fair value when market data is sparse or complex.

#### Fixed NPC Items

NPC price is the hard nominal anchor.

```text
baseline = npcPrice
```

Player convenience price:

```text
marketBaseline = npcPrice * convenienceMarkup
```

`convenienceMarkup` depends on travel distance, availability, urgency, and
agent laziness/richness.

#### Drop-Only Stackables

```text
baseline =
  expectedFarmTimeMinutes * mesoPerMinuteOpportunityCost
  + potionCost
  + travelCost
  + deathRiskCost
  + rarityPremium
```

#### Quest Items And Materials

```text
baseline =
  farmCost
  + activeQuestDemandPremium
  + futureQuestDemandPremium
  + materialDemandPremium
```

#### Scrolls

```text
scrollValue =
  expectedUpgradeValue
  * demandScore / max(supplyScore, 1)
  * confidenceAdjustment
```

Expected upgrade value:

```text
expectedUpgradeValue =
  successRate * averagePremiumCreated
  - failurePenalty
```

Scroll demand rises when relevant equips, classes, or event rewards become
popular.

#### Equips

Equip value is not linear.

```text
equipValue =
  baseItemValue
  + statPremium
  + slotPremium
  + scrollHistoryPremium
  + rarityPremium
  + classDemandPremium
  - defectPenalty
```

Stat premium:

```text
statPremium =
  statUnitValue * effectiveStatPoints ^ exponent
```

Recommended exponent:

```text
1.4 to 2.2
```

Rarity premium:

```text
rarityPremium =
  basePremium * log(1 + rarityMultiplier)
```

Rarity multiplier:

```text
rarityMultiplier =
  1 / probabilityOfRollingOrScrollingAtLeastThisGood
```

Equip comparable buckets:

- same item id.
- level range.
- remaining slots bucket.
- main stat bucket.
- attack/magic attack bucket.
- scrolled/clean bucket.
- quality tier: `below-average`, `average`, `above-average`, `excellent`,
  `godly`, `scrolled-low`, `scrolled-mid`, `scrolled-high`, `endgame`.

Class-specific effective score:

```text
effectiveBuildScore =
  sum(usefulStatValueForClass * statAmount)
```

Examples:

- assassin: LUK, weapon attack, DEX requirement support, avoid.
- warrior: STR, weapon attack, accuracy.
- mage: INT, magic attack, lukless compatibility.
- bowman: DEX, weapon attack, speed.

### 7. Agent Belief And Profile Layer

Purpose: make agents differ.

Each agent has imperfect local beliefs:

- remembered prices.
- dry streaks.
- profitable/failed trades.
- trusted sellers/buyers.
- preferred FM rooms.
- favorite farming maps.
- price confidence by item.
- personal item goals.

Profile fields:

- frugality.
- impatience.
- farming patience.
- market curiosity.
- risk appetite.
- price sensitivity.
- hoarding tendency.
- speculation tolerance.
- wealth comfort.
- generosity.
- loyalty to party/friends.

Personal acceptable buy price:

```text
acceptableBuyPrice =
  fairValue
  * urgencyMultiplier
  * personalityMultiplier
  * wealthMultiplier
  * confidenceAdjustment
```

Personal minimum sell price:

```text
minimumSellPrice =
  max(vendorValue, fairValue * minimumMargin)
```

Agent examples:

- frugal farmer buys only below fair value.
- impatient quester overpays when blocked.
- rich trader buys underpriced rare goods.
- crafter hoards ore/materials.
- social supplier sells cheaply to trusted party members.
- picky assassin overpays for a strong claw, while a casual assassin accepts a
  cheap average one.

### 8. Decision Engine Layer

Purpose: convert facts and beliefs into explainable proposals.

Actions:

- `buy`.
- `sell`.
- `hold`.
- `farm`.
- `vendor`.
- `stash`.
- `craft`.
- `trade`.
- `merchant-list`.
- `resupply`.
- `scan-market`.

Decision object:

```json
{
  "decisionId": "eco-uuid",
  "agentId": 51,
  "action": "buy",
  "itemId": 1472055,
  "quantity": 1,
  "maxBuyPrice": 280000,
  "confidence": "few-samples",
  "urgency": "medium",
  "summary": "Dexless assassin needs a Maple Claw soon; market option is cheaper than expected farming time.",
  "influences": [
    "build-target",
    "cheap-market-listing",
    "long-farming-dry-streak",
    "within-budget"
  ],
  "rejectedAlternatives": [
    {
      "action": "farm",
      "reason": "dry-streak-and-low-drop-confidence"
    },
    {
      "action": "wait",
      "reason": "would slow level progression"
    }
  ]
}
```

The decision engine proposes. The plan engine schedules. Capabilities execute
only after validators pass.

### 9. Plan Card Integration

Economy plan cards:

- `sell-trash`.
- `sell-market-items`.
- `stash-valuable-items`.
- `resupply-consumables`.
- `buy-blocking-quest-item`.
- `farm-for-sale`.
- `scan-fm-market`.
- `merchant-listing`.
- `reprice-merchant`.
- `craft-for-use`.
- `craft-for-profit`.

Plan-card objectives should be specific enough to execute but not hardcode all
valuation decisions.

Example:

```json
{
  "objectiveType": "economy-policy",
  "policy": "sell-trash",
  "constraints": {
    "protectQuestItems": true,
    "protectFutureQuestItems": true,
    "allowVendor": true,
    "allowMarketListing": false
  }
}
```

The economy engine returns item candidates. The plan runner turns them into
validated inventory/shop/trade objectives.

### 10. Manipulation And Abuse Defense

Player manipulation risks:

- fake high listings.
- fake low listings.
- self-buying.
- accomplice buying.
- circular trades.
- repeated relisting.
- bait listings.
- low-volume price pumping.

Agent manipulation risks:

- agents flooding one item.
- agents buying out a category.
- synchronized undercutting.
- agent-to-agent circular trades.
- LLM accidentally coordinating hundreds of agents into the same market action.

Defenses:

- use median or trimmed median, not lowest/highest.
- separate listings from confirmed sales.
- use time-to-sale and unsold age.
- downweight outliers.
- require minimum independent sellers/buyers.
- downweight repeated same-account/same-IP/same-relationship patterns.
- exclude or heavily downweight agent-to-agent trades for market confidence.
- cap agent market share by item/category.
- cap buys/listings per item per time window.
- randomize scan/listing timing.
- prevent circular agent trades.
- add suspicious market flags.
- never let LLM override validators.

Suspicion score:

```text
suspicionScore =
  outlierPriceScore
  + circularTradeScore
  + repeatedCounterpartyScore
  + fastRelistScore
  + lowSampleHighMovementScore
  + agentMarketShareScore
```

If suspicion is high:

- reduce confidence.
- tighten buy/sell limits.
- require more samples.
- mark item as manual/conservative.
- optionally block agent trading for that item.

### 11. Listing Age And Sale Velocity

Listing duration is part of price truth.

Signals:

- fast sale near price: demand is real.
- long unsold listing: likely overpriced or low demand.
- repeated expiry: price is too high or item is illiquid.
- low listing not bought: low demand, bad visibility, or suspicious bait.
- instant high-price sale: possible urgency or manipulation.

Metrics:

- `timeToSaleMedianMs`.
- `unsoldAgeMedianMs`.
- `expirationRate`.
- `partialSellRate`.
- `relistCount`.
- `saleVelocity`.
- `listingChurn`.

Confidence increases when independent sellers repeatedly sell in a normal time
window. Confidence decreases when prices are mostly unsold listings.

### 12. Memory And Learning

Market learning updates:

- price summaries.
- demand/supply scores.
- inflation index.
- confidence.
- volatility.
- manipulation risk.
- item category trends.

Agent learning updates:

- personal price beliefs.
- seller/buyer trust.
- farming patience.
- map efficiency beliefs.
- buy-vs-farm preference.
- item category interest.
- bad-trade avoidance.

Learning may adjust beliefs and preferences. It must not adjust validator
limits directly without operator-approved policy.

### 13. Validators

Before buy:

- listing still exists.
- item id, stats, quantity, and price match.
- total spend within command limit.
- price within max buy price.
- agent keeps meso reserve.
- item allowed by role/profile.
- inventory has space.
- cooldown permits action.
- market manipulation risk acceptable.
- agent market share cap not exceeded.

Before sell/list:

- item is not quest-required.
- item is not future-plan protected.
- item is tradeable.
- price within min/max policy.
- listing count under cap.
- undercut policy respected.
- market flood risk acceptable.
- agent has required shop/merchant ability.

Before trade:

- no self/circular trade.
- counterpart relationship/risk acceptable.
- item valuation within fairness bounds.
- high-value trade policy passes.
- no protected items.

### 14. LLM Interface

LLM gets read-only economy tools:

- `economy.find_item_sources(itemId)`.
- `economy.get_price_summary(itemId)`.
- `economy.get_item_fair_value(itemKey, agentId)`.
- `economy.compare_farm_locations(itemId)`.
- `economy.recommend_sell_items(agentId)`.
- `economy.recommend_buy_items(agentId)`.
- `economy.evaluate_trade(agentId, offer)`.
- `economy.propose_resupply_plan(agentId)`.
- `economy.propose_market_scan_plan(agentId)`.
- `economy.explain_decision(decisionId)`.

LLM can propose:

- target items.
- goals.
- risk tolerance within configured bounds.
- plan priority.

LLM cannot:

- directly buy.
- directly sell.
- directly trade.
- directly alter price models.
- bypass budget, item protection, or manipulation validators.

### 15. Implementation Order

1. Static value model from existing catalogs.
2. Economy query API over item sources, shops, resupply, and NPC shop inventory.
3. Market observation event schema.
4. Observation append-only storage.
5. Price summary compaction job.
6. Basic fair-value engine for stackables and NPC-sold items.
7. Basic buy/sell/hold decision engine.
8. Decision journal integration.
9. Runtime validators.
10. FM scan capability and plan card.
11. Listing lifecycle tracking.
12. Agent economy memory.
13. Demand/supply signal engine.
14. Equip stat valuation.
15. Scroll expected-value model.
16. Manipulation risk engine.
17. Inflation/meso stability index.
18. Merchant/listing plan cards.
19. LLM read-only economy tools.
20. Dynamic tax simulation, initially observe-only.
21. Dynamic tax enforcement, only after stable measurements.

## Improvements Over The Initial Idea

- Treats market observations as untrusted signals, not truth.
- Keeps agent-originated trades separate from player-originated trades.
- Uses listing age and unsold listings, not only completed sales.
- Adds intrinsic value so new items can be priced before market data exists.
- Adds separate equip and scroll valuation models.
- Adds global inflation and barter-share tracking to protect meso relevance.
- Lets agents learn individually without letting learning bypass safety.
- Keeps all execution behind validators and plan cards.
- Supports volatility through behavior, events, class growth, scarcity, and
  imperfect information instead of random price noise alone.

