# Game Knowledge Catalog Exporter

This toolkit prepares offline game knowledge data for the future Agent engine
and LLM autonomy layer.

It does not modify or wire into runtime code.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\Export-GameKnowledgeCatalog.ps1
```

The export parses WZ XML and SQL seed files. It can take a few minutes.

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
