# Economy runtime runbook

## What starts a valid run

1. Configure `economy-engine.yaml`. YAML contains behavior and scenario policy only; credentials are
   environment variables.
2. Set distinct `ECONOMY_DB_*` credentials and initialize the separate PostgreSQL database through
   migrations V001-V023.
3. Start Cosmic MySQL and the game server normally.
4. Have at least `population.maximumAgents` live autonomous characters on the configured channel.
   The runtime deterministically binds scenario slots to characters of the same real Cosmic job
   family; within each eligible pool, ascending character id is the stable tie-breaker. The detached
   observation harness stages future cohorts and materializes them at the FM entrance when their
   admission becomes due; they do not need to be manually parked in the FM.
5. For `activity.executionMode: RULE_EXACT`, capture at least
   `activity.minimumCalibrationSamples` completed real autonomous sessions for every
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
   The independent Commerce profile uses `activity.executionMode: DISABLED`; preflight then skips
   calibration and released agents re-enter Commerce after their configured cooldown without farming,
   drops, EXP, mesos, consumable use, or any other synthetic settlement.
6. The default entry policy grants exactly one configured real PlayerShop permit when an entrant owns
   none. The verified pool is `5140000`, `5140001`, `5140002`, `5140003`, `5140004`, and `5140006`;
   `503xxxx` Hired Merchants remain rejected. Each grant is a Cosmic transaction and an explicit
   `VENUE_SUBSIDY` lot, never a mob drop or ordinary production. Set
   `bootstrap.shopPermitPolicy: REQUIRE_OWNED_REAL_ITEM` for a no-subsidy experiment.
7. Run `!economy preflight`. It verifies the scenario-sized live roster, exact job-family binding,
   configured-channel presence, permit-policy validity, the separate evidence database, and matching
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

`!economy advance-day` closes one run-relative 24-hour interval. The instant passed to `start` is
always Day 1 Hour 0, regardless of its civil date or timezone. Day advancement is half-open: events
at exactly the next day’s Hour 0 remain queued until that new day begins, so a new cohort cannot be
counted in the prior day. Use `!economy advance 0` to process those Hour 0 events without moving the
clock. A clean close writes `economy_day_close`; relay failures, quarantine, invariant violations,
or live-character/escrow holdings mismatches leave the run retryably `DAY_CLOSE_BLOCKED`. Fix the
evidence issue and run `advance-day` again to reconcile at the same instant without skipping a day.

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
- `VENUE_SUBSIDY` events/lots: FM-issued permits, separable from organic item production.
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
- Active open-chat sales are `SELL_INTEREST` intents over one exact real inventory fingerprint and
  bundle. The shared inventory ledger reserves that holding; bounded advertisements are social events;
  an interested agent physically approaches the seller before agent or human purchases settle through
  Cosmic `PLAYER_TRADE`. `!economy openchat` exposes the
  current in-memory execution state while the durable intent, Trade receipt, decision, and social
  evidence remain dashboard sources.
- `economy_session_event`: accepted/deferred/rejected entries and released/deferred/rejected exits,
  including deadline, retry, and reason evidence.
- `item_valuation_query`: every agent valuation request, its private observation count/median, catalog
  anchor, selected source, and the audited YAML override reason when an override applies.
- `agent_presence_event` and lifecycle tables: FM/offscreen state and physical location.
- `item_market_daily`, `meso_flow_daily`, `agent_state_projection`: rebuildable dashboard read models.
- `economy_day_close`: immutable internal-day checkpoint hash, relay/ingestion counts, and clean-close gate.
- `economy_invariant_violation`: durable accounting and lifecycle failures.

`economy-database/queries/macro_dashboard.sql` returns ending meso supply and velocity, wealth
distribution/Gini, seller HHI, stall-room utilization, room traffic, disposition channels, item
creation/burn, price series, search failures, unmet demand, and invariant counts. It is an
administrator projection and is never an agent price source.
`item_economy_detail.sql` supplies price/volume, every settlement, listing exposure, demand reasons,
and lot provenance for one item. `internal_day_reconciliation.sql` supplies the day-close, meso
creation/destruction, market volume, and transaction series used by a future day slider.
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

The production YAML files enable the necessary real paths: physical browsing, PlayerShop sales and
buys, entrance-only remote access to real NPC shop rules, private knowledge, durable inventory protection,
structured intent calls, taxes, calibrated activity, external quest lifecycle, a ten-percent
open-chat seller cohort, and full evidence. The following implemented
features start off: public stall offers/arrangements, barter, scroll
application, chair collection preference/direct trade, repricing, seasonal overlays, and ambient
fidgets. Enable one family at a time and compare its journal/report output against a control run.

For a local read-only report, run `tools/economy/Export-EconomyDashboard.ps1` and serve
`economy-dashboard`. The exporter reads only PostgreSQL and includes raw transaction, listing,
lot-provenance, decision, social, agent, daily price, meso-flow, and validation evidence for later
UI filtering.
- Browsers enter the actual PlayerShop visitor state and remain for
  `market.stallInspectionDurationPerListing × listing rows` before learning that exact stall.
  `market.interactionBehaviorProvider` can disable the optional locality/jitter presentation layer
  without disabling mandatory physical proximity or changing economic decisions.
- `AgentEconomyRuntime.requestStorage` walks an Agent back to the FM entrance and then opens the real
  account storage with its ordinary level, fee, capacity, and security rules. It is a temporary
  service seam for the future entrance storage NPC.
- A zero-NPC-floor collectible receives no invented cold-start meso value. It can be retained or
  exchanged through reciprocal-need barter. Positive-floor scarce items can seed an ask from their
  exact NPC opportunity cost and configured seller markup.
- Humans can transact through normal Cosmic PlayerShop validation and can answer an active open-chat
  seller by opening Trade and placing a meso-only offer. The seller places its exact reserved item,
  accepts at or above its structured reserve, and rejects unvalued item barter. Humans are not marked
  as agents and do not receive agent-only participant attribution.
- Quest acceptance and turn-in are owned by external activity and use Cosmic's authoritative Quest.wz
  requirements/actions under the logical clock and a named RNG stream. The reviewed Victoria policy catalog is extended at runtime
  only with WZ quests whose NPCs and objective sources are proven on generated Victoria maps.
  Timed, scripted, field-entry, buff, pet, reactor-only, and otherwise unsupported state remains
  fail-closed. One quest action is considered per external activity boundary and concurrent acceptance is
  YAML-bounded, preventing synchronized mass acceptance.

The 30-day profile sets `population.onboardingDuration: PT24H`. Each cohort opens and immediately
releases a bootstrap session (so starting holdings have provenance), then completes repeated exact
calibration-backed external activity segments. It cannot execute a market cycle until a fresh
admission after that deadline. This is deliberately not a synthetic “Maple Island completion” or
automatic character reset: the bound characters’ real starting levels, jobs, quests, inventories,
and matching calibration coverage remain authoritative. A true fresh-beginner/job-advancement
cohort therefore still requires prepared characters and captured beginner/advancement activity;
the economy engine will not invent that progression.
`activity.targetMarketParticipationFraction: 0.40` applies to the whole arrived population, not
only cohorts already eligible to trade. While newer cohorts are onboarding, the planner raises the
required FM share of eligible cohorts (bounded below 100%); it converges to 40% once all cohorts are
eligible. Small deterministic profile variation prevents an otherwise lockstep market/farm cycle.
- Farm settlement advances actual Cosmic kill counters from calibrated kill evidence. Quest costs,
  item rewards, mesos, realized EXP across level boundaries, selection, NPC, and input/output lots
  are committed through the same outbox transaction as quest status, fame, skill, inventory, and
  progression persistence.
- Owned scroll projects use the same authoritative Cosmic mutation as player packets. The transaction
  consumes the real scroll, applies success/failure/curse with a named deterministic RNG stream, and
  records the input equipment lot and transformed or destroyed result in the economy outbox.

## Independent Commerce test

Use `config/economy/economy-engine-basic.yaml`. It is a one-room, ten-agent, real-time profile with
external farming disabled and open-chat selling enabled for a fifty-percent seller sample. It still
requires the separate PostgreSQL evidence database, ten live autonomous characters on channel 1,
and real imported inventories; it deliberately does not create test items or mesos.

1. Park or stage the agents in the FM, then run
   `!economy preflight config/economy/economy-engine-basic.yaml`.
2. Start with an explicit run id so it can be resumed:
   `!economy start <uuid> config/economy/economy-engine-basic.yaml`.
3. Watch room 1 and the entrance. Use `!economy status` for lifecycle state and
   `!economy openchat` for offer id, seller, exact item, ask, reserve, room, advertisement count,
   expiry, and Trade state.
4. From a human client, Trade an advertising seller and place mesos. A confirmed offer below reserve
   is declined and the item is returned; an offer at or above reserve commits through normal Trade.
5. Run `!economy audit`, then `!economy stop` when finished.

For code-only isolation, run
`./mvnw.cmd -q '-Dtest=EconomyConfigLoaderTest,CosmicOpenChatSaleServiceTest,EconomyRunCoordinatorTest,CosmicNegotiatedTradeExecutorTest' test`.
