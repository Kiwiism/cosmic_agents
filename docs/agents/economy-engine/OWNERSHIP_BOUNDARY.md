# Economy ownership boundary

The economy engine owns a bounded economic session, disposition knowledge, structured economic
communication, and settlement policy. It does not own farming, ordinary questing, combat, or the
agent's next objective. Existing agent policies remain proposal sources; they do not authorize
inventory mutation.

## Runtime sequence

1. An agent physically reaches the FM entrance/room and requests entry. The typed result is
   `ACCEPTED`, `DEFERRED`, or `REJECTED`; every result and later release is journaled.
2. On the first market cycle after accepted admission, the facade captures an immutable inventory
   snapshot and records every item as `PROTECTED_UNREVIEWED`.
3. The engine creates an entry plan from current holdings, level/job needs, accepted quest demand,
   private observations, catalog facts, and configured valuation overrides.
4. The agent physically browses its configured room set and builds private market knowledge. Buy or
   offer decisions occur immediately after physically observing each stall, rather than after an
   omniscient room scan.
5. The existing seller planner proposes NPC sales and PlayerShop listings. The facade appraises the
   current inventory, creates exact-item reservations, and issues one-use NPC sale authorizations.
6. Both FM remote-NPC sales and the ordinary agent NPC trash-sale path claim an authorization before
   invoking Cosmic's real shop transaction. A run-bound agent remains protected after session release;
   it can call the same inventory-review wrapper outside the FM before an NPC sale.
7. A shadow evaluator records disagreements for equipment, scrolls, chairs, and quest items without
   changing behavior in this first phase.
8. Buyers may leave a durable structured stall offer containing buyer, seller, listing, fingerprint,
   quantity, ask, and offered mesos. The seller reviews that record on a later owner tick. Public
   stall chat and the seller reply are human-facing renderings only; no behavior parses chat text.
9. An accepted offer becomes a durable private arrangement. The buyer returns physically, approaches
   the seller, and Cosmic Trade revalidates the exact fingerprint, quantity, funds, proximity, and
   participants before settlement.
10. Release drains open shops/trades and ends economy ownership. The separate external-activity port
    may then run a rule-exact calibrated farm and return the character through the FM entrance. A new
    admission always performs a fresh scan; farm plans and drop resolution are not economy APIs.

The normal production composition passes `EconomySessionPort` and `ExternalAgentActivityPort`
separately. `EconomyWorldPort` and the activity-bearing constructors on
`CosmicEconomyWorldAdapter` are deprecated checkpoint/test compatibility only.

Production admission is per Agent through `AgentCommerceSessionRuntime`; its restart state is
stored through `AgentCommerceSessionStore`. Population growth, logical-day scheduling, and
calibrated external activity live under `server.agents.observation.commerce` and may decorate the
session port for an experiment, but are not Commerce dependencies. See
`COMMERCE_TARGET_ARCHITECTURE_AND_12_PHASE_ROLLOUT.md` for the migration and rollout contract.

An authorization is tied to character, logical agent, inventory type, slot, item ID, item fingerprint,
quantity, venue, and source snapshot revision. Its guard validates the current physical item and then
consumes the authorization once. Restart/resume fails closed and performs a new entry scan/appraisal;
database records are evidence, not a dashboard-controlled source of authority.

## Ten-agent live profile

Use `config/economy/economy-engine-basic.yaml`. It runs ten agents in FM room 1 at real time, with one
stall maximum per agent and the same real catalogs, NPC transactions, quests, drops, and scrolling
rules as the baseline. Only population, room breadth, bargaining, and timing are reduced.

From an administrator character:

```text
!economy preflight config/economy/economy-engine-basic.yaml
!economy start <new-uuid> config/economy/economy-engine-basic.yaml
!economy status
!economy audit
!economy stop
```

The PostgreSQL tables intended for later dashboard/API reads are `economy_session_event`,
`economic_intent`, `inventory_review`,
`item_disposition_decision`, `economic_asset_reservation`, `economic_action_authorization`, and
`economic_action_guard_event`, plus `stall_offer` for the public offer lifecycle and
`private_trade_arrangement` for durable accepted agreements and their physical settlement outcome.
Per-agent valuation queries use only that agent's durable observations, catalog anchors, and explicit
run configuration overrides; they are journaled in `item_valuation_query` for later explanation.

Agent code calls the stable `CosmicAgentEconomyFacade.valueItem(agentId, itemId, logicalAt)` boundary.
It does not read projections or administrative market aggregates. To pin a balancing value without
changing agent code, add an audited entry under `valuation.customOverrides`:

```yaml
valuation:
  observationMemory: PT168H
  minimumObservedListings: 1
  catalogAnchorMarkupBasisPoints: 5000
  customOverrides:
    - itemId: 1302013
      unitValueMesos: 400000
      reason: "temporary Korean Fan balance experiment"
```

Structured intents and numeric fields are authoritative for economic communication. Public chat is
only a rendering via `StallOfferTextRenderer`; a later template library or LLM renderer can replace it
without changing offer validation, bidding, or settlement rules.
The optional agent-facing `AgentEconomyRuntime` exposes valuation, inventory review, publish,
discovery, and authorized resolve operations without exposing database projections. These calls are
valid outside an active FM visit for durably bound run agents; they never transfer an item or meso.
`economic_intent_trace.sql` and `economy_session_trace.sql` are dashboard-ready inspection contracts.
`economy-database/queries/inventory_ownership_trace.sql` returns one agent/item trace suitable for an
inspector view.

Active sellers are assigned to the lowest configured room with capacity, so room 1 fills before
room 2. Within a room, physical placement ranks the authored safe spots by distance from the real FM
entrance portal. Authored spot IDs remain stable evidence identifiers; their numeric ID is not
treated as proximity. An assignment is released whenever the market trip finishes.
