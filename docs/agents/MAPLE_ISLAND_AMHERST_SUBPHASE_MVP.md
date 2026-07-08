# Maple Island Amherst Sub-Phase MVP

This is a reduced pre-MVP slice before the full Maple Island questline MVP.

The Agent starts at `10000 Mushroom Town`, completes quests that can be completed
from the beginning route through `1000000 Amherst`, then stops in Amherst. The
capability surface stays aligned with the full Maple Island MVP, but the plan
completion scope is smaller for faster testing.

This sub-phase must run through the post-reconstruction capability runtime. It
is not a direct script around quest APIs.

Execution model:

```text
Plan objective
  -> objective capability
  -> explicit primitive capability handoff
  -> live state verification
  -> objective success
```

Only one capability frame should be active at a time. If an objective needs
navigation, combat, looting, NPC interaction, item use, or reactor interaction,
it returns a handoff request and resumes after the child capability reaches a
terminal state.

## Scope

Start:

```text
10000 Mushroom Town
```

Stop:

```text
1000000 Amherst
```

The sub-phase is complete when:

- Agent is in `1000000 Amherst`.
- Selected Amherst-slice quests are completed.
- No route advances to `1010000 Entrance to Adventurer Training Center`.
- No Shanks/off-island travel is used.
- Quests that require later maps are either not started or left explicitly out of
  this sub-phase.

## Covered Quest Roster

These quests can be completed within the beginning-to-Amherst slice.

| Quest | Name | Start NPC | Complete NPC | Segment | Main Pattern |
| --- | --- | --- | --- | --- | --- |
| `1000` | Borrowing Sera's Mirror | Heena `2101` | Sera `2100` | Mushroom Town | NPC delivery |
| `1001` | Bringing a Mirror to Heena | Sera `2100` | Heena `2101` | Mushroom Town | NPC delivery, quest item |
| `1031` | Heena and Sera | Heena `2101` | Sera `2100` | Mushroom Town | NPC follow-up |
| `1021` | Roger's Apple | Roger `2000` | Roger `2000` | Snail Garden | Item-use special |
| `1003` | What Sen wants to eat | Nina `2102` | Sen `2001` | Nina/Sen | NPC delivery |
| `1004` | Returning to Nina | Sen `2001` | Nina `2102` | Nina/Sen | NPC delivery |
| `1032` | Nina's Brother Sen | Nina `2102` | Sen `2001` | Nina/Sen | NPC follow-up |
| `1033` | What Sen Wants | Sen `2001` | Nina `2102` | Nina/Sen | NPC follow-up |
| `1034` | Tasty Mushroom Candy | Nina `2102` | Sen `2001` | Nina/Sen | Item turn-in |
| `1029` | Sam's Advice | Sam `2005` | Sam `2005` | Sam | NPC/simple requirement |
| `1037` | Help Hunt the Snails | Sam `2005` | Maria `2103` | Sam to Amherst | Kill objective, NPC delivery |
| `1038` | Maria's Letter | Maria `2103` | Lucas `12000` | Amherst | Quest-item delivery |
| `1005` | Letter for Lucas | Maria `2103` | Lucas `12000` | Amherst | Quest-item delivery |
| `1006` | Lucas' Reply | Lucas `12000` | Maria `2103` | Amherst | Quest-item delivery |
| `1025` | Maria's Nutritious Juice | Maria `2103` | Maria `2103` | Amherst | Item collection/turn-in |
| `1030` | Maria's Map Reading | Maria `2103` | Auto-complete | Amherst | Auto-complete/no complete NPC special |
| `1009` | Rain's Maple Quiz 1 | Rain `12101` | Rain `12101` | Amherst | Quiz |
| `1010` | Rain's Maple Quiz 2 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1011` | Rain's Maple Quiz 3 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1012` | Rain's Maple Quiz 4 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1013` | Rain's Maple Quiz 5 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1014` | Rain's Maple Quiz 6 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1015` | Rain's Maple Quiz 7 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1008` | Pio's Collecting Recycled Goods | Pio `10000` | Pio `10000` or requirement-complete wrapper | Amherst | Reactor/item special |
| `1020` | Pio and the Recycling | Pio `10000` | Pio `10000` | Amherst | Follow-up reward |
| `8031` | Protect Lucas's Farm | Lucas `12000` | Lucas `12000` | Amherst | Kill objective |

## Starts Before Or At Amherst But Completes Later

These should not be part of Amherst sub-phase completion unless a later map is
explicitly added to the slice.

| Quest | Name | Why Excluded |
| --- | --- | --- |
| `1019` | Sam's Suggestion | Completes at Mai `12100` in `1010000`, beyond Amherst. |
| `1022` | Lucas' Cute Daughter | Completes at Yoona `20100` in `1010000`, beyond Amherst. |
| `1026` | Delivering Nutritious Juice to Shanks | Completes at Shanks `22000`, beyond Amherst. |
| `1027` | Mai's Request | Belongs to the Mai/Training Center segment. |
| `1040` | Chief's Introduction | Requires Mai training quests `1041`-`1044`. |
| `1018` | Todd's How-to-Hunt | Legacy/tutorial-sensitive; excluded from the original MVP by default. |
| `1035` | Todd's Hunting Method | Legacy/tutorial-sensitive; excluded from the original MVP by default. |

## Explicitly Out Of Scope

| Quest | Name | Reason |
| --- | --- | --- |
| `8020`-`8025` | Yoona's Quiz on Shopping chain | Yoona is beyond Amherst. |
| `1016`, `1017`, `1041`-`1044` | Mai training quests | Mai is beyond Amherst. |
| `1007` | Bigg's Collection of Items | Southperry quest. |
| `1046` | Biggs's Story on Victoria Island. | Southperry start-only full-MVP marker. |
| `1028` | To Lith Harbor! | Off-island quest; do not complete. |
| `8142` | Todd's How-to-Hunt | Old tutorial-map quest, not reachable from `10000`. |

## Suggested Minimal Acceptance Slice

For fastest first testing, select one representative of each pattern:

| Pattern | Quest |
| --- | --- |
| NPC delivery | `1000`, `1001` |
| Item-use special | `1021` |
| Multi-map delivery | `1003`, `1004` |
| Kill objective | `1037` |
| Quest-item delivery | `1005`, `1006` |
| Quiz | `1009` |
| Reactor/item special | `1008` |
| Auto-complete special | `1030` |

The broader Amherst sub-phase can then add the remaining covered quests without
introducing new map segments beyond Amherst.

## Required Capability Coverage

The Amherst run should prove these capability chains:

| Objective Type | Required Chain |
| --- | --- |
| Quest start/complete | `NpcQuestObjectiveCapability -> NavigationCapability -> NpcInteractionCapability -> QuestStateCapability` |
| Item use | `InventoryUseObjectiveCapability -> InventoryCapability -> ItemUseCapability -> QuestStateCapability` |
| Kill objective | `CombatQuestObjectiveCapability -> NavigationCapability -> CombatCapability -> QuestProgressCapability` |
| Reactor objective | `ReactorQuestObjectiveCapability -> NavigationCapability -> ReactorInteractionCapability -> LootCapability -> InventoryCapability` |
| Final stop | `PlanStopObjectiveCapability -> NavigationCapability -> final-state verification` |

Primitive `NavigationCapability` and `CombatCapability` should wrap the existing
reconstructed nutnnut behavior first and pass parity tests before Amherst
objective constraints are enabled.
