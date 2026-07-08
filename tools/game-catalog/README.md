# Game Knowledge Catalog Exporter

This toolkit prepares offline game knowledge data for the future Agent engine
and LLM autonomy layer.

It does not modify or wire into runtime code.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\Export-GameKnowledgeCatalog.ps1
```

The export parses WZ XML and SQL seed files. It can take a few minutes.

Machine-readable compact export summary:

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\Export-GameKnowledgeCatalog.ps1 -SummaryOnly -Json
```

The exporter JSON includes `summaryOnly`, `rowsOmitted`, `outputFileCount`,
`returnedOutputFileCount`, and a compact counts object for maps, mobs,
drops, items, shops, quests, and skills. Summary mode omits detailed output
file rows while still writing the same generated catalog artifacts.

## Verify

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\Test-GameKnowledgeCatalog.ps1
powershell -ExecutionPolicy Bypass -File tools\game-catalog\Test-GameKnowledgeCatalog.ps1 -SummaryOnly -Json
```

The verifier checks generated files, JSON validity, required row shapes,
cross-catalog references, and key Maple Island MVP source facts before derived
Agent/LLM catalogs consume this bundle. Known non-mob drop source conventions
are reviewed in `docs/agents/catalog-overrides/drop-source-classifications.catalog.json`.
Compact JSON sets `summaryOnly`, `rowsOmitted`, `checkCount`, `passCount`,
`warningIds`, `failureIds`, and `returnedCheckCount`, preserves catalog
`counts`, and omits detailed check rows.

## Drop Source Gap Report

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\New-DropSourceGapReport.ps1 `
  -OutputPath tmp\drop-source-gap-report.md
```

This explains drop source IDs that are not present in the generated mob catalog,
classifying them as item-id-like, reactor/global, event/special, low-id, or
unknown source conventions for review.

Compact machine-readable report:

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\New-DropSourceGapReport.ps1 -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `sourceCount`,
`returnedSourceCount`, `classCount`, and `returnedClassCount`. It keeps
classification rows and the compact `summary` object, and omits detailed
missing source rows.
Compact drop-source output omits detailed missing source rows by design.

## Outputs

Generated files are written to `tmp/game-catalog/`:

- `generated_map_catalog.json`
- `generated_mob_catalog.json`
- `generated_drop_catalog.json`
- `generated_item_catalog.json`
- `generated_shop_catalog.json`
- `generated_quest_catalog.json`
- `generated_skill_catalog.json`
- `GAME_CATALOG_SUMMARY.md`

## Sources

- `wz/Map.wz/Map`
- `wz/Mob.wz`
- `wz/Skill.wz`
- `wz/String.wz`
- `wz/Quest.wz/Check.img.xml`
- `wz/Quest.wz/Act.img.xml`
- `src/main/resources/db/data/101-shops-data.sql`
- `src/main/resources/db/data/102-shopitems-data.sql`
- `src/main/resources/db/data/152-drop-data.sql`

## Relationship To NPC Catalog

NPC-specific placement, interaction-box, approach-point, and dialogue timing data
remain in `tmp/npc-catalog/`.

The game catalog provides the broader world knowledge needed by planners:

- maps and portals
- mobs and map placements
- drops
- items
- shops
- quests
- skills

Future runtime integration should join these catalogs through repository
interfaces and still validate live server state before executing actions.
