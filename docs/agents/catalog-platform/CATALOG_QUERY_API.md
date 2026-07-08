# Catalog Query API

The Catalog Query API is a read-only interface consumed by Agent engine and LLM
tooling.

It must not expose server runtime classes or mutate live server state.

## Service Shape

```java
public interface GameCatalog {
    CatalogManifest manifest();

    Optional<MapInfo> map(int mapId);
    Optional<NpcInfo> npc(int npcId);
    Optional<ItemInfo> item(int itemId);
    Optional<MobInfo> mob(int mobId);
    Optional<QuestInfo> quest(int questId);
    Optional<SkillInfo> skill(int skillId);
    Optional<ReactorInfo> reactor(int reactorId);
    Optional<TravelServiceInfo> travelService(String serviceId);
    Optional<JobBuildInfo> jobBuild(String jobBuildId);
    Optional<ServerRuleInfo> serverRules();

    List<NpcPlacement> findNpcPlacements(int npcId);
    List<MobLocation> findMobLocations(int mobId);
    List<DropSource> findDropSources(int itemId);
    List<ShopSource> findShopSources(int itemId);
    List<CraftingSource> findCraftingSources(int itemId);
    List<RewardSource> findRewardSources(int itemId);
    List<ScrollUpgradeInfo> findScrollsForTarget(int itemId);
    List<QuestInfo> findQuestsForNpc(int npcId);
    List<QuestInfo> findQuestsRequiringItem(int itemId);
    List<QuestInfo> findQuestsRequiringMob(int mobId);
    List<QuestRewardChoice> findQuestRewardChoices(int questId);
    List<DialogueOptionInfo> findDialogueOptions(int npcId);
    List<ReactorPlacement> findReactorsInMap(int mapId);
    List<ReactorPlacement> findReactorsById(int reactorId);
    List<ReactorPlacement> findReactorsForQuest(int questId);
    List<ReactorPlacement> findReactorsDroppingItem(int itemId);
    List<ReactorPlacement> findMapleIslandPioReactors();
    List<SafePoint> findSafePoints(int mapId, SafePointQuery query);
    List<ResupplyOption> findResupplyOptions(int fromMapId, ResupplyQuery query);
    List<TrainingMapOption> findTrainingOptions(TrainingQuery query);
    List<BossInfo> findBosses(BossQuery query);
    List<RiskFlag> findRiskFlags(RiskQuery query);

    StaticRouteResult planStaticRoute(int fromMapId, int toMapId);
    StaticRouteEta estimateRouteEta(RouteEtaQuery query);
    ItemAcquisitionOptions findAcquisitionOptions(int itemId);
    CatalogSearchResult search(CatalogSearchRequest request);
}
```

Current Java prep API:

```java
CatalogQueryService queries = AgentCatalogService.loadFromRepoRoot(Path.of(".")).queries();

queries.reactor().reactorsInMap(1000000);
queries.reactor().findReactorById(2001);
queries.reactor().findReactorsForQuest(1008);
queries.reactor().findReactorsDroppingItem(4031161);
queries.reactor().mapleIslandPioReactors();
```

These methods return immutable `CatalogRecord` lists. They are static lookup
inputs only. Future Amherst/Maple Island Reactor Capability must still confirm
live map, range, quest state, and reactor alive/active state before execution.

## LLM-Safe Query Layer

The LLM should not receive giant raw records by default. It should use compact
summaries.

```java
public interface LlmKnowledgeTools {
    LlmMapSummary getMapSummary(int mapId);
    LlmNpcSummary findNpc(String nameOrId);
    LlmQuestSummary findQuest(String nameOrId);
    LlmItemSummary findItem(String nameOrId);
    LlmFarmingPlan findFarmingOptions(int itemId, AgentProfileView profile);
    LlmShopPlan findPurchaseOptions(int itemId, AgentProfileView profile);
    LlmAcquisitionSummary findAcquisitionOptions(String itemNameOrId, AgentProfileView profile);
    LlmQuestlineSummary findQuestline(String nameOrId, AgentProfileView profile);
    LlmTrainingSummary findTrainingOptions(AgentProfileView profile);
    LlmResupplySummary findResupplyOptions(int mapId, AgentProfileView profile);
    LlmRiskSummary explainRisk(CatalogRiskRequest request);
}
```

## Query Result Rules

Portable query envelopes are tracked as:

- `docs/agents/catalog-platform/catalog-query-request.schema.json`
- `docs/agents/catalog-platform/catalog-query-result.schema.json`

These contracts are transport-neutral. They can be used by a local runtime API,
an Agent Console endpoint, an LLM tool gateway, or a standalone catalog test
tool without exposing server classes or mutating live state.

Every query result should include confidence:

```json
{
  "confidence": "generated",
  "reasons": ["wz-source", "sql-source"],
  "requiresLiveValidation": true
}
```

Confidence levels:

- `generated`
- `derived`
- `manual`
- `runtime-observed`
- `low-confidence`
- `blocked`

## Static Route Query

Static route planning can use portals/travel catalog only.

```json
{
  "fromMapId": 100000000,
  "toMapId": 910000000,
  "constraints": {
    "allowScriptPortals": false,
    "allowPaidTravel": true,
    "maxMesosCost": 50000
  }
}
```

Result:

```json
{
  "status": "candidate",
  "steps": [
    {
      "type": "portal",
      "fromMapId": 100000000,
      "toMapId": 100000001,
      "portalName": "east00"
    }
  ],
  "requiresLiveValidation": true,
  "risks": []
}
```

The Agent engine must still validate live map, portal, script, and movement
reachability.

## Farming Query

Input:

```json
{
  "itemId": 4000004,
  "agent": {
    "level": 12,
    "jobId": 200,
    "mapId": 100000000
  },
  "constraints": {
    "maxDeathRisk": "low",
    "preferNearby": true,
    "allowCrowdedMaps": false
  }
}
```

Output:

```json
{
  "itemId": 4000004,
  "sources": [
    {
      "mobId": 100100,
      "mobName": "Orange Mushroom",
      "mapCandidates": [
        {
          "mapId": 100010100,
          "score": 0.82,
          "reasons": ["mob-present", "nearby", "low-danger"],
          "risks": []
        }
      ]
    }
  ]
}
```

## Economy Query

Static catalog can answer:

- sold by NPC shop
- dropped by mobs
- possible crafting source

Live overlay answers:

- observed FM prices
- recent liquidity
- seller locations
- price volatility

The query API should keep these separated:

```java
ItemAcquisitionOptions findAcquisitionOptions(int itemId);
MarketPriceView findObservedMarketPrice(int itemId);
```

## Performance Expectations

Runtime should support:

- O(1) ID lookup for maps/items/mobs/NPCs/quests/skills.
- O(1) or O(log n) reverse index lookups.
- Batch queries for LLM summaries.
- Optional lazy loading for large text-heavy fields.
- No live server locks during static catalog queries.
- No full catalog scans from agent tick loops.
- Precomputed top-N or bounded ranking indexes for common queries such as
  farming options, resupply, training maps, and item acquisition.
- Query results that state which index answered the request, for diagnostics.
