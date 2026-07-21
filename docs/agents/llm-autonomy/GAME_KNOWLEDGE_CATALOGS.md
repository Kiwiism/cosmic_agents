# Game Knowledge Catalogs

The LLM needs a read-only knowledge layer that explains what exists in the game.
Catalogs should be generated from WZ XML, SQL seed data, server config, and later
runtime observations.

Generated data should stay replaceable. Manual tuning should live in override
files or database tables.

Initial generated catalogs are produced by:

```powershell
powershell -ExecutionPolicy Bypass -File tools\game-catalog\Export-GameKnowledgeCatalog.ps1
```

Outputs are written to `tmp/game-catalog/`. NPC-specific approach and timing
data remains under `tmp/npc-catalog/`.

## Catalog Index

| Catalog | Purpose | Primary Sources | Runtime Needed |
| --- | --- | --- | --- |
| Map | map names, regions, NPCs, mobs, portals, flags | `Map.wz`, `String.wz` | yes |
| Portal/Travel | map graph, taxis, ships, FM entry/exit | `Map.wz`, scripts, overrides | yes |
| NPC | placements, shops, quests, approach points | existing NPC catalog | yes |
| Quest | requirements, objectives, rewards, chains | `Quest.wz`, scripts | yes |
| Mob | level, HP, EXP, skills, danger | `Mob.wz`, `String.wz` | yes |
| Drop | item sources and farming locations | SQL/WZ drop data | yes |
| Item | item type, stats, use effects, trade rules | `Item.wz`, `String.wz`, SQL | yes |
| Shop | NPC shop inventory and prices | SQL shop data | yes |
| Skill | range, cost, cooldown, hit count, buffs | `Skill.wz` | yes |
| Job/Class | advancement, stat/skill builds | WZ, server logic, overrides | yes |
| Reactor/Field Object | boxes, harvestables, clickable objects, scripted triggers | `Map.wz`, scripts | yes |
| Foothold/Reachability | footholds, ropes, ladders, safe points, interact boxes | `Map.wz`, derived nav graph | yes |
| Portal Script/Travel Service | taxis, ships, scripted portals, route costs, schedules | scripts, SQL/WZ, overrides | yes |
| Quest Reward Choice | selectable rewards, random pools, class choices | `Quest.wz`, scripts | yes |
| Dialogue Option | NPC menu affordances without full dialogue replay | scripts, manual overrides | yes |
| Maker/Crafting | recipes, materials, stimulators, crystals, outputs | WZ, server logic | advisory |
| Gachapon/Event Reward | gachapon pools, boxes, event reward sources | SQL, scripts, manual | advisory |
| Party/PQ | entry NPCs, min/max players, stages, rewards | scripts/manual | gated |
| Boss/Area Boss | spawn maps, timers, drops, danger, recommended level | WZ, scripts, overrides | advisory |
| Monster Skill Risk | status/debuff/damage risks by mob skill | `Mob.wz`, server logic | yes |
| Return/Resupply | nearest town, potion/recharge/storage routes | maps, shops, route graph | yes |
| Scroll/Upgrade | scroll targets, stat changes, success rates, demand | WZ, server config | yes |
| Config/Rules | rates, caps, trade rules, feature toggles | `config.yaml`, server config | yes |
| Training | recommended maps by class/level/risk | generated + manual | advisory |
| Economy | observed prices, supply, demand, liquidity | runtime FM scans | yes |
| Event/Script Risk | unsafe/manual NPCs and scripts | scripts/manual | yes |
| Social Hotspots | towns/FM/channel density | runtime observation | advisory |
| Risk | death/stuck/script/economy risks | runtime + manual | yes |

## Map Catalog

Key:

```text
mapId
```

Fields:

```json
{
  "mapId": 100000000,
  "streetName": "Henesys",
  "mapName": "Henesys",
  "region": "Victoria Island",
  "flags": ["town", "safe", "fm-access"],
  "portals": [],
  "npcIds": [],
  "mobIds": [],
  "footholdSummary": {
    "walkableSegments": 0,
    "hasRopes": false,
    "hasLadders": false,
    "hasSwim": false
  },
  "risk": {
    "deathRisk": "none",
    "stuckRisk": "low",
    "scriptRisk": "low"
  }
}
```

Important derived fields:

- `isTown`
- `isDungeon`
- `isEventMap`
- `isFreeMarket`
- `isShipOrTransit`
- `hasNearestTownReturn`
- `supportsRandomRoam`
- `safeIdleSpots`

## Portal And Travel Catalog

Purpose: route planning across maps.

Key:

```text
fromMapId + portalName
```

Fields:

```json
{
  "fromMapId": 100000000,
  "portalName": "east00",
  "toMapId": 100010000,
  "type": "walk",
  "requiresScript": false,
  "requiresItemId": null,
  "costMesos": 0,
  "risk": "safe",
  "notes": []
}
```

Travel types:

- `walk`
- `portal-script`
- `taxi`
- `vip-taxi`
- `ship`
- `subway`
- `fm-entry`
- `fm-exit`
- `event-warp`
- `manual-review`

## Quest Catalog

Key:

```text
questId
```

Fields:

```json
{
  "questId": 1000,
  "name": "Example Quest",
  "repeatable": false,
  "levelRange": [1, 10],
  "startNpcId": 1002000,
  "completeNpcId": 1002001,
  "startMapCandidates": [100000000],
  "completeMapCandidates": [100000001],
  "prerequisiteQuestIds": [],
  "exclusiveQuestIds": [],
  "requirements": {
    "items": [],
    "kills": [],
    "mesos": 0,
    "jobIds": [],
    "fame": 0
  },
  "rewards": {
    "exp": 0,
    "mesos": 0,
    "items": [],
    "choiceItems": []
  },
  "risk": {
    "scriptSensitive": false,
    "requiresManualReview": false
  }
}
```

Derived planning fields:

- `estimatedTravelCost`
- `estimatedCombatDifficulty`
- `requiredCatalogs`
- `questChainId`
- `recommendedAgentProfiles`

## Mob Catalog

Key:

```text
mobId
```

Fields:

```json
{
  "mobId": 100100,
  "name": "Example Mob",
  "level": 10,
  "hp": 250,
  "mp": 0,
  "exp": 20,
  "boss": false,
  "elementProfile": {},
  "skills": [],
  "mapIds": [],
  "drops": [],
  "danger": {
    "touchDamage": 0,
    "magicDamage": 0,
    "statusEffects": [],
    "deathRiskByLevel": {}
  }
}
```

Useful derived fields:

- `expPerHp`
- `dropValueScore`
- `classSuitability`
- `safeLevelRange`
- `potionPressure`

## Drop Catalog

Key:

```text
itemId + sourceType + sourceId
```

Fields:

```json
{
  "itemId": 4000000,
  "sourceType": "mob",
  "sourceId": 100100,
  "mapIds": [],
  "chance": null,
  "questOnly": false,
  "tradeable": true,
  "farmScore": {
    "levelFit": 0.0,
    "mobDensity": 0.0,
    "travelCost": 0.0,
    "marketValue": 0.0
  }
}
```

## Item Catalog

Key:

```text
itemId
```

Fields:

```json
{
  "itemId": 2000000,
  "name": "Red Potion",
  "category": "use",
  "subCategory": "potion",
  "vendorPrice": 50,
  "sellPrice": 25,
  "maxStack": 100,
  "tradeable": true,
  "oneOfAKind": false,
  "questItem": false,
  "effects": {
    "hp": 50,
    "mp": 0,
    "buffs": []
  },
  "requirements": {},
  "equipStats": null
}
```

Important classifications:

- potion
- scroll
- throwing-star/bullet
- quest item
- crafting material
- equip upgrade
- vendor trash
- market candidate
- do-not-drop

## Shop Catalog

Key:

```text
shopId
```

Fields:

```json
{
  "shopId": 1000,
  "npcId": 1002000,
  "npcName": "Example Shopkeeper",
  "mapIds": [],
  "items": [
    {
      "itemId": 2000000,
      "price": 50,
      "rank": 0,
      "quantityLimit": null
    }
  ],
  "risk": "safe"
}
```

## Skill Catalog

Key:

```text
skillId + level
```

Fields:

```json
{
  "skillId": 2001002,
  "level": 1,
  "jobId": 200,
  "type": "attack",
  "mpCost": 10,
  "cooldownMs": 0,
  "range": {
    "lt": [-100, -50],
    "rb": [100, 50]
  },
  "mobCount": 1,
  "hitCount": 1,
  "durationMs": 0,
  "requirements": {
    "weaponTypes": [],
    "ammoItemIds": []
  }
}
```

## Reactor And Field Object Catalog

Purpose: let agents know which map objects can be clicked, hit, harvested, or
treated as scripted/manual-review objects.

Key:

```text
reactorId + mapId + lifeIndex
```

Fields:

```json
{
  "reactorId": 1002000,
  "mapId": 100000000,
  "position": [120, 45],
  "type": "reactor",
  "interactions": ["hit", "click", "quest-trigger"],
  "drops": [],
  "questIds": [],
  "scriptName": null,
  "automation": {
    "confidence": "generated",
    "doNotAutoUse": true,
    "reasons": ["script-sensitive-unreviewed"]
  }
}
```

## Foothold And Reachability Catalog

Purpose: fast movement planning, NPC approach selection, random stop points, and
background ETA estimates.

Key:

```text
mapId
```

Fields:

```json
{
  "mapId": 100000000,
  "footholds": [],
  "ropes": [],
  "ladders": [],
  "safeStandingZones": [],
  "npcInteractionBoxes": [],
  "portalApproachPoints": [],
  "mobPlatformRegions": [],
  "blockedRegions": [],
  "requiresMovementSkills": ["teleport", "flash-jump"],
  "routeEtaHints": []
}
```

Required derived indexes:

- `map_to_safe_points`
- `map_to_npc_interaction_boxes`
- `map_to_portal_approach_points`
- `map_to_mob_platform_regions`
- `map_to_route_eta`

## Portal Script And Travel Service Catalog

Purpose: route across services that are not simple static portals.

Fields:

```json
{
  "serviceId": "victoria-taxi-henesys",
  "type": "taxi",
  "npcId": 1012000,
  "fromMapIds": [100000000],
  "toMapIds": [102000000],
  "costMesos": 1000,
  "requiredItems": [],
  "requiredLevel": null,
  "schedule": null,
  "scriptName": "taxi.js",
  "automation": {
    "requiresLiveValidation": true,
    "requiresManualReview": false
  }
}
```

## Quest Reward Choice Catalog

Purpose: choose rewards without reading dialogue text at runtime.

Fields:

```json
{
  "questId": 1000,
  "rewardSetId": "1000-complete-choice-1",
  "selectionRequired": true,
  "choices": [
    {
      "itemId": 2000000,
      "quantity": 10,
      "classBias": [],
      "economicScore": 0.2,
      "profileTags": ["practical", "low-budget"]
    }
  ],
  "defaultPolicy": "best-profile-fit"
}
```

## Dialogue Option Catalog

Purpose: classify NPC script affordances without requiring agents to walk
through every dialogue line.

Fields:

```json
{
  "npcId": 1012000,
  "scriptName": "taxi.js",
  "mapIds": [100000000],
  "options": [
    {
      "optionId": "taxi-victoria",
      "labelHint": "Travel",
      "actionType": "travel",
      "requiresSelection": true,
      "safeForAutomation": true,
      "requiresLiveValidation": true
    }
  ],
  "manualReviewReasons": []
}
```

## Maker And Crafting Catalog

Fields:

```json
{
  "recipeId": "maker-1302000",
  "outputItemId": 1302000,
  "outputQuantity": [1, 1],
  "requiredJobIds": [],
  "requiredLevel": 0,
  "requiredMesos": 0,
  "materials": [],
  "optionalInputs": ["stimulator", "crystal"],
  "risk": {
    "canFail": false,
    "statVariance": true
  }
}
```

## Reward Source Catalog

Purpose: non-monster item sources such as gachapon, boxes, PQs, events, coupons,
and scripted NPC rewards.

Fields:

```json
{
  "sourceId": "gachapon-henesys",
  "sourceType": "gachapon",
  "mapIds": [100000000],
  "npcId": 9100100,
  "cost": {
    "itemId": 5220000,
    "quantity": 1,
    "mesos": 0
  },
  "rewardPool": [],
  "automation": {
    "safeForAutomation": false,
    "requiresManualReview": true
  }
}
```

## Party Quest And Event Catalog

Fields:

```json
{
  "eventId": "kpq",
  "entryNpcId": 9020000,
  "entryMapId": 103000000,
  "levelRange": [21, 30],
  "partySize": [3, 6],
  "stages": [],
  "rewards": [],
  "failureRules": [],
  "automation": {
    "requiresPartyCoordination": true,
    "requiresManualReview": true
  }
}
```

## Boss And Area Boss Catalog

Fields:

```json
{
  "mobId": 9300012,
  "bossType": "area-boss",
  "spawnMapIds": [],
  "spawnTimerMs": null,
  "recommendedLevelRange": [20, 40],
  "danger": {
    "touchDamage": 0,
    "statusEffects": [],
    "requiresParty": false
  },
  "drops": []
}
```

## Monster Skill Risk Catalog

Fields:

```json
{
  "mobSkillId": 120,
  "level": 1,
  "effectType": "seal",
  "durationMs": 10000,
  "counterplay": ["all-cure", "holy-shield"],
  "classRisk": {
    "mage": "high",
    "warrior": "medium"
  }
}
```

## Return And Resupply Catalog

Fields:

```json
{
  "mapId": 100010100,
  "nearestTownMapId": 100000000,
  "nearestPotionShopNpcId": 1012004,
  "nearestRechargeNpcId": 1012004,
  "nearestStorageNpcId": 1012005,
  "returnScrollItemIds": [2030000],
  "routeCosts": {
    "toTownEtaMs": 45000,
    "toPotionShopEtaMs": 50000
  }
}
```

## Job And Build Progression Catalog

Fields:

```json
{
  "jobPathId": "dexless-assassin",
  "jobIds": [400, 410, 411, 412],
  "statPlan": {
    "dexCap": 25,
    "primaryStat": "luk"
  },
  "milestoneItems": [
    {
      "level": 35,
      "itemId": 1472055,
      "acquisitionStrategies": ["buy-market", "farm-drop", "craft"]
    }
  ],
  "skillPriority": []
}
```

## Scroll And Upgrade Catalog

Fields:

```json
{
  "scrollItemId": 2043001,
  "targetItemTypes": ["one-handed-sword"],
  "successRate": 60,
  "destroyRate": 0,
  "statChanges": {},
  "strategyTags": ["budget-upgrade", "market-demand"],
  "configAdjustedSuccessRate": null
}
```

## Server Config And Rule Catalog

Purpose: make the LLM aware of server-specific rules without reading raw config
files at runtime.

Fields:

```json
{
  "serverFamily": "cosmic",
  "rates": {
    "exp": 1,
    "meso": 1,
    "drop": 1
  },
  "customRules": {
    "maxHpMpCap": 300000,
    "untradeableItemsTradeable": true,
    "oneOfAKindCheckDisabled": true,
    "scrollSuccessBonus": {
      "enabled": false,
      "flatBonus": 0
    }
  }
}
```

## Training Catalog

This is an advisory catalog built from maps, mobs, drops, route cost, and agent
performance.

Key:

```text
levelBand + classGroup + fundingTier
```

Fields:

```json
{
  "levelBand": [10, 15],
  "classGroup": "mage",
  "fundingTier": "low",
  "recommendedMaps": [
    {
      "mapId": 100010100,
      "score": 0.82,
      "reasons": ["safe", "good-density", "near-town"],
      "risks": []
    }
  ]
}
```

## Risk Catalog

Track risks that are difficult to infer from static WZ data.

Risk types:

- `death-risk`
- `stuck-risk`
- `script-sensitive`
- `bad-route`
- `crowded`
- `market-manipulation-risk`
- `manual-review`
- `economy-rate-limit`

## Fast Retrieval Indexes

The runtime should not scan full JSON arrays during agent ticks or LLM batch
queries. The bundle should prebuild reverse indexes for common decisions.

Identity and name lookup:

- `id_to_map`
- `id_to_npc`
- `id_to_item`
- `id_to_mob`
- `id_to_quest`
- `id_to_skill`
- `name_to_maps`
- `name_to_npcs`
- `name_to_items`
- `name_to_mobs`
- `name_to_quests`

Map and navigation:

- `map_to_portals`
- `map_to_neighbors`
- `map_to_region`
- `map_to_npcs`
- `map_to_mobs`
- `map_to_reactors`
- `map_to_shops`
- `map_to_safe_points`
- `map_to_route_eta`
- `portal_edge_index`
- `nearest_town_by_map`
- `nearest_resupply_by_map`

NPC and quest:

- `npc_to_maps`
- `npc_to_quests_started`
- `npc_to_quests_completed`
- `npc_to_shops`
- `npc_to_services`
- `quest_to_start_npcs`
- `quest_to_complete_npcs`
- `quest_to_required_items`
- `quest_to_required_mobs`
- `quest_to_prerequisites`
- `quest_chain_index`
- `quest_reward_choice_index`

Items and economy:

- `item_to_mob_drops`
- `item_to_quest_sources`
- `item_to_shop_sources`
- `item_to_crafting_sources`
- `item_to_reward_sources`
- `item_to_scroll_targets`
- `item_to_market_observations`
- `item_to_resupply_role`
- `item_to_protection_rules`

Mob, combat, and training:

- `mob_to_maps`
- `mob_to_drops`
- `mob_to_skills`
- `mob_to_level_band`
- `level_band_to_training_maps`
- `class_to_training_maps`
- `class_to_skill_priority`
- `mob_skill_to_counterplay`

Risk and automation:

- `script_to_manual_review`
- `npc_to_manual_review`
- `map_to_risk_flags`
- `quest_to_risk_flags`
- `action_to_allowed_capabilities`
- `action_to_required_live_validation`

LLM summary indexes:

- `map_summary_by_region`
- `item_acquisition_summary`
- `questline_summary`
- `training_summary_by_profile`
- `economy_summary_by_item_class`
- `agent_action_affordance_summary`

## Current Generated Outputs

The first offline exporter currently prepares:

- `generated_map_catalog.json`
- `generated_mob_catalog.json`
- `generated_drop_catalog.json`
- `generated_item_catalog.json`
- `generated_shop_catalog.json`
- `generated_quest_catalog.json`
- `generated_skill_catalog.json`

The extended target should also prepare:

- `generated_reactor_catalog.json`
- `generated_navigation_topology_catalog.json` *(implemented: footholds,
  components, climbables, anchors, and non-authoritative transitions)*
- `generated_combat_map_policy_catalog.json` *(implemented: farming anchors,
  capacity, incidental-mob policy, and party partitions)*
- `generated_travel_service_catalog.json` *(implemented: literal destinations,
  costs, placements, and live-validation boundaries)*
- `generated_progression_item_policy_catalog.json` *(implemented: equipment,
  recovery supply, scroll, and inventory-policy facts)*
- `generated_quest_chain_policy_catalog.json` *(implemented: complete chain and
  special-handler classification)*
- `generated_quest_reward_choice_catalog.json`
- `generated_dialogue_option_catalog.json`
- `generated_maker_crafting_catalog.json`
- `generated_reward_source_catalog.json`
- `generated_party_event_catalog.json`
- `generated_boss_catalog.json`
- `generated_mob_skill_risk_catalog.json`
- `generated_return_resupply_catalog.json`
- `generated_job_build_catalog.json`
- `generated_scroll_upgrade_catalog.json`
- `generated_server_rule_catalog.json`
- `generated_fast_lookup_indexes.json`
- `generated_llm_summary_indexes.json`

These are preparation data for repositories and LLM tools. They are not runtime
truth by themselves.
