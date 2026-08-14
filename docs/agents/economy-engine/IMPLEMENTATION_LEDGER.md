# Economy engine implementation ledger

This ledger follows the attached master goal without redefining completion around partial work.

| Phase | Deliverable | Status |
|---|---|---|
| 0 | Architecture decisions and invariants | Complete |
| 1 | Authoritative versioned catalog foundation | Baseline code complete: hashed current-scope bundle, WZ/SQL Cosmic adapter, NPC locations |
| 2 | Economic event, outbox, and ledger foundation | Baseline code complete: atomic Cosmic outbox, exact receipt ingestion, lot ledger; live recovery soak pending |
| 3 | Scenario, population, logical clock, RNG, checkpoints | Baseline code complete for fixed growth and max-throughput: named streams, monotonic fast-forward, resume state |
| 4 | Remote real-NPC commerce and full disposition | Baseline code complete: exact buy/sell/recharge with source NPC/map evidence |
| 5 | Rule-exact offscreen production and calibration | Baseline complete: WZ drops, operational live-session calibration capture, and exact resource runway; death/downtime remains fail-closed |
| 6 | Needs, quests, complements, valuation, beliefs | Partial: resource reserves and accepted live quests are exact; autonomous quest acceptance/turn-in and economy-owned scroll application remain pending |
| 7 | Physical FM seller lifecycle | Baseline code complete: physical room, spot, escrow, one-stall, evidence-driven repricing and closure |
| 8 | Physical FM buyer lifecycle | Baseline code complete: walking, private observations, exact listing identity, real purchases |
| 9 | Public chat and direct negotiation | Baseline code complete: public transcript, meso and reciprocal item barter, real Trade settlement |
| 10 | Replaceable ambient behavior | Baseline code complete: owned-chair and constrained fidget policy seam |
| 11 | Rebuildable data projections and query contracts | Baseline code complete: item/meso/agent read models, explanation query, invariant audit |
| 12 | Experiments, human readiness, soak and completion audit | PostgreSQL V001-V010 executed and startup verifier passed; live multi-agent soak and counterfactual matrix remain pending |

## Deliberate baseline constraint

The v83 PlayerShop permit is a Cash Shop asset. The default scenario uses
`REQUIRE_OWNED_REAL_ITEM`; therefore an imported character without a real permit cannot open a
stall. The strict YAML rejects administrative endowment. A valid stall experiment must provision
legitimately held permits before the run; the engine never silently grants them.

The goal is complete only after the requirement-by-requirement audit proves every definition-of-done
item with current code, tests, migrations, configuration, and runtime evidence.

## Remaining release blockers

- Run the real Cosmic quest start/turn-in path under logical offscreen progression so level-driven
  quest waves consume requirements and create exact rewards. Current demand intentionally recognizes
  only quests already accepted in live character state.
- Apply owned scrolls through a reusable Cosmic scrolling transaction with deterministic RNG and
  outbox evidence. Until then, each observed scroll project reserves at most one owned scroll, which
  prevents an artificial accumulation loop but does not model scroll consumption.
- Implement exact death/downtime consequences before enabling offscreen death. The validator rejects
  that setting rather than estimating penalties.
- Complete a 50-to-200 live-agent soak, restart/recovery exercise, and paired multi-seed scenarios.
