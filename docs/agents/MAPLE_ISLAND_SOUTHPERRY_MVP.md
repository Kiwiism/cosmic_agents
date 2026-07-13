# Maple Island Southperry MVP

## Scope

This phase starts from the captured, successful AmherstRun end state and covers
only the modern Cosmic quest path from Amherst to Southperry. It does not rerun
the completed Amherst MVP.

Verified route:

```text
1000000 Amherst
-> 1010000 Entrance to Adventurer Training Center
-> 1020000 Split Road of Destiny
-> 2000000 Southperry
```

Mai's four training maps are temporary branches from `1010000` through the
live `entertraining` scripted portal and return through their normal `out00`
portals.

## Quest List

| Quest | NPC | Required result | Live requirement |
| --- | --- | --- | --- |
| `1039 Helping Out Yoona` | Yoona `20100` | Complete | 10 Blue Snails and 10 Shrooms |
| `1040 Chief's Introduction` | Lucas `12000` | Complete | Amherst quest 1038, then quests 1041-1044 |
| `1041 Mai's First Training` | Mai `12100` | Complete | 5 Stumps and 3 Tree Branches |
| `1042 Mai's Second Training` | Mai `12100` | Complete | 5 Red Snails |
| `1043 Mai's Third Training` | Mai `12100` | Complete | 3 Slimes and 1 Squishy Liquid |
| `1044 Mai's Last Training` | Mai `12100` | Complete | 2 training Orange Mushrooms |
| `1045 Bari's Test` | Bari `20001` | Complete | 1 Orange Mushroom and 1 Orange Mushroom Cap |
| `1046 Biggs's Story on Victoria Island.` | Biggs `20002` | Start only | Quest 1045 complete |

Quest rewards, EXP, kill progress, item consumption, and quest transitions use
the normal server quest APIs.

## Verified Exclusions

- `1007 Bigg's Collection of Items` requires item `1042003`. The captured
  female AmherstRun baseline has starter top `1041011`, so 1007 is not
  available and is not forced or granted.
- The old Yoona and Shanks chains also depend on legacy starter-item
  requirements and are excluded from this fixed baseline.
- `1028 To Lith Harbor!` must remain incomplete.
- Shanks `22000` may be treated as an NPC, but his transport script is outside
  the plan and transport to `104000000` is forbidden.

## Commands

```text
!mapleisland reset AmherstRun
!mapleisland start AmherstRun
!mapleisland next AmherstRun
!mapleisland status AmherstRun
!mapleisland run AmherstRun
```

`run` resolves or spawns the configured Agent at Amherst portal 0, without
follow or party mode, restores `SOUTHPERRY_MVP_START`, clears only the
Southperry progress journal, waits three seconds, and starts automatic mode.

Manual mode runs one objective at a time. `next` authorizes the next live,
unsatisfied objective. Progress is persisted under the existing Agent plan
progress store and reconciled against live quest/map/inventory state on resume.

## Configuration

```yaml
AGENT_AMHERST_DEBUG_MESSAGES_ENABLED: false
AGENT_AMHERST_INTENTION_CHAT_ENABLED: true
AGENT_AMHERST_NPC_INTERACTION_DELAY_MIN_MS: 600
AGENT_AMHERST_NPC_INTERACTION_DELAY_MAX_MS: 1400
AGENT_AMHERST_NEXT_OBJECTIVE_DELAY_MIN_MS: 900
AGENT_AMHERST_NEXT_OBJECTIVE_DELAY_MAX_MS: 1800
AGENT_MAPLE_ISLAND_SHOWCASE_ENABLED: true
AGENT_MAPLE_ISLAND_SHOWCASE_AGENT_NAME: AmherstRun
```

The established Amherst interaction and objective delays are shared so both
plans retain the same pacing controls.

## Runtime Contract

- One capability is active at a time; navigation, portal, NPC, combat, and loot
  work are child handoffs that resume their parent objective.
- Map travel uses real portals. The four Mai destinations use portal 1 on map
  `1010000`, whose live script chooses the destination from the active quest.
- NPC and combat actions wait for the Agent to be grounded.
- NPC interactions face the NPC and retain the configured random delay.
- Combat continues farming the declared mobs until both kill and item counts
  are satisfied.
- Terminal success requires map `2000000`, quests 1039-1045 completed, quest
  1046 active, and quest 1028 not completed.

## Verification

Automated coverage includes fixture loading, exact captured fields and items,
catalog/scope policy, prerequisite order, scripted and normal portal routing,
Shanks transport blocking, 1046 start-only final state, 1028 exclusion,
retry, persisted resume, no Amherst replay, and a simulated full run to
Southperry.

The focused 121-test Amherst/Southperry regression slice passes with:

```powershell
.\mvnw.cmd -q "-Dtest=server.agents.plans.amherst.**.*Test,server.agents.plans.mapleisland.**.*Test,server.agents.capabilities.objective.**.*Test,server.agents.capabilities.primitive.**.*Test,server.agents.capabilities.quest.**.*Test,server.agents.runtime.AgentLifecycleServiceTest,server.agents.runtime.AgentLiveTickGateServiceTest" test
```

Live gameplay still needs one visual pass to confirm movement framing, combat
pacing, ordinary item drops, and the client-visible portal transitions on the
running server.
