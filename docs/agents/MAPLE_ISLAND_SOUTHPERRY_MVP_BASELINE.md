# Maple Island Southperry MVP Baseline

## Purpose

The Southperry MVP starts from the proven end state of the successful Amherst
MVP instead of rerunning Mushroom Town through Amherst during every development
cycle.

This fixture is test-only. Normal Agent character loading and persistence are
unchanged.

## Source

- Source character: `AmherstRun`
- Captured from the offline `cosmic` database on `2026-07-13`
- Resource: `src/main/resources/agents/fixtures/amherstrun-post-amherst-baseline.json`
- Reset mode: `AmherstTestResetMode.SOUTHPERRY_MVP_START`

## Captured Character State

| Field | Value |
| --- | ---: |
| Map | `1000000 Amherst` |
| Level | `6` |
| EXP | `79` |
| Job | `0 Beginner` |
| STR / DEX / INT / LUK | `37 / 5 / 4 / 4` |
| HP / Max HP | `121 / 121` |
| MP / Max MP | `58 / 58` |
| Mesos | `224` |
| AP / SP | `0 / 0` |
| Gender | `1 Female` |
| Hair | `31000` |
| Face | `21001` |
| Skin | `0` |

The fixture also records and restores all 12 equipped and inventory items,
including the Relaxer, and the 18 completed Amherst quests.

## Reset Contract

`SOUTHPERRY_MVP_START` must:

1. Stop and clear the current Agent capability/runtime state.
2. Restore the captured stats, appearance, equipment, inventory, and mesos.
3. Clear skills and cooldowns because the captured Agent is a Beginner with no
   learned skills.
4. Reset all selected post-Amherst and Southperry quest states.
5. Restore the 18 completed Amherst quest states.
6. Place and ground the Agent at `1000000 Amherst`.
7. Save the restored character before the Southperry plan begins.

The reset must not modify the level-1 Amherst reset or its showcase command.

## Southperry Plan Entry

The Maple Island command invokes the guarded reset with:

```java
new AmherstTestResetRequest(
        agent.getId(),
        agent.getName(),
        AmherstTestResetMode.SOUTHPERRY_MVP_START,
        0)
```

After a successful reset, clear the Southperry plan progress store and begin the
first post-Amherst objective. Do not execute any Amherst objective again.

The executable plan and implementation notes are in:

- `docs/agents/plans/maple-island-southperry-mvp.plan.json`
- `docs/agents/MAPLE_ISLAND_SOUTHPERRY_MVP.md`

## Exit Contract

The Southperry MVP succeeds when:

- the selected post-Amherst quests are completed through normal game APIs;
- the Agent reaches `2000000 Southperry` through portals;
- quest `1046` is started and remains incomplete;
- quest `1028` remains incomplete;
- Shanks `22000` never transports the Agent off Maple Island; and
- the plan records a terminal success state at Southperry.
