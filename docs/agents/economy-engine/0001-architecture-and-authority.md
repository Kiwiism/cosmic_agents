# Economy engine architecture and authority

Status: accepted for implementation on `simulation/economy-engine`.

## Authority

- Cosmic remains authoritative for live character inventory, mesos, PlayerShop, Trade, NPC-shop,
  quest, scroll, and item-use mutations.
- The economy domain proposes commands and consumes immutable results. It never mutates Cosmic
  runtime objects directly.
- A dedicated economy database owns run configuration, append-only evidence, beliefs, decisions,
  projections, and checkpoints.
- A minimal Cosmic-side transaction outbox is required because a separate database cannot commit
  atomically with the character database. Delivery into the economy database is idempotent.

## Time

- Economy runs use a monotonic logical clock and scheduled-event queue.
- Fast-forward advances to the next meaningful event; it does not multiply wall-clock time.
- Runs never move backward. A past checkpoint starts a new run.
- Randomness is split into named streams so ambient changes cannot perturb loot or market draws.

## World boundary

- Market-facing activity is limited to one configured channel, FM entrance, and rooms 1-22.
- Offscreen activity makes an agent unavailable to stalls, chat, browsing, and trades.
- Remote access to real NPC shops is a configured simulation exception. It preserves actual shop
  stock, prices, restrictions, NPC identity, and source map and is replaceable by physical travel.

## Catalog boundary

- WZ/SQL facts, derived mechanical facts, policy hints, and scenario overlays remain separate.
- Runtime uses versioned catalog bundles and refuses a mismatched resume.
- Server loaders and established predicates remain the source of truth for game behavior.

## Market invariants

- No procedural stock, synthetic merchants, artificial price waves, or market-maker purchases.
- One stall per agent.
- Listings are observations, not completed-sale prices.
- Every item and meso delta has explicit provenance.
- Policy never performs direct inventory mutation.
- Agents learn from private, incomplete observations; global projections are administrative only.

