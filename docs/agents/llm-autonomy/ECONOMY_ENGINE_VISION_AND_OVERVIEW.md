# Economy Engine Vision And Overview

This document explains the Economy Engine in a reader-friendly way. It is meant
for understanding the vision, behavior, safeguards, and future potential without
reading the full technical specification first.

## Short Version

The Economy Engine is a portable decision and learning system for market
behavior.

It does not directly buy items, sell items, trade, create mesos, change prices,
or mutate the server. Instead, it observes the world, estimates value, proposes
economic actions, and explains why those actions make sense.

In simple terms:

```text
Catalog tells what an item is.
Market observations tell what players and Agents are doing.
Economy engine estimates value and risk.
Agent profile decides personal preference.
Plan runtime turns approved choices into objectives.
Capabilities execute only after live server validation.
```

## What The Economy Engine Is

The Economy Engine is the system that helps Agents understand economic value.

It should answer questions like:

- Should this Agent sell this item, keep it, vendor it, trade it, or use it?
- Is this market price cheap, normal, expensive, or suspicious?
- Is it better to farm an item or buy it?
- Is demand rising because many Agents are becoming assassins?
- Is a scroll worth more because a new event reward has better upgrade slots?
- Is a player trying to manipulate the price?
- How much should a stat-bearing equip be worth?
- How long was an item listed before it sold?
- Is meso still healthy as the main currency?

The economy engine is not a shop bot by itself. It is a market brain and
decision service.

## What It Preserves

The design preserves important MapleStory economy principles:

- mesos remain the main unit of value.
- NPC shop prices provide fixed baselines for some items.
- drops, quest demand, crafting demand, and class demand affect value.
- rare items and scrolls can become valuable stores of value.
- equips are not priced linearly because stats and slots matter.
- players remain first-class market participants.
- Agents do not receive perfect market knowledge.
- market behavior should be explainable, not magic.

The engine should not flatten the economy into one fixed price table. It should
let prices move, but with safeguards.

## What It Hopes To Achieve

### 1. Agents Make Better Economic Decisions

Without an economy engine, Agents may sell useful items, overpay for bad items,
or hoard junk forever.

With an economy engine, Agents can decide:

- keep quest items.
- preserve future crafting materials if their profile cares.
- sell common junk.
- list useful drops at reasonable prices.
- farm an item instead of buying it if the market is too expensive.
- buy a blocker item if it saves too much time.

### 2. Mesos Stay Meaningful

The economy should avoid collapsing into item-only barter.

Mesos stay useful because:

- NPC shops require mesos.
- taxes are paid in mesos.
- utility fees are paid in mesos.
- valuations are shown in mesos.
- Agents enforce meso reserve policies.
- trade friction discourages pure item-currency replacement.

Some items can still become valuable stores of value, but the engine should
monitor when an item starts behaving like a replacement currency.

### 3. Prices Become Dynamic

Prices should respond to:

- supply.
- demand.
- farming difficulty.
- class population.
- quest requirements.
- event rewards.
- crafting needs.
- scroll value.
- listing age.
- actual sale velocity.
- inflation or deflation.
- player and Agent behavior.

This makes the world feel more alive than a fixed static value list.

### 4. Agents Behave Differently

Not every Agent should make the same market decision.

Examples:

- a crafter keeps ore materials.
- a casual grinder vendors more junk.
- a merchant holds items longer.
- a frugal Agent farms instead of buying.
- an impatient Agent buys quest blockers.
- a dexless assassin values different claws than a normal thief.

The economy engine gives facts and options. The profile system decides how much
the individual Agent cares.

### 5. Market Manipulation Is Damped

Players may list items far too high or too low. Players may also buy their own
items or coordinate with another account to fake demand.

The engine should not trust every observation equally.

It should consider:

- listing age.
- whether an item actually sold.
- repeated self-like trading patterns.
- outlier prices.
- seller/buyer concentration.
- volume.
- liquidity.
- whether price movement matches real demand.

Suspicious observations should be downweighted, not blindly accepted.

## Main Design Ideas

### Observation Instead Of Authority

What it means:

```text
The economy engine observes the market. It does not define the market by force.
```

Why use it:

- players still shape the world.
- Agents respond to actual activity.
- bad observations can be weighted by confidence.
- the server remains authoritative.

### Baseline Value Model

What it means:

```text
Every item should have a starting value estimate before live market data exists.
```

Sources:

- NPC shop price.
- NPC vendor price.
- drop difficulty.
- quest demand.
- crafting demand.
- job/build demand.
- rarity.
- event availability.
- scroll/equip usefulness.

Why use it:

- new markets do not start from zero knowledge.
- Agents can make early decisions before enough sales exist.
- price estimates stay grounded.

### Market Observation Log

What it means:

```text
Listings, sales, trades, expired listings, and price changes are stored as
observations.
```

Why use it:

- sale price is more meaningful than listing price.
- listing age matters.
- unsold items are useful negative evidence.
- repeated observations build confidence.
- suspicious outliers can be detected later.

### Confidence-Based Pricing

What it means:

```text
Every price estimate has confidence.
```

Example confidence levels:

- unknown.
- few samples.
- observed.
- stable.
- volatile.
- suspicious.
- manual-review.

Why use it:

- Agents should not overreact to one weird listing.
- LLM summaries can be honest about uncertainty.
- market manipulation is easier to dampen.

### Personal Belief Layer

What it means:

```text
The global market estimate and an individual Agent's belief are separate.
```

Example:

- global market says an item is worth 100,000 mesos.
- one Agent saw several unsold listings and thinks it is harder to sell.
- another Agent sold one quickly and is more confident.

Why use it:

- Agents behave less identically.
- individual histories matter.
- market behavior feels less robotic.

### Decision Journal

What it means:

```text
Important economy choices record why they happened.
```

Example:

```text
Agent wanted Maple Claw for dexless build.
Farmed for 10 hours with no drop.
Market listing was cheap enough.
Agent bought a below-average claw and used safe scrolls.
```

Why use it:

- easier debugging.
- easier balancing.
- useful for LLM summaries.
- lets you study Agent lifecycle decisions.

### Proposal, Not Execution

What it means:

```text
Economy engine proposes actions. Capabilities execute after validation.
```

Example:

```text
Economy proposes: buy 10 orange potions if price <= X.
Plan runtime creates objective.
Shop/market capability validates mesos, item, range, and policy.
Server adapter executes if live state allows.
```

Why use it:

- avoids unsafe direct mutations.
- keeps market logic portable.
- keeps server validation central.

## Economic Inputs

### Static Catalog Data

Catalog tells:

- item category.
- item name.
- NPC shop price.
- vendor price.
- drop sources.
- mob level and map.
- quest requirements.
- quest rewards.
- crafting/maker recipes later.
- scroll effects.
- equip stat ranges.
- event reward sources later.

Static catalog answers:

```text
What is this item and where can it come from?
```

### Live Market Observations

Live observations include:

- FM listings seen.
- listing price changes.
- listing age.
- expired listings.
- sold listings.
- direct trades.
- merchant sales.
- Agent buy/sell outcomes.
- NPC shop snapshots.

Live observations answer:

```text
What are people actually trying to buy and sell?
What actually sells?
```

### Agent And World Signals

Signals include:

- class/job population.
- plan demand.
- quest bottlenecks.
- crafting demand.
- event effects.
- item creation/destruction.
- meso creation/destruction.
- tax rate.
- potion/ammo consumption.

These answer:

```text
Why might demand or supply change?
```

## Price Formation

The engine should combine:

```text
baseline value
+ live sale evidence
+ listing evidence
+ supply/demand pressure
+ liquidity
+ inflation/deflation
+ item-specific rules
+ manipulation risk adjustment
= estimated market value
```

### Fixed-Price Items

Some items have a natural baseline:

- NPC shop consumables.
- travel/utility-related items.
- items with stable vendor value.

For these, market price should not drift too far without reason because buyers
can compare against fixed NPC price.

### Drop-Only Items

Drop-only items start from:

- drop source difficulty.
- expected farming time.
- mob/map accessibility.
- demand from quests/crafting/builds.
- observed sales.

### Scrolls

Scroll value depends on:

- equipment category.
- success rate.
- stat gained.
- slot value.
- job demand.
- risk appetite.
- expected value.
- rarity.

### Equips

Equips need a separate valuation model because stats are nonlinear.

Important factors:

- item level.
- required job/stats.
- clean stat roll.
- weapon attack/magic attack.
- remaining slots.
- successful scrolls.
- failed slots.
- rare stat combinations.
- build compatibility.
- class population demand.

Reason:

```text
Each additional good stat can be exponentially harder to achieve, so price
should not increase linearly.
```

## Market Manipulation Safeguards

The economy engine should resist manipulation through:

- outlier detection.
- listing-age tracking.
- sale confirmation weighting.
- repeated buyer/seller pattern detection.
- volume checks.
- confidence decay.
- per-Agent budget limits.
- cooldowns on repeated actions.
- max price deviation policies.
- relationship/counterparty risk.

Example:

```text
One player lists item for 1 meso.
Engine records it as listing_seen.
It does not instantly reset the market price.
If it sells instantly to a related buyer, risk increases.
If many unrelated sales occur near that price, confidence rises slowly.
```

## Tax And Meso Stability

The starting tax can be 1%.

In a real economy, tax pressure may rise when:

- inflation is high.
- too many mesos are being created.
- market velocity is too high.
- speculation is excessive.
- item-currency substitutes are replacing mesos too much.

Tax pressure may fall when:

- deflation is strong.
- market activity is too low.
- new players/Agents cannot afford progression.
- liquidity is weak.

In-game, tax changes should be conservative. The economy engine can recommend
tax adjustments or simulate them, but server config changes should remain an
operator decision.

## Keeping Mesos Valuable

Methods:

- preserve NPC meso sinks.
- use trade/listing tax.
- add or tune utility fees carefully.
- keep common progression items priced in mesos.
- track item-currency substitution.
- prevent Agents from overusing barter.
- give Agents meso reserve policies.
- avoid unlimited meso generation from Agent farming.
- monitor inflation index.

The goal is not to stop valuable item trading. The goal is to prevent mesos from
becoming irrelevant.

## Agent Economy Behavior

Agents should make economy decisions through their profile and plan context.

Examples:

### Sell Trash Plan

Agent decides what to sell based on:

- quest needs.
- future plan needs.
- item value.
- inventory pressure.
- crafting personality.
- market demand.
- rarity.

A crafter keeps ores. A non-crafter may sell them.

### Buy Quest Blocker

Agent may buy an item when:

- farming time is too high.
- market price is acceptable.
- item is needed for current plan.
- meso reserve remains safe.
- profile is impatient or efficiency-focused.

### Farm Instead Of Buy

Agent may farm when:

- market price is too high.
- drop source is reachable.
- profile is self-sufficient.
- farming also gives EXP or other useful drops.

### Hold For Future Demand

Agent may hold when:

- event or class trend suggests price may rise.
- item is rare or liquid.
- profile is merchant/speculator.
- inventory pressure is low.

## Economy And Plan Cards

Economy decisions should become plan cards or objectives.

Examples:

- sell trash.
- buy potion supply.
- farm item for quest.
- scan Free Market for target item.
- list item in merchant.
- hold item until price improves.
- craft item for profit.

The economy engine should not directly run these. It proposes them. The plan
runtime schedules them. Capabilities execute them.

## Economy And Profiles

Profiles make economic behavior diverse.

Profile dimensions:

- frugality.
- patience.
- greed.
- risk tolerance.
- market curiosity.
- self-sufficiency.
- crafting interest.
- hoarding tendency.
- quality pickiness.
- wealth level.
- job/build direction.

This prevents all Agents from making the same buy/sell decision.

## Economy And LLM

The LLM can use the economy engine to reason about strategy.

Allowed:

- ask for price summaries.
- ask why an Agent bought/sold/held.
- compare farming versus buying.
- assign an economy-related plan.
- request a profile/economy strategy change.

Not allowed:

- directly buy.
- directly sell.
- directly trade.
- directly change prices.
- directly change mesos or inventory.
- bypass validators.

## What This Does Not Do Yet

The current state is planning and catalog preparation.

It does not yet implement:

- runtime market observation store.
- confirmed sale tracking.
- live Free Market scanner.
- item valuation runtime.
- equip stat pricing runtime.
- manipulation risk engine.
- inflation/deflation model.
- Agent buy/sell/hold execution.
- economy plan generation runtime.
- LLM economy tools.

Those are planned as future packages.

## Benefits

### For Agents

- better item decisions.
- less junk hoarding.
- safer item selling.
- more believable market behavior.
- individual economic personalities.

### For Server Economy

- prices can evolve.
- mesos can remain meaningful.
- item-currency substitution can be monitored.
- manipulation can be dampened.
- supply/demand can react to population and events.

### For Debugging

- decisions are explainable.
- market confidence is visible.
- suspicious prices can be reviewed.
- Agent lifecycle journals show economic reasoning.

### For LLM

- LLM gets compact market summaries.
- LLM can assign high-level economic goals.
- LLM does not need raw database access.
- LLM cannot bypass server validation.

## Roadmap In Plain Language

### Step 1: Build Static Baselines

Use catalogs to estimate item value before live market data exists.

### Step 2: Record Observations

Record listings, sales, expired listings, trades, and Agent outcomes.

### Step 3: Learn Market State

Build per-item market summaries:

- price range.
- confidence.
- volume.
- supply.
- demand.
- volatility.
- liquidity.

### Step 4: Add Agent Beliefs

Let each Agent keep imperfect personal market memory.

### Step 5: Make Decisions

Generate buy/sell/hold/farm/vendor/stash recommendations.

### Step 6: Convert To Plans

Turn approved decisions into plan objectives.

### Step 7: Execute Through Capabilities

Buy/sell/trade/shop actions happen only through validated capabilities.

### Step 8: Learn From Outcomes

Feed sale success, failed purchases, listing age, and farming results back into
the economy model.

## Final Vision

The Economy Engine should make the world feel economically alive without giving
Agents unfair perfect knowledge or unsafe direct control.

It should help Agents make believable economic choices, keep mesos relevant,
support dynamic prices, resist manipulation, and provide clear explanations for
why market decisions happened.

The long-term goal is not just an automated market bot. The goal is a portable
economic reasoning layer that Agents, profiles, plans, and LLM systems can all
use safely.
