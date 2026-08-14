# Economy engine release audit

Audit date: 2026-08-14  
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
- Clean PostgreSQL 17 initialization through V012 passes the runtime schema verifier, deferred
  double-entry balance trigger, item-flow projections, invariant audit, and atomic revision-0
  configuration persistence (source YAML, normalized JSON, schema version, hash, effective logical
  timestamp, and validation result).
- Local authoritative WZ gates verify Victoria quest facts and item `5140000`, Regular Store Permit.
  Hired Merchant items (`503xxxx`) are rejected.
- The selected economy, Cosmic economy-boundary, architecture, death, quest persistence, PlayerShop
  escrow, catalog, and deterministic replay suites pass.

## Definition-of-done disposition

| Requirement | Status | Evidence or remaining gate |
|---|---|---|
| YAML starts at 50 and grows by 10 to 200 | Automated | `PopulationAdmissionPlannerTest`, deterministic roster binding |
| Forward fast-forward for 30+ days | Automated for logical work | `SimulationRunEngineTest`; physical actions intentionally pause rather than being fabricated |
| One channel, FM entrance and rooms 1-22 | Implemented, live evidence pending | strict config validation and `CosmicFreeMarketPhysicalGateway` |
| Offscreen agents cannot trade or remain visible | Automated | coordinator state machine and `CosmicEconomyWorldAdapter` guards |
| Farming output uses legitimate sources | Automated at rule boundary, live calibration pending | WZ/SQL catalog, calibrated work, Cosmic drop/equipment mutation, lot ledger |
| Return through FM entrance | Automated at adapter boundary, live soak pending | coordinator plus adapter postcondition |
| Remote real-NPC buy/sell/recharge | Automated | real shop gateway, price/stock/restriction preservation, source NPC/map evidence |
| Physical browsing and private knowledge | Automated at capability boundary, live soak pending | room travel, range checks, observed-listing memory; no global price feed |
| At most one real PlayerShop | Automated | runtime registry, real inventory/escrow, PostgreSQL partial unique index |
| Public negotiation and normal Trade settlement | Automated at integration boundary, live soak pending | structured/public transcript and real Cosmic Trade lifecycle |
| Chairs require legitimate ownership | Automated | owned-chair ambient eligibility and real inventory settlement |
| Quest and scroll demand follows actual state | Automated and WZ-backed | exact quest start/turn-in/consumption and real deterministic Cosmic scrolling |
| Configurable tax and seasonal preparation | Automated | scheduled tax execution; seasonal overlays typed and fail closed while disabled |
| Item and meso provenance | Automated on controlled settlements, live reconciliation pending | balanced ledger, lots/instances, Cosmic transactional outbox, idempotent ingestion |
| Decision evidence and alternatives | Automated | the evidence contract rejects empty alternatives; operational paths retain an explicit rejected choice and reason |
| Reconstruction from journal/checkpoints | Automated on controlled restart | full domain checkpoint, queue/RNG equality, idempotent relay |
| Dashboard data queryable without live objects | Mostly automated | item, meso, agent, decision, negotiation, provenance, velocity, wealth/Gini, seller HHI, room utilization/traffic, search, burn, and invariant contracts execute in the clean PostgreSQL gate; a configured fixed-basket CPI and scenario-comparison projection remain incomplete |
| Ambient behavior cannot perturb loot | Automated | independent named streams and architecture boundary |
| Adapter replacement does not rewrite domain | Automated | architecture test and `EconomyWorldPort`/Cosmic adapter boundary |
| Human-safe participation | Schema and settlement flags implemented; live evidence pending | human-counterparty attribution exists, but no human/agent live test has run |

## Exact live-soak prerequisites for the default seed

The current seed samples this maximum roster by real job family:

| Job family | Required live characters |
|---|---:|
| Warrior | 36 |
| Magician | 37 |
| Bowman | 33 |
| Thief | 46 |
| Pirate | 48 |
| Total | 200 |

It also samples 147 willing sellers, including 38 of the initial 50. Consequently the strict run
requires 147 characters to already own real `5140000` permits. This is intentionally demanding:
the permit is a Cash Shop asset and cannot be produced by Victoria farming or ordinary NPC access.
The engine will not manufacture it. A practical experiment must therefore do one of the following
explicitly in YAML and real provisioning: acquire those permits through a modeled legitimate Cash
Shop/NX path, lower merchant participation, or define and approve an evented bootstrap exception.
The last option changes the current no-administrative-endowment invariant and must not be inferred.

Every mapped character must also:

- be a live autonomous runtime character of the sampled job family;
- be on configured channel 1 and in the FM entrance/rooms when admitted;
- have enough real holdings for calibrated potion/ammunition consumption;
- have at least five matching live calibration samples for the current build, nearby level, job
  family, and a legal Maple/Victoria farm map; and
- gain new calibration coverage before progression reaches an uncovered level band.

Run `!economy preflight` before mutation. It reproduces the same seeded roster and reports roster,
permit, initial FM, evidence database, and current-level calibration blockers.

## Current environment result

The server currently running on this workstation loads classes from the original
`Cosmic Agents` worktree, not this branch, and its launch target names only `agent123` for the
Amherst reset workflow. No `ECONOMY_DB_*` runtime credentials are present. Restarting or replacing
that user-owned server was outside the authorized scope, so no physical 50-to-200 soak was run.

## Remaining release blockers

1. Provision the exact live roster, legitimate permits, consumables, and level/map/job calibration
   coverage, then run `!economy preflight` from a server built from this branch.
2. Run 50-to-200 physical FM activity through at least 30 logical days, including a mid-activity and
   mid-stall restart, outbox redelivery, escrow reconciliation, and a zero-violation invariant audit.
3. Run paired multi-seed scenario branches for population/class, tax, NPC access, and quest-wave
   changes. Reports must distinguish programmed reasons, observed association, and paired causal
   differences.
4. Add a configured fixed-basket price index and cross-run scenario-comparison projection.
5. Add live human-versus-agent PlayerShop and Trade validation before enabling humans in a run.

Until these gates pass, Phase 12 and the complete-release goal remain open.
