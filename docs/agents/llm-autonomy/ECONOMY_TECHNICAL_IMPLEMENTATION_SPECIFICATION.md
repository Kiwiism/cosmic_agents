# Economy Technical Implementation Specification

This document specifies the technical implementation for the portable Agent
economy engine. It should be implemented outside Cosmic core where possible,
with a small server adapter/capability layer for live observations and validated
execution.

Design behavior lives in:
`docs/agents/llm-autonomy/ECONOMY_DESIGN_SPECIFICATION.md`.

Architecture reference:
`docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md`.

## Package Layout

Recommended portable package:

```text
server/agents/economy/
  api/
  model/
  catalog/
  observation/
  pricing/
  valuation/
  demand/
  decision/
  memory/
  validation/
  journal/
  llm/
  integration/
```

Server-specific adapter package:

```text
server/agents/integration/cosmic/economy/
```

Portable code must not depend directly on Cosmic concrete classes unless it is
inside the integration package.

## External Inputs

Catalog inputs:

- `tmp/game-catalog/generated_item_catalog.json`.
- `tmp/game-catalog/generated_drop_catalog.json`.
- `tmp/game-catalog/generated_shop_catalog.json`.
- `tmp/agent-llm-catalog/generated_item_source_index.json`.
- `tmp/agent-llm-catalog/generated_resupply_catalog.json`.
- `tmp/npc-catalog/generated_npc_shop_inventory.json`.
- future maker/crafting catalog.
- future event reward catalog.
- future equip stat baseline catalog.
- future scroll effect catalog.

Live inputs:

- FM listing scan results.
- confirmed sales.
- trade events.
- NPC shop snapshots.
- Agent inventory events.
- meso created/destroyed events.
- item created/destroyed events.
- active plan demand.
- job/class population snapshots.

## Core Identifiers

Use stable IDs:

```text
worldId
channelId
mapId
roomId
agentId
characterId
accountIdHash
npcId
shopId
itemId
itemInstanceId
itemValuationKey
listingId
observationId
decisionId
journalId
```

Do not store raw IP or sensitive identity in portable economy logs. If needed
for abuse checks, use server-side hashed relationship keys.

## Data Models

### ItemValuationKey

Simple stackable:

```json
{
  "type": "simple",
  "itemId": 4000004,
  "key": "item:4000004"
}
```

Equip:

```json
{
  "type": "equip",
  "itemId": 1472055,
  "slotBucket": "clean-7",
  "mainStatBucket": "luk-plus-2",
  "attackBucket": "watk-average",
  "qualityTier": "above-average",
  "key": "equip:1472055:clean-7:luk-plus-2:watk-average:above-average"
}
```

### MarketObservation

```json
{
  "schemaVersion": 1,
  "observationId": "obs-uuid",
  "eventType": "listing_seen",
  "worldId": 0,
  "channelId": 1,
  "mapId": 910000001,
  "fmRoom": 1,
  "itemId": 2040804,
  "itemValuationKey": "item:2040804",
  "quantity": 1,
  "unitPrice": 1200000,
  "totalPrice": 1200000,
  "listingId": "listing-uuid",
  "sellerKind": "player",
  "buyerKind": null,
  "sellerIdentityHash": "seller-hash",
  "buyerIdentityHash": null,
  "observedByAgentId": 51,
  "listingCreatedAtMs": 123000000,
  "observedAtMs": 123456789,
  "listingAgeMs": 456789,
  "source": "fm_shop_scan"
}
```

Event types:

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
- `agent_item_looted`.
- `agent_item_consumed`.
- `agent_item_vendored`.
- `agent_item_listed`.
- `agent_item_bought`.

### MarketItemState

```json
{
  "schemaVersion": 1,
  "worldId": 0,
  "itemValuationKey": "item:2040804",
  "itemId": 2040804,
  "window": "24h",
  "sampleCount": 42,
  "listingCount": 12,
  "confirmedSaleCount": 9,
  "medianListingPrice": 1300000,
  "medianSalePrice": 1180000,
  "trimmedMedianSalePrice": 1200000,
  "lowPrice": 1000000,
  "highPrice": 1600000,
  "movingAveragePrice": 1220000,
  "volatility": 0.22,
  "liquidity": "medium",
  "confidence": "observed",
  "trend": "up",
  "timeToSaleMedianMs": 900000,
  "unsoldAgeMedianMs": 7200000,
  "expirationRate": 0.35,
  "manipulationRisk": "low",
  "demandScore": 1.35,
  "supplyScore": 0.82,
  "scarcityScore": 1.64,
  "updatedAtMs": 123456789
}
```

### EconomyDecision

```json
{
  "schemaVersion": 1,
  "decisionId": "eco-uuid",
  "agentId": 51,
  "worldId": 0,
  "action": "buy",
  "itemId": 1472055,
  "itemValuationKey": "equip:1472055:clean-7:luk-plus-2:watk-average:above-average",
  "quantity": 1,
  "maxBuyPrice": 280000,
  "minSellPrice": null,
  "confidence": "few-samples",
  "urgency": "medium",
  "summary": "Build target needs Maple Claw soon; listing is cheaper than expected farming time.",
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
    }
  ],
  "createdAtMs": 123456789
}
```

### AgentEconomyMemory

```json
{
  "schemaVersion": 1,
  "agentId": 51,
  "worldId": 0,
  "priceBeliefs": {
    "item:2040804": {
      "median": 1200000,
      "confidence": "observed",
      "lastCheckedMs": 123456789
    }
  },
  "preferredMarkets": [1, 2, 3],
  "profitableItems": [],
  "badTrades": [],
  "trustedCounterparties": [],
  "suspiciousCounterparties": []
}
```

## Persistence

Recommended tables:

```text
economy_market_observations
economy_market_item_state
economy_price_window_summary
economy_agent_memory
economy_decision_journal
economy_trade_intents
economy_suspicion_events
economy_global_meso_state
economy_item_flow_daily
```

### economy_market_observations

Append-only. Do not update except for retention/archival.

Columns:

```text
id bigint primary key
observation_id varchar unique
world_id int
channel_id int
map_id int
fm_room int null
event_type varchar
item_id int
item_valuation_key varchar
quantity int
unit_price bigint null
total_price bigint null
listing_id varchar null
seller_kind varchar null
buyer_kind varchar null
seller_identity_hash varchar null
buyer_identity_hash varchar null
observed_by_agent_id int null
listing_created_at_ms bigint null
observed_at_ms bigint
listing_age_ms bigint null
source varchar
payload_json text
```

Indexes:

```text
world_id, item_valuation_key, observed_at_ms
world_id, item_id, observed_at_ms
listing_id
seller_identity_hash, buyer_identity_hash
event_type, observed_at_ms
```

### economy_market_item_state

Current compact state.

Key:

```text
world_id + item_valuation_key + window
```

Windows:

- `1h`.
- `24h`.
- `7d`.
- `30d`.
- `all`.

### economy_agent_memory

One row per agent/world. Store compact JSON.

### economy_decision_journal

Append-only decision record. Links to plan/objective/action result when
available.

## Services

### EconomyCatalogService

Read-only catalog facts.

Methods:

```java
ItemStaticFacts itemFacts(int itemId);
List<ItemSourceFacts> itemSources(int itemId);
List<NpcShopPriceFacts> npcShopSources(int itemId);
List<ResupplyShopFacts> resupplySources(AgentId agentId, ResupplyType type);
Optional<EquipStaticFacts> equipFacts(int itemId);
Optional<ScrollStaticFacts> scrollFacts(int itemId);
```

### MarketObservationService

Writes observations and normalizes raw adapter input.

Methods:

```java
void recordListingSeen(ListingSnapshot listing, ObservationContext context);
void recordListingSold(ListingSaleSnapshot sale, ObservationContext context);
void recordTradeCompleted(TradeSnapshot trade, ObservationContext context);
void recordNpcShopSeen(NpcShopSnapshot shop, ObservationContext context);
```

### PriceSummaryService

Compacts observations.

Methods:

```java
MarketItemState summarize(ItemValuationKey key, int worldId, PriceWindow window);
void rebuildWindow(int worldId, PriceWindow window, Instant from, Instant to);
List<MarketItemState> findStates(MarketQuery query);
```

### StaticBaselineValueService

Computes intrinsic values.

Methods:

```java
Money intrinsicValue(ItemValuationInput input);
Money npcAnchorValue(int itemId);
Money farmCostValue(int itemId, AgentContext context);
Money questDemandPremium(int itemId, AgentContext context);
```

### EquipValuationService

Computes equip valuation key and value.

Methods:

```java
ItemValuationKey valuationKey(EquipSnapshot equip);
EquipQualityTier classifyQuality(EquipSnapshot equip);
Money estimateEquipValue(EquipSnapshot equip, AgentContext context);
double effectiveBuildScore(EquipSnapshot equip, JobClass targetClass);
```

### ScrollValuationService

Methods:

```java
Money expectedUpgradeValue(int scrollItemId, ItemValuationContext context);
Money estimateScrollValue(int scrollItemId, ItemValuationContext context);
```

### DemandSupplySignalService

Methods:

```java
DemandSupplySignal computeSignal(int itemId, int worldId);
double demandScore(int itemId, int worldId);
double supplyScore(int itemId, int worldId);
double scarcityScore(int itemId, int worldId);
```

### InflationIndexService

Methods:

```java
GlobalMesoState getGlobalMesoState(int worldId);
InflationIndex currentInflationIndex(int worldId);
TaxRecommendation simulateTax(int worldId);
```

First release should simulate tax only. Enforcement comes later.

### ManipulationRiskService

Methods:

```java
ManipulationRisk assess(ItemValuationKey key, int worldId);
CounterpartyRisk assessCounterparty(String identityHash);
boolean isSuspiciousListing(ListingSnapshot listing);
```

Risk components:

- outlier price.
- circular trades.
- repeated counterparties.
- fast relisting.
- low sample high price movement.
- agent market share.
- same seller concentration.

### AgentEconomyMemoryService

Methods:

```java
AgentEconomyMemory load(int agentId, int worldId);
void updateAfterObservation(int agentId, MarketObservation observation);
void updateAfterDecision(EconomyDecision decision, EconomyActionResult result);
```

### EconomyDecisionService

Methods:

```java
EconomyDecision recommendForInventoryItem(AgentContext agent, ItemSnapshot item);
List<EconomyDecision> recommendSellItems(AgentContext agent);
List<EconomyDecision> recommendBuyItems(AgentContext agent, BuyNeed need);
EconomyDecision evaluateTradeOffer(AgentContext agent, TradeOffer offer);
EconomyDecision compareFarmBuyHold(AgentContext agent, int itemId, int quantity);
```

### EconomyActionValidator

Methods:

```java
ValidationResult validateBuy(AgentContext agent, BuyCommand command);
ValidationResult validateSell(AgentContext agent, SellCommand command);
ValidationResult validateTrade(AgentContext agent, TradeCommand command);
ValidationResult validateMerchantListing(AgentContext agent, MerchantListingCommand command);
```

### EconomyJournalService

Methods:

```java
void recordDecision(EconomyDecision decision);
void recordValidation(EconomyDecision decision, ValidationResult result);
void recordOutcome(EconomyDecision decision, EconomyActionResult result);
DecisionExplanation explain(String decisionId);
```

## Capability Integration

Economy engine proposes capability requests:

- `NavigateToFreeMarket`.
- `ScanFreeMarketRoom`.
- `InspectPlayerShop`.
- `BuyListing`.
- `SellToNpc`.
- `OpenMerchant`.
- `ListMerchantItem`.
- `TradeWithCharacter`.
- `StashItem`.
- `FarmItem`.
- `ResupplyAtNpcShop`.

Each request must include:

```text
decisionId
agentId
itemId
quantity
price limit
reason
expiration
validator requirements
```

Capabilities must return structured results:

```text
success
blocked
changed
expired
not_found
price_changed
inventory_full
insufficient_mesos
validator_rejected
```

## LLM Economy Tools

Read-only tools:

```text
economy.find_item_sources(itemId)
economy.get_price_summary(itemId, valuationKey?)
economy.get_item_fair_value(itemId, agentId?)
economy.compare_farm_locations(itemId, agentId?)
economy.recommend_sell_items(agentId)
economy.recommend_buy_items(agentId)
economy.evaluate_trade(agentId, offer)
economy.propose_resupply_plan(agentId)
economy.propose_market_scan_plan(agentId)
economy.explain_decision(decisionId)
economy.get_meso_stability(worldId)
economy.get_manipulation_risk(itemId)
```

LLM tools should never expose raw buy/sell/trade execution.

## Jobs

### Price Compaction Job

Cadence:

```text
1h window: every 5 minutes
24h window: every 30 minutes
7d window: every 2 hours
30d window: every 6 hours
```

Responsibilities:

- compute medians.
- trim outliers.
- update volatility.
- update confidence.
- update listing age metrics.
- update liquidity.

### Inflation Job

Cadence:

```text
every 15 minutes
```

Responsibilities:

- summarize meso supply.
- summarize meso creation/destruction.
- compute basket price index.
- compute barter share.
- simulate tax recommendation.

### Manipulation Scan Job

Cadence:

```text
every 10 minutes
```

Responsibilities:

- detect circular trades.
- detect outlier listings.
- detect repeated counterparties.
- detect agent market share caps.
- mark risky item keys.

### Agent Memory Compaction Job

Cadence:

```text
every 30 minutes
```

Responsibilities:

- compress old observations into beliefs.
- expire stale memory.
- keep high-impact memories.

## Formulas

### Trimmed Median

For enough samples:

```text
sort prices
drop top 10%
drop bottom 10%
median remaining
```

For few samples, do not trim. Mark confidence `few-samples`.

### Confidence

```text
confidenceScore =
  independentSellerScore
  + confirmedSaleScore
  + sampleCountScore
  + lowManipulationScore
  + stableTimeToSaleScore
  - volatilityPenalty
```

Map score to:

- `unknown`.
- `few-samples`.
- `observed`.
- `stable`.
- `volatile`.
- `manual`.

### Fair Value

```text
fairValue =
  intrinsicValue * intrinsicWeight
  + observedMarketValue * marketWeight
  + demandPremium
  - oversupplyDiscount
  + inflationAdjustment
```

Weight rules:

```text
if confidence unknown/few-samples:
  intrinsicWeight high
  marketWeight low

if confidence stable:
  intrinsicWeight lower
  marketWeight high

if manipulation risk high:
  marketWeight low
```

### Equip Stat Premium

```text
statPremium =
  statUnitValue * pow(effectiveStatPoints, exponent)
```

Recommended exponent:

```text
1.4 to 2.2
```

Use logarithmic dampening for extreme rarity:

```text
rarityPremium =
  basePremium * log(1 + rarityMultiplier)
```

### Scroll Expected Value

```text
scrollExpectedValue =
  successRate * averagePremiumCreated
  - failurePenalty
```

### Agent Buy Price

```text
acceptableBuyPrice =
  fairValue
  * urgencyMultiplier
  * personalityMultiplier
  * wealthMultiplier
  * confidenceAdjustment
```

### Agent Sell Price

```text
minimumSellPrice =
  max(vendorValue, fairValue * minimumMargin)
```

## Validation Rules

### Buy Validation

Reject if:

- listing disappeared.
- listing item/stats/quantity changed.
- price exceeds decision max.
- total spend exceeds budget.
- agent would violate meso reserve.
- inventory lacks space.
- item is not allowed by role.
- market risk is too high.
- seller cooldown violated.
- item buy cap exceeded.
- agent market share cap exceeded.

### Sell Validation

Reject if:

- item is current quest requirement.
- item is future protected requirement.
- item is reserved equipment/material.
- item is untradeable.
- price below minimum.
- market flood cap exceeded.
- listing cooldown violated.
- merchant/shop unavailable.

### Trade Validation

Reject if:

- self trade.
- circular trade.
- suspicious counterpart risk too high.
- value imbalance exceeds policy.
- high-value manual threshold exceeded.
- protected item included.
- tax/fee cannot be paid.

## Anti-Manipulation Implementation

Maintain:

```text
item_suspicion_score
counterparty_suspicion_score
agent_market_share
seller_concentration
buyer_seller_pair_frequency
circular_trade_graph
```

Downweight:

- agent-to-agent trades.
- same seller repeated sales.
- suspicious counterpart trades.
- outlier prices.
- low-volume sudden spikes.
- listings with no sale.

Block or require manual review when:

- suspicion score exceeds hard cap.
- item price moves too fast with few samples.
- agent market share exceeds item/category cap.
- circular trade pattern repeats.

## Runtime Boundaries

The economy engine must not:

- call Cosmic inventory mutation directly.
- call Cosmic shop buy/sell directly.
- call Cosmic trade mutation directly.
- complete quests.
- move characters.

It may emit:

- recommendations.
- plan card proposals.
- capability requests.
- validation requirements.
- journal entries.

The Agent capability layer executes after validation.

## Metrics

Track:

- total market observations.
- confirmed sales.
- unsold listings.
- median time to sale.
- barter share.
- meso supply.
- meso created/destroyed.
- agent market share.
- manipulation flags.
- rejected buy/sell/trade actions.
- decision confidence distribution.
- price summary confidence distribution.
- LLM proposal accept/reject rate.

## Rollout Plan

### Phase 1: Read-Only Static Economy

- load item sources, shops, resupply, NPC shop inventory.
- expose read-only query API.
- no live market actions.

### Phase 2: Observation Logging

- record NPC shop observations.
- record FM listing scans.
- record trade/listing lifecycle events where available.
- no automated buying yet.

### Phase 3: Price Summaries

- compact observations.
- expose price summary API.
- show confidence/risk.

### Phase 4: Basic Decisions

- sell-trash recommendation.
- hold quest/future items.
- resupply recommendation.
- conservative buy recommendation.

### Phase 5: Validators And Execution

- buy validator.
- sell validator.
- trade validator.
- merchant listing validator.
- capability integration.

### Phase 6: Learning

- agent economy memory.
- decision journal.
- personal price beliefs.
- farm-vs-buy adaptation.

### Phase 7: Advanced Valuation

- equip stat valuation.
- scroll expected-value model.
- crafting/maker model.

### Phase 8: Market Protection

- manipulation risk engine.
- agent market-share caps.
- barter monitoring.
- inflation index.

### Phase 9: LLM Economy Tools

- read-only tools.
- explainable recommendations.
- no direct execution.

### Phase 10: Dynamic Policy

- observe-only dynamic tax simulation.
- operator dashboard/reports.
- optional dynamic tax enforcement after validation.

## Test Plan

Unit tests:

- baseline value for NPC item.
- drop-only farm cost value.
- trimmed median.
- confidence scoring.
- equip valuation bucket.
- nonlinear stat premium.
- scroll expected value.
- buy validation rejects price change.
- sell validation protects quest item.
- trade validation rejects circular trade.

Integration tests:

- scan shop and record listings.
- listing sale updates price summary.
- unsold listing lowers confidence.
- sell-trash plan excludes protected items.
- resupply plan finds NPC shop.
- buy recommendation creates capability request.
- validator blocks suspicious market item.

Soak tests:

- 30-day observation retention/compaction simulation.
- high-volume listing churn.
- agent market-share cap.
- inflation index drift.
- many Agents scanning without synchronized behavior.

## Open Decisions

- Exact DB schema style for this codebase.
- Whether to store economy tables in existing DB or separate portable DB.
- How much player identity hashing is acceptable.
- Whether player shop internals expose enough listing lifecycle data.
- First item categories to support for equip valuation.
- First tax policy should remain fixed or expose observe-only simulation.

