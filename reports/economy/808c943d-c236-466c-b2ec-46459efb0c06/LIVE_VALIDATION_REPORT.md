# Economy live validation report

Run: `808c943d-c236-466c-b2ec-46459efb0c06`
Scenario: `victoria-fm-baseline`
Observed logical interval: 2026-01-01 00:00:00Z through 01:34:10Z
Live population: 50 admitted of 200 reserved characters

## Outcome

The clean 200-character fixture passed preflight with the exact configured job distribution, 50
initial FM characters, 200 owned Regular Store Permits, no missing activity calibrations, and a
separate compatible PostgreSQL database. The engine then performed real FM movement, remote real-NPC
commerce, calibrated offscreen farming, quest state changes, and a real PlayerShop open.

The run is intentionally retained as `FAILED`. The first attributed PlayerShop receipt proved that
the real shop escrows its permit in addition to listed merchandise; the original materializer treated
every escrow posting as merchandise and rejected the receipt. The patched materializer consumes the
one owned permit as stall-lifecycle escrow, then requires every remaining escrow posting to match an
exact listing. The same retained receipt was reprocessed successfully by
`EconomyLiveOutboxRecoveryIntegrationTest`: one stall and two listings materialized with no quarantine
or invariant violation. The quarantine JSON serializer was also corrected to encode `Instant` values
explicitly.

## Measured activity

| Measure | Value |
|---|---:|
| Presence events | 869 |
| Distinct FM maps reached | 23 (entrance plus rooms 1-22) |
| Economic events | 108 |
| Transactions/lifecycle records | 58 |
| Gross meso recorded | 8,096 |
| Decision records | 64 |
| Item provenance lots | 559 |
| Calibrated farm sessions started/completed | 50 / 17 |
| Open stalls/listings materialized | 1 / 2 |
| Open invariant violations | 0 |
| Quarantined ingestion failures | 0 |

Transaction mix was 24 quest starts, 16 NPC sales, nine farm settlements, five quest turn-ins,
three NPC purchases, and one PlayerShop listing lifecycle. NPC sales introduced 2,030 mesos, farm
settlements introduced 4,566 mesos, and NPC purchases removed 1,500 mesos. This is too short an
interval for a defensible price trend, velocity, Gini, or seller-concentration conclusion.

## Item introduction signals

The largest non-bootstrap flows were 1,500 Crossbow Arrows bought from a real NPC, 346 Bow Arrows
from calibrated mob drops, 256 Bubbling's Huge Bubbles, and 256 Crossbow Arrows from drops. Smaller
flows included Red/Blue Potions, ores, Magic Rocks, equipment, cards, and one Cape INT 100% scroll.
The first shop offered a Fish Spear for 31,545 mesos and the Cape INT 100% scroll for 2 mesos.

Two catalog facts deserve balancing review rather than engine-side suppression: the authoritative
drop evidence produced three item `4031865` (Nexon Game Card - 100 points), and the scenario produced
large arrow supply before there was enough market time to observe clearing demand. If those outcomes
are undesirable, correct or version the authoritative WZ/SQL catalog inputs; do not add synthetic
exceptions to the simulation.

## Restart and recovery evidence

The preceding retained run `7025221e-ac99-43a8-83dd-f20c2fb133e4` passed a stop, hard process
restart, checkpoint restore at the exact logical timestamp, and continued calibrated settlements.
This run added the missing live PlayerShop/outbox proof. A later graceful-shutdown attempt was invalid
as a shutdown test because Maven replaced the JAR while it was still running, causing lazy class
loading to fail; the process was terminated and the reversible fixture restored. Builds and live
server execution must remain separate phases.

## Interpretation limits

- No PlayerShop purchase, barter settlement, or public negotiation completed in this short window.
- The configured three-day advance did not finish because physical actions consume real wall time and
  the pre-patch listing contract stopped the run at 01:34:10 logical time.
- The report therefore proves plumbing, provenance, physical market entry, and fail-closed behavior;
  it does not claim a stable economy or multi-day equilibrium.
- Human participation remains untested and must stay disabled for release claims.

Use the exported interactive dashboard at `economy-dashboard/index.html` for item filters, provenance,
room traffic, agent state, decision alternatives, stalls/listings, meso flow, and validation evidence.
