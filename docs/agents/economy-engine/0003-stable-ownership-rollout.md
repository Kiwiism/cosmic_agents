# Stable ownership rollout

## Default observable behavior

With either supplied YAML, a run-bound character requests a bounded economy session only after it is
at the FM entrance or in a room. Accepted agents walk through real portals, approach physically
visible stalls, enter each PlayerShop for the configured per-listing dwell, learn only that stall,
decide at each stall, use real PlayerShop/NPC transactions, and either open one owned-permit stall or
release the session. NPC buy/sell/recharge and temporary storage access are available only after the
Agent reaches the FM entrance. Room assignment fills
the lowest room first; spot assignment uses distance from the real entrance portal, not authored spot
number.

After release, the external-activity adapter owns the character. The current adapter removes it from
visible FM state, resolves only a matching live-calibration plan against authoritative drops and
Cosmic inventory/progression rules, and returns it through map `910000000`. Re-entry is a new request
and a fresh inventory/needs/knowledge scan. In `REALTIME`, this repeats without `advance` commands.

## First real-agent test

Start with `config/economy/economy-engine-basic.yaml` and
calibration coverage for the chosen agents. From an administrator character:

```text
!economy preflight config/economy/economy-engine-basic.yaml
!economy start <new-uuid> config/economy/economy-engine-basic.yaml
!economy status
!economy audit
!economy stop
```

Expected first-pass observations:

- agents enter room 1 before later rooms and take the nearest safe unoccupied stall positions;
- buyers walk to stalls instead of consuming a global listing feed;
- ordinary potion/ammunition restocking and inventory disposition use real NPC catalog rules;
- one character owns at most one PlayerShop and must own one verified `514xxxx` permit to open it;
  an entrant with none receives one explicitly journaled FM venue-subsidy permit by default;
- agents with no remaining work release after their post-trip delay; an unproductive non-stall
  session is drained after five logical minutes and every session after 30 logical minutes;
- no public bargaining, barter, autonomous quest start/turn-in, scrolling, repricing, chair collecting,
  or ambient fidgets occur with the default flags.

## Incremental feature switches

Change one family per candidate run, retaining an unchanged control run:

1. `market.publicOffersEnabled: true` enables public numeric stall offers, outbidding, seller review,
   durable arrangements, and physical exact-fingerprint Trade settlement.
2. `market.barterEnabled: true` may be enabled only with public offers.
3. `quests.enabled: true` enables autonomous real Quest.wz lifecycle mutation; accepted quest demand
   remains available while it is off.
4. `scrolling.enabled: true` enables owned-equipment, real-rate scroll projects.
5. `market.maximumReprices: 1` enables one close/research/reopen cycle using the existing positive
   observation interval.
6. `chairs.collectionPreferenceEnabled: true`, then `chairs.allowDirectTrade: true`, adds chair demand
   and direct trade separately.
7. `ambient.enabled: true` adds the replaceable idle/walk/sit/fidget seam last.

Seasonal overlays, congestion, circular-trade detection, dedicated merchants, partitioning, and
checkpoint compression remain fail-closed rather than pretending to work.

## Fast-forward

Use a separate YAML/run with `clock.mode: MAX_THROUGHPUT`, then:

```text
!economy start <new-uuid> <max-throughput-yaml>
!economy advance 30
!economy status
!economy audit
```

The target advances monotonically. Long activity and waiting intervals compress, while portal travel,
stall opening, browsing, chat, and settlement still pause for real capability completion. This is a
scenario runner over the same session/activity ports, not an alternate source of fabricated market
transactions. Do not use the same run as a realtime observation control.

## Dashboard-ready data

The UI can remain absent. These read contracts are stable inputs:

- `economy_session_trace.sql`: why/when an agent entered, waited, was rejected, timed out, or released;
- `economic_intent_trace.sql`: structured terms, display text, counterparties, venue, and resolution;
- `inventory_ownership_trace.sql`: reviews, reservations, one-use authorizations, and guard outcomes;
- `item_market_explanation.sql`: observations, listings, sales, lots, demand, and decision reasons;
- `agent_journal.sql` / `decision_trace.sql`: alternatives, beliefs, needs, utility, and public actions;
- `macro_dashboard.sql`, `meso_flow.sql`, and daily projections: supply, sinks/sources, velocity,
  distribution, utilization, traffic, and invariant counts.

Agent code must use `AgentEconomyRuntime` for valuation, inventory review, publish/discover/resolve
intent calls. It must not read these administrator projections as price knowledge. A structured
intent can be created outside the FM, but it never transfers holdings; actual settlement still uses a
physical PlayerShop or Trade path.
