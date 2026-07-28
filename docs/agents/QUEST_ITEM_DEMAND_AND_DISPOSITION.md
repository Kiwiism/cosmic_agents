# Quest Item Demand and Disposition

This subsystem answers one bounded question: **how many copies of an ETC item should an
Agent protect before suggesting a transfer, storage, or NPC sale?** It does not choose
quests, mutate quest state, or let an LLM perform inventory mutations.

## Generated facts

`tools/agent-llm-catalog/Export-VictoriaAdaptiveQuestCatalogs.ps1` generates
`victoria-quest-item-demand-index.json` from the same Cosmic quest facts used by the
adaptive hunt catalogs. Each entry records the item, every quest that consumes it, the
required count, level range, job restrictions, prerequisites, autonomous-selection
eligibility, and the shared source revision.

Regenerating the catalog after WZ, quest script, drop, or quest-chain changes creates a
new revision. Runtime policy consumes the generated resource and never hand-maintains a
second item-demand table.

## Character-specific demand categories

The read-only forecast filters completed and ineligible quests, then classifies remaining
demand:

1. `ACTIVE` — the character has already started the quest.
2. `COMMITTED` — the current durable plan has committed to the quest.
3. `WITHIN_5_LEVELS` — an eligible autonomous quest within ±5 levels.
4. `WITHIN_15_LEVELS` — an eligible autonomous quest within ±15 levels.
5. `WITHIN_25_LEVELS` — an eligible autonomous quest within ±25 levels.

Job, planned first job, prerequisite state, minimum/maximum level, and autonomous-start
policy are evaluated before a future quest contributes demand. Active quests remain
authoritative even if the character has since changed job.

## Reservation calculation

For each item:

```text
authoritative demand = active demand + committed demand
quest forecast       = active + committed + 5-level + 15-level + 25-level demand
protected quantity   = min(owned, max(existing reservation, quest forecast))
safe surplus         = max(0, owned - protected quantity)
```

Only active and committed demand is written into the shared inventory reservation ledger.
The ledger records the full required quantity, including items not collected yet, so new
drops are protected without waiting for the next forecast refresh.
Future horizons remain read-only forecasting evidence. The disposition policy nevertheless
protects the full configured horizon before proposing a sale, making the initial rollout
deliberately conservative.

## Disposition precedence

Every proposal uses one stable precedence:

1. Keep for an active quest.
2. Keep for a committed quest.
3. Keep an existing capability reservation.
4. Keep for an eligible quest within 5 levels.
5. Keep for an eligible quest within 15 levels.
6. Keep for an eligible quest within 25 levels.
7. Suggest transfer to a cohort member with an evidenced shortage.
8. Suggest storage when an authoritative storage destination is available.
9. Sell only clearly safe ETC surplus.
10. Hold for review when no earlier action is proven safe.

Transfer and storage are proposals only. They deliberately do not invent delivery,
ownership, or storage mutations.

## Shop rollout

`AgentQuestItemSellMode.MODE` controls NPC-shop integration:

- `0`: disabled.
- `1`: shadow mode; log the exact sale proposal and evidence without selling.
- `2`: enforced; sell only catalog-known, tradeable, non-quest-classified ETC surplus.

Enforced mode recalculates live owned and reserved quantities immediately before every
sale. Unknown ETC items and any item without a positive proven surplus are never included.
Promotion from shadow to enforced should require logs showing no reserved-item violation.

## LLM integration contract

An LLM may consume the versioned forecast and disposition proposal as structured context.
It may recommend a quest, transfer, storage action, or sale, but it cannot mutate Cosmic
directly. The deterministic policy remains authoritative:

```text
character snapshot
  -> versioned demand forecast
  -> typed inventory reservations
  -> read-only disposition proposal
  -> deterministic validation at the mutation boundary
  -> audited capability action
```

This keeps explanations and future dialogue flexible while inventory safety, idempotency,
and plan ownership remain inside the Agent engine.
