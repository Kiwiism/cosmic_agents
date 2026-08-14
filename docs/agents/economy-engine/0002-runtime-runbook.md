# Economy runtime runbook

## What starts a valid run

1. Configure `economy-engine.yaml`. YAML contains behavior and scenario policy only; credentials are
   environment variables.
2. Set distinct `ECONOMY_DB_*` credentials and initialize the separate PostgreSQL database through
   migrations V001-V010.
3. Start Cosmic MySQL and the game server normally.
4. Have at least `population.maximumAgents` live autonomous characters. The runtime maps them by
   ascending character id to `agent-1`, `agent-2`, and so on. Characters admitted at a future
   logical date must still be live and in the FM entrance or rooms at admission time.
5. Capture at least `activity.minimumCalibrationSamples` completed real autonomous sessions for every
   build/job/level/map cohort that may farm. Use
   `!economy calibration start <agent-character-id>` while that live agent is on the farm map, then
   `!economy calibration stop <agent-character-id> [died]`. The event bus records actual kills and
   explicit item consumption; the stop command writes the sample to the separate PostgreSQL database.
   `!economy calibration status <agent-character-id>` inspects an active capture. No matching
   calibration means no farm simulation.
6. Ensure intended stall owners legitimately possess the configured real PlayerShop permit. In v83
   this is a Cash Shop item and is neither a mob drop nor normal NPC stock.
7. Run `!economy start`, then `!economy advance 0` to process initial admissions. Use
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

`economy-database/queries/item_market_explanation.sql` is the item drill-down contract. It links
transactions, observations, lots, listing exposure, and decision reasons without treating an ask as
a completed price or imputing a meso price to barter.

## Deliberate fail-closed limits

- Seasonal overlays are typed but cannot be enabled until each mob-item relation is catalog-checked
  and the rule-exact resolver applies it.
- Offscreen death and farm congestion remain disabled until exact Cosmic death penalties and
  journaled active-map occupancy exist.
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
