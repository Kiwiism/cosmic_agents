# Agent LLM Catalog Exporter

This toolkit builds derived, decision-ready catalogs for the future Agent
engine and LLM autonomy layer.

It consumes the existing raw game and NPC catalog outputs. It does not modify or
wire into runtime code.

## Prerequisites

Run the base exporters first when WZ or SQL source data changes:

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\Export-GameKnowledgeCatalog.ps1
powershell -ExecutionPolicy Bypass -File tools\npc-catalog\Export-NpcCatalog.ps1
```

## Run

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Export-AgentLlmCatalog.ps1
```

## Outputs

Generated files are written to `tmp/agent-llm-catalog/`:

- `generated_portal_graph.json`
- `generated_mob_spawn_catalog.json`
- `generated_map_summary_index.json`
- `generated_quest_objective_catalog.json`
- `generated_item_source_index.json`
- `generated_resupply_catalog.json`
- `generated_action_affordance_catalog.json`
- `generated_maple_island_mvp_catalog.json`
- `generated_maple_island_mvp_fast_indexes.json`
- `generated_catalog_manifest.json`
- `AGENT_LLM_CATALOG_SUMMARY.md`

The Maple Island MVP catalog is a curated slice for the first reconstructed
Agent quest milestone. It records the `10000` start route, Yoona-before-Mai
ordering, Pio reactor-box handling, Roger's Apple item use, Yoona Cash Shop item
grant, auto-complete quest assumptions, Biggs `1046` start-only behavior, and
Shanks/Lith Harbor forbidden actions.

## Intended Consumers

- LLM planner: map summaries, quest objectives, item sources, action affordances.
- Agent engine: portal graph, mob spawns, NPC catalog, NPC approach points,
  dialogue timing, and live server state validation.
- Server adapter: only reads these catalogs through future repository/query
  interfaces. Catalog facts are planning hints, not runtime authority.
