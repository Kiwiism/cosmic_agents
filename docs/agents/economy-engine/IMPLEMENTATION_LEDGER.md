# Economy engine implementation ledger

This ledger follows the attached master goal without redefining completion around partial work.

| Phase | Deliverable | Status |
|---|---|---|
| 0 | Architecture decisions and invariants | Complete |
| 1 | Authoritative versioned catalog foundation | Baseline code complete: hashed current-scope bundle, WZ/SQL Cosmic adapter, NPC locations |
| 2 | Economic event, outbox, and ledger foundation | Baseline code complete: atomic Cosmic outbox, exact receipt ingestion, lot ledger; live recovery soak pending |
| 3 | Scenario, population, logical clock, RNG, checkpoints | Complete baseline for fixed growth and max-throughput: named streams, monotonic fast-forward, recurring durable checkpoints, restart-equivalent event/RNG replay, zero-population growth |
| 4 | Remote real-NPC commerce and full disposition | Baseline code complete: exact buy/sell/recharge with source NPC/map evidence |
| 5 | Rule-exact offscreen production and calibration | Baseline complete: WZ drops, operational live-session calibration capture, exact resource runway, calibrated trip-ending death, shared Cosmic penalty rules, and logical respawn downtime |
| 6 | Needs, quests, complements, valuation, beliefs | Baseline complete: heterogeneous logical quest acceptance, exact Cosmic start/turn-in, kill/item objectives, reward selection, accepted-demand waves, and owned scroll projects |
| 7 | Physical FM seller lifecycle | Baseline code complete: physical room, spot, escrow, one-stall, evidence-driven repricing and closure |
| 8 | Physical FM buyer lifecycle | Baseline code complete: walking, private observations, exact listing identity, real purchases |
| 9 | Public chat and direct negotiation | Baseline code complete: public transcript, meso and reciprocal item barter, real Trade settlement |
| 10 | Replaceable ambient behavior | Baseline code complete: owned-chair and constrained fidget policy seam |
| 11 | Rebuildable data projections and query contracts | Baseline code complete: item/meso/agent read models, explanation query, invariant audit |
| 12 | Experiments, human readiness, soak and completion audit | PostgreSQL V001-V012 clean initialization, atomic validated configuration revision, startup contract, balance guard, deterministic 50-to-200 scheduler soak, and non-mutating live preflight verified; live multi-agent soak and counterfactual matrix remain pending |

## Deliberate baseline constraint

The authoritative v83 WZ identifies `5140000` as the 16-listing Regular Store Permit. The
`503xxxx` family is Hired Merchant inventory and is rejected by configuration validation. The
PlayerShop permit is a Cash Shop asset. The default scenario uses
`REQUIRE_OWNED_REAL_ITEM`; therefore an imported character without a real permit cannot open a
stall. The strict YAML rejects administrative endowment. A valid stall experiment must provision
legitimately held permits before the run; the engine never silently grants them.

Before mutation, `!economy preflight` reproduces the seeded scenario roster and verifies maximum
population capacity, exact real job-family binding, permits reserved for willing sellers, initial
channel/FM presence, separate PostgreSQL compatibility, and current-level activity calibration.
Start uses the same deterministic binding. A bound character whose real job family differs from its
profile is rejected again at admission.

The goal is complete only after the requirement-by-requirement audit proves every definition-of-done
item with current code, tests, migrations, configuration, and runtime evidence.

## Remaining release blockers

- Complete a 50-to-200 live-agent soak, restart/recovery exercise, and paired multi-seed scenarios.
