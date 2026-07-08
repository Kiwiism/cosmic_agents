# Portable Platform TODO

This TODO tracks the future portable Agent Knowledge Platform.

## Documentation Contracts

- [x] Pre-reconstruction safe prep readiness map.
- [x] Catalog platform architecture.
- [x] Catalog bundle spec.
- [x] Catalog query API.
- [x] Catalog builder validation/report specification.
- [x] Profile runtime architecture.
- [x] Profile decision API.
- [x] Profile adaptation system.
- [x] Agent profile system design specification.
- [x] Agent profile system technical specification.
- [x] Agent platform package registry.
- [x] Agent engine scaling track.
- [x] Agent gameplay track.
- [x] Post-reconstruction Agent platform specification.
- [x] Layman Agent engine vision and reconstruction overview.
- [x] Server adapter contract.
- [x] Minimal Cosmic edit install target.
- [x] Plan Runtime design specification.
- [x] Plan Runtime technical specification.
- [x] Capability Runtime design specification.
- [x] Capability Runtime technical specification.
- [x] NPC Quest Capability design specification.
- [x] NPC Quest Capability technical specification.
- [x] Agent Event Bus design specification.
- [x] Agent Event Bus technical specification.
- [x] Agent Event Bus envelope schema.
- [x] Agent Event Bus subscription and replay schemas.
- [x] Recovery Policy design specification.
- [x] Recovery Policy technical specification.
- [x] Agent Observability design specification.
- [x] Agent Observability technical specification.
- [x] Interaction Realism design specification.
- [x] Interaction Realism technical specification.
- [x] Agent Simulation Tier design specification.
- [x] Agent Simulation Tier technical specification.
- [x] Agent Simulation Tier decision and materialization schemas.
- [x] Perception Runtime design specification.
- [x] Perception Runtime technical specification.
- [x] Background Action Runtime design specification.
- [x] Background Action Runtime technical specification.
- [x] Background Action request, result, and virtual-state schemas.
- [x] Agent Soak Test Harness design specification.
- [x] Agent Soak Test Harness technical specification.
- [x] Agent Soak Test Harness scenario manifest and summary schemas.
- [x] Agent Soak Test Harness population preset schema and first seeded preset.
- [x] LLM Gateway design specification.
- [x] LLM Gateway technical specification.
- [x] Agent Population Director design specification.
- [x] Agent Population Director technical specification.
- [x] Portable Installer technical specification.
- [x] Quest Objective Policy design specification.
- [x] Quest Objective Policy technical specification.
- [x] Social Relationship Runtime design specification.
- [x] Social Relationship Runtime technical specification.

## Builder Work

- [x] Document unified catalog bundle output and validation/report requirements.
- [x] Add catalog bundle prep verifier and draft manifest reporter.
- [ ] Move current `tools/game-catalog` and `tools/npc-catalog` outputs toward a unified catalog bundle layout.
- [x] Add draft manifest generation for current generated catalog outputs.
- [x] Add generated catalog file hashes to draft manifests.
- [x] Add opt-in source hashing for WZ, SQL, scripts, and catalog overrides
  during catalog refresh runs.
- [ ] Add final portable-bundle `manifest.json` generation.
- [ ] Add final portable-bundle source hashing for WZ, SQL, scripts, and overrides.
- [ ] Add derived index generation.
- [ ] Add override merge step.
- [x] Define validation report requirements for dangling references.
- [ ] Add bundle compatibility checks.
- [ ] Add extended full-game catalogs:
  - [x] reactor/field object placement catalog prep:
    `tools/reactor-catalog/Export-ReactorCatalog.ps1`,
    `tmp/reactor-catalog/generated_reactor_catalog.json`, and Java
    `ReactorCatalogQuery`.
  - [ ] final portable field-object normalization beyond WZ reactors.
  - foothold/reachability catalog.
  - jump-quest route graph catalog.
  - travel service catalog.
  - quest reward choice catalog.
  - dialogue option catalog.
  - maker/crafting catalog.
  - reward source catalog for gachapon, boxes, events, PQ rewards, and scripted rewards.
  - party/event/PQ catalog.
  - boss/area boss catalog.
  - monster skill risk catalog.
  - return/resupply catalog.
  - job/build progression catalog.
  - scroll/upgrade catalog.
  - server config/rule catalog.
- [ ] Generate fast lookup indexes:
  - ID and name indexes.
  - map/navigation indexes.
  - NPC/quest reverse indexes.
  - item/economy source indexes.
  - mob/combat/training indexes.
  - risk/manual-review/action-affordance indexes.
  - quest edge-case status indexes.
- [x] Document Victoria <30 quest status catalog override:
  - `docs/agents/catalog-overrides/victoria-lt30-quest-status.catalog.json`.
  - `docs/agents/catalog-overrides/VICTORIA_LT30_QUEST_STATUS_CATALOG.md`.
- [ ] Merge catalog override inputs into the portable catalog builder so they
  are emitted with normal bundle manifests and hashes.
- [ ] Generate LLM summary indexes for maps, item acquisition, questlines,
  training, economy, and action affordances.
- [x] Add read-only smoke verification for representative generated catalog
  lookups used by Agent/LLM runtime.

## Runtime Work

- [ ] Create read-only catalog runtime interfaces.
- [ ] Load JSON bundle.
- [ ] Build in-memory indexes.
- [ ] Add optional SQLite cache.
- [ ] Add LLM-safe summary APIs.
- [ ] Add batch query APIs.
- [ ] Add zero-scan lookup rule: agent tick and LLM batch paths must use
  prebuilt indexes or bounded top-N indexes, never full catalog scans.
- [ ] Add cache warmup path for high-frequency indexes used by navigation,
  quest, NPC, item acquisition, and resupply decisions.
- [ ] Implement economy engine backlog from `docs/agents/llm-autonomy/ECONOMY_ENGINE_TODO.md`.

## Profile Work

- [x] Create profile schema files.
- [x] Add data-only `maple-island-mvp-tester` and `islander` starter profile
  templates.
- [x] Add profile template verifier for required fields, trait ranges, and
  Maple Island hard constraints.
- [ ] Create profile store interface.
- [ ] Create mood engine.
- [ ] Create build intent model for job path, stat build, equipment goals, and
  acquisition preferences.
- [ ] Create relationship memory model for agent/player trust, affinity, trade
  reliability, party compatibility, and avoidance.
- [x] Document Social Relationship Runtime design and technical specifications.
- [ ] Create behavior sampler.
- [ ] Create policy engine.
- [ ] Add event feedback model.
- [x] Define `AgentExperienceEvent` and `ProfilePatch` schema files.
- [ ] Add append-only profile event store.
- [ ] Add append-only profile patch store.
- [ ] Add profile adaptation engine with bounded rule evaluation.
- [x] Add profile patch validators that prevent hard policy mutation.
- [ ] Add adaptation modes: off, observe-only, bounded, and fast-learn-test.
- [ ] Add replay tooling to rebuild profile state from event and patch logs.
- [ ] Add adaptation rules for plan/objective outcomes, navigation failures,
  combat danger, farming dry streaks, market outcomes, and relationships.
- [ ] Add strategic decision journal with reason codes, influences,
  alternatives considered, and outcomes.
- [x] Add portable Profile Decision request/result schemas.
- [ ] Add profile decision APIs for build progression and equipment acquisition.
- [ ] Add profile decision APIs for relationship actions, trade counterparties,
  and party/help requests.
- [x] Add LLM-safe profile summary schema and offline summary generator.
- [ ] Add live LLM profile summary API.

## Population Director Work

- [x] Define Agent Population Director design and technical specifications.
- [x] Create world population plan schema files.
- [x] Create cohort and role target schema files.
- [x] Create map capacity policy schema files.
- [x] Create population snapshot schema contract.
- [x] Create population assignment and rebalance proposal schema contracts.
- [x] Create population demand signal schema contract for Economy Engine.
- [ ] Create population snapshot provider interface.
- [ ] Create target-vs-current gap planner.
- [ ] Create capacity-aware spawn wave planner.
- [ ] Create stable rebalance proposal planner.
- [ ] Add anti-thrash cooldown and proposal rejection memory.
- [ ] Add hard constraint validation against Profile Platform.
- [ ] Add map capacity validation against Catalog Platform.
- [ ] Add population demand signal builder for Economy Engine.
- [x] Add seeded population presets for soak tests.
- [ ] Add observe-only and plan-only modes before live assignment.
- [ ] Add decision journal records for assignments and rebalances.
- [ ] Add Agent Console population overview and proposal preview.

## Plan Card Work

- [x] Document Plan Card system contract.
- [x] Document Maple Island MVP implementation plan.
- [x] Document Maple Island capability 100% completion plan.
- [x] Define Plan Runtime design and technical specifications.
- [x] Define Plan Card JSON schema files.
- [x] Add plan bundle format for reusable plans.
- [ ] Add plan objective dependency graph model.
- [ ] Add plan scheduler interface.
- [ ] Add objective runner interface.
- [x] Add sidetrack/plan-stack persistence model.
- [x] Add plan event envelope schema.
- [ ] Add LLM plan command tools.
- [ ] Add direct LLM `NAVIGATE_TO_POINT` command as a temporary sidetrack plan/objective.
- [x] Add Maple Island sample plan card.
- [x] Add validator for plan exit criteria and forbidden actions.

## Maple Island MVP Work

- [x] Document final Maple Island MVP route handoff.
- [x] Document Maple Island MVP design specification.
- [x] Document Maple Island MVP technical implementation specification.
- [x] Add read-only plan card loader as an offline prep tool.
- [x] Add plan progress state model.
- [x] Add objective status model.
- [ ] Add capability command/result model.
- [ ] Implement QuestCapability read APIs.
- [ ] Implement `NpcQuestInteractionCapability` validation-only path.
- [ ] Implement quest start/complete execution using `Quest.start` and `Quest.complete`.
- [ ] Add navigation objective adapters for NPC, map, portal, and point targets.
- [ ] Add portal travel verification.
- [ ] Add inventory count/free-slot APIs.
- [ ] Add loot objective stop conditions.
- [ ] Add combat objective stop conditions.
- [ ] Add objective focus state and exit criteria model.
- [ ] Add quest combat spawn-pressure target policy.
- [ ] Add future quest loot/preloot policy.
- [x] Document Quest Objective Policy design and technical specifications.
- [ ] Add reactor objective capability execution.
- [x] Add reactor catalog lookup prep for Amherst/Maple Island Pio boxes.
- [ ] Add jump-quest navigation graph support.
- [ ] Add basic RecoveryCapability retry/block policy.
- [x] Build Maple Island catalog slice exporter.
- [x] Build Maple Island fast lookup indexes from
  `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md`.
- [x] Add Maple Island validation report generation.
- [x] Add `maple-island-mvp` plan card.
- [ ] Add test command to assign `maple-island-mvp` to one agent.
- [ ] Add objective progress logging.
- [ ] Add one-agent full-run integration test.
- [ ] Add relog/restart resume test.
- [x] Add forbidden Shanks interaction test.

## Server Adapter Work

- [ ] Define DTOs in code after reconstruction boundary is stable.
- [ ] Implement Cosmic server adapter.
- [x] Document minimal Cosmic install patcher technical contract.
- [x] Add portable installer manifest, plan, patch operation, and verify report schemas.
- [x] Add portable installer contract verifier.
- [ ] Implement minimal Cosmic install patcher from `docs/agents/server-adapter/MINIMAL_COSMIC_EDIT_INSTALL_TARGET.md`.
- [ ] Add installer verification for clean Cosmic clones.
- [ ] Add installer uninstall/update mode using `AGENT_PLATFORM_BEGIN` marker blocks.
- [ ] Add live state snapshots.
- [ ] Add validation pipeline.
- [ ] Add safe action execution.
- [ ] Add adapter capability declaration.

## Integration Order

1. Keep current exporters as offline tools.
2. Add bundle manifest and indexes.
3. Add read-only runtime loader.
4. Add profile runtime.
5. Add Plan Card schema and read-only planner.
6. Add server adapter read-only snapshots.
7. Add LLM read-only tools.
8. Add low-risk command submission.
9. Add gated NPC/quest/shop/economy execution.
