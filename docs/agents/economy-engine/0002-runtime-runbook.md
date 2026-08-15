# Economy runtime runbook

## What starts a valid run

1. Configure `economy-engine.yaml`. YAML contains behavior and scenario policy only; credentials are
   environment variables.
2. Set distinct `ECONOMY_DB_*` credentials and initialize the separate PostgreSQL database through
   migrations V001-V021.
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
   For a real multi-agent calibration cohort already farming on one map, use
   `!economy calibration start-all <map-id>` and `!economy calibration stop-all <map-id> [died]`.
   These batch commands only observe live autonomous characters on that exact map; they do not move,
   equip, heal, reward, or otherwise mutate the cohort.
6. Ensure every configured stall owner legitimately possesses the configured real PlayerShop permit.
   The baseline uses WZ item `5140000`, Regular Store Permit (16 listings). In v83 this is a Cash
   Shop item and is neither a mob drop nor normal NPC stock. `503xxxx` items are Hired Merchants and
   are deliberately rejected.
7. Run `!economy preflight`. It verifies the scenario-sized live roster, exact job-family binding,
   seller permit ownership, initial FM/channel presence, the separate evidence database, and matching
   current-level activity calibration coverage without starting or mutating a run. Later level bands
   remain fail-closed and must be calibrated before agents reach them.
8. Run `!economy start`. With the default `clock.mode: REALTIME`, logical time then advances
   automatically at 1x wall-clock speed; no advance command is needed. Use `!economy status` to
   inspect clock mode, logical time, and admitted population. For an analysis run configured with
   `clock.mode: MAX_THROUGHPUT`, run `!economy advance N`; one request remains active until its
   target is reached.

`start` reruns the same audit and refuses to create a run when any blocker remains; `preflight` is
not merely advisory.

## Clock semantics

`REALTIME` is the production-equivalent mode. One elapsed wall-clock second advances the target by
one logical second, capped by the configured scenario horizon. The runtime polls that target once
per second. Physical walking, stalls, browsing, chat, trades, and external capability completion
remain mandatory gates. If a physical action takes longer than expected, logical execution waits
and catches up only after the real action finishes. Stopping the run or stopping the server pauses
the clock: resume anchors wall time to the persisted logical checkpoint, so downtime is not counted.
`!economy advance` is rejected in this mode.

`MAX_THROUGHPUT` is the explicit experiment and fast-forward mode. It never starts advancing merely
because wall time passes. `!economy advance N` advances monotonically by N logical days. Farm
duration, population arrivals, stall duration, demand changes, and checkpoints skip through logical
time. Physical portal travel, walking, stall opening, visiting, chat, and trades do not become fake
instantaneous actions: the run pauses at those boundaries, lets the normal agent capability tick
complete them, and automatically resumes toward the requested target. A second advance command is
not required at every physical boundary. Rewinding is rejected.

Evidence promotion is scoped to the active run. A quarantined or incomplete receipt belonging to a
historical run remains visible for diagnosis but cannot block a new scenario.

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
- `stall_offer`: authoritative numeric offers tied to an exact listing and item fingerprint. Its
  public pending rows allow physically present interested buyers to make budget-constrained higher bids.
  `public_text` is display-only flavor and is never parsed to make an economic decision.
- `private_trade_arrangement`: the accepted winning offer and exact-item rendezvous contract. It does
  not itself transfer holdings; the enabled arrangement worker physically reunites both agents and
  validates the fingerprint again through Cosmic Trade before marking it executed.
- `economic_intent`: structured buy/sell interest and numeric offers usable outside an active FM
  session. Flavor text is display-only; only a participant may resolve a directed intent.
- `economy_session_event`: accepted/deferred/rejected entries and released/deferred/rejected exits,
  including deadline, retry, and reason evidence.
- `item_valuation_query`: every agent valuation request, its private observation count/median, catalog
  anchor, selected source, and the audited YAML override reason when an override applies.
- `agent_presence_event` and lifecycle tables: FM/offscreen state and physical location.
- `item_market_daily`, `meso_flow_daily`, `agent_state_projection`: rebuildable dashboard read models.
- `economy_invariant_violation`: durable accounting and lifecycle failures.

`economy-database/queries/macro_dashboard.sql` returns ending meso supply and velocity, wealth
distribution/Gini, seller HHI, stall-room utilization, room traffic, disposition channels, item
creation/burn, price series, search failures, unmet demand, and invariant counts. It is an
administrator projection and is never an agent price source.
`fixed_basket_price_index.sql` accepts an explicit item/quantity basket and reports price coverage;
it never imputes an unobserved clearing price. `scenario_comparison.sql` reports baseline/candidate
ledger deltas and explicitly refuses to label an unpaired difference as causal.

Completed activity evidence includes the calibrated death exposure, exact occurrence time, farm
map and field limit, ordinary Agent respawn delay, EXP lost or safety charm consumed, penalty reason,
and restored HP. Gross mob EXP and the death loss are separate balanced ledger flows, so a dashboard
can distinguish weak farming from death leakage.

`economy-database/queries/item_market_explanation.sql` is the item drill-down contract. It links
transactions, observations, lots, listing exposure, and decision reasons without treating an ask as
a completed price or imputing a meso price to barter.

## Deliberate fail-closed limits

- `REALTIME` and `MAX_THROUGHPUT` have distinct runtime semantics. `ACCELERATED` and
  configuration-driven `REPLAY` remain rejected; checkpoint restore is the supported deterministic
  recovery path.
- Dedicated-merchant population, durable circular-trade detection, declarative event partitioning,
  and checkpoint compression are not decorative toggles: the baseline pins them off and validation
  rejects attempts to advertise them before their implementations and recovery tests exist.
- Seasonal overlays are typed but cannot be enabled until each mob-item relation is catalog-checked
  and the rule-exact resolver applies it.
- Farm congestion remains disabled until active-map occupancy is journaled. Offscreen death is
  enabled only with matching live-session calibration. It shares the same Cosmic v83
  beginner/LUK/town/field-limit/safety-charm rule as live deaths, truncates unperformed kills and
  consumable use, cancels ordinary death state, and uses the configured Agent respawn delay.
- Equipment offers identify the exact listing fingerprint. Accepted equipment remains
  `ACCEPTED_AWAITING_SETTLEMENT` until the arrangement worker physically reunites buyer and seller;
  the item-ID-only Trade selector is never used for it.

## Conservative first rollout

Both supplied YAML files enable the necessary real paths: physical browsing, PlayerShop sales and
buys, remote access to real NPC shop rules, private knowledge, durable inventory protection,
structured intent calls, taxes, calibrated activity, and full evidence. The following implemented
features start off: public offers/arrangements, barter, autonomous quest lifecycle mutation, scroll
application, chair collection preference/direct trade, repricing, seasonal overlays, and ambient
fidgets. Enable one family at a time and compare its journal/report output against a control run.

For a local read-only report, run `tools/economy/Export-EconomyDashboard.ps1` and serve
`economy-dashboard`. The exporter reads only PostgreSQL and includes raw transaction, listing,
lot-provenance, decision, social, agent, daily price, meso-flow, and validation evidence for later
UI filtering.
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
