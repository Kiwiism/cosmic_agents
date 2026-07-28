# Victoria Adaptive Quest Hunting

## Outcome

Victoria hunting quests now have a generated, versioned evidence layer without
giving generated choices authority over proven MVP routes.

The current rollout is:

1. fixed plan choices remain preferred;
2. the adaptive selector predicts a choice in shadow mode and records its evidence;
3. adaptive data is used only when the fixed choices cannot currently run;
4. fully adaptive selection remains opt-in by policy and is not the default.

## Data flow

| Stage | Artifact | Responsibility |
|---|---|---|
| Cosmic-derived source snapshots | `tmp/game-catalog` and `tmp/agent-llm-catalog` | Raw quest, drop, map, spawn, and navigation topology observations |
| Quest facts | `victoria-quest-facts.json` | Quest eligibility, NPC endpoints, objectives, required counts, and source mobs |
| Mob/drop facts | `victoria-mob-drop-facts.json` | Drops for mobs present in Victoria maps, including quest restrictions and a marker for current quest-target items |
| Map facts | `victoria-map-facts.json` | Spawn composition plus component, foothold, climbable, width, vertical-span, and complexity metrics |
| Derived index | `victoria-quest-hunt-index.json` | Ranked candidates and an explicit score breakdown for each hunting objective |
| Runtime policy | `victoria-quest-hunt-selection-policy.json` | Decides whether fixed or adaptive candidates have authority |
| Runtime selector | `AgentAdaptiveQuestHuntSelector` | Applies route, occupancy, personality, and rollout policy to the candidates |

All four generated artifacts share one SHA-256 revision calculated from their
source snapshots and generator. Generated files are normalized and compressed;
they should be regenerated rather than hand-edited.

## Map evidence

`MapFacts` includes:

- total spawn entries and unique mob count;
- per-mob spawn count and concentration;
- spawn point, foothold, and connected-component identity;
- horizontal span and total traversable width;
- largest connected-component width and total component count;
- foothold, rope, ladder, and total climbable counts;
- vertical span and topology complexity.

This lets the selector distinguish a compact, concentrated hunting map from a
large map that happens to contain one relevant monster.

## Score evidence

Every derived candidate retains the components used to produce its catalog score:

| Evidence | Effect |
|---|---|
| Target spawn count | Rewards more relevant spawn points |
| Target concentration | Rewards maps where a larger share of spawns advances the objective |
| Co-objective coverage | Rewards maps that advance other objectives in the same quest |
| Other required spawns | Rewards useful secondary targets |
| Expected drop yield | Rewards source mobs with stronger relevant drop evidence |
| Irrelevant spawn count | Penalizes time spent among unrelated mobs |
| Traversable width | Penalizes oversized hunting spaces |
| Component spread | Penalizes target populations split across disconnected regions |
| Climbables and complexity | Penalizes navigation-heavy layouts |
| Level hazard | Penalizes maps whose strongest mobs substantially exceed the quest level |

Catalog score is static evidence. At runtime it is adjusted by route
availability, current population, the Agent's progression profile, current map,
and map capacity.

## Selection policies

| Mode | Behavior | Intended use |
|---|---|---|
| `FIXED` | Uses only the plan's tested map order | Regression-sensitive or explicitly scripted content |
| `PREFERRED_ADAPTIVE` | Uses the tested order first; adaptive candidates are fallback only | Current default and safe rollout |
| `ADAPTIVE` | Uses generated candidates first; fixed candidates remain a safety fallback | Future non-MVP quests after evidence review |

The policy can set separate MVP and non-MVP defaults and can override individual
quests. `adaptiveFallbackEnabled` is an independent kill switch for fallback
while `shadowModeEnabled` controls decision evidence logging.

The current policy deliberately uses `PREFERRED_ADAPTIVE` for both categories.
This enables adaptive fallback first and does **not** enable fully adaptive
non-MVP selection prematurely.

## Shadow evidence

When a hunt map is selected, shadow mode compares:

- the result of the established fixed selection;
- the highest eligible adaptive prediction;
- the adaptive catalog score;
- its runtime-adjusted score;
- the score evidence components.

The structured log message begins with `Agent hunt shadow`. Identical decisions
are deduplicated per Agent so the log records decisions rather than every tick.

The generated baseline comparison is in
`VICTORIA_ADAPTIVE_HUNT_SHADOW_BASELINE.md`. Differences are review candidates,
not automatic migrations. In particular, all tested MVP routes remain fixed
even where the generated scorer currently prefers a different map.

## Regeneration and review

Run:

```powershell
powershell -ExecutionPolicy Bypass -File tools/agent-llm-catalog/Export-VictoriaAdaptiveQuestCatalogs.ps1
powershell -ExecutionPolicy Bypass -File tools/agent-llm-catalog/Compare-VictoriaAdaptiveQuestChoices.ps1
```

Before enabling `ADAPTIVE` for a quest:

1. confirm the source revision matches the intended Cosmic assets;
2. review the fixed-versus-predicted baseline;
3. collect runtime shadow logs under realistic occupancy;
4. verify route coverage and portal traversal for the predicted maps;
5. run the quest repeatedly with failure recovery;
6. add a quest-specific policy override;
7. keep the fixed choice available as rollback evidence.

Fully adaptive selection should first be enabled for non-MVP quests with stable
facts, good route coverage, and repeated shadow agreement. MVP quests should
remain preferred-fixed until their adaptive alternatives have equivalent run
evidence.
