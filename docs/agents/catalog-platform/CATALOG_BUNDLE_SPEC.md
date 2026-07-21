# Catalog Bundle Spec

A catalog bundle is a portable, versioned directory produced by the catalog
builder and consumed by the catalog runtime.

## Directory Shape

```text
catalog-bundle/
  manifest.json
  catalogs/
    maps.json
    portals.json
    npcs.json
    npc_placements.json
    npc_approach_points.json
    quests.json
    mobs.json
    drops.json
    items.json
    shops.json
    skills.json
    reactors.json
    foothold_reachability.json
    travel_services.json
    quest_reward_choices.json
    dialogue_options.json
    maker_crafting.json
    reward_sources.json
    party_events.json
    bosses.json
    mob_skill_risks.json
    return_resupply.json
    job_builds.json
    scroll_upgrades.json
    server_rules.json
    training.json
    risk.json
  indexes/
    id_to_map.json
    id_to_npc.json
    id_to_item.json
    id_to_mob.json
    id_to_quest.json
    id_to_skill.json
    name_to_maps.json
    name_to_npcs.json
    name_to_items.json
    name_to_mobs.json
    name_to_quests.json
    map_to_npcs.json
    map_to_mobs.json
    map_to_portals.json
    map_to_neighbors.json
    map_to_reactors.json
    map_to_shops.json
    map_to_safe_points.json
    map_to_route_eta.json
    nearest_town_by_map.json
    nearest_resupply_by_map.json
    npc_to_maps.json
    npc_to_quests_started.json
    npc_to_quests_completed.json
    npc_to_shops.json
    npc_to_services.json
    mob_to_maps.json
    mob_to_drops.json
    mob_to_skills.json
    mob_to_level_band.json
    item_to_drops.json
    item_to_shops.json
    item_to_quests.json
    item_to_crafting_sources.json
    item_to_reward_sources.json
    item_to_scroll_targets.json
    item_to_resupply_role.json
    item_to_protection_rules.json
    quest_to_items.json
    quest_to_mobs.json
    quest_to_start_npcs.json
    quest_to_complete_npcs.json
    quest_to_prerequisites.json
    quest_chain_index.json
    quest_reward_choice_index.json
    level_band_to_training_maps.json
    class_to_training_maps.json
    class_to_skill_priority.json
    mob_skill_to_counterplay.json
    script_to_manual_review.json
    npc_to_manual_review.json
    map_to_risk_flags.json
    quest_to_risk_flags.json
    action_to_allowed_capabilities.json
    action_to_required_live_validation.json
  summaries/
    map_summary_by_region.json
    item_acquisition_summary.json
    questline_summary.json
    training_summary_by_profile.json
    economy_summary_by_item_class.json
    agent_action_affordance_summary.json
  reports/
    summary.md
    validation.md
    gaps.md
    validation.json
    gaps.json
    source_hashes.json
    compatibility.json
    index_coverage.json
  overrides/
    applied_overrides.json
```

## Current decision-catalog mapping

The preparation workflow currently generates the following source artifacts
before final bundle packaging:

| Generated artifact | Bundle responsibility |
| --- | --- |
| `generated_navigation_topology_catalog.json` | `foothold_reachability.json`, safe-point and spatial indexes |
| `generated_combat_map_policy_catalog.json` | `training.json`, party partitions, combat-map capacity |
| `generated_travel_service_catalog.json` | `travel_services.json` and service-route indexes |
| `generated_progression_item_policy_catalog.json` | item/equipment facts, `return_resupply.json`, and `scroll_upgrades.json` inputs |
| `generated_quest_chain_policy_catalog.json` | prerequisite, chain, risk, and manual-review indexes |

These generated files are not renamed into the portable bundle yet. The bundle
builder should split the combined progression-item envelope and derive compact
indexes without changing its source facts.

Small servers may omit optional catalogs, but `manifest.json` must list what is
present.

Current generated reactor source:

```text
tmp/reactor-catalog/generated_reactor_catalog.json
tmp/reactor-catalog/REACTOR_CATALOG_SUMMARY.md
```

The reactor catalog is currently emitted as an envelope with `schemaVersion`,
`source`, `counts`, `acceptedGaps`, and `entries`. Each entry represents a map
reactor placement and includes map id/name, reactor id/name, x/y, foothold when
available, source paths, inferred quest/item links, drops, confidence, and
flags. The final portable bundle can rename this file to `catalogs/reactors.json`
while preserving the row shape.

## Manifest

The portable manifest contract is tracked as:

- `docs/agents/catalog-platform/catalog-bundle-manifest.schema.json`

Current safe-prep tooling can write a draft manifest for the generated catalog
outputs:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogBundlePrep.ps1 `
  -OutputPath tmp\catalog-bundle-prep-report.md `
  -OutputManifestPath tmp\draft-catalog-bundle-manifest.json
```

The draft manifest includes SHA-256 hashes for generated files. Full source-root
hashing is available during catalog refresh runs, or by passing
`-OutputSourceHashesPath` to the bundle-prep tool. This is still prep tooling,
not the final portable runtime bundle builder.

```json
{
  "schemaVersion": 1,
  "bundleId": "cosmic-v83-main-20260630",
  "builder": {
    "name": "portable-agent-catalog-builder",
    "version": "0.1.0"
  },
  "game": {
    "family": "maplestory",
    "version": "v83",
    "serverFamily": "cosmic",
    "locale": "en"
  },
  "generatedAt": "2026-06-30T09:00:00+08:00",
  "sources": {
    "wzRootHash": "sha256:...",
    "sqlRootHash": "sha256:...",
    "scriptRootHash": "sha256:...",
    "overrideHash": "sha256:..."
  },
  "catalogs": {
    "maps": {
      "path": "catalogs/maps.json",
      "schemaVersion": 1,
      "rowCount": 5262
    }
  },
  "indexes": {
    "itemToDrops": {
      "path": "indexes/item_to_drops.json",
      "schemaVersion": 1
    }
  },
  "compatibility": {
    "requiresServerAdapterVersion": ">=1.0.0",
    "requiresCatalogRuntimeVersion": ">=1.0.0"
  }
}
```

## Core Row Rules

Every row should include:

```json
{
  "schemaVersion": 1,
  "id": 0,
  "source": {
    "kind": "wz",
    "path": "wz/Map.wz/Map/Map1/100000000.img.xml"
  }
}
```

When a row is merged from multiple sources:

```json
{
  "sources": [
    { "kind": "wz", "path": "..." },
    { "kind": "sql", "path": "..." },
    { "kind": "override", "path": "..." }
  ]
}
```

## Override Rules

Generated data must remain replaceable. Manual tuning belongs in overrides.

Override priority:

```text
base WZ/SQL/script extraction
derived builder inference
manual override
server-specific override
runtime live overlay
```

Overrides should be explicit:

```json
{
  "target": {
    "catalog": "npc_placements",
    "key": {
      "mapId": 100000000,
      "lifeIndex": "0",
      "npcId": 1012100
    }
  },
  "patch": {
    "interactionBox": {
      "left": 160,
      "right": 160,
      "up": 120,
      "down": 240
    },
    "automation": {
      "confidence": "manual",
      "doNotAutoUse": false
    }
  }
}
```

## Validation Reports

Every build should emit:

- missing names
- missing placements
- missing approach points
- quest NPCs without placement
- shop NPCs without placement
- travel services without reachable source or destination
- quest reward choices without a default policy
- dialogue options marked safe without a validation rule
- item acquisition summaries without any source
- route ETA indexes with missing portal edges
- script-sensitive entries
- do-not-auto-use entries
- duplicate IDs
- rows with unresolved source references
- indexes with dangling keys

Reports are not runtime contracts, but they are required for review.

Detailed validation/reporting requirements are defined in:

- `docs/agents/catalog-platform/CATALOG_BUILDER_VALIDATION_REPORT_SPEC.md`

## Compatibility Rules

Catalog runtime must reject a bundle when:

- manifest is missing
- schema version is unsupported
- required catalog file is missing
- declared row count does not match loaded row count
- source hash policy fails
- bundle requires newer runtime than available

Catalog runtime may load with warnings when:

- optional catalog is missing
- optional index is missing and can be rebuilt
- validation report contains known accepted gaps
