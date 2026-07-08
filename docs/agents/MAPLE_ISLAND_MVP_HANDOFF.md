# Maple Island MVP Handoff

This is the implementation handoff for after bot-to-Agent reconstruction is
stable enough to add new Agent capabilities.

## Goal

Spawn one Agent with `maple-island-mvp`, let it finish the selected Maple Island
quest route, and stop at `2000000 Southperry`.

Success means:

- selected required quests complete.
- `1046` is started and left incomplete.
- `1028` is not completed.
- `8142` is excluded.
- Todd `1018` and `1035` are skipped unless manually enabled later.
- Shanks `22000` may be used for quest `1026`, but never for travel off Maple
  Island.
- failures become structured blockers instead of loops or forced quest bypasses.

## Prepared Assets

| Area | File |
| --- | --- |
| Route sequence | `docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md` |
| MVP vision overview | `docs/agents/MAPLE_ISLAND_MVP_VISION_AND_OVERVIEW.md` |
| Capability checklist | `docs/agents/MAPLE_ISLAND_CAPABILITY_COMPLETION_PLAN.md` |
| Implementation plan | `docs/agents/MAPLE_ISLAND_MVP_IMPLEMENTATION_PLAN.md` |
| MVP design specification | `docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md` |
| MVP technical specification | `docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md` |
| Agent TODO alignment | `docs/agents/MAPLE_ISLAND_MVP_AGENT_TODO_ALIGNMENT.md` |
| Interaction realism toggle | `docs/agents/INTERACTION_REALISM_POLICY.md` |
| Quest focus/combat policy | `docs/agents/QUEST_FOCUS_AND_COMBAT_POLICY.md` |
| Victoria <30 edge-case review | `docs/agents/VICTORIA_LT30_QUEST_EDGE_CASE_REVIEW.md` |
| Victoria <30 quest status catalog | `docs/agents/catalog-overrides/victoria-lt30-quest-status.catalog.json` |
| Victoria <30 quest status review table | `docs/agents/catalog-overrides/VICTORIA_LT30_QUEST_STATUS_CATALOG.md` |
| Draft plan card | `docs/agents/plans/maple-island-mvp.plan.json` |
| Catalog exporter | `tools/agent-llm-catalog/Export-AgentLlmCatalog.ps1` |

Generated catalog outputs after running the exporter:

```text
tmp/agent-llm-catalog/generated_maple_island_mvp_catalog.json
tmp/agent-llm-catalog/generated_maple_island_mvp_fast_indexes.json
```

These generated files are preparation data and should be regenerated from source
catalogs when WZ/SQL changes.

## Final Route Decisions

- Start: `10000 Mushroom Town`.
- Final stop: `2000000 Southperry`.
- Yoona runs before Mai.
- Rain `1009-1015` is included.
- Pio `1008` needs reactor-box item handling.
- Roger `1021` needs apple-use handling.
- Yoona `8020` needs scripted/granted shopping guide item `4031180`.
- Auto-complete assumptions:
  - `1030`
  - `8023`
- Start-only:
  - `1046`
- Excluded:
  - `1028`
  - `8142`
- Optional review:
  - `1018`
  - `1035`

## Toggle For Fast Testing

Use interaction realism `OFF` for the first implementation run:

```yaml
agents:
  interactionRealism:
    mode: OFF
```

This keeps the first test focused on quest sequence correctness. Random NPC
approach points and dialogue-length delays can be enabled after the deterministic
run works.

Use quest combat spawn-pressure clearing `OFF` for the first implementation run:

```yaml
agents:
  questCombat:
    enableSpawnPressureClearing: false
    enableFutureQuestLootPriority: false
```

After the deterministic route works, enable spawn-pressure clearing so Agents can
clear useful filler mobs when the target mob is scarce and the map is clogged.

## Implementation Tracks

### 1. Plan Runtime

Implement:

- plan card loader.
- objective runner.
- objective status model.
- plan progress persistence.
- live state reconciliation after relog/restart.
- `maple-island-mvp` assignment command/test hook.

Do first because every capability should report back to the objective runner.

### 2. Catalog Runtime

Implement:

- read-only catalog repository.
- Maple Island MVP fast-index loader.
- NPC placement and approach point lookup.
- quest rule lookup by quest id.
- forbidden action lookup.

Catalog facts are planning hints. Live server state remains authoritative.

### 3. Quest Capability

Implement:

- quest state read.
- start/complete requirement check.
- start/complete execution through normal Cosmic quest APIs.
- reward choice handling.
- no `forceStart` or `forceComplete` in normal Agent runtime.

Special cases:

- `1030` and `8023`: auto-complete when catalog marks no complete NPC.
- `1046`: allow start, block completion for MVP.
- `1028`: block completion for MVP.

### 4. NPC Capability

Implement:

- validation-only NPC interaction command.
- quest start at NPC.
- quest complete at NPC.
- live NPC presence/range check.
- selected approach point logging.
- interaction realism mode support.

For first test:

- realism mode `OFF`.
- deterministic nearest valid approach is acceptable.

After first success:

- enable `LIGHT` or `FULL`.
- use seeded random approach points.
- apply dialogue-length delay.

### 5. Navigation And Portal Travel

Implement objective adapters:

- navigate to map.
- navigate to NPC.
- navigate to point.
- navigate through portal path.

Required hard gates:

- destination map exists.
- expected arrival map matches actual arrival map.
- no route may use Shanks travel or leave Maple Island before exit.

### 6. Combat, Loot, And Inventory

Implement objective modes:

- kill required mob count.
- farm required quest item.
- prioritize current quest drops.
- keep explicit objective focus until exit criteria is met.
- optionally clear filler mobs when the target mob is scarce and map spawns are
  clogged.
- prefer filler mobs with prelootable future quest value when safe.
- stop immediately when live quest/inventory state satisfies the objective.
- block on full inventory for MVP.

Do not add sell-trash or economy cleanup to this milestone.

### 7. Recovery

Implement:

- retry same action up to three times.
- refresh live state between retries.
- try alternate approach point after NPC approach failure.
- try alternate mob area after no-mob failure.
- block with reason after repeated failure.
- never use force quest APIs as recovery.
- never use Shanks travel as recovery.

## Required Tests

Unit tests:

- plan JSON loads.
- quest start rejects wrong NPC.
- quest complete rejects unmet item/mob requirement.
- blocked quest completion for `1028`.
- start-only rule for `1046`.
- Shanks travel action is blocked.
- Shanks quest completion for `1026` is allowed.
- interaction realism `OFF` produces zero delay.
- interaction realism `FULL` can choose different valid approach points.
- spawn-pressure clearing is disabled in deterministic mode.
- spawn-pressure clearing prefers current quest target while target count is
  healthy.
- spawn-pressure clearing can select useful filler mobs when target count is low
  and map is clogged.

Integration tests:

- one fresh Agent completes the Amherst sub-phase and stops at `1000000`.
- Amherst objectives run through Plan Runtime plus Capability Runtime, not
  direct scripted mutation.
- one fresh Agent completes the route to Southperry.
- relog/restart resumes without duplicate rewards.
- missing NPC returns `NPC_MISSING`, not exception.
- missing portal returns `MISSING_PORTAL`, not loop.
- inventory full returns `INVENTORY_FULL`.
- death returns blocked/recovery state.

## Open Decisions Before Coding

These are intentionally deferred until implementation starts:

- Whether Rain `1009-1015` should be mandatory in the first test run or moved to
  a second pass if quest scripts behave oddly.
- Whether Todd `1018` and `1035` are visible to real players in this client.
- Exact reactor-box API for Pio `1008`.
- Exact scripted grant mechanism for Yoona item `4031180`.
- Whether auto-complete quests should call an existing server method or use a
  small Agent-only quest completion adapter.
- How much plan progress state should persist in DB versus in-memory for the
  first test.

## Recommended First Coding Order

1. Load and validate `maple-island-amherst-subphase.plan.json`.
2. Add Plan Runtime progress state with one active capability frame and a
   paused-frame stack.
3. Add objective capability dispatch; do not script movement/NPC/combat
   directly from Plan Runtime.
4. Add primitive navigation wrapper over reconstructed movement and prove
   parity with the legacy route behavior.
5. Add primitive combat wrapper over reconstructed grind/combat and prove
   parity before adding quest-specific mob constraints.
6. Add quest state read APIs and NPC validation-only command.
7. Add quest start/complete through normal server APIs.
8. Add item-use, reactor-hit, and reactor-box item objectives for Amherst.
9. Run the Amherst sub-phase from map `10000` through stop at map `1000000`.
10. Add resume test for active frame, child handoff, and parent resume.
11. Load and validate `maple-island-mvp.plan.json`.
12. Expand the same objective capability path through Rain/Maria/Lucas/Pio/
    Yoona/Mai chains.
13. Add Southperry finalization: `1007`, `1026`, start-only `1046`, stop.
14. Add spawn-pressure combat clearing after deterministic success.
15. Add interaction realism `LIGHT` and `FULL`.

The first green milestone should be deterministic with interaction realism
`OFF` and spawn-pressure clearing disabled. The second milestone can make Agents
look less identical and combat more adaptive.
