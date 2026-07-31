# Agent Demand Portfolio Specification

Status: design for review. No autonomous market execution is enabled by this
document.

This document defines the internal system behind an Agent-facing "wishlist".
The preferred technical name is **Agent Demand Portfolio** because the system
must represent quantities, alternatives, future equipment dependencies,
personal value, and acquisition plans rather than only a flat list of item IDs.

Related documents:

- `ECONOMY_ENGINE_VISION_AND_OVERVIEW.md`
- `ECONOMY_DESIGN_SPECIFICATION.md`
- `ECONOMY_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md`
- `ECONOMY_ENGINE_TODO.md`
- `ADAPTIVE_ECONOMY_SYSTEM_PLAN.md`
- `../QUEST_ITEM_DEMAND_AND_DISPOSITION.md`

## 1. Purpose

The Demand Portfolio should answer:

- Which items would improve this Agent's current or future progression?
- Which exact items or acceptable substitutes satisfy the need?
- How many copies are needed, already owned, reserved, or missing?
- Is an inventory item personally useful, useful to another Agent, marketable,
  safe to NPC-sell, or safe to discard?
- What is the most reasonable acquisition method: use an owned item, transfer,
  trade, NPC purchase, Free Market purchase, quest reward, or hunting?
- How much should this Agent personally be willing to pay?
- At what price should this Agent prefer a market sale over an NPC sale?
- Why was a demand entry created, committed, satisfied, replaced, or expired?

The portfolio is an input to valuation, disposition, acquisition, and planning.
It must never mutate inventory, mesos, quests, trades, shops, or character
state directly.

## 2. Terminology

Use **wishlist** only as a presentation term.

Use these technical terms:

- `AgentDemandPortfolio`: all current demand entries for one Agent.
- `DemandEntry`: one quantified need and its evidence.
- `ItemSelector`: an exact item or a predicate describing acceptable items.
- `LoadoutBundleGoal`: a group of interdependent equipment and upgrade needs.
- `PersonalReservationPrice`: the maximum price this Agent may rationally pay.
- `MarketDemandSignal`: anonymized, bounded aggregate demand across Agents.
- `AcquisitionProposal`: a read-only proposal for satisfying a shortfall.
- `DispositionProposal`: a read-only proposal for keeping, transferring,
  listing, NPC-selling, or discarding an owned quantity.

## 3. Design Principles

1. **Needs are not prices.** Portfolio demand may influence price confidence
   and pressure, but must not directly define market price.
2. **Candidates are not commitments.** The Agent may know about many useful
   items while actively pursuing only a small bounded set.
3. **Selectors allow substitutes.** A goal should not become blocked because
   one exact item is unavailable when an acceptable alternative exists.
4. **Build feasibility is evaluated as a whole.** An item that requires extra
   stats may need a loadout bundle rather than an isolated wishlist entry.
5. **Reservations are authoritative for protection.** Committed needs protect
   quantities through the shared inventory reservation ledger.
6. **Proposals do not mutate.** Economy services propose; universal plans and
   validated capabilities execute.
7. **All decisions carry evidence.** Every keep, sell, buy, trade, or farm
   recommendation must be explainable.
8. **Derived candidates are rebuildable.** Catalog or build-profile changes
   may regenerate candidates without corrupting committed progress.
9. **Market knowledge is imperfect.** Confidence, observation age, liquidity,
   and manipulation risk remain visible.
10. **The LLM uses the same contracts.** It may propose goals or change
    preferences, but cannot bypass deterministic policy or server validation.

## 4. Existing Foundations

The following systems already provide useful foundations:

| Foundation | Current use |
| --- | --- |
| Career/build bundles and AP profiles | Identify job path and projected stat distribution |
| Equipment optimizer | Compare complete feasible loadouts and equipment improvement |
| Equipment reserve policy | Protect useful owned equipment |
| Quest item demand index | Forecast active, committed, and future quest quantities |
| Inventory reservation ledger | Share protected quantities across capabilities |
| Item disposition proposals | Explain quest-aware keep/transfer/store/NPC-sell decisions |
| Item/drop/shop/source catalogs | Describe acquisition sources |
| Supply purchasing policy | Buy validated HP/MP/ammunition/travel supplies |
| Trade capability | Execute validated Agent/player trades |
| Free Market stall capability | Open a real shop from explicit validated listings |
| Universal plan runtime | Execute suspendable and resumable objectives |

The missing connection is a portable personal-demand and valuation layer that
turns career, AP, equipment, quest, supply, and market facts into bounded,
explainable demand and acquisition proposals.

## 5. Demand Entry Contract

A portable entry should contain at least:

```text
demandId
agentId
itemSelector
purpose
desiredQuantity
ownedQuantity
reservedQuantity
shortfallQuantity
priority
utilityScore
levelHorizon
deadline
status
allowedAcquisitionMethods
personalReservationPrice
minimumSalePrice
source
evidence
catalogRevision
buildRevision
createdAt
updatedAt
expiresAt
```

### 5.1 Purpose

Initial purposes:

- `EQUIP_NOW`
- `EQUIP_FUTURE`
- `LOADOUT_COMPONENT`
- `UPGRADE_BASE`
- `UPGRADE_INPUT`
- `ACTIVE_QUEST`
- `COMMITTED_QUEST`
- `FUTURE_QUEST`
- `SUPPLY_RESERVE`
- `TRADE_STOCK`
- `COLLECTIBLE`

`TRADE_STOCK` must never outrank protected personal progression needs.
Speculative stock should be added later and remain bounded by a separate risk
policy.

### 5.2 Status

```text
CANDIDATE
COMMITTED
ACQUIRING
SATISFIED
BLOCKED
OBSOLETE
EXPIRED
```

State rules:

- Candidate entries are read-only possibilities and create no inventory lock.
- Committed entries reserve the required quantity.
- Acquiring entries have an accepted acquisition proposal and plan linkage.
- Satisfied entries retain outcome evidence for a bounded journal period.
- Obsolete entries are superseded by a better goal or invalid build/catalog
  revision.
- Blocked entries record the reason and retry/review policy.

### 5.3 Item Selector

Selectors may be:

- exact `itemId`.
- any item in an equipment slot.
- a compatible weapon family.
- an equip predicate with required level, job, stat, attack, slots, or quality.
- one of a ranked substitute set.
- a stackable item category and potency range.

Example:

```text
compatible claw usable by level 25
AND expected damage improvement >= 8%
AND feasible under projected AP/build dependencies
AND acquisition cost <= portfolio budget
```

Exact item goals remain valid for quests, collectibles, or intentional build
milestones.

## 6. Demand Sources And Precedence

Demand generators remain separate and publish entries into the portfolio:

1. Active objective and quest requirements.
2. Committed universal plans.
3. Survival and supply safety floors.
4. Current equipment upgrade opportunities.
5. Future equipment/loadout milestones from career and AP projection.
6. Upgrade bases and scroll/material dependencies.
7. Explicit social/cohort requests.
8. Marketable inventory opportunities.
9. Personality preferences.
10. Operator or LLM proposals after validation.

When entries overlap, merge compatible quantities and preserve all evidence.
Do not double-count a quantity needed by both an active objective and its
committed parent plan.

## 7. Build And AP-Derived Equipment Demand

### 7.1 Inputs

The equipment demand generator consumes:

- durable career/build-bundle assignment.
- selected AP profile.
- current level, job, base stats, and projected AP.
- current equipment and bag inventory.
- item requirements and stat data.
- weapon compatibility and skill requirements.
- equipment optimizer recommendations.
- known item sources and estimated acquisition cost.
- expected useful lifetime before likely replacement.

### 7.2 Milestones

Evaluate at bounded milestones instead of every possible future level:

- current level.
- current level plus 5.
- current level plus 10.
- next job advancement.
- explicit build milestone, such as level 30.

Re-evaluate after:

- level or job advancement.
- AP profile change.
- equipment acquired, equipped, scrolled, traded, or sold.
- relevant catalog revision.
- material market-price change above a configured threshold.

### 7.3 Candidate Ranking

For each slot or weapon family:

1. Filter job-incompatible items.
2. Project whether requirements can be met at the target milestone.
3. Evaluate the best complete loadout containing the item.
4. Measure marginal combat/progression improvement against the current or
   expected loadout.
5. Estimate useful lifetime.
6. Estimate acquisition time, meso cost, travel, risk, and dependencies.
7. Keep only a small Pareto frontier of candidates.
8. Commit only the best justified goal within portfolio and budget limits.

Do not wishlist every technically better item. This would create unrealistic
demand, inventory hoarding, and price distortion.

### 7.4 Loadout Bundle Goals

Some builds require jointly planned equipment.

Example: a low-DEX claw thief may want a stronger claw whose DEX requirement
cannot be met by base AP alone. The demand generator should compare:

- the target claw.
- a weaker immediately usable claw.
- DEX-providing overall, shoes, cape, earrings, or other equipment.
- relevant scrolls and expected success.
- total meso/time cost and useful lifetime.

The goal is committed only if the complete dependency bundle has greater
expected progression value than its alternatives. If any required component
becomes infeasible, the bundle is re-evaluated rather than leaving unrelated
items permanently protected.

## 8. Personal Utility And Reservation Prices

Market value and personal value are separate.

Suggested normalized utility components:

```text
personalUtility =
    progressionGain
  + survivalGain
  + planBlockerRelief
  + usefulLifetime
  + personalityPreference
  - acquisitionTime
  - travelAndDeathRisk
  - dependencyRisk
  - inventoryCarryingCost
  - opportunityCost
```

Convert utility into a bounded meso reservation price through a deterministic
policy:

```text
maxBuyPrice =
  min(
    budgetCap,
    fairMarketUpperBound * urgencyMultiplier,
    progressionValueInMesos - expectedAlternativeCost
  )
```

The result must preserve:

- minimum supply/operating meso reserve.
- per-item and per-category exposure limits.
- confidence-based discounts.
- anti-manipulation ceilings.
- personality variation within global bounds.

Minimum sale price:

```text
minSellPrice =
  max(
    npcSaleValue,
    replacementCostIfProtected,
    expectedMarketNetValue,
    committedTradeValue
  )
```

For unprotected surplus:

```text
expectedMarketNetValue =
    expectedSalePrice * probabilityOfSale
  - listingCost
  - carryingCost
  - expectedWaitingCost
  - riskDiscount
```

NPC-selling is preferred when expected market net value does not safely exceed
the NPC value.

## 9. Inventory Disposition Precedence

The unified disposition service should evaluate:

1. Equipped, locked, unique, or operator-protected item.
2. Active quest/objective reservation.
3. Committed plan reservation.
4. Committed loadout goal or required dependency.
5. Committed upgrade base or input.
6. Supply safety reserve.
7. Existing shared capability reservation.
8. Near-term personal candidate demand.
9. Confirmed cohort/Agent demand.
10. Marketable trade stock.
11. Safe NPC-sale surplus.
12. Emergency discard.

Every proposal includes:

- owned quantity.
- protected quantity.
- proposed quantity.
- destination or action.
- precedence.
- valuation evidence.
- demand evidence.
- catalog/build/market revisions.

The shared reservation ledger remains the authoritative protection mechanism.
The Demand Portfolio does not maintain a competing lock system.

## 10. Aggregate Demand And Price Formation

World demand is derived from anonymized shortfalls, not raw wishlist counts.

```text
portfolioDemand =
    committedShortfall * committedWeight
  + nearTermCandidateShortfall * candidateWeight
  + speculativeShortfall * speculativeWeight
```

Required safeguards:

- one bounded contribution per Agent and item/valuation key.
- no contribution from satisfied, obsolete, or expired entries.
- committed demand weighs more than candidate demand.
- time decay.
- build/population segment evidence.
- no double-counting across merged entries.
- separation between Agent and observed player demand.
- protection against self-trade and circular Agent demand.

Portfolio demand is a leading indicator. Fair value should primarily use:

- confirmed completed trades.
- credible listings and listing age.
- NPC price anchors.
- expected farming/replacement cost.
- liquidity and time-to-sale.
- item/equip quality bucket.
- market confidence and manipulation risk.

Demand pressure may adjust a fair-value range within policy bounds. It must not
set the price directly.

## 11. Acquisition Proposals

For a committed shortfall, compare:

1. Use an already owned unreserved item.
2. Withdraw from personal storage.
3. Transfer from cohort storage or another Agent.
4. Buy from an NPC.
5. Buy from a player or Free Market listing.
6. Obtain from a quest or crafting system.
7. Hunt a drop source.
8. Acquire an acceptable substitute.
9. Postpone or abandon the goal.

Rank methods using:

- total meso cost.
- expected completion time.
- travel time.
- drop probability and source concentration.
- current level/combat capability.
- death and resource risk.
- map crowd/capacity.
- interruption cost to the active plan.
- market confidence.
- item substitution quality.

An `AcquisitionProposal` contains no mutation callback. Once accepted, it is
converted into universal plan objectives and executed through existing
capability validators.

Minor upgrades should normally wait between objectives. A survival blocker,
required weapon, or committed plan dependency may suspend the current
objective through the remediation supervisor and resume it afterward.

## 12. Lifecycle And Events

Recommended lifecycle:

```text
facts changed
-> candidate generation
-> candidate ranking
-> commitment
-> inventory reservation
-> acquisition proposal
-> policy validation
-> universal plan
-> capability execution
-> economy/item events
-> portfolio reconciliation
```

Relevant events:

- `DemandCandidateCreated`
- `DemandCommitted`
- `DemandBlocked`
- `DemandSatisfied`
- `DemandObsoleted`
- `AcquisitionProposed`
- `AcquisitionAccepted`
- `AcquisitionRejected`
- `ItemLooted`
- `ItemBought`
- `ItemTraded`
- `ItemEquipped`
- `ItemUpgraded`
- `ItemListed`
- `ItemSold`
- `ItemNpcSold`

Event handlers update projections and journals. They do not perform the
original economic mutation again.

## 13. Persistence And Reconciliation

Persist:

- committed and acquiring entries.
- loadout bundle progress.
- accepted proposal and universal-plan linkage.
- reservation provenance.
- last reconciliation revision.
- compact decision/outcome evidence.

Rebuild:

- uncommitted candidates.
- derived utility scores.
- market-pressure summaries.
- substitute rankings.

At login, relog, restart, or catalog migration:

1. Load durable career/build and committed portfolio state.
2. Re-read authoritative inventory, equipment, quests, mesos, and plans.
3. Reconcile quantities idempotently.
4. Rebuild reservations from valid commitments.
5. Mark satisfied, missing, invalid, or obsolete entries.
6. Resume only the still-valid universal objective.

## 14. LLM Integration Boundary

Read-only LLM queries may expose:

- current portfolio summary.
- why an item is wanted or protected.
- current shortfalls and substitutes.
- farm-versus-buy comparison.
- price range and confidence.
- why an item is proposed for NPC sale, market sale, trade, or storage.

Proposal-only LLM actions may:

- suggest a candidate item or loadout goal.
- suggest a priority change.
- suggest a budget within policy bounds.
- request an acquisition comparison.
- request an economy plan proposal.

The LLM must not:

- directly reserve or release inventory.
- directly buy, sell, trade, list, equip, scroll, or discard.
- directly change mesos or market prices.
- bypass build feasibility, budget, risk, anti-abuse, plan, or capability
  validators.

LLM proposals use the same `DemandEntry` and `AcquisitionProposal` contracts as
deterministic generators. Their provenance is recorded as `LLM_PROPOSAL`.

## 15. Observability

Record enough evidence to answer:

- Why did this Agent want the item?
- Which AP/build projection made it useful?
- Which substitutes were rejected and why?
- Why was a quantity reserved?
- Why was an item NPC-sold instead of market-listed?
- Why did the Agent farm rather than buy?
- What price and confidence were used?
- Did the acquisition improve the expected or actual loadout?
- Was the goal later replaced or made obsolete?

Metrics:

- candidate, committed, satisfied, blocked, obsolete counts.
- average time and mesos to satisfy demand.
- acquisition method distribution.
- projected versus realized equipment improvement.
- protected-item violation count.
- NPC-sale versus expected market-net difference.
- market listing success and time-to-sale.
- portfolio demand contribution by purpose and confidence.
- LLM proposal acceptance/rejection reasons.

## 16. Rollout

### Phase 1: Read-Only Demand

- Define portable portfolio contracts.
- Generate equipment candidates from career/AP projection.
- Import quest and supply demand as evidence.
- Expose diagnostics only.

### Phase 2: Protection And Disposition Shadow Mode

- Commit a bounded number of equipment goals.
- Publish committed quantities into the reservation ledger.
- Merge personal demand into disposition proposals.
- Log what would be kept, transferred, listed, or NPC-sold.
- Confirm zero reserved-item violations.

### Phase 3: Read-Only Acquisition Planning

- Rank owned, transfer, NPC, market, quest/craft, hunt, and substitute methods.
- Compare recommendations with known successful progression plans.
- Record expected cost/time and actual outcomes without autonomous execution.

### Phase 4: Safe Acquisition Execution

- Enable owned-item use, storage withdrawal, cohort transfer, NPC purchase, and
  bounded hunting through universal plans.
- Prove suspend/resume and idempotent recovery.
- Keep Free Market purchases disabled initially.

### Phase 5: Market Observation And Valuation

- Store listings, confirmed trades, expirations, and time-to-sale.
- Produce fair-value ranges, liquidity, confidence, and risk.
- Feed bounded demand pressure into summaries.

### Phase 6: Autonomous Market Actions

- Enable conservative player/FM purchases.
- Enable listing selection, pricing, repricing, proceeds, close, and unsold
  recovery.
- Enforce market-share, budget, counterparty, self-trade, and manipulation
  controls.

### Phase 7: LLM Economy Support

- Add read-only portfolio and valuation tools.
- Add proposal-only goal and acquisition tools.
- Measure proposal acceptance, rejection, cost, and outcome quality.

## 17. Recommended First Implementation Slice

1. Add portable `DemandEntry`, `AgentDemandPortfolio`, `ItemSelector`,
   `DemandPurpose`, `DemandStatus`, and evidence contracts.
2. Add career/AP milestone projection.
3. Build equipment candidate generation on the existing optimizer.
4. Add `LoadoutBundleGoal` for stat-dependent equipment.
5. Publish committed shortfalls to the shared reservation ledger.
6. Extend disposition proposals with equipment, supply, and confirmed external
   demand.
7. Run disposition in shadow mode and journal every decision.
8. Add read-only acquisition proposals using existing source/shop catalogs.
9. Compare proposals against successful level-15 and future level-30 runs.
10. Enable only safe universal-plan acquisition methods after review.

## 18. Review Decisions

The following should be explicitly reviewed before implementation:

- Maximum committed equipment goals per Agent.
- Milestone levels and candidate frontier size.
- Utility-to-meso conversion policy.
- How long candidates and trade stock remain valid.
- Whether Agent-to-Agent demand is world-wide, cohort-first, or both.
- Storage and inventory carrying-cost policy.
- Which equipment quality buckets are required initially.
- First supported acquisition methods.
- Shadow-mode evidence required before NPC sale or FM automation.
- Persistence storage and schema migration strategy.
- Market-share and circular-trade safeguards.

## 19. Acceptance Criteria

The first production-safe version is acceptable when:

- the same job with different AP profiles produces explainably different
  equipment goals where appropriate.
- stat-dependent loadout bundles do not protect unusable pieces forever.
- active/committed quest and supply reservations remain intact.
- no protected item is sold, traded, listed, or discarded.
- safe surplus decisions explain personal, cohort, market, and NPC value.
- acquisition proposals compare alternatives without direct mutation.
- accepted proposals execute through universal plans and validated
  capabilities.
- restart/relog reconciliation is idempotent.
- aggregate demand cannot be inflated by duplicate wishes or circular Agent
  activity.
- LLM proposals cannot bypass deterministic policy.

