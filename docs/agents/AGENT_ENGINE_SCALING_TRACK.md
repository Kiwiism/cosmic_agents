# Agent Engine Scaling Track

This track separates Agent engine optimization/scaling work from gameplay
capability work.

Primary target:

```text
Support 2000 concurrent Agents with stable server responsiveness.
```

Secondary target:

```text
Keep the architecture ready for gameplay packages, but do not let gameplay
feature work block scaling hardening after reconstruction.
```

Current sequencing decision:

```text
Before scaling work starts, prove the reconstructed Agent engine through the
Capability Runtime, Amherst MVP, and Maple Island MVP. Then decide which
NuTNNuT-original behaviors are retained, gated, or disabled as legacy. Scaling
starts after that stable gameplay baseline exists.
```

This sequencing gates production behavior changes and default enablement. It
does not block behavior-preserving scheduler foundation work behind disabled
rollout modes. Central scheduler Phases 0-6 may establish measurement, APIs,
mailbox ownership, bounded heaps, budgets, async completion, affinity evidence,
and fixed shard ownership while the legacy guarded tick remains the default.
Production multi-shard rollout, cadence changes, simulation-tier optimization,
and making the new scheduler the default remain blocked until capability
parity and live/soak evidence exist.

Current scheduler implementation checkpoint on
`feature/agent-central-scheduler-runtime`:

- Phase 0 baseline evidence and Phase 1 scheduler API are complete.
- Phase 2 mandatory mailbox ownership is complete for live external ingress
  and generation-scoped delayed callbacks.
- Phase 3 replaces the central-sequential global scan/sort with bounded
  coalesced ingress and a one-shard indexed due-time heap. Deterministic
  50/100/250/500-session cadence tests pass without changing gameplay cadence.
- Phase 4 adds explicit work/priority classes, 10 ms and 256-item default cycle
  guards, cost EWMA, critical/visible reserved passes, repeated starvation
  aging, immediate bounded continuations, and 2048-sample rolling delay/cost
  percentiles.
- Legacy per-Agent scheduling remains the production default and rollback.
- Phase 5 is complete: navigation graph work, Amherst persistence, LLM/network,
  and trade/item analysis run on isolated bounded executors with generation/
  request-stamped mailbox completion. Static tests prohibit direct Agent future
  waits and restrict synchronous graph construction to explicit tools.
- Phase 6 is locally complete: every root gateway contract has a closed
  affinity classification, known sibling writes enter destination mailboxes,
  and fixed stable-hash shards expose local and aggregate queue metrics.
  `CENTRAL_SHARDED` remains explicit opt-in pending live parity and staged soak.
- Phase 7 is locally complete behind `agents.scheduler.simulation.enabled`.
  O(1) real-player map-observation transitions wake affected owner shards;
  presentation cadence is unchanged, unobserved Agents may use a reduced
  authoritative cadence, and abstract execution remains denied. Live-client
  materialization parity and staged mixed-mode soaks remain rollout gates.
- Phase 8 is locally complete behind `agents.scheduler.tickSlicing.enabled`.
  The guarded tick is an ordered bounded frame with continuation limits,
  per-slice metrics, and lifecycle cleanup. Mailbox drain, early-return,
  failure, and movement-settlement semantics remain unchanged. It is disabled
  by default pending live-client parity and measured p99 slice evidence.
- Phase 9 is locally complete behind `agents.scheduler.loadShedding.enabled`.
  Scheduler/JVM pressure drives six reason-coded levels with escalation and
  recovery hysteresis. Cosmetic dialogue, selected external lanes, idle
  low-priority background ticks, and new admission shed in order; visible,
  critical, navigation, and mailbox/completion work stay admitted. The
  configured 2000-session ceiling does not replace staged soak evidence.
- Phase 10's scheduler-owned quiescence contract is locally complete in all
  scheduler modes. Generation-bound tokens wait for bounded frame completion,
  session async work, and critical completion delivery while ordinary mailbox
  actions remain frozen. Timeout and lifecycle cancellation fail safely. The
  removed Double Agent/profile runtime was not recreated; a future profile
  implementation must consume this boundary and separately prove canonical
  restoration.
- Phase 11's deterministic pre-soak gate runs central-sequential cadence through
  2,000 sessions and four stable-hash shards through 40,000 updates. It rejects
  duplicate self-execution and verifies that registrations, due/ready state,
  ingress, and scheduler ownership all return to zero after cancellation.
  This is not a substitute for live-client or sustained server soak evidence.
- Process-level Agent shutdown is locally complete: admission closes before
  Agent teardown, live schedule handles are cancelled, central shard state and
  pending async requests are drained, workload executors stop under a bounded
  deadline, and restart reopens only a clean runtime. Live server shutdown and
  restart timing remain part of the staged gate.

## Track Split

### Track A - Optimization And Scaling

Focus:

- agent scheduler cost.
- tick budgets.
- simulation tiers.
- background abstraction.
- broadcast suppression.
- perception throttling.
- route ETA.
- profiling and diagnostics.
- memory and lifecycle cleanup.
- event/audit batching.
- backpressure.
- server safety under many Agents.

Success:

- thousands of Agents can exist and make progress.
- real player packet handling remains responsive.
- no runaway tick, memory, or broadcast cost.
- Agent work is observable and throttleable.

### Track B - Gameplay Capability

Focus:

- quest start/complete.
- NPC interaction.
- combat correctness.
- looting.
- inventory policy.
- shop/trade.
- skill coverage.
- profile/economy/LLM behavior.
- Maple Island and later quest packages.

Success:

- Agents can complete meaningful gameplay objectives.
- capabilities behave like players where required.
- all mutating actions are validated.

## Recommended Post-Reconstruction Priority

After reconstruction is stable, do not start the scaling track immediately.
First prove that the reconstructed engine has a stable capability boundary and
can complete real gameplay.

Capability-first baseline order:

1. reconstruction closeout.
2. `agent-capability-runtime` foundation.
3. minimal `agent-plan-runtime` loader and objective runner.
4. minimal server adapter action boundary.
5. Amherst catalog/runtime slice.
6. NPC/quest, inventory/item-use, combat/loot, reactor, and recovery capability
   adapters.
7. Amherst MVP smoke test.
8. full Maple Island MVP.
9. NuTNNuT-original behavior review and gating.
10. stable Agent engine baseline.

Then start scaling foundations:

1. `agent-observability`
2. `agent-event-bus`
3. `agent-scheduler-runtime`
4. `agent-simulation-tier-runtime`
5. `agent-perception-runtime`
6. `agent-route-eta-runtime`
7. `agent-background-action-runtime`
8. `agent-load-shedding-policy`
9. `agent-memory-lifecycle-runtime`
10. scale soak tests

Reason:

- Scaling before capability proof optimizes an engine whose gameplay contract is
  still unstable.
- Amherst and Maple Island provide concrete acceptance tests for navigation,
  NPC quest interaction, item use, combat, loot, reactor handling, recovery, and
  plan execution.
- Once the gameplay baseline is proven, scaling work can optimize a known-good
  behavior surface instead of chasing behavior drift.

## Agent Runtime Bottleneck Backlog

Moved from the server bottleneck review so these do not remain mixed into the
server-only hardening backlog.

### Highest-Value Agent Optimizations

1. Sharded/budgeted Agent scheduler.
   - Current bottleneck: per-Agent scheduled futures and broad tick callbacks
     can multiply timer queue pressure.
   - Target change: move Agent ticks into bounded scheduler shards with per-tick
     budgets, queue depth metrics, p50/p95/p99 delay metrics, and load shedding.
   - Expected value: very high for 2000 concurrent Agents.

2. Simulation tier runtime.
   - Current bottleneck: invisible Agents do too much presentation-grade work.
   - Target change: switch Agents between presentation, background active, and
     background abstract modes based on real-player map presence and map
     sensitivity.
   - Expected value: very high for CPU, broadcast, movement, and perception cost.

3. Server-state perception instead of packet-based perception.
   - Current bottleneck: packet construction and client-shaped state are poor
     interfaces for Agent decision making.
   - Target change: build compact perception snapshots from server state and let
     Agents consume those instead of raw packet side effects.
   - Expected value: very high for invisible/background Agents and LLM readiness.

4. Broadcast/cosmetic suppression for non-visible Agents.
   - Current bottleneck: movement, attack, effect, chat, emote, and fidget
     presentation work has no value when no real player can observe it.
   - Target change: suppress or defer visual packets/cosmetics outside
     presentation mode while preserving validated state changes.
   - Expected value: high, especially on maps populated only by Agents.

5. Route ETA and background action runtime.
   - Current bottleneck: full physics ticks for unobserved navigation and
     same-map travel.
   - Target change: use portal-to-portal route ETA catalogs and same-map ETA
     heuristics, then materialize to valid footholds when needed.
   - Expected value: high for large background populations.

6. Agent memory lifecycle ownership.
   - Current bottleneck: plans, profiles, route state, perception snapshots,
     targets, conversations, and LLM state can retain stale `Character`,
     `MapleMap`, or `MapObject` references if ownership is unclear.
   - Target change: every Agent runtime cache declares owner, max expected size,
     cleanup hook, and diagnostic count.
   - Expected value: high for 30-day uptime.

7. Agent-specific degradation policy.
   - Current bottleneck: under server pressure, all Agent work may compete with
     real player work unless explicitly prioritized.
   - Target change: shed work in order: LLM calls, cosmetic chat, proactive
     offers, long-range planning, background catalog queries, non-critical
     movement/combat, while preserving core safety and player-visible state.
   - Expected value: high for stability under bursts.

### Server Dependency Notes

- Core server scheduler lanes, DB diagnostics, map growth diagnostics, and cache
  counts remain server-only support work.
- Agent runtime should consume those diagnostics but must not require core server
  files to depend on concrete Agent implementation classes.
- `AgentPresence` provider installation belongs to the portable Agent package,
  not core server hardening.

## Scaling Packages

### 1. Agent Observability

Purpose:

- See where Agent CPU, memory, queue time, tick time, failures, and blocked work
  are going.

Must provide:

- per-Agent current mode, plan, objective, and tick cost.
- per-map Agent count and mode breakdown.
- scheduler queue depth.
- tick budget overrun counters.
- capability latency counters.
- event backlog counters.
- memory counts for Agent state, route state, perception state, and journals.
- top slow Agents.
- top slow maps.
- command/debug views.

Current scheduler implementation provides bounded top slow/overdue Agent,
map, mailbox, current failure-window, and instrumented capability views plus
per-Agent and per-map drill-down. Plan/objective and memory/journal counts
remain capability-observability work outside scheduler ownership.

Why first:

- Without observability, scaling work becomes guessing.

### 2. Agent Event Bus

Purpose:

- Decouple plan, capability, profile, economy, diagnostics, and server adapter
  events.

Must provide:

- bounded queues.
- per-topic budgets.
- async low-priority consumers.
- high-priority safety events.
- event dropping/compaction rules for noisy events.
- replay or audit mode for selected Agents.

Scaling rule:

- Never let low-value Agent telemetry block real player server work.

### 3. Agent Scheduler Runtime

Purpose:

- Replace naive per-Agent full ticks with budgeted, prioritized work.

Must provide:

- per-tick budget.
- per-Agent next-run time.
- priority classes.
- map-aware scheduling.
- fairness.
- backpressure.
- overload behavior.

Suggested priority order:

```text
real player server work
visible Agent safety/presentation
visible Agent gameplay
sensitive-map background Agent work
abstract background Agent work
cosmetic/social Agent work
LLM/economy/background learning
```

Recommended scheduler model:

```text
AgentWorkItem:
  agentId
  workKind
  priority
  dueAtMs
  estimatedCost
  mapId
  simulationMode
  cancellationToken
```

### 4. Simulation Tier Runtime

Purpose:

- Run Agents at different fidelity depending on player visibility and map
  sensitivity.

Modes:

- `PRESENTATION`: real player in same map; full fidelity.
- `BACKGROUND_ACTIVE`: no real player, but map is sensitive.
- `BACKGROUND_ABSTRACT`: no real player, map safe to abstract.

Must provide:

- real-player-presence detection per map.
- sensitive map classifier.
- mode transition hooks.
- materialization rules.
- mode-specific capability behavior.
- safety validation before committed state changes.

Target effect:

- Only a minority of Agents should require full movement/physics/presentation
  at any moment.

### 5. Perception Runtime

Purpose:

- Avoid every Agent scanning live maps at full frequency.

Must provide:

- map-level shared perception snapshots.
- Agent-local filtered views.
- refresh intervals by simulation mode.
- invalidation on important changes.
- bounded entity lists.
- no full scans inside every Agent tick.

Suggested cadence:

```text
PRESENTATION:       frequent, accurate
BACKGROUND_ACTIVE:  reduced, shared per map
BACKGROUND_ABSTRACT: event/ETA driven
```

### 6. Route ETA Runtime

Purpose:

- Replace invisible full physics travel with estimated travel timing.

Must provide:

- portal-to-portal ETA catalog.
- point-to-target ETA heuristic.
- movement-profile speed/jump modifiers.
- route progress state.
- materialization point selection.
- interruption handling.

First version:

```text
effectivePx = dx + dy * 2
if target.y < current.y: effectivePx *= 1.25
if currentFoothold != targetFoothold: effectivePx += 400
etaMs = effectivePx / speedPxPerSec * 1000
etaMs *= random(0.85, 1.25)
etaMs = clamp(500, 30000)
```

### 7. Background Action Runtime

Purpose:

- Execute background Agent actions with state fidelity and minimal presentation
  cost.
- Keep "cheats" in a separate background path instead of mixing them into normal
  player-visible combat/loot/server paths.

Must provide:

- presentation path versus background path contract.
- background navigation arrival.
- background combat rounds.
- direct loot credit without map item creation when no real player can observe.
- virtual loot and meso buffers.
- inventory buffer reconciliation.
- outcome batching for hidden farming slices.
- probability smoothing for common drops and explicit rare-event rolls.
- background NPC/quest validation and execution.
- background loot pickup decisions.
- background shop transaction shortcuts.
- background recovery.
- background death shortcut.
- potion/rest resource model.
- quest item reservation policy.
- map crowding and region allocation hooks.
- fairness/progress budgets.
- strict debug comparison mode.
- final state commit.
- materialization when a real player enters the map.

Rule:

- Background actions still validate live state before mutation.
- Background actions do not generate visual packets unless a real player can
  observe them.
- Background actions may skip presentation mechanics, but must not skip server
  validation or produce impossible final state.
- Rare, player-relevant, or economy-sensitive outcomes should be journaled and
  checkpointed earlier than routine background progress.

### 8. Load Shedding Policy

Purpose:

- Keep server responsive during overload.

Must provide:

- reduce cosmetic behavior first.
- reduce background perception cadence.
- pause low-priority Agents.
- delay LLM/economy/profile adaptation.
- cap per-map Agent active work.
- preserve visible/safety-critical Agents.

Never shed:

- real player packet handling.
- server shutdown/save safety.
- Agent cleanup required to avoid stale state.

### 9. Memory Lifecycle Runtime

Purpose:

- Prevent 2000 Agents from leaking state across relog, death, map changes,
  cancellation, and shutdown.

Must provide:

- lifecycle ownership per Agent.
- cleanup hooks.
- route/perception/combat state expiration.
- event/journal compaction.
- bounded caches.
- stale Agent detection.
- shutdown flush policy.

## Gameplay Packages Deferred During Scaling Focus

Do not start these until the scaling foundation is measurable:

- full defensive buff parity.
- full skill capability matrix.
- Teleport/Flash Jump.
- generalized shop automation.
- economy engine runtime.
- LLM gateway.
- population director.
- relationship/social graph split.

Exceptions:

- Small gameplay hooks needed to test scaling loops are allowed, but they should
  stay minimal and observable.

## 2k Agent Test Plan

### Stage 0 - Baseline

Goal:

- Know current cost before optimization.

Run:

- 50 Agents.
- 100 Agents.
- 250 Agents.

Measure:

- server tick time.
- Agent tick time.
- CPU.
- heap.
- GC pauses.
- map scan counts.
- packet/broadcast counts.
- queue depth.

### Stage 1 - Scheduler And Observability

Goal:

- Budget and monitor Agent work.

Run:

- 250 Agents.
- 500 Agents.

Pass:

- no unbounded queue growth.
- slow Agents/maps visible in diagnostics.
- real player login/channel actions stay responsive.

### Stage 2 - Broadcast And Cosmetic Suppression

Goal:

- Remove invisible presentation cost.

Run:

- 500 Agents.
- 1000 Agents.

Pass:

- maps without real players do not emit movement/attack/cosmetic broadcasts.
- visible maps still look normal.

### Stage 3 - Background Active Mode

Goal:

- Reduce work in no-player sensitive maps without abstracting unsafe state.

Run:

- 1000 Agents.

Pass:

- reduced movement/perception cadence.
- no broken map state in sensitive maps.

### Stage 4 - Background Abstract Mode

Goal:

- Use route ETA and abstract action state for safe invisible maps.

Run:

- 1000 Agents.
- 1500 Agents.

Pass:

- Agents continue making progress.
- materialization works when player enters map.
- no invalid map positions after mode switch.

### Stage 5 - 2000 Agent Soak

Goal:

- Sustain 2000 Agents.

Run:

- 2000 Agents.
- at least 8 hours first.
- later 24 hours.
- later multi-day soak.

Pass:

- no memory growth trend after warmup.
- bounded event queues.
- bounded scheduler delay.
- real player actions responsive.
- no mass stuck/death loops.
- clean shutdown and restart.

## Metrics To Add

Core:

- active Agent count.
- Agent count by simulation mode.
- Agent count by map.
- scheduler queue depth.
- scheduler delay p50/p95/p99.
- Agent work time p50/p95/p99.
- capability result counts.
- failure/blocker counts.
- materialization count.
- live validation failure count.

Cost:

- movement ticks skipped/suppressed.
- broadcasts suppressed.
- map scans avoided.
- perception cache hits.
- route ETA usage.
- abstract combat rounds.
- background action commits.

Health:

- heap used.
- GC pause time.
- Agent state object counts.
- stale Agent state count.
- event backlog.
- journal backlog.
- cleanup count.

## Acceptance Criteria For Scaling Track

Minimum acceptance before gameplay-heavy work:

- 1000 Agents can run with no real players observing most maps.
- real player can login, move, change maps, and interact normally.
- visible Agents still present correctly.
- invisible Agents do not generate unnecessary broadcasts.
- scheduler and event queues remain bounded.
- diagnostics show top costs.

Target acceptance:

- 2000 Agents can run in mixed modes.
- at least 24-hour soak without memory growth trend.
- no runaway Agent loops.
- materialization from background mode is safe.
- server remains responsive to real players.

## First Implementation Cut

Implement only:

1. observability counters.
2. per-map real-player presence detection.
3. `AgentSimulationMode`.
4. scheduler budget and next-run time.
5. invisible broadcast/cosmetic suppression.
6. reduced background perception cadence.
7. background same-map ETA.
8. materialization validation.
9. load shedding for low-priority Agent work.
10. 250/500/1000 Agent soak scripts.

Leave gameplay behavior as-is except where needed to expose mode-specific hooks.
