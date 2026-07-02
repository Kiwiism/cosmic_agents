# Portable Platform TODO

This TODO tracks the future portable Agent Knowledge Platform.

## Documentation Contracts

- [x] Catalog platform architecture.
- [x] Catalog bundle spec.
- [x] Catalog query API.
- [x] Profile runtime architecture.
- [x] Profile decision API.
- [x] Server adapter contract.
- [x] Minimal Cosmic edit install target.

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
- [ ] Define Plan Card JSON schema files.
- [ ] Add plan bundle format for reusable plans.
- [ ] Add plan objective dependency graph model.
- [ ] Add plan scheduler interface.
- [ ] Add objective runner interface.
- [ ] Add sidetrack/plan-stack persistence model.
- [ ] Add LLM plan command tools.
- [ ] Add direct LLM `NAVIGATE_TO_POINT` command as a temporary sidetrack plan/objective.
- [ ] Add Maple Island sample plan card.
- [ ] Add validator for plan exit criteria and forbidden actions.

## Maple Island MVP Work

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
- [ ] Add basic RecoveryCapability retry/block policy.
- [ ] Build Maple Island catalog slice.
- [ ] Build Maple Island fast lookup indexes and validation report from
  `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md`.
- [ ] Add `maple-island-mvp` plan card.
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
