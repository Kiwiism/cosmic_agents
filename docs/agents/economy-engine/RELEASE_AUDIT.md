# Economy engine release audit

Audit date: 2026-08-15
Branch: `simulation/economy-engine`

This audit separates implemented behavior, automated evidence, and live evidence. A green unit or
logical-scheduler test is never represented as a physical Free Market soak.

## Verified release evidence

- The default seeded schedule creates 200 profiles: 50 at the logical start and 10 per logical day,
  with staggered arrivals, until the cap. A 30-day maximum-throughput scheduler run processes all
  200 admissions and 120 six-hour checkpoints without sleeping.
- A 90-day scheduler scaling gate processes 1,000 profiles and recurring checkpoints without
  wall-clock ticks. This validates queue shape and bounded scheduling only, not live market throughput.
- Checkpoint/restore produces the same remaining event order and named RNG state as an uninterrupted
  run. Changed configuration and catalog hashes are rejected.
- Clean disposable PostgreSQL 16.4 initialization through V022 passes the runtime verifier, deferred
  double-entry balance trigger, item-flow projections, invariant audit, session/intent contracts,
  arrangement resolution, dashboard queries, and atomic revision-0
  configuration persistence (source YAML, normalized JSON, schema version, hash, effective logical
  timestamp, and validation result).
- Local authoritative WZ gates verify Victoria quest facts and item `5140000`, Regular Store Permit.
  Hired Merchant items (`503xxxx`) are rejected.
- The selected economy, Cosmic economy-boundary, architecture, death, quest persistence, PlayerShop
  escrow, catalog, and deterministic replay suites pass.
- Realtime clock mapping and max-throughput scheduling have distinct automated contracts.
  Unsupported clock modes, dedicated merchants, circular-trade detection, PostgreSQL partitioning,
  and checkpoint compression fail closed rather than accepting configuration with no runtime effect.

## Definition-of-done disposition

| Requirement | Status | Evidence or remaining gate |
|---|---|---|
| YAML starts at 50 and grows by 10 to 200 | Automated | `PopulationAdmissionPlannerTest`, deterministic roster binding |
| Continuous 1x realtime without advance commands | Live verified | Run `2b4c511a-7519-4935-bea6-659be25d978b` advanced 0s to 30s automatically, rejected manual advance, resumed at 30s after 19s stopped, and advanced to 40s from new elapsed time |
| Forward fast-forward for 30+ days | Automated for logical work | `SimulationRunEngineTest`; physical actions intentionally pause rather than being fabricated |
| One channel, FM entrance and rooms 1-22 | Live verified | 50 agents produced 869 presence events across entrance plus rooms 1-22 in run `808c943d-c236-466c-b2ec-46459efb0c06` |
| Offscreen agents cannot trade or remain visible | Automated | separate session/activity ports, coordinator state machine, and activity ownership guards |
| Farming output uses legitimate sources | Live partially verified | 50 calibrated sessions started, 17 completed before the retained failure; 320 mob-drop lots were journaled from authoritative resolution |
| Return through FM entrance | Automated at adapter boundary, live soak pending | coordinator plus adapter postcondition |
| Remote real-NPC buy/sell/recharge | Automated | real shop gateway, price/stock/restriction preservation, source NPC/map evidence |
| Physical browsing and private knowledge | Live verified for cold-start traversal | physical portal movement and per-agent presence evidence; no global price feed |
| At most one real PlayerShop | Live verified for opening | attributed real shop, owned permit escrow, one stall and two exact listings; purchase/closure soak pending |
| Public negotiation and normal Trade settlement | Automated at integration boundary, live soak pending | structured/public transcript and real Cosmic Trade lifecycle |
| Open-chat inventory sale | Automated candidate/reservation/intent/checkpoint contracts; physical matrix pending | exact real stack, bounded advertising, human/agent meso-only Trade, shutdown cleanup |
| Chairs require legitimate ownership | Automated | owned-chair ambient eligibility and real inventory settlement |
| Quest and scroll demand follows actual state | Automated and WZ-backed | exact quest start/turn-in/consumption and real deterministic Cosmic scrolling |
| Configurable tax and seasonal preparation | Automated | scheduled tax execution; seasonal overlays typed and fail closed while disabled |
| Item and meso provenance | Automated on controlled settlements, live reconciliation pending | balanced ledger, lots/instances, Cosmic transactional outbox, idempotent ingestion |
| Decision evidence and alternatives | Automated | the evidence contract rejects empty alternatives; operational paths retain an explicit rejected choice and reason |
| Reconstruction from journal/checkpoints | Automated on controlled restart | full domain checkpoint, queue/RNG equality, idempotent relay |
| Dashboard data queryable without live objects | Automated | item, meso, agent, decision, negotiation, provenance, velocity, wealth/Gini, seller HHI, room utilization/traffic, search, burn, fixed-basket coverage/index, scenario comparison, and invariant contracts execute in the clean PostgreSQL gate |
| Ambient behavior cannot perturb loot | Automated | independent named streams and architecture boundary |
| Adapter replacement does not rewrite domain | Automated | `EconomySessionPort`, `ExternalAgentActivityPort`, and no-farming-dependency architecture test; deprecated composite remains compatibility-only |
| Human-safe participation | Schema, incoming open-chat Trade handling, and settlement flags implemented; live evidence pending | human-counterparty attribution exists, but no human/agent live settlement has run |

## Exact live-soak prerequisites for the default seed

The current seed samples this maximum roster by real job family:

| Job family | Required live characters |
|---|---:|
| Warrior | 37 |
| Magician | 46 |
| Bowman | 48 |
| Thief | 33 |
| Pirate | 36 |
| Total | 200 |

It also samples 147 willing sellers, including 38 of the initial 50. The current default explicitly
adopts the approved evented FM participation exception: an entrant who owns no verified PlayerShop
permit receives one random real `514xxxx` permit. Every grant is a `VENUE_SUBSIDY` transaction and
lot, so the 147 possible grants remain measurable and removable from organic supply analysis. For a
strict no-subsidy comparison, set `bootstrap.shopPermitPolicy: REQUIRE_OWNED_REAL_ITEM`; that profile
again requires pre-owned permits or a modeled Cash Shop/NX acquisition path.

Every mapped character must also:

- be a live autonomous runtime character of the sampled job family;
- be on configured channel 1 and in the FM entrance/rooms when admitted;
- have enough real holdings for calibrated potion/ammunition consumption;
- have at least five matching live calibration samples for the current build, nearby level, job
  family, and a legal Maple/Victoria farm map; and
- gain new calibration coverage before progression reaches an uncovered level band.

Run `!economy preflight` before mutation. It reproduces the same seeded roster and reports roster,
permit-policy, initial FM, evidence database, and current-level calibration blockers.
Startup reruns this audit and creates no run while a blocker remains.

## Current environment result

The branch-built server loaded 200 deterministic autonomous characters and passed the strict live
preflight. Run `808c943d-c236-466c-b2ec-46459efb0c06` admitted 50, reached every FM map, started 50
calibrated sessions, completed 17, and opened an attributed real PlayerShop. The first listing exposed
and preserved a permit-escrow materialization defect; the patched ingestor reprocessed that exact
receipt into one stall and two listings with zero quarantine and zero invariant violations. See the
run-specific live validation report and dashboard export.

Realtime run `2b4c511a-7519-4935-bea6-659be25d978b` then proved the production clock on the packaged
server. It progressed without an advance command, admitted the initial 50 agents, and generated 377
physical FM presence events while agents moved through rooms. `!economy advance 1` was rejected.
Stopping at logical 30 seconds, waiting about 19 wall seconds, and resuming restarted at exactly 30
seconds; ten new wall seconds reached logical 40 seconds. The stopped run retains 200 bindings, 50
arrivals, 71 economic events, zero ingestion failures, and zero invariant violations.

## Remaining release blockers

1. Run 50-to-200 physical FM activity through at least 30 logical days, including a mid-stall
   restart, sale/closure, outbox redelivery, escrow reconciliation, and a zero-violation invariant audit.
2. Complete PlayerShop purchases, public negotiation/barter, and seller closure/repricing live paths.
3. Run paired multi-seed scenario branches for population/class, tax, NPC access, and quest-wave
   changes. Reports must distinguish programmed reasons, observed association, and paired causal
   differences.
4. Add live human-versus-agent PlayerShop and Trade validation before enabling humans in a run.

Until these gates pass, Phase 12 and the complete-release goal remain open.
