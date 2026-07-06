# Portable Platform TODO

This TODO tracks the future portable Agent Knowledge Platform.

## Documentation Contracts

- [x] Pre-reconstruction safe prep readiness map.
- [x] Catalog platform architecture.
- [x] Catalog bundle spec.
- [x] Catalog query API.
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

## Builder Work

- [ ] Move current `tools/game-catalog` and `tools/npc-catalog` outputs toward a unified catalog bundle layout.
- [ ] Add `manifest.json` generation.
- [ ] Add source hashing for WZ, SQL, scripts, and overrides.
- [ ] Add derived index generation.
- [ ] Add override merge step.
- [ ] Add validation report for dangling references.
- [ ] Add bundle compatibility checks.
- [ ] Add extended full-game catalogs:
  - reactor/field object catalog.
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

- [ ] Create profile schema files.
- [ ] Create profile store interface.
- [ ] Create mood engine.
- [ ] Create build intent model for job path, stat build, equipment goals, and
  acquisition preferences.
- [ ] Create relationship memory model for agent/player trust, affinity, trade
  reliability, party compatibility, and avoidance.
- [ ] Create behavior sampler.
- [ ] Create policy engine.
- [ ] Add event feedback model.
- [ ] Define `AgentExperienceEvent` and `ProfilePatch` schema files.
- [ ] Add append-only profile event store.
- [ ] Add append-only profile patch store.
- [ ] Add profile adaptation engine with bounded rule evaluation.
- [ ] Add profile patch validators that prevent hard policy mutation.
- [ ] Add adaptation modes: off, observe-only, bounded, and fast-learn-test.
- [ ] Add replay tooling to rebuild profile state from event and patch logs.
- [ ] Add adaptation rules for plan/objective outcomes, navigation failures,
  combat danger, farming dry streaks, market outcomes, and relationships.
- [ ] Add strategic decision journal with reason codes, influences,
  alternatives considered, and outcomes.
- [ ] Add profile decision APIs for build progression and equipment acquisition.
- [ ] Add profile decision APIs for relationship actions, trade counterparties,
  and party/help requests.
- [ ] Add LLM profile summary API.

## Plan Card Work

- [x] Document Plan Card system contract.
- [x] Document Maple Island MVP implementation plan.
- [x] Document Maple Island capability 100% completion plan.
- [x] Define Plan Runtime design and technical specifications.
- [ ] Define Plan Card JSON schema files.
- [ ] Add plan bundle format for reusable plans.
- [ ] Add plan objective dependency graph model.
- [ ] Add plan scheduler interface.
- [ ] Add objective runner interface.
- [ ] Add sidetrack/plan-stack persistence model.
- [ ] Add LLM plan command tools.
- [ ] Add direct LLM `NAVIGATE_TO_POINT` command as a temporary sidetrack plan/objective.
- [x] Add Maple Island sample plan card.
- [ ] Add validator for plan exit criteria and forbidden actions.

## Maple Island MVP Work

- [x] Document final Maple Island MVP route handoff.
- [x] Document Maple Island MVP design specification.
- [x] Document Maple Island MVP technical implementation specification.
- [ ] Add read-only plan card loader.
- [ ] Add plan progress state model.
- [ ] Add objective status model.
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
- [ ] Add reactor objective capability and catalog lookup.
- [ ] Add jump-quest navigation graph support.
- [ ] Add basic RecoveryCapability retry/block policy.
- [x] Build Maple Island catalog slice exporter.
- [x] Build Maple Island fast lookup indexes from
  `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md`.
- [ ] Add Maple Island validation report generation.
- [x] Add `maple-island-mvp` plan card.
- [ ] Add test command to assign `maple-island-mvp` to one agent.
- [ ] Add objective progress logging.
- [ ] Add one-agent full-run integration test.
- [ ] Add relog/restart resume test.
- [ ] Add forbidden Shanks interaction test.

## Server Adapter Work

- [ ] Define DTOs in code after reconstruction boundary is stable.
- [ ] Implement Cosmic server adapter.
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
