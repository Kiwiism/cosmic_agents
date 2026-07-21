# Agent Decision Catalogs

## Purpose

This slice turns five high-value areas of static game knowledge into generated,
versionable inputs for the Agent engine:

1. navigation topology;
2. combat anchors and party partitions;
3. NPC travel services;
4. equipment, inventory, supply, and scroll policy facts;
5. quest chains and special-handler classification.

The catalogs prepare decisions. They do not execute movement, combat, purchases,
inventory mutations, travel, quest transitions, or reward choices. Those remain
inside live Agent capabilities and server-side validators.

## Source facts versus policy

Each catalog keeps two categories distinct:

- **Source facts** come from WZ XML, SQL-derived catalogs, NPC scripts, and
  reviewed resource overlays. Examples are foothold endpoints, a potion's HP
  recovery, an equip requirement, a literal taxi destination, or a quest
  prerequisite.
- **Policy hints** are replaceable recommendations. Examples are a jump/drop
  candidate, recommended map capacity, a party split, incidental-mob handling,
  or an inventory keep/sell default.

Policy hints must never be promoted to source facts. Runtime observations such
as occupancy, monster position, item ownership, inventory space, prices,
character build, quest state, or current physics state are deliberately absent.

## 1. Navigation topology

Output: `generated_navigation_topology_catalog.json`

The default exporter catalogs every Maple Island and Victoria map with at least
one foothold. `-AllRegions` expands the same schema to the complete WZ map set:

- exact foothold ID, page/group, endpoints, previous/next links, width, rise,
  and wall status;
- connected foothold components and bounds;
- conservative safe-point candidates on wide footholds;
- ladder and rope endpoints with nearest component attachments;
- portal anchors and their nearest component/foothold;
- mob spawn coordinates, roaming bounds, foothold, and component;
- bounded jump and drop candidates;
- a compact terrain-complexity summary.

Jump, drop, ladder, and rope records are explicitly non-executable. The physics
engine must validate reachability and produce the live movement command.

This shared component vocabulary lets navigation, NPC interaction, combat,
recovery, and future LLM perception refer to the same spatial units.

## 2. Combat-map policy

Output: `generated_combat_map_policy_catalog.json`

Only maps with monster spawns receive rows. A row contains:

- farming anchors grouped by foothold component;
- mob IDs, spawn pressure, location, and level range per anchor;
- recommended and maximum Agent capacity;
- deterministic party partitions for party sizes one through four;
- incidental-mob policy for monsters already in the attack arc or blocking a
  route;
- terrain facts and any Victoria training overlay;
- live-occupancy and runtime-reachability requirements.

The default partition assigns spatially contiguous anchors ordered by y and
then x. It is intentionally simple and replaceable. Future formation, party,
and personality policies can choose another partition without changing source
topology.

## 3. Travel services

Output: `generated_travel_service_catalog.json`

The travel exporter joins NPC service classifications, interaction spots, and
script literals. It extracts:

- the NPC and all placed interaction spots;
- literal destination arrays and direct literal warp calls;
- selection indexes;
- known full and beginner-discounted costs;
- item, meso, and selection gates visible in the script;
- evidence strength and review requirements.

A row may be safe for planning when a literal destination can be proven. No row
is safe for execution from the catalog alone. The travel capability must still
validate live meso, items, quest state, NPC presence, dialogue selection, and
the resulting destination.

## 4. Progression-item policy

Output: `generated_progression_item_policy_catalog.json`

The single envelope contains four related but separately scoped sections:

- `equipment`: slot, requirements, stats, upgrade slots, attack speed, price,
  and cash status from `Character.wz`;
- `supplies`: HP/MP flat or percentage effects, NPC shop sources, lowest known
  price, and simple efficiency facts;
- `scrolls`: success/cursed rates and literal stat effects;
- `inventoryRules`: conservative keep, restock, sell, and last-resort drop
  defaults.

Negative HP/MP effects remain cataloged but are marked
`harmful-or-tradeoff-effect` and `doNotProcureByDefault` rather than being
silently treated as potions.

The catalog does not pick best-in-slot equipment, decide AP bridges, predict
market demand, choose restock quantities, or authorize a scroll. Those choices
need the live build bundle, inventory, meso, market, quest, and risk policy.

## 5. Quest-chain and special handlers

Output: `generated_quest_chain_policy_catalog.json`

Every canonical quest receives exactly one row containing:

- quest name, parent, and area;
- prerequisite quest IDs and reverse dependents;
- level/job/interval eligibility;
- objective types and required Agent capabilities;
- NPC placement status;
- reward-choice candidates;
- flags and special-handler classifications;
- planning/execution safety boundaries.

Special classifications currently include automatic quest phases, timed or
repeatable quests, reward choices, missing NPC placements, and non-NPC-driven
phases. This is deliberately exhaustive: unsupported quests remain visible and
blocked with a reason instead of disappearing from planning.

## Generation and verification

Run the base game and NPC exporters first, then:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Export-AgentDecisionCatalogs.ps1
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Test-AgentDecisionCatalogs.ps1
```

The normal `Export-AgentLlmCatalog.ps1` and `Update-AllCatalogs.ps1` flows also
invoke this exporter. It is an offline build and may take several minutes
because it parses every in-scope traversable map and every equipment
definition. This default matches the current Maple Island/Victoria level-30
milestone; use `-AllRegions` for a future world-wide bundle.

For a fast structural smoke test only:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Export-AgentDecisionCatalogs.ps1 `
  -MapLimit 20 -EquipmentFileLimit 20
powershell -ExecutionPolicy Bypass -File tools\agent-llm-catalog\Test-AgentDecisionCatalogs.ps1 `
  -AllowPartial
```

Limited outputs are marked `partialExport` in
`generated_agent_decision_catalog_manifest.json` and must not be published as a
production bundle.

The verifier checks full map and item coverage, component and combat-anchor
integrity, non-authoritative movement candidates, travel evidence, supply and
scroll facts, inventory policy boundaries, one row per quest, prerequisite
inverse links, and reward-choice safety.

## Runtime integration boundary

| Catalog | Read owner | State-changing owner |
| --- | --- | --- |
| Navigation topology | navigation/perception repositories | navigation + physics validator |
| Combat map policy | combat/party policy | combat capability |
| Travel service | travel planner | NPC interaction + travel capability |
| Progression item policy | equipment/supply/inventory policy | equipment, shop, inventory, upgrade capabilities |
| Quest chain policy | objective supervisor | quest + NPC interaction capabilities |

The event system may announce source changes or live outcomes, but it should not
duplicate these catalogs in event payloads. Events should carry stable IDs and
technical context; listeners resolve static facts through catalog repositories.
