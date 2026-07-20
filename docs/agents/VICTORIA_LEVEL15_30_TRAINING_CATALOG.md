# Victoria Island level 15-30 training catalog

## Purpose

`victoria-level15-30-training-v1` gives autonomous agents several believable grinding choices at every level from 15 through 30. Explicit ranks determine fill order. Runtime policy must still reject a map the agent cannot enter, navigate, hit reliably, survive, or share without overcrowding. Retained weights are future tuning hints and do not override rank.

The source-controlled catalog is:

- `src/main/resources/agents/catalogs/victoria-level15-30-training-catalog.json`
- loaded and cross-validated by `AgentVictoriaTrainingCatalogRepository`
- selected through `AgentVictoriaTrainingMapSelector`
- guarded against local WZ drift by `AgentVictoriaTrainingWzCatalogTest`

## Recommended choices by level

The first map is rank 1. Older maps intentionally remain in later lists, normally for one or two levels, so a character can stay put or use them when popular maps are full. Full ranks, weights, reasons, and conditions live in the JSON catalog.

| Level | Default | Other eligible choices |
|---:|---|---|
| 15 | The Tree That Grew II | Henesys Hunting Ground I, Mushroom Garden, The Tree That Grew I, Henesys Pig Farm, Rain-Forest East, Subway Area 1 |
| 16 | Henesys Pig Farm | The Tree That Grew II, Mushroom Garden, Caution Falling Down, Henesys Hunting Ground I, Subway Area 1, Rain-Forest East |
| 17 | Henesys Pig Farm | Pig Beach, Caution Falling Down, The Tree That Grew II, Mushroom Garden, Subway Area 1 |
| 18 | Henesys Pig Farm | Pig Beach, Caution Falling Down, Deep Valley I, Subway Area 1, Mushroom Garden |
| 19 | Pig Beach | Deep Valley I, Caution Falling Down, Subway Area 1, Pig Park, Henesys Pig Farm |
| 20 | Pig Park | Blue Mushroom Forest, Deep Valley I, Subway Area 1, Ant Tunnel I, Pig Beach, Henesys Pig Farm |
| 21 | Blue Mushroom Forest | Deep Valley I, Excavation Site I, Subway Area 1, Ant Tunnel I, Pig Park, Pig Beach |
| 22 | Excavation Site I | Blue Mushroom Forest, Ant Tunnel I, Ant Tunnel II, Deep Valley I, Subway Area 1 |
| 23 | Excavation Site I | Ant Tunnel II, Excavation Site II, Ant Tunnel I, Land of Wild Boar II, Ant Tunnel III |
| 24 | Excavation Site II | Ant Tunnel II, Land of Wild Boar II, Ant Tunnel III, Excavation Site III, Ant Tunnel IV, Ant Tunnel I, Pig Beach |
| 25 | Land of Wild Boar II | Ant Tunnel II, Ant Tunnel III, Excavation Site II, Ant Tunnel IV, Excavation Site III, Land of Wild Boar, Subway Area 1 |
| 26 | Ant Tunnel III | Land of Wild Boar II, Excavation Site III, Ant Tunnel IV, Excavation Site II, Tree Dungeon Forest Up North, Ant Tunnel II, Land of Wild Boar, Blue Mushroom Forest |
| 27 | Ant Tunnel III | Tree Dungeon Forest Up North, Land of Wild Boar II, Excavation Site III, Ant Tunnel IV, Cave of Evil Eye I, Excavation Site II, Land of Wild Boar |
| 28 | Ant Tunnel III | Tree Dungeon Forest Up North, Land of Wild Boar II, Excavation Site III, Ant Tunnel IV, Cave of Evil Eye I, Land of Wild Boar, Excavation Site II |
| 29 | Ant Tunnel III | Tree Dungeon Forest Up North, Land of Wild Boar II, Excavation Site III, Cave of Evil Eye I, Ant Tunnel IV, Land of Wild Boar, Excavation Site II |
| 30 | Ant Tunnel III | Tree Dungeon Forest Up North, Excavation Site III, Land of Wild Boar II, Cave of Evil Eye I, Ant Tunnel IV, Land of Wild Boar, Excavation Site II |

Subway is ticketed and ladder-heavy, and Bubblings can punish low-accuracy melee builds. Deeper Ant Tunnel and Evil Eye maps require adequate supplies and navigation confidence. The local WZ version also contains high-level hazards in Pig Beach and Land of Wild Boar, so they are cataloged rather than ignored.

## Research basis

Old-school guides disagree on exact cutovers, so their shared preferences are evidence rather than fixed truth:

- The Wikibooks guide names the Tree That Grew, Pig Beach, mushroom trees, Ant Tunnel, and Wild Boars across levels 11-30: <https://en.wikibooks.org/wiki/MapleStory/Training_Guide>
- A 2009 GameFAQs discussion recommends Pig Beach, Caution Falling Down, slime trees, green/horny mushrooms, Ant Tunnel, and Wild Boars: <https://gamefaqs.gamespot.com/boards/924697-maplestory/50017600>
- A MapleRoyals magician guide describes Henesys Hunting Ground and Horny Mushrooms while warning that Ant Tunnel is dangerous for a weak new magician: <https://royals.ms/forum/threads/guide-for-newbie-magician-to-level-30-by-dave.198200/>
- A MapleRoyals bowman guide includes pigs, boars, Ant Tunnel, and late-20s excavation masks: <https://royals.ms/forum/threads/bowman-training-guide.434/>
- A MapleLegends guide adds Henesys Pig Farm, Subway Area 1, and Cave of Evil Eye routes as alternatives: <https://forum.maplelegends.com/index.php?threads/comprehensive-training-guide-v2-0.54165/>
- The MapleRoyals grinding overview also lists Bubblings, Wild Boars, and Ant Tunnel for this band: <https://mapleroyals.net/game-tips/grinding-spot>

Community evidence decides which maps are plausible and historically recognizable. Map IDs, names, mob IDs, levels, and exact spawn counts are verified against this repository's local `wz/Map.wz` and `wz/Mob.wz` data.

## Runtime selection policy

`AgentVictoriaTrainingMapSelector` makes rank and occupancy semantics executable:

1. Begin with the exact-level list and remove choices rejected by external route, hit-rate, survival, supply, or navigation checks.
2. Keep the current map when it remains within the top four, is eligible, and has not exceeded recommended occupancy. This avoids an artificial change every level.
3. Otherwise choose the first ranked map below `recommendedAgents`.
4. If all choices reached their recommended sizes, choose the first map below `maximumAgents`.
5. Return no choice if every eligible map reached hard capacity; the supervisor should wait, quest, change channel, or recalculate later.

Occupancy includes loaded-map real-player counts plus active Agent training assignments. Re-evaluate after a level,
supply interruption, repeated navigation failure, relog recovery, or destination failure -- not on every tick.

`AgentVictoriaTrainingObjectiveRuntime` invokes the selector for the durable
`progression.victoria-training` objective. It can run in `mixed` mode (eligible quests plus grinding) or `grind` mode
(grinding only), and can be controlled with `!victoria train`, `!victoria stop`, and `!victoria status`.

## Plan interruption, timeout, and relog behavior

The Amherst/Maple Island runner already has durable objective progress and bounded in-session recovery:

- Progress snapshots are atomically stored under `.runtime/agents/plans`.
- Starting or resuming reloads the snapshot, reopens interrupted `RUNNING` objectives, and reconciles steps against live quest, kill, item, and map state.
- Capability deadlines and the objective watchdog cancel stale frames, clear stale movement, and retry recoverable failures up to the configured limit.
- Supply maintenance can suspend a foreground objective and resume it through the objective kernel.

Generic foreground/suspended objective checkpoints are atomically stored under
`.runtime/agents/plans/objective-checkpoints`, and career checkpoints under
`.runtime/agents/progression/checkpoints`. Registration restores these records and reattaches recognized plan runners
with bounded retry. A resumed runner recalculates the first live-unsatisfied objective instead of replaying the last
command. Training and quest state are also reconstructed from the objective plus live level, map, and quest state.

## Level-30-and-below quest hunting maps

`generated_victoria_lt30_quest_hunting_catalog.json` is generated alongside the Agent/LLM catalogs. It covers all 183 quests in the current reachable Victoria level-30-and-below status scope. The current data contains 93 quests with 136 true hunting objectives and 72 non-hunting acquisition objectives. All 109 hunting objectives belonging to quests currently allowed for autonomous start have at least one preferred Victoria map.

For each hunting objective it records:

- the required mob or item and quest-valid source mobs;
- up to five preferred Victoria maps ranked by target coverage, exact local spawn density, level training rank, and hazard penalties;
- `recommendedAgents` and `maximumAgents` for top-down occupancy selection;
- a combined map ranking for quests with multiple hunting requirements;
- missing-source, entry, high-level-mob, scripted-transfer, and region warnings.

Items without a quest-valid Victoria mob source are separated as `nonHuntingAcquisitionObjectives` so shop goods and scripted items are not assigned bogus grinding maps.

Only quests marked runnable by the status catalog may be started automatically. Quests leaving Victoria stay cataloged but have `autonomousStartAllowed: false`. The Manji boundary is explicit: `2122` may run locally, but automatic continuation into `2123` or `2127` is blocked; `To the Desert...` remains deferred until world-travel policy exists.

The executable subset is generated into `victoria-lt30-quest-runtime-catalog.json`. It currently contains 71
conservative entries: each has at least one supported hunting objective, no unsupported non-hunting acquisition, both
NPC endpoints on Victoria, and no scripted world transfer. `AgentVictoriaQuestSchedulerRuntime` resumes an already
started catalog quest first; otherwise `victoria-progression-policy.json` makes one deterministic quest-versus-grind
decision per Agent and level. NPC interaction waits use a deterministic two-to-six-second delay.

Regenerate directly with:

```powershell
powershell -ExecutionPolicy Bypass -File tools/agent-llm-catalog/Export-VictoriaLt30QuestHuntingCatalog.ps1
```

Its revision hashes the generator, quest objectives, spawn catalog, map catalog, quest-status policy, training catalog, and manual quest policy. Changes to drops, spawns, ranking logic, or overrides produce a new auditable revision.

## Drop-table versioning

Drop facts deliberately do not live in the stable training JSON. Generate a hunting overlay from the current drop catalog:

```powershell
powershell -ExecutionPolicy Bypass -File tools/game-catalog/Export-VictoriaTrainingDropOverlay.ps1
```

The output uses the drop-catalog SHA-256 in its filename and `dropTableRevision`. Regenerating after a drop-table change creates another version without changing stable map strategy. The quest-hunting catalog is also regenerated by the normal Agent/LLM export, so item-source changes update quest map rankings.

## Current boundary

The catalog, selector, occupancy accounting, generated 626-edge standard portal graph, bounded route replanning,
conservative quest scheduler, explicit reward policy, relog reattachment, and level evidence capture are wired into the
level 15-30 objective. Scripted portal preconditions, rich per-map navigation anchors, live hit-rate/survival scoring,
channel changes, inventory-space recovery, and telemetry-based rank/capacity calibration remain later policy layers.
