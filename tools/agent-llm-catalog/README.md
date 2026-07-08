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

Machine-readable compact export summary:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Export-AgentLlmCatalog.ps1 -SummaryOnly -Json
```

The exporter JSON includes `summaryOnly`, `rowsOmitted`, `outputFileCount`,
`returnedOutputFileCount`, and compact derived catalog `counts` for portal
edges, mob spawn maps, map summaries, quest objective plans, item source
indexes, resupply shops, action affordances, and Maple Island MVP rows.
Summary mode omits detailed output file rows while still writing the same generated Agent/LLM catalog artifacts.

## Verify

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Test-AgentLlmCatalog.ps1
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Test-AgentLlmCatalog.ps1 -SummaryOnly -Json
```

The verifier checks required generated files, JSON validity, manifest entries,
non-empty derived indexes, and the core Maple Island MVP invariants. It is a
standalone prep gate only; it does not modify or wire into runtime code.
Compact JSON sets `summaryOnly`, `rowsOmitted`, `checkCount`, `passCount`,
`warningIds`, `failureIds`, and `returnedCheckCount`, and omits detailed check
rows.

## Maple Island MVP Validation Report

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\New-MapleIslandMvpValidationReport.ps1 `
  -OutputPath tmp\maple-island-mvp-validation-report.md
```

This writes a human-readable validation report for the generated Maple Island
MVP catalog slice, including route summary, required checks, quest rules,
special rules, and forbidden actions. It reuses the Agent/LLM catalog verifier
and does not assign plans or execute live Agent behavior.

Compact machine-readable report:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\New-MapleIslandMvpValidationReport.ps1 -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `checkCount`, `passCount`,
`failCount`, `warnCount`, `failureIds`, `warningIds`, `returnedCheckCount`,
`returnedQuestRuleCount`, `returnedSpecialRuleCount`, and
`returnedForbiddenActionCount`. It keeps the compact `summary` object and omits
detailed check, quest-rule, special-rule, and forbidden-action rows.

## Compare

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Compare-AgentLlmCatalog.ps1 `
  -OldCatalogDir tmp\agent-llm-catalog-before `
  -NewCatalogDir tmp\agent-llm-catalog `
  -OutputPath tmp\agent-llm-catalog-diff.md
```

The diff report compares generated bundle files, SHA-256 hashes, JSON row
counts, manifest counts, and Maple Island MVP quest membership. Use it after
WZ, SQL, script, or exporter changes to review catalog drift before runtime
integration consumes a refreshed bundle.

Compact machine-readable diff:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Compare-AgentLlmCatalog.ps1 `
  -OldCatalogDir tmp\agent-llm-catalog-before `
  -NewCatalogDir tmp\agent-llm-catalog `
  -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `fileCount`,
`returnedFileCount`, `manifestCountChangeCount`,
`returnedManifestCountChangeCount`, `oldMapleIslandQuestCount`,
`newMapleIslandQuestCount`, and `returnedMapleIslandQuestListCount`. It keeps
the compact `summary` object and Maple Island added/removed quest ids, while
omitting detailed file rows, manifest-count rows, and full old/new quest-id
lists.
Compact diff output is omitting detailed file rows, manifest-count rows, and full old/new quest-id lists by design.

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
