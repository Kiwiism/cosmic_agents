# Economy Design Specification

This document defines the intended behavior of the Agent economy. It describes
what the economy should feel like, how prices form, how Agents differ, how mesos
stay relevant, and how the system avoids manipulation.

Technical implementation details live in:
`docs/agents/llm-autonomy/ECONOMY_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md`.

Architecture reference:
`docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md`.

## Design Goals

- Mesos remain the primary unit of account.
- Items can still become valuable stores of value, but should not fully replace
  mesos as the main currency.
- Prices evolve from supply, demand, inflation, scarcity, player behavior, and
  Agent behavior.
- Agents do not share perfect market knowledge.
- Different Agents make different economic decisions.
- Market behavior is explainable through decision journals.
- Players and Agents can influence the market, but manipulation is detected,
  dampened, and rate-limited.
- The LLM can reason about the economy but cannot directly buy, sell, trade, or
  alter prices.

## Economy Participants

### Real Players

Real players are first-class market participants. Their listings, sales, and
trades are important signals, but not automatically trusted.

Player behavior can include:

- normal buying and selling.
- hoarding.
- bargain hunting.
- urgent purchasing.
- speculation.
- manipulation.
- barter.
- market cornering.

### Agents

Agents participate through plan cards and validated capabilities.

Agent behavior can include:

- farming for own progression.
- selling trash.
- selling useful drops.
- buying quest blockers.
- resupplying potions/ammo.
- scanning Free Market shops.
- trading with players/Agents.
- merchant listing.
- crafting for use or profit.
- holding items for future demand.

Agent market activity must be tracked separately from player market activity.

### LLM

The LLM may:

- ask for price summaries.
- compare farm/buy/sell options.
- suggest goals.
- assign plan cards.
- request explanations.

The LLM may not:

- directly mutate inventory.
- directly mutate mesos.
- directly buy or sell.
- directly trade.
- override validators.
- manually alter market confidence.

## Currency Design

### Meso As Primary Currency

Mesos should remain useful because:

- NPC shops price essentials in mesos.
- trade taxes are paid in mesos.
- travel and utility fees are paid in mesos.
- crafting/maker fees are paid in mesos.
- merchant/listing fees are paid in mesos.
- Agent valuations are denominated in mesos.
- LLM economy summaries report values in mesos first.

### Currency Substitute Items

Some items may naturally become stores of value:

- high-demand scrolls.
- rare use items.
- rare equips.
- high-stat scrolled equips.
- event-limited items.

These should be monitored, not banned.

An item becomes a currency-substitute candidate when:

```text
barterShare high
+ stable demand
+ high liquidity
+ low consumption
+ high value density
+ repeated use in trades
```

When an item becomes a currency substitute:

- track it separately.
- increase manipulation scrutiny.
- avoid using it as proof of meso price by itself.
- apply barter valuation guardrails.
- optionally apply high-value barter tax/friction.

## Trade Tax Design

Initial tax:

```text
baseTradeTax = 1%
```

First implementation should keep tax fixed and record what a dynamic tax would
have done. Dynamic enforcement should come later.

Tax may rise when:

- global inflation is above target.
- speculation is excessive.
- suspicious/circular trading increases.
- luxury item prices overheat.
- meso sinks are too weak.
- wealth concentration becomes severe.

Tax may fall when:

- deflation is high.
- market liquidity is low.
- low-level goods stop moving.
- new-player accessibility is poor.
- post-event recovery needs support.

Recommended future tax components:

```text
tax =
  baseTradeTax
  + inflationSurcharge
  + luxurySurcharge
  + manipulationSurcharge
  + highValueSurcharge
  - liquidityRelief
  - newbieGoodsDiscount
```

## Price Formation

Each item has several values:

- `intrinsicValue`: value from source cost and utility.
- `marketValue`: value from observed listings/sales.
- `forecastValue`: value from expected future demand/supply.
- `agentPerceivedValue`: value as seen by a specific Agent.
- `validatorLimit`: hard safety bound for execution.

The engine should not treat one global price as truth.

General fair value:

```text
fairValue =
  intrinsicValue * intrinsicWeight
  + observedMarketValue * marketWeight
  + demandForecastPremium
  - oversupplyDiscount
  + inflationAdjustment
```

When market confidence is low, intrinsic value gets more weight.

When market confidence is high, observed market value gets more weight.

## Baseline Value Rules

### NPC-Sold Items

NPC price is the hard nominal anchor.

```text
intrinsicValue = npcPrice
```

Player market price may include convenience markup:

```text
marketBaseline = npcPrice * convenienceMarkup
```

Convenience markup rises when:

- item is far from the Agent.
- Agent is urgent.
- Agent is rich.
- travel cost is high.
- stock is hard to reach.

### Drop-Only Items

```text
intrinsicValue =
  expectedFarmTimeMinutes * mesoPerMinuteOpportunityCost
  + potionCost
  + travelCost
  + deathRiskCost
  + rarityPremium
```

### Quest Items

```text
intrinsicValue =
  farmCost
  + activeQuestDemandPremium
  + futureQuestDemandPremium
```

Quest-required items should not be sold by Agents unless:

- not needed by current or future plan.
- surplus exceeds reserve.
- item is tradeable.
- inventory pressure is high.

### Crafting And Maker Materials

```text
intrinsicValue =
  max(farmCost, expectedCraftUseValue)
```

Agents with crafting-oriented profiles should reserve more materials.

### Scrolls

Scroll value comes from expected upgrade value and market demand.

```text
scrollValue =
  expectedUpgradeValue
  * demandScore / max(supplyScore, 1)
  * confidenceAdjustment
```

```text
expectedUpgradeValue =
  successRate * averagePremiumCreated
  - failurePenalty
```

### Equips

Equips need stat-aware valuation.

```text
equipValue =
  baseItemValue
  + nonlinearStatPremium
  + slotPremium
  + scrollHistoryPremium
  + rarityPremium
  + classDemandPremium
  - defectPenalty
```

Stat premium is nonlinear:

```text
nonlinearStatPremium =
  statUnitValue * effectiveStatPoints ^ exponent
```

Recommended exponent range:

```text
1.4 to 2.2
```

This models the fact that each extra good stat point is harder to obtain and is
not priced linearly.

## Equip Quality Buckets

Equip comparison should use buckets, not exact equality only.

Buckets:

- `below-average`.
- `average`.
- `above-average`.
- `excellent`.
- `godly`.
- `clean-high-stat`.
- `scrolled-low`.
- `scrolled-mid`.
- `scrolled-high`.
- `endgame`.

Comparable keys:

```text
itemId
+ remainingSlotsBucket
+ mainStatBucket
+ attackOrMagicAttackBucket
+ cleanOrScrolledBucket
+ levelRange
+ classUseBucket
```

## Demand Design

Demand sources:

- active quest requirements.
- future quest requirements.
- job/class population growth.
- build targets.
- equipment upgrades.
- scroll usage.
- crafting/maker plans.
- event rewards.
- party/relationship requests.
- observed player purchases.
- observed fast sales.
- LLM-assigned world goals.

Demand score:

```text
demandScore =
  questDemand
  + buildDemand
  + classPopulationDemand
  + eventDemand
  + observedSalesVelocity
  + activeAgentPlanDemand
  + speculationDemand
```

## Supply Design

Supply sources:

- NPC shop availability.
- drop availability.
- mob/map farm efficiency.
- event supply.
- crafting output.
- market listings.
- agent inventories.
- known hoards/stashes.

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

## Inflation And Deflation

Track:

- total meso supply.
- meso created.
- meso destroyed.
- market basket price index.
- trade volume.
- barter share.
- agent market share.
- wealth concentration.

Meso inflation pressure:

```text
mesoInflationPressure =
  (mesoCreated - mesoDestroyed) / max(totalMesoSupply, 1)
```

Item price pressure:

```text
itemPricePressure =
  globalInflationPressure
  + itemDemandPressure
  - itemSupplyPressure
  + speculationPressure
```

NPC prices should remain stable anchors unless the operator intentionally wants
dynamic NPC prices.

## Listing Duration And Sale Velocity

Listing age matters.

Strong signals:

- fast sale at price: real demand.
- long unsold listing: likely overpriced or illiquid.
- repeated expiration: price probably too high.
- low listing unsold: low demand, bad visibility, or suspicious bait.
- instant high sale: possible urgency or manipulation.

Track:

- listing age.
- time to first sale.
- time to full sale.
- partial sell rate.
- expiration rate.
- relist count.
- price change frequency.

## Agent Economic Personalities

Profiles should create diverse behavior.

Important fields:

- frugality.
- impatience.
- farming patience.
- market curiosity.
- speculation tolerance.
- price sensitivity.
- hoarding tendency.
- meso reserve preference.
- generosity.
- social trading trust.
- shop scan patience.

Examples:

- self-sufficient farmer: farms instead of buying.
- impatient quester: overpays for blocking quest items.
- FM scout: scans often, buys rarely.
- opportunistic trader: buys underpriced goods with strict limits.
- supplier: keeps party stocked with potions/ammo.
- crafter: hoards ore/materials and evaluates craft value.
- casual grinder: vendors trash and ignores markets.

## Agent Personal Price Beliefs

Agents should not share perfect knowledge.

Each Agent remembers:

- seen prices.
- bought prices.
- failed sale prices.
- profitable items.
- bad trades.
- trusted sellers.
- suspicious sellers.
- preferred FM rooms.
- farm dry streaks.
- market confidence per item.

Personal buy price:

```text
acceptableBuyPrice =
  fairValue
  * urgencyMultiplier
  * personalityMultiplier
  * wealthMultiplier
  * confidenceAdjustment
```

Personal sell price:

```text
minimumSellPrice =
  max(vendorValue, fairValue * minimumMargin)
```

## Decision Journal

Every important economy decision should produce a journal entry.

Record:

- action chosen.
- rejected alternatives.
- item facts.
- market facts.
- profile influences.
- relationship influences.
- price confidence.
- validator result.
- final outcome.

Example summary:

```text
Agent wanted dexless Assassin build and needed Maple Claw soon. Farming had a
long dry streak, market listing was within budget, and profile was not picky.
Agent bought a below-average claw and kept meso reserve intact.
```

## Manipulation Design

Player manipulation risks:

- fake high listings.
- fake low listings.
- self-buying.
- accomplice buying.
- circular trades.
- bait listings.
- repeated relisting.
- low-volume price pumping.

Agent manipulation risks:

- flooding supply.
- synchronized undercutting.
- buying out categories.
- circular Agent trades.
- LLM coordinating too many Agents into one item.

Defenses:

- use median/trimmed median.
- separate listings from confirmed sales.
- use listing age and unsold listings.
- require independent sellers/buyers.
- downweight same-account/same-IP/same-relationship patterns.
- downweight Agent-originated trades.
- cap Agent market share.
- cap buys/listings per item per time window.
- detect circular trades.
- randomize scan/listing times.
- block high-risk items from automation.

Suspicion:

```text
suspicionScore =
  outlierPriceScore
  + circularTradeScore
  + repeatedCounterpartyScore
  + fastRelistScore
  + lowSampleHighMovementScore
  + agentMarketShareScore
```

## Validator Philosophy

Learning can adjust beliefs and preferences. It cannot bypass validators.

Validators protect:

- budget.
- meso reserve.
- quest items.
- future plan items.
- tradeability.
- listing accuracy.
- inventory space.
- cooldowns.
- market manipulation limits.
- high-value trade rules.
- operator item bans.

## Player Experience Principles

- Agents should make the world feel active, not dominate it.
- Players should be able to trade with Agents, but Agents should not be an
  infinite guaranteed buyer/seller.
- Agent prices should vary.
- Agents should sometimes miss deals.
- Agents should sometimes overpay within personality limits.
- Agents should not instantly converge to perfect market efficiency.
- Market volatility should come from behavior and events, not pure noise.

## Success Criteria

- Mesos remain the main displayed and internal valuation unit.
- Barter share is monitored and does not silently replace meso pricing.
- Agents make different decisions for the same item.
- Price summaries explain confidence and risk.
- Rare equips are valued by stats, not only item id.
- Scrolls are valued by expected upgrade value and demand.
- Unsold listings affect price belief.
- Agent-originated trades do not define player market truth.
- Economy actions produce decision journal entries.
- LLM can explain and propose but cannot execute unsafe actions.

