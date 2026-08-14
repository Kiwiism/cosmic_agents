# Economy runtime runbook

## What starts a valid run

1. Configure `economy-engine.yaml`. YAML contains behavior and scenario policy only; credentials are
   environment variables.
2. Set distinct `ECONOMY_DB_*` credentials and initialize the separate PostgreSQL database through
   migrations V001-V010.
3. Start Cosmic MySQL and the game server normally.
4. Have at least `population.maximumAgents` live autonomous characters. The runtime deterministically
   binds scenario slots to characters of the same real Cosmic job family; within each eligible pool,
   ascending character id is the stable tie-breaker. Characters admitted at a future logical date
   must still be live and in the FM entrance or rooms at admission time.
5. Capture at least `activity.minimumCalibrationSamples` completed real autonomous sessions for every
   build/job/level/map cohort that may farm. Use
   `!economy calibration start <agent-character-id>` while that live agent is on the farm map, then
   `!economy calibration stop <agent-character-id> [died]`. The event bus records actual kills and
   explicit item consumption; the stop command writes the sample to the separate PostgreSQL database.
   `!economy calibration status <agent-character-id>` inspects an active capture. No matching
   calibration means no farm simulation. When `activity.allowDeath` is enabled, only the observed
   cohort death rate can end a logical trip; a death ends that trip rather than permitting synthetic
   repeated-death loops.
6. Ensure every configured stall owner legitimately possesses the configured real PlayerShop permit.
   The baseline uses WZ item `5140000`, Regular Store Permit (16 listings). In v83 this is a Cash
   Shop item and is neither a mob drop nor normal NPC stock. `503xxxx` items are Hired Merchants and
   are deliberately rejected.
7. Run `!economy preflight`. It verifies the scenario-sized live roster, exact job-family binding,
   seller permit ownership, initial FM/channel presence, the separate evidence database, and matching
   current-level activity calibration coverage without starting or mutating a run. Later level bands
   remain fail-closed and must be calibrated before agents reach them.
8. Run `!economy start`, then `!economy advance 0` to process initial admissions. Use
   `!economy status` to inspect logical time and admitted population.

## Fast-forward semantics

`!economy advance N` advances monotonically by N logical days. Farm duration, population arrivals,
stall duration, demand changes, and checkpoints skip through logical time. Physical portal travel,
walking, stall opening, visiting, chat, and trades do not become fake instantaneous actions: the run
pauses at those boundaries, lets the normal agent capability tick complete them, and resumes on the
next advance command. Rewinding is rejected.

This means a 30-day economic run can compress the long offscreen and waiting intervals, but cannot
compress away the real wall-clock work needed to observe a physical market. That is the necessary
tradeoff for later swapping calibrated activity with fully live agents under the same constraints.

The configured logical checkpoint cadence is recurring rather than preallocated to the default
reporting horizon. A managed run persists the full event queue, named RNG states, pending resolved
activity outcomes, coordinator state, and world state at every cadence boundary even during one
large advance. Checkpoint restore rejects changed configuration/catalog hashes and replay tests
require identical remaining event order and RNG state.

## Evidence available to a future dashboard

- `economic_event` and `ledger_posting`: immutable authority and balanced meso/item flows.
- `item_lot` and `market_listing_lot`: item provenance and escrow allocation.
- `market_stall`, `market_listing`, `economic_transaction`: stall lifecycle and settlement.
- `market_observation`: private observed asks, exact item fingerprints, and rolled attributes.
- `decision_journal`: alternatives, beliefs, needs, utilities, and reason codes.
- `social_event` and `negotiation_session`: public dialogue, proposals, counteroffers, and outcomes.
- `agent_presence_event` and lifecycle tables: FM/offscreen state and physical location.
- `item_market_daily`, `meso_flow_daily`, `agent_state_projection`: rebuildable dashboard read models.
- `economy_invariant_violation`: durable accounting and lifecycle failures.

`economy-database/queries/macro_dashboard.sql` returns ending meso supply and velocity, wealth
distribution/Gini, seller HHI, stall-room utilization, room traffic, disposition channels, item
creation/burn, price series, search failures, unmet demand, and invariant counts. It is an
administrator projection and is never an agent price source.

Completed activity evidence includes the calibrated death exposure, exact occurrence time, farm
map and field limit, ordinary Agent respawn delay, EXP lost or safety charm consumed, penalty reason,
and restored HP. Gross mob EXP and the death loss are separate balanced ledger flows, so a dashboard
can distinguish weak farming from death leakage.

`economy-database/queries/item_market_explanation.sql` is the item drill-down contract. It links
transactions, observations, lots, listing exposure, and decision reasons without treating an ask as
a completed price or imputing a meso price to barter.

## Deliberate fail-closed limits

- `MAX_THROUGHPUT` is the only accepted clock mode in this release. `REALTIME`, `ACCELERATED`, and
  configuration-driven `REPLAY` are rejected until they have distinct tested runtime semantics;
  checkpoint restore remains the supported deterministic recovery path.
- Seasonal overlays are typed but cannot be enabled until each mob-item relation is catalog-checked
  and the rule-exact resolver applies it.
- Farm congestion remains disabled until active-map occupancy is journaled. Offscreen death is
  enabled only with matching live-session calibration. It shares the same Cosmic v83
  beginner/LUK/town/field-limit/safety-charm rule as live deaths, truncates unperformed kills and
  consumable use, cancels ordinary death state, and uses the configured Agent respawn delay.
- Direct negotiation excludes equipment because the real Trade selector is item-id based; rolled
  equipment remains safely tradable through fingerprinted PlayerShop escrow.
- A zero-NPC-floor collectible receives no invented cold-start meso value. It can be retained or
  exchanged through reciprocal-need barter. Positive-floor scarce items can seed an ask from their
  exact NPC opportunity cost and configured seller markup.
- Humans can later transact through normal Cosmic PlayerShop/Trade validation. They are not marked
  as agents, do not receive agent-only tax metadata, and are absent from the default scenario.
- Quest acceptance and turn-in use Cosmic's authoritative Quest.wz requirements/actions under the
  logical clock and a named RNG stream. The reviewed Victoria policy catalog is extended at runtime
  only with WZ quests whose NPCs and objective sources are proven on generated Victoria maps.
  Timed, scripted, field-entry, buff, pet, reactor-only, and otherwise unsupported state remains
  fail-closed. One quest action is considered per physical market cycle and concurrent acceptance is
  YAML-bounded, preventing synchronized mass acceptance.
- Farm settlement advances actual Cosmic kill counters from calibrated kill evidence. Quest costs,
  item rewards, mesos, realized EXP across level boundaries, selection, NPC, and input/output lots
  are committed through the same outbox transaction as quest status, fame, skill, inventory, and
  progression persistence.
- Owned scroll projects use the same authoritative Cosmic mutation as player packets. The transaction
  consumes the real scroll, applies success/failure/curse with a named deterministic RNG stream, and
  records the input equipment lot and transformed or destroyed result in the economy outbox.
