# Economy engine validation — 2026-08-15

## Outcome

The ownership boundary, real Cosmic NPC disposition path, run-scoped farm settlement,
room/spot packing, structured stall-offer contract, PostgreSQL evidence, and static dashboard export
were compiled and validated on `simulation/economy-engine`.

No item or market transaction was procedurally invented for validation. Runtime evidence came from
the prepared 200-character Cosmic roster, real PlayerShop objects, real NPC shop mutations, and
previously captured live farm calibration cohorts. The offer state machine was tested with isolated
deterministic fixtures because the short runtime smoke produced no economically justified natural
offer; no offer was injected into the reported scenario.

## Extended clean-fixture run

Run `61807dee-00d2-4c89-87f4-d5244867f28c` used ten admitted agents and FM room 1. It was stopped at
11:05:20 logical time to rebuild and smoke-test the final V016 binary, so its durable status remains
`WAITING_PHYSICAL_ACTION` rather than being misreported as a completed 24-hour scenario.

| Evidence | Result |
|---|---:|
| Economic transactions | 400 |
| Completed calibrated farm sessions | 65 |
| Real NPC sales | 309 transactions / 5,906 items / 37,558 mesos |
| Farm-settlement meso introduced | 41,281 |
| NPC purchases | 4 transactions / 1,726 items / 1,726 mesos |
| PlayerShop listings | 13 |
| Physical stalls | 7 stalls / 7 sellers |
| Physical market observations | 49 / 8 observers / 8 item IDs |
| Inventory reviews | 153 / all 10 agents |
| Guarded economic mutations | 309 allowed / 0 blocked |
| Open invariant violations | 0 |
| Evidence ingestion failures | 0 |

Largest realized item flows by gross mesos were Bubbling's Huge Bubble (`4000037`, 18,410 mesos),
Sapphire Ore (`4020005`, 6,150), Blue Potion (`2000003`, 6,000), Mithril Ore (`4010002`, 4,650),
and Red Potion (`2000000`, 2,050). Arrow purchases were a visible meso sink: 1,726 mesos for 3,423
Crossbow Arrows (`2061000`).

The decision journal contains 741 ambient market actions, 309 NPC dispositions, 13 observed
purchases, 8 quest starts, 7 resource-procurement decisions, 7 stall openings, 6 repricing research
cycles, and 1 quest turn-in. These are reasons and alternatives, not inferred explanations from
price movement alone.

## Final V016 packaged-binary smoke

Run `2e9479f4-9298-4ff0-be2a-e0c8bb503d19` was started by the final packaged runtime after V016
schema verification and a clean preflight. By logical 01:48:30 it had recorded 45 transactions,
7 completed and 9 active farm sessions, 2 physical stalls, 24 physical observations, and zero
invariant violations.
Agent 6 physically opened a six-listing PlayerShop in room 1 at x=854, the nearest safe authored
stall position to the entrance portal at x=790. No structured offer was generated because no buyer's
actual needs, funds, and willingness-to-pay justified one during this window.

## Structured offer guarantees

- The authoritative payload contains run, buyer, seller, stall, exact listing, item ID, item
  fingerprint and attributes, quantity, ask, numeric meso offer, timestamps, and lifecycle status.
- Public stall chat is generated from that payload and is never parsed for a price or decision.
- Seller review reads the numeric offer and current exact listing, recomputes the reserve from NPC
  opportunity cost and seller profile, and records acceptance, rejection, expiry, or listing change.
- A changed equipment fingerprint cancels the offer. An accepted equipment offer stays
  `ACCEPTED_AWAITING_SETTLEMENT`; it is not passed to Cosmic's item-ID-only direct-trade selector.
- A buyer cannot leave an offer exceeding currently held mesos.

## Verification gates

- Focused room, placement, buyer-offer, seller-review, and config suites: passing after restoring
  the checked-in real-time profile.
- Broad economy/market/ownership/shop selection: 132 tests executed, 126 passed, and 6 live/WZ
  integration tests were intentionally skipped by their environment gates; zero failures.
- PostgreSQL V016 integration contract: passing, including create/read, structured numeric amount,
  accepted-to-executed transition, and cleanup.
- Final Maven package: passing.
- Dashboard JavaScript syntax, exported JSON load, Market navigation, structured-offer metrics,
  offer-table columns, and zero-offer empty state: verified in the in-app browser.

## Report artifacts

`economy-dashboard/data/latest.json` contains the extended run's full raw export. Serve the
`economy-dashboard` directory and use its day slider, item explorer, transactions, market, decisions,
ownership, and validation pages. The Market page includes structured offer ask, offer, status,
public flavor, seller response, and fingerprint whenever a scenario naturally creates offers.
