# Agent Platform Package Registry

This registry lists the modular packages that are currently well defined, the
packages that are partially defined, and the remaining discussed components
that should be promoted into full design and technical specifications.

The purpose is to make future implementation manageable after the Agent
reconstruction: pick one package, implement it behind stable interfaces, then
integrate it without mixing unrelated systems.

Top-level post-reconstruction platform specification:

- `docs/agents/POST_RECONSTRUCTION_AGENT_PLATFORM_SPECIFICATION.md`

Pre-reconstruction readiness and safety status:

- `docs/agents/PRE_RECONSTRUCTION_SAFE_PREP_STATUS.md`

## Package Classification

```text
Well defined:
  Has clear purpose, boundaries, contracts, and enough design/technical docs to
  begin implementation.

Partially defined:
  Has useful docs or policy, but still needs a dedicated design specification
  and/or technical implementation specification.

Backlog package:
  Discussed concept that should be packaged before implementation.
```

## Well-Defined Packages

### 1. Catalog Platform

Status: well defined.

Purpose:

- Build portable static game knowledge from WZ/XML, SQL, scripts, and
  overrides.
- Package fast indexes for Agent runtime and LLM lookup.
- Avoid taxing the live server during planning and decision loops.

Primary docs:

- `docs/agents/catalog-platform/CATALOG_PLATFORM_ARCHITECTURE.md`
- `docs/agents/catalog-platform/CATALOG_BUNDLE_SPEC.md`
- `docs/agents/catalog-platform/CATALOG_QUERY_API.md`
- `docs/agents/catalog-platform/PORTABLE_PLATFORM_TODO.md`
- `docs/agents/llm-autonomy/GAME_KNOWLEDGE_CATALOGS.md`

Owns:

- offline builders.
- catalog bundle manifest.
- static game data indexes.
- read-only query runtime.
- LLM-safe summaries.

Does not own:

- live state.
- quest/shop/script execution.
- profile decisions.
- server validation.

Implementation focus:

1. Normalize current exporters into one bundle.
2. Add manifest/hash/override merge.
3. Add read-only runtime.
4. Add fast query APIs and zero-scan rule.

### 2. NPC Catalog Package

Status: well defined for catalog data; runtime interaction is partially
defined.

Purpose:

- Catalog NPC placements, available actions, quest links, shops, dialogue
  timing hints, reward choices, services, and interaction spots.

Primary docs:

- `docs/agents/NPC_CATALOG_SCHEMA.md`
- `docs/agents/NPC_CATALOG_INTEGRATION_CONTRACT.md`
- `tools/npc-catalog/README.md`

Owns:

- NPC metadata.
- placement indexes.
- quest/action reverse indexes.
- shop/service/reward choice data.
- interaction spot candidates.
- dialogue length/timing data.

Does not own:

- actual NPC action execution.
- quest validation.
- server-side script execution.

Implementation focus:

1. Fold NPC outputs into Catalog Platform bundle.
2. Add generated fast indexes to runtime.
3. Add validation reports for missing quest/shop/script links.

### 3. Profile Platform

Status: well defined.

Purpose:

- Provide portable agent identity, traits, policy, memory, relationships,
  plan preferences, adaptation, decision journal, and LLM summaries.

Primary docs:

- `docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_DESIGN_SPECIFICATION.md`
- `docs/agents/profile-platform/AGENT_PROFILE_SYSTEM_TECHNICAL_SPECIFICATION.md`
- `docs/agents/profile-platform/PROFILE_RUNTIME_ARCHITECTURE.md`
- `docs/agents/profile-platform/PROFILE_DECISION_API.md`
- `docs/agents/profile-platform/PROFILE_ADAPTATION_SYSTEM.md`
- `docs/agents/llm-autonomy/AGENT_PROFILE_SCHEMA.md`
- `docs/agents/llm-autonomy/PROFILE_PLAN_SET_SYSTEM.md`

Owns:

- profile templates.
- profile snapshots.
- traits and mood.
- build intent.
- relationship memory.
- economy preferences.
- plan weights.
- profile adaptation from events.
- decision journal.
- LLM profile summary.

Does not own:

- action execution.
- server validation.
- static catalog facts.

Implementation focus:

1. Template loader and read-only profile runtime.
2. decision API.
3. event and journal foundation.
4. bounded adaptation.
5. LLM summary and patch preview.

### 4. Economy Engine

Status: well defined.

Purpose:

- Model item value, market behavior, buy/sell/hold/farm decisions, market
  observations, manipulation resistance, inflation/deflation signals, and
  agent economic behavior.

Primary docs:

- `docs/agents/llm-autonomy/ECONOMY_DESIGN_SPECIFICATION.md`
- `docs/agents/llm-autonomy/ECONOMY_TECHNICAL_IMPLEMENTATION_SPECIFICATION.md`
- `docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md`
- `docs/agents/llm-autonomy/ECONOMY_SYSTEM_SCHEMA.md`
- `docs/agents/llm-autonomy/ECONOMY_ENGINE_TODO.md`

Owns:

- price observations.
- liquidity and listing-age models.
- item valuation.
- equip stat valuation.
- supply/demand signals.
- market manipulation suspicion.
- buy/sell/hold/farm proposals.
- merchant strategy.

Does not own:

- direct trade execution.
- inventory mutation.
- profile identity.
- catalog parsing.

Implementation focus:

1. Price observation store.
2. baseline valuation.
3. market decision engine.
4. inventory valuation.
5. risk/manipulation policy.
6. plan card proposal generation.

### 5. Server Adapter Package

Status: well defined.

Purpose:

- Keep portable Agent platform packages decoupled from Cosmic server classes.
- Convert live server state into portable snapshots/events.
- Validate and execute safe server actions.

Primary docs:

- `docs/agents/server-adapter/SERVER_ADAPTER_CONTRACT.md`
- `docs/agents/server-adapter/MINIMAL_COSMIC_EDIT_INSTALL_TARGET.md`

Owns:

- live state snapshots.
- server action validation.
- safe action execution boundary.
- installer/patcher contract for clean Cosmic clones.
- event adapter from Cosmic runtime to portable events.

Does not own:

- profile learning logic.
- catalog static facts.
- economy decisions.
- plan scheduling.

Implementation focus:

1. DTOs and adapter interfaces.
2. read-only snapshots.
3. validation pipeline.
4. safe action execution.
5. clean Cosmic installer patcher.

### 6. Maple Island MVP Package

Status: well defined as first vertical slice.

Purpose:

- Make one agent complete the Maple Island questline MVP after reconstruction.
- Validate the minimum useful combination of plan runtime, catalog runtime,
  profile preferences, NPC/quest interaction, navigation, combat, loot, and
  recovery.

Primary docs:

- `docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md`
- `docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md`
- `docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md`
- `docs/agents/MAPLE_ISLAND_MVP_IMPLEMENTATION_PLAN.md`
- `docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md`
- `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md`
- `docs/agents/plans/maple-island-mvp.plan.json`

Owns:

- MVP route and objective sequence.
- MVP fallback policy.
- MVP test profile assumptions.
- MVP integration tests.

Does not own:

- full quest engine.
- full economy.
- all Victoria Island progression.

Implementation focus:

1. read-only plan card loader.
2. objective runner.
3. NPC quest start/complete capability.
4. combat/loot stop conditions.
5. recovery fallback.
6. full-run integration test.

### 7. Plan Runtime Package

Status: well defined.

Existing docs:

- `docs/agents/plan-runtime/PLAN_RUNTIME_DESIGN_SPECIFICATION.md`
- `docs/agents/plan-runtime/PLAN_RUNTIME_TECHNICAL_SPECIFICATION.md`
- `docs/agents/llm-autonomy/PLAN_CARD_SYSTEM.md`
- `docs/agents/llm-autonomy/PROFILE_PLAN_SET_SYSTEM.md`
- `docs/agents/plans/maple-island-mvp.plan.json`
- Maple Island MVP design/technical docs.

What is already clear:

- Plan Cards are portable data.
- Plans contain objectives, dependencies, exit criteria, focus modes, and
  allowed sidetracks.
- Plan selection is influenced by profile, economy, catalog, and live state.

Defined:

- plan loader.
- objective dependency graph.
- plan stack/sidetrack model.
- objective runner state machine.
- persistence/resume model.
- plan scheduler API.
- event emission contract.
- LLM plan command bridge.

Recommended package:

```text
agent-plan-runtime
```

### 8. Capability Runtime Package

Status: well defined.

Existing docs:

- `docs/agents/capability-runtime/CAPABILITY_RUNTIME_DESIGN_SPECIFICATION.md`
- `docs/agents/capability-runtime/CAPABILITY_RUNTIME_TECHNICAL_SPECIFICATION.md`
- `docs/agents/BOT_TO_AGENT_RECONSTRUCTION_MAP.md`
- `docs/agents/RECONSTRUCTION_RULES.md`
- `docs/agents/AGENT_FIX_TODO.md`
- `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md`

What is already clear:

- Navigation, combat, looting, inventory, trade, dialogue, build, equipment,
  supplies, and runtime state are being split from legacy bot code.
- Capabilities should expose safe commands/results.

Defined:

- common command/result model.
- capability lifecycle.
- capability validator interface.
- capability event emission.
- retry/fallback conventions.
- capability ownership boundaries.
- how capabilities consult profile/catalog/economy.

Recommended package:

```text
agent-capability-runtime
```

### 9. NPC / Quest Interaction Capability Package

Status: well defined.

Existing docs:

- `docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_DESIGN_SPECIFICATION.md`
- `docs/agents/npc-quest-capability/NPC_QUEST_CAPABILITY_TECHNICAL_SPECIFICATION.md`
- `docs/agents/NPC_CAPABILITY_PLAN.md`
- `docs/agents/NPC_CATALOG_INTEGRATION_CONTRACT.md`
- `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md`

What is already clear:

- Agents can skip visual dialogue but must satisfy requirements.
- Runtime must validate level, prequests, items, NPC range, and map.
- NPC realism should be optional for fast tests.

Defined:

- quest start/complete API.
- validation-only dry run.
- NPC range and approach spot integration.
- reward choice handling.
- script-sensitive action policy.
- auto-complete quest handling.
- failure/block reasons.
- event emission.

Recommended package:

```text
agent-npc-quest-capability
```

### 10. Runtime Event Bus

Status: well defined.

Primary docs:

- `docs/agents/event-bus/AGENT_EVENT_BUS_DESIGN_SPECIFICATION.md`
- `docs/agents/event-bus/AGENT_EVENT_BUS_TECHNICAL_SPECIFICATION.md`

Purpose:

- Decouple plan runtime, capability runtime, profile adaptation, economy,
  observability, LLM summaries, soak tests, and server adapter events.
- Provide bounded queues, priority classes, backpressure, durable append, and
  replay without storing raw Cosmic object references.

Owns:

- event envelope.
- publish/subscribe API.
- bounded queues.
- consumer budgets.
- drop/compact policy.
- durable append and replay contract.
- event bus diagnostics.

Does not own:

- plan state.
- capability execution.
- profile adaptation rules.
- economy valuation.
- LLM calls.
- server mutation.

Implementation focus:

1. event envelope and priority queues.
2. bounded subscriptions and consumer budgets.
3. durable append interface.
4. replay query API.
5. diagnostics snapshot.
6. integration with Plan Runtime and Capability Runtime.

### 11. Recovery / Survival Policy

Status: well defined.

Primary docs:

- `docs/agents/recovery-policy/AGENT_RECOVERY_POLICY_DESIGN_SPECIFICATION.md`
- `docs/agents/recovery-policy/AGENT_RECOVERY_POLICY_TECHNICAL_SPECIFICATION.md`

Purpose:

- Decide how Agents recover from danger, death, low HP/MP, no potions, no
  mesos, stuck movement, blocked objectives, full inventory, and repeated plan
  failures.
- Return bounded retry, rest, resupply, sidetrack, postpone, fail, or review
  recommendations without directly mutating server state.

Owns:

- blocker classification.
- recovery decision model.
- bounded recovery memory.
- recovery action proposals.
- recovery events and evidence.

Does not own:

- direct capability execution.
- server mutation.
- plan completion state.
- profile preference storage.
- economy valuation.

Implementation focus:

1. common recovery request/decision DTOs.
2. Maple Island MVP deterministic fallback rules.
3. death/low HP/no potion/navigation stuck policies.
4. inventory full and NPC/quest blocker policies.
5. bounded failure memory.
6. Plan Runtime and Capability Runtime integration.

### 12. Agent Observability / Diagnostics

Status: well defined.

Primary docs:

- `docs/agents/observability/AGENT_OBSERVABILITY_DESIGN_SPECIFICATION.md`
- `docs/agents/observability/AGENT_OBSERVABILITY_TECHNICAL_SPECIFICATION.md`

Purpose:

- Explain what Agents are doing, why, where they are blocked, and how much
  runtime cost they create.
- Provide per-Agent, per-map, scheduler, capability, plan, Event Bus, and
  memory snapshots for 2000-Agent scaling and future Agent Console pages.

Owns:

- metric counters.
- rolling latency/count windows.
- compact snapshots.
- top-N reports.
- incident records.
- soak exports.

Does not own:

- plan execution.
- capability execution.
- profile adaptation.
- economy decisions.
- LLM calls.

Implementation focus:

1. event-driven counters and snapshots.
2. per-Agent and per-map read models.
3. capability and plan latency/blocker metrics.
4. scheduler/Event Bus/memory snapshots.
5. soak JSONL/CSV export.
6. Agent Console query API.

### 13. Interaction Realism Package

Status: well defined.

Purpose:

- Make Agent NPC/shop/quest interactions less identical without putting
  exact timing and exact stop points inside Plan Cards.
- Provide optional random approach points, dialogue-length delays, profile
  variance, and anti-clustering.
- Keep deterministic mode available for fast Maple Island MVP testing.

Primary docs:

- `docs/agents/INTERACTION_REALISM_POLICY.md`
- `docs/agents/interaction-realism/INTERACTION_REALISM_DESIGN_SPECIFICATION.md`
- `docs/agents/interaction-realism/INTERACTION_REALISM_TECHNICAL_SPECIFICATION.md`

Owns:

- realism mode.
- approach point selection.
- dialogue delay calculation.
- point reservations.
- repeat-dialogue memory.
- profile timing samples.
- realism audit payloads.

Does not own:

- navigation pathfinding.
- NPC/quest/shop execution.
- plan objective state.
- server validation.
- profile storage.

Implementation focus:

1. implement `OFF`, `LIGHT`, and `FULL` modes.
2. add seeded approach point selection.
3. add dialogue-length delay calculation.
4. add bounded point reservations and repeat-dialogue memory.
5. integrate with NPC Quest and Shop capabilities after reconstruction.

### 14. Agent Simulation Tier Runtime

Status: well defined for simulation tier selection.

Purpose:

- Choose Agent simulation fidelity based on real-player map presence, map
  sensitivity, server load, and current capability needs.
- Keep visible Agents on a presentation path while allowing safe unobserved
  Agents to use cheaper background paths.
- Define materialization rules when an Agent becomes visible again.

Primary docs:

- `docs/agents/AGENT_ENGINE_OPTIMIZATION.md`
- `docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_DESIGN_SPECIFICATION.md`
- `docs/agents/simulation-tier-runtime/AGENT_SIMULATION_TIER_TECHNICAL_SPECIFICATION.md`

Owns:

- simulation mode selection.
- map sensitivity classification.
- allowed background shortcut declaration.
- materialization policy.
- per-Agent mode transition auditing.
- cost budget hints for scheduler/capabilities.

Does not own:

- Agent intent or plan selection.
- direct movement/combat/loot/NPC/shop execution.
- server validation rules.
- profile/economy/LLM decisions.
- packet generation.

Implementation focus:

1. implement `PRESENTATION`, `BACKGROUND_ACTIVE`,
   `BACKGROUND_ABSTRACT`, and `STRATEGIC_OFFLINE` decisions.
2. force presentation when real players are in the Agent map.
3. classify sensitive maps from live state and catalog metadata.
4. expose allowed shortcuts to capabilities.
5. add materialization planner and observability events.

### 15. Perception Runtime Package

Status: well defined.

Purpose:

- Convert live server state, catalog context, plan context, profile hints, and
  bounded memory into compact Agent/LLM perception snapshots.
- Provide urgent, active, strategic, and batch views without exposing raw
  server internals.
- Rank nearby entities by objective relevance, danger, market value, distance,
  and profile/social interest.

Primary docs:

- `docs/agents/llm-autonomy/PERCEPTION_MEMORY_SCHEMA.md`
- `docs/agents/perception-runtime/PERCEPTION_RUNTIME_DESIGN_SPECIFICATION.md`
- `docs/agents/perception-runtime/PERCEPTION_RUNTIME_TECHNICAL_SPECIFICATION.md`

Owns:

- snapshot assembly.
- snapshot detail levels.
- nearby entity summarization.
- relevance scoring.
- bounded snapshot caches.
- LLM-safe summaries.
- batch status rows.
- perception audit events.

Does not own:

- action execution.
- plan scheduling.
- profile storage.
- economy valuation.
- catalog building.
- server mutation.

Implementation focus:

1. implement bounded `URGENT`, `ACTIVE`, `STRATEGIC`, and `BATCH` snapshots.
2. add live-state plus catalog-context summary assembly.
3. add nearby entity relevance ranking.
4. add refresh policy tied to simulation tier.
5. add LLM-safe summarizer and batch row output.

### 16. Background Action Runtime

Status: well defined.

Purpose:

- Execute unobserved Agent movement, combat, loot, NPC/quest, shop, recovery,
  and plan-slice actions through validated low-fidelity simulation.
- Keep all player-visible behavior on the normal presentation path.
- Make 2000 concurrent Agents plausible by skipping invisible packets, map item
  creation, full physics, and per-action DB writes when safe.

Primary docs:

- `docs/agents/AGENT_ENGINE_OPTIMIZATION.md`
- `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`
- `docs/agents/background-action-runtime/BACKGROUND_ACTION_RUNTIME_DESIGN_SPECIFICATION.md`
- `docs/agents/background-action-runtime/BACKGROUND_ACTION_RUNTIME_TECHNICAL_SPECIFICATION.md`

Owns:

- background action routing.
- background navigation execution.
- background combat execution.
- background loot resolution.
- virtual loot/meso buffers.
- inventory reconciliation requests.
- background NPC/quest/shop execution wrappers.
- fairness budget checks.
- background action journals.
- strict debug comparison hooks.

Does not own:

- simulation tier decisions.
- plan selection.
- capability validation rules.
- catalog building.
- profile storage.
- economy price modeling.
- direct unvalidated server mutation.

Implementation focus:

1. add background action router and allowed-shortcut checks.
2. add route ETA and same-map ETA background navigation.
3. add abstract combat slices with shared formula calibration.
4. add virtual loot buffers and inventory reconciliation.
5. add direct validated NPC/quest/shop wrappers.
6. add fairness budgets, fail-closed behavior, and strict debug comparison.

## Partially Defined Packages

### 17. LLM Control Gateway Package

Status: contract defined, technical package not fully defined.

Existing docs:

- `docs/agents/llm-autonomy/LLM_CONTROL_CONTRACT.md`
- `docs/agents/llm-autonomy/README.md`
- `docs/agents/llm-autonomy/PERCEPTION_MEMORY_SCHEMA.md`

What is already clear:

- LLM gives intents and typed commands.
- Agent engine executes validated behavior.
- LLM should not spoof packets or directly mutate server state.
- Direct navigation-to-point should be exposed as a controlled command.

Missing package docs:

- `LLM_GATEWAY_DESIGN_SPECIFICATION.md`
- `LLM_GATEWAY_TECHNICAL_SPECIFICATION.md`

Needs definition:

- tool schemas.
- permission model.
- rate limits.
- command queue.
- plan proposal API.
- profile patch API.
- perception summary API.
- multi-agent assignment model.
- audit logs.

Recommended package:

```text
agent-llm-gateway
```

### 18. Quest / Combat Focus Policy Package

Status: policy defined, package not fully defined.

Existing docs:

- `docs/agents/QUEST_FOCUS_AND_COMBAT_POLICY.md`
- `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md`

What is already clear:

- Agents may focus on objective mobs but should kill other mobs when target
  spawn is depleted.
- Other mobs with relevant future quest loot can be prioritized.
- Recovery and postpone logic should be profile-aware.

Missing package docs:

- `QUEST_OBJECTIVE_POLICY_DESIGN_SPECIFICATION.md`
- `QUEST_OBJECTIVE_POLICY_TECHNICAL_SPECIFICATION.md`

Needs definition:

- objective focus state.
- mob spawn-pressure policy.
- preloot policy.
- quest item target policy.
- combat target scoring.
- fallback/postpone reasons.
- catalog dependencies.

Recommended package:

```text
agent-quest-objective-policy
```

## Backlog Packages To Promote

### 19. Agent Population Director

Status: discussed, not yet packaged.

Purpose:

- Control world-level distribution of archetypes, jobs, economic roles,
  social idlers, farmers, merchants, islanders, and progression paths.

Why useful:

- Prevents every agent from becoming the same optimal build.
- Lets the world feel intentionally varied.
- Can drive economy demand by class population growth.

Suggested docs:

- `AGENT_POPULATION_DIRECTOR_DESIGN_SPECIFICATION.md`
- `AGENT_POPULATION_DIRECTOR_TECHNICAL_SPECIFICATION.md`

Recommended package:

```text
agent-population-director
```

### 20. Relationship / Social Graph Runtime

Status: included inside profile docs, but may deserve its own package later.

Purpose:

- Maintain relationship memories, social groups, trust, party compatibility,
  trade reputation, and repeated interaction summaries.

Why useful:

- Makes social behavior and party/trade decisions more believable.
- Enables LLM or dashboards to inspect community dynamics.

Initial recommendation:

- Keep inside Profile Platform for MVP.
- Split into package only when relationship logic becomes large.

Potential package:

```text
agent-social-relationship-runtime
```

### 21. Portable Installer / Patcher

Status: contract exists, implementation package not fully specified.

Purpose:

- Install the portable platform into clean Cosmic-like servers with minimal
  marker-block edits.

Why useful:

- Keeps the agent platform portable.
- Makes future updates/uninstall possible.

Existing docs:

- `docs/agents/server-adapter/MINIMAL_COSMIC_EDIT_INSTALL_TARGET.md`

Suggested extra docs:

- `PORTABLE_INSTALLER_TECHNICAL_SPECIFICATION.md`

Recommended package:

```text
agent-platform-installer
```

### 22. Agent Soak Test Harness

Status: partially defined; implementation specification exists.

Purpose:

- Provide repeatable long-running scale tests for the reconstructed Agent
  runtime.
- Validate 50, 250, 500, 1000, and 2000-agent stages.
- Capture server, Agent, persistence, map, and gameplay-validity metrics.
- Test population spread, hidden simulation, materialization, DB pressure, and
  shutdown/restart behavior.

Primary docs:

- `docs/agents/AGENT_SOAK_TEST_IMPLEMENTATION_SPEC.md`
- `docs/SOAK_TEST_CHECKLIST.md`
- `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`

Owns:

- soak command surface.
- scenario runner.
- population preset loading for tests.
- spawn wave runner.
- periodic snapshot collector.
- CSV/JSON output.
- materialization storm tests.
- DB pressure tests.
- stage pass/fail reporting.

Does not own:

- Agent gameplay capability implementation.
- player 500-concurrency testing.
- LLM/economy validation.
- server production monitoring outside soak runs.

Implementation focus:

1. Add snapshot collector and log writer.
2. Add population preset runner.
3. Add `!soak agents ...` command surface.
4. Add scenario runner.
5. Add materialization and DB pressure tests.
6. Add long-run stage reports.

Recommended package:

```text
agent-soak-test-harness
```

## Recommended Implementation Order

There are now two implementation tracks:

- scaling-first track: `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`.
- gameplay track: `docs/agents/AGENT_GAMEPLAY_TRACK.md`.

If the immediate post-reconstruction goal is 2000 concurrent Agents, follow the
scaling-first order before gameplay-heavy packages.

### Scaling-First Order

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

### Gameplay Order

For after reconstruction, implement packages in this order:

1. `agent-event-bus`
2. `agent-catalog-platform`
3. `agent-profile-platform` read-only mode
4. `agent-plan-runtime` read-only loader and objective state
5. `agent-capability-runtime` common command/result interfaces
6. `agent-npc-quest-capability`
7. `agent-recovery-policy`
8. `maple-island-mvp`
9. `agent-observability`
10. `agent-economy-engine`
11. `agent-interaction-realism`
12. `agent-simulation-tier-runtime`
13. `agent-llm-gateway`
14. `agent-population-director`

Reasoning:

- Event bus first keeps later packages decoupled.
- Catalog/profile/plan provide read-only decision foundations.
- Capability runtime and NPC quest unlock Maple Island MVP.
- Observability should come before large-scale economy/LLM behavior.
- Simulation tiers and LLM gateway are powerful, but safer after the basic
  autonomous loop is visible and explainable.

## Next Specs To Write

Highest priority:

1. Agent Soak Test Harness command/runner spec.
2. Catalog builder validation/report spec.
3. LLM Gateway design + technical specs.
4. Population Director design + technical specs.
5. Portable Installer technical spec.

Second priority:

1. Quest Objective Policy design + technical specs.
2. Relationship / Social Graph split-out spec if profile package grows large.

Later:

1. Advanced full-game quest objective policy specs after Maple Island MVP.
