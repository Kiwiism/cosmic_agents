# Economy ownership boundary

The economy module now owns economic disposition knowledge for admitted run participants. Existing
agent policies remain proposal sources; they do not authorize inventory mutation.

## Runtime sequence

1. On the first market cycle after physical FM admission, the facade captures an immutable inventory
   snapshot and records every item as `PROTECTED_UNREVIEWED`.
2. The agent physically browses its configured room set and builds private market knowledge.
3. The existing seller planner proposes NPC sales and PlayerShop listings. The facade appraises the
   current inventory, creates exact-item reservations, and issues one-use NPC sale authorizations.
4. Both FM remote-NPC sales and the ordinary agent NPC trash-sale path claim an authorization before
   invoking Cosmic's real shop transaction. Non-participants retain legacy behavior during rollout.
5. A shadow evaluator records disagreements for equipment, scrolls, chairs, and quest items without
   changing behavior in this first phase.
6. Buyers may leave a durable structured stall offer containing buyer, seller, listing, fingerprint,
   quantity, ask, and offered mesos. The seller reviews that record on a later owner tick. Public
   stall chat and the seller reply are human-facing renderings only; no behavior parses chat text.

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

The PostgreSQL tables intended for later dashboard/API reads are `inventory_review`,
`item_disposition_decision`, `economic_asset_reservation`, `economic_action_authorization`, and
`economic_action_guard_event`, plus `stall_offer` for the public offer lifecycle and
`private_trade_arrangement` for durable accepted agreements awaiting physical settlement.
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
`economy-database/queries/inventory_ownership_trace.sql` returns one agent/item trace suitable for an
inspector view.

Active sellers are assigned to the lowest configured room with capacity, so room 1 fills before
room 2. Within a room, physical placement ranks the authored safe spots by distance from the real FM
entrance portal. Authored spot IDs remain stable evidence identifiers; their numeric ID is not
treated as proximity. An assignment is released whenever the market trip finishes.
