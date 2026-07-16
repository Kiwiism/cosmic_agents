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
| `8020 Yoona's Quiz on Shopping : Start` | Yoona `20100` | Complete | Simulated Cash Shop visit obtains the Shopping Guide |
| `8021`-`8022 Yoona's Quiz on Shopping 1-2` | Yoona `20100` | Complete | Ordered server completion bypasses legacy top `1042003` |
| `8023 Yoona's Quiz on Shopping 3` | Yoona `20100` | Complete | Also has no WZ completion NPC |
| `8024`-`8025 Yoona's Quiz on Shopping 4-5` | Yoona `20100` | Complete | Ordered server completion bypasses legacy top `1042003` |
| `1040 Chief's Introduction` | Lucas `12000` | Complete | Amherst quest 1038, then quests 1041-1044 |
| `1041 Mai's First Training` | Mai `12100` | Complete | 5 Stumps and 3 Tree Branches |
| `1042 Mai's Second Training` | Mai `12100` | Complete | 5 Red Snails |
| `1043 Mai's Third Training` | Mai `12100` | Complete | 3 Slimes and 1 Squishy Liquid |
| `1044 Mai's Last Training` | Mai `12100` | Complete | 2 training Orange Mushrooms |
| `1045 Bari's Test` | Bari `20001` | Complete | 1 Orange Mushroom and 1 Orange Mushroom Cap |
| `1046 Biggs's Story on Victoria Island.` | Biggs `20002` | Start only | Quest 1045 complete |

Quest rewards, EXP, kill progress, item consumption, and quest transitions use
the normal server quest APIs. The explicit Yoona overrides preserve the
`8020`-`8025` order while bypassing the client-only Shopping Guide acquisition,
the incompatible legacy male starter-top checks, and the missing completion
NPC for `8023`.

## Verified Exclusions

- `1007 Bigg's Collection of Items` requires item `1042003`. The captured
  female AmherstRun baseline has starter top `1041011`, so 1007 is not
  available and is not forced or granted.
- The Yoona shopping quiz is included. Its WZ chain is
  `8020 -> 8021 -> 8022 -> 8023 -> 8024 -> 8025`.
- `1028 To Lith Harbor!` must remain incomplete.
- Shanks `22000` may be treated as an NPC, but his transport script is outside
  the plan and transport to `104000000` is forbidden.

## Commands

```text
!mapleisland reset AmherstRun
!mapleisland start AmherstRun
!mapleisland resume AmherstRun
!mapleisland next AmherstRun
!mapleisland status AmherstRun
!mapleisland run AmherstRun
```

`run` resolves or spawns the configured Agent at Amherst portal 0, without
follow or party mode, restores `SOUTHPERRY_MVP_START`, clears only the
Southperry progress journal, waits three seconds, and starts automatic mode.

Manual mode runs one objective at a time. `next` authorizes the next live,
unsatisfied objective. `resume` starts automatic mode without resetting the
character or deleting progress; completed objectives are skipped from live
quest and inventory state. Progress is persisted under the existing Agent plan
progress store and reconciled against live state on resume.

## Configuration

```yaml
AGENT_AMHERST_DEBUG_MESSAGES_ENABLED: false
AGENT_AMHERST_INTENTION_CHAT_ENABLED: true
AGENT_MAPLE_ISLAND_SHOWCASE_ENABLED: true
AGENT_MAPLE_ISLAND_SHOWCASE_AGENT_NAME: AmherstRun
```

Both the Amherst and Southperry runtimes assign the executable
`maple-island-quester` behavior profile. Its presentation settings live in
`src/main/resources/agents/profiles/maple-island-quester.profile.json` and own
the randomized NPC-interaction and between-objective delay ranges. The same
profile also enables the first conservative navigation fidgets: grounded
`WAIT`, `PRONE`, and `SPAM_PRONE` actions at bounded cooldowns. Profile-declared
encounter and rest preferences are loaded for later policy work but do not yet
alter combat targeting or Relaxer selection.

## Runtime Contract

- One capability is active at a time; navigation, portal, NPC, combat, and loot
  work are child handoffs that resume their parent objective.
- Map travel uses real portals. The four Mai destinations use portal 1 on map
  `1010000`, whose live script chooses the destination from the active quest.
- NPC and combat actions wait for the Agent to be grounded.
- NPC interactions face the NPC and use the assigned profile's random delay.
- Combat continues farming the declared mobs until both kill and item counts
  are satisfied.
- Relaxer destinations come from fixed WZ-derived catalogs and are reserved by
  Agent character id so two active Agents are never assigned the same spot.
- Amherst has 117 catalog spots. Southperry has 220 total: 107 in the left
  half and 113 in the right half, divided at map midpoint `x = 1890`.
- The standalone Southperry plan may use the full Southperry catalog. The full
  beginning-to-Southperry plan uses only the 113-position right-half catalog.
- Terminal success requires map `2000000`, quests 1039-1045 and 8020-8025
  completed, quest 1046 active, and quest 1028 not completed.

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
