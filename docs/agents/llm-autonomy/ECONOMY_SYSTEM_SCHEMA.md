# Economy System Schema

The economy layer lets agents farm, buy, sell, craft, and discover Free Market
prices without giving the LLM unsafe direct control over server state.

Portable JSON contracts:

- `docs/agents/llm-autonomy/economy-market-observation.schema.json`
- `docs/agents/llm-autonomy/economy-market-item-state.schema.json`
- `docs/agents/llm-autonomy/economy-decision.schema.json`

These schemas describe observations, compact market state, and decision
proposals. They do not execute buys, sells, trades, listings, or inventory
mutations.

Implementation backlog: `docs/agents/llm-autonomy/ECONOMY_ENGINE_TODO.md`.

Layered adaptive market architecture:
`docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md`.

Full design specification:
`docs/agents/llm-autonomy/ECONOMY_DESIGN_SPECIFICATION.md`.

Full technical implementation specification:
`docs/agents/llm-autonomy/ECONOMY_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md`.

## Economy Goals

- Agents can discover prices by scanning shops.
- Agents can decide whether to farm, buy, sell, hold, or craft.
- Agents have different economic personalities.
- Market actions are budgeted, rate-limited, and explainable.
- The server can prevent runaway buying/selling loops.

## Market Observation

Key:

```text
itemId + sellerId/shopId + timestamp
```

```json
{
  "schemaVersion": 1,
  "itemId": 4000004,
  "itemName": "Orange Mushroom Cap",
  "price": 300,
  "quantity": 100,
  "unitPrice": 300,
  "sellerName": "ShopName",
  "fmRoom": 1,
  "shopObjectId": 12345,
  "shopPosition": {
    "x": 120,
    "y": 45
  },
  "observedByAgentId": 51,
  "timestampMs": 123456789
}
```

## Price Model

Key:

```text
itemId + worldId
```

```json
{
  "itemId": 4000004,
  "worldId": 0,
  "sampleCount": 42,
  "lastObservedMs": 123456789,
  "medianPrice": 320,
  "lowPrice": 250,
  "highPrice": 500,
  "movingAveragePrice": 335,
  "volatility": 0.18,
  "liquidity": "medium",
  "confidence": "observed",
  "trend": "flat"
}
```

Confidence levels:

- `unknown`
- `few-samples`
- `observed`
- `stable`
- `volatile`
- `manual`

## Trade Intent

```json
{
  "intentId": "trade-uuid",
  "agentId": 88,
  "type": "buy-for-resale",
  "itemId": 4000004,
  "quantity": 100,
  "maxUnitPrice": 250,
  "targetSellUnitPrice": 340,
  "maxSpendMesos": 25000,
  "expiresAtMs": 123456789,
  "risk": "low"
}
```

Types:

- `buy-for-use`
- `buy-for-resale`
- `sell-loot`
- `sell-crafted`
- `craft-for-profit`
- `farm-for-sale`
- `hold-item`
- `dump-item`

## Economy Roles

### Self-Sufficient Farmer

- Farms own quest/material items.
- Avoids FM unless blocked.
- Keeps higher meso reserve.

### Loot Seller

- Farms normal maps.
- Sells useful drops.
- Vendors trash frequently.

### FM Scout

- Scans rooms and records prices.
- Rarely buys.
- High patience, high curiosity.

### Opportunistic Trader

- Buys underpriced goods.
- Lists with margin.
- Requires strict budget and cooldowns.

### Supplier

- Buys potions/ammo/materials for assigned group.
- Avoids risky speculation.

### Crafter/Maker

- Tracks recipes and materials.
- Compares craft cost to market value.
- Needs stronger validators.

## Free Market Discovery Flow

```text
go_free_market
select room scan order
enter room
list shops in room
inspect selected shops
record observations
update price model
decide buy/sell/continue
respect scan cooldowns
```

The LLM should not parse UI text. The engine should expose structured shop
observations.

## Buy Validator

Before buying:

- Agent is in FM/shop range.
- Listing still exists.
- Item ID and quantity match observation.
- Unit price is within command limit.
- Total spend is within agent policy.
- Price model confidence is sufficient.
- Item is allowed by economy role.
- Purchase cooldown/rate limit allows it.
- Agent has enough inventory space.
- Agent keeps minimum reserve mesos.

## Sell Validator

Before listing/selling:

- Item is not quest-required.
- Item is not locked/reserved.
- Item is tradeable.
- Price is within policy.
- Listing count is within limits.
- Agent has shop permission if needed.
- Undercut rules are respected.

## Profit Model

```text
expectedProfit =
  expectedSellPrice
  - acquisitionCost
  - listingCost
  - travelCost
  - timeCost
  - riskPenalty
```

Use the model to compare:

- farm item
- buy item
- craft item
- skip
- sell immediately
- hold

## Economy Safety

Hard controls:

- per-agent budget
- per-role budget
- max purchase per item per hour
- max listing changes per hour
- do-not-trade item list
- suspicious price guard
- manual review for high-value trades

Soft controls:

- diversify items
- avoid buying entire market
- prefer natural quantities
- vary scan route
- vary sell timing
- avoid synchronized relisting

## Economy Memory

```json
{
  "agentId": 51,
  "observedItems": [4000004],
  "preferredMarkets": [1, 2, 3],
  "profitableItems": [],
  "badTrades": [],
  "priceBeliefs": {
    "4000004": {
      "median": 320,
      "confidence": "observed",
      "lastCheckedMs": 123456789
    }
  }
}
```
