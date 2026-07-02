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

After reconstruction is stable, implement scaling foundations first:

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

Then return to gameplay packages:

1. `agent-plan-runtime`
2. `agent-capability-runtime`
3. `agent-npc-quest-capability`
4. `agent-recovery-policy`
5. `maple-island-mvp`

Reason:

- Gameplay features multiply Agent work.
- Scaling foundations should exist before many Agents start running complex
  plan/capability loops.
- Observability should come before optimization so bottlenecks are measurable.

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

Must provide:

- background navigation arrival.
- background combat rounds.
- background NPC/quest validation and execution.
- background loot pickup decisions.
- background recovery.
- final state commit.

Rule:

- Background actions still validate live state before mutation.
- Background actions do not generate visual packets unless a real player can
  observe them.

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
