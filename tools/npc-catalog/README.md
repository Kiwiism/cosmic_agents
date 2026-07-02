# NPC Catalog Exporter

This toolkit prepares offline NPC catalog data from the extracted Cosmic assets.
It does not modify or wire into runtime code.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File tools\npc-catalog\Export-NpcCatalog.ps1
```

The full export parses map footholds and can take several minutes.

## Outputs

Generated files are written to `tmp/npc-catalog/`:

- `generated_npc_catalog.json`
- `generated_npc_placements.json`
- `generated_npc_approach_points.json`
- `generated_quest_dialogue_timing.json`
- `generated_npc_action_catalog.json`
- `generated_npc_dialogue_options.json`
- `generated_npc_services.json`
- `generated_npc_reward_choices.json`
- `generated_npc_fast_indexes.json`
- `generated_map_npc_summary.json`
- `NPC_CATALOG_SUMMARY.md`
- `NPC_CATALOG_VALIDATION.md`

## Sources

- `wz/Map.wz/Map`
- `wz/String.wz/Npc.img.xml`
- `wz/String.wz/Map.img.xml`
- `wz/Quest.wz/Check.img.xml`
- `wz/Quest.wz/Say.img.xml`
- `wz/Quest.wz/Act.img.xml`
- `scripts/npc`
- `src/main/resources/db/data/101-shops-data.sql`
- `src/main/resources/db/data/102-shopitems-data.sql`

## Notes

Approach points are generated from a default box and raw foothold samples. They
are preparation data, not final runtime truth. Future Agent integration should
still validate live NPC presence, interaction range, and navigation reachability.

Interaction types, confidence, and do-not-auto-use flags are generated review
hints. They should gate future automation until a runtime validator and manual
override path exist.

The expanded target also needs service classification, dialogue-option
classification, and reward-choice metadata. Quest requirements and rewards are
canonical in the broader game catalog, while this NPC catalog emits compact
NPC-facing quest action rows and fast reverse indexes for runtime lookup. Agent
tick paths should query prebuilt indexes instead of scanning raw catalog arrays.
