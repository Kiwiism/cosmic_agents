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
| `1031` | Heena and Sera | Heena `2101` | Sera `2100` | Mushroom Town | NPC follow-up |
| `1021` | Roger's Apple | Roger `2000` | Roger `2000` | Snail Garden | Item-use special |
| `1032` | Nina's Brother Sen | Nina `2102` | Sen `2001` | Nina/Sen | NPC follow-up |
| `1033` | What Sen Wants | Sen `2001` | Nina `2102` | Nina/Sen | NPC follow-up |
| `1034` | Tasty Mushroom Candy | Nina `2102` | Sen `2001` | Nina/Sen | Item turn-in |
| `1035` | Todd's Hunting Method | Todd `2004` | Peter `2002` | Training Ground | Kill Tutorial Jr. Sentinel and loot shellpiece |
| `1036` | Robin the Walking Encyclopedia | Robin `2003` | Robin `2003` | Dangerous Forest | Tutorial quiz |
| `1037` | Help Hunt the Snails | Sam `2005` | Maria `2103` | Sam to Amherst | Kill objective, NPC delivery |
| `1038` | Maria's Letter | Maria `2103` | Lucas `12000` | Amherst | Quest-item delivery |
| `1009` | Rain's Maple Quiz 1 | Rain `12101` | Rain `12101` | Amherst | Quiz |
| `1010` | Rain's Maple Quiz 2 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1011` | Rain's Maple Quiz 3 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1012` | Rain's Maple Quiz 4 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1013` | Rain's Maple Quiz 5 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1014` | Rain's Maple Quiz 6 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1015` | Rain's Maple Quiz 7 | Rain `12101` | Rain `12101` | Amherst | Quiz chain |
| `1008` | Pio's Collecting Recycled Goods | Pio `10000` | Pio `10000` or requirement-complete wrapper | Amherst | Reactor/item special |
| `1020` | Pio and the Recycling | Pio `10000` | Pio `10000` | Amherst | Follow-up reward |

## Starts Before Or At Amherst But Completes Later

These should not be part of Amherst sub-phase completion unless a later map is
explicitly added to the slice.

| Quest | Name | Why Excluded |
| --- | --- | --- |
| `1000`, `1001`, `1003`-`1006`, `1025`, `1029`, `1030` | Legacy Maple Island variants | WZ start requirements require the Wizet Plain Suit `1042003`, which is not a normal character-creation item. Cosmic identifies these as GM-clothing-gated quests. |
| `8031` | Protect Lucas's Farm | Requires completed quest `1006`, so it is indirectly gated by the same legacy Wizet-suit chain. |
| `1019` | Sam's Suggestion | Completes at Mai `12100` in `1010000`, beyond Amherst. |
| `1022` | Lucas' Cute Daughter | Completes at Yoona `20100` in `1010000`, beyond Amherst. |
| `1026` | Delivering Nutritious Juice to Shanks | Completes at Shanks `22000`, beyond Amherst. |
| `1027` | Mai's Request | Belongs to the Mai/Training Center segment. |
| `1040` | Chief's Introduction | Requires Mai training quests `1041`-`1044`. |
| `1018` | Todd's How-to-Hunt | Legacy/tutorial-sensitive; excluded from the original MVP by default. |

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
| NPC delivery | `1031` |
| Item-use special | `1021` |
| Multi-map delivery | `1032`, `1033`, `1034` |
| Kill objective | `1037` |
| Quest-item delivery | `1038` |
| Quiz | `1009` |
| Reactor/item special | `1008` |

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

## Phase 1 Capability Services

The earlier validators remain useful as pure planning services. Phase 1 adds
live primitive adapters and integrates one active capability into the Agent tick
without replacing ordinary behavior:

| Need | Service | Current State |
| --- | --- | --- |
| NPC interaction validation | `AgentNpcInteractionCapability` | Validates NPC/map/range/catalog action and estimates dialogue delay; returns a plan when no `NpcGateway` is present. |
| Quest start | `AgentQuestStartCapability` | Validates Amherst quest metadata, current quest status, level/job/prerequisite rules, NPC, and range; gateway execution deferred. |
| Quest complete | `AgentQuestCompleteCapability` | Validates started state, required items, mob kills, progress values, auto-complete cases, NPC, and range; gateway execution deferred. |
| Reactor hit objective | `AgentReactorInteractionCapability` | Plans reactor target and item objective; live hit/loot execution remains behind `AgentReactorExecutionPort`. |
| Primitive runtime | `AgentCapabilityRuntime` | Executes one top frame, supports child handoff/resume, timeout, bounded retry, cancellation, cleanup, and a bounded journal. |
| Live Amherst actions | `server.agents.capabilities.primitive.*` | Navigation/combat delegate to reconstructed ticks; Cosmic quest, NPC, item, portal, reactor, pickup, recovery, and live-state verification are wired. |

## Phase 2 Objective And Plan Runtime

Phase 2 is implemented without executing the complete Amherst route. Objective
parents are in `server.agents.capabilities.objective`; plan loading,
validation, progress, reconciliation, handlers, and execution are in
`server.agents.plans.amherst`.

| Objective parent | Primitive child sequence |
| --- | --- |
| `NpcQuestObjectiveCapability` | portal/navigation, NPC talk, quest start or complete, quest-state verification |
| `InventoryUseObjectiveCapability` | inventory inspection, normal item use, inventory/quest verification |
| `CombatQuestObjectiveCapability` | quest state, portal/navigation, reconstructed combat, quest-progress verification |
| `QuestItemDeliveryObjectiveCapability` | quest state, portal/navigation, inventory inspection, NPC talk, normal completion, quest/inventory verification |
| `QuizObjectiveCapability` | portal/navigation, NPC talk, normal deterministic quest transitions, quest-state verification |
| `ReactorLootObjectiveCapability` | quest state, portal/navigation, reactor hit, normal loot pickup, inventory inspection, optional normal completion |
| `AutoCompleteQuestObjectiveCapability` | portal/navigation, NPC talk, normal quest start, completed-state verification |
| `PlanStopObjectiveCapability` | portal/navigation when needed, final-state verification |

The card loader reads the existing JSON rather than reproducing its route in
Java. Authored `objectiveId` values are retained; cards without them receive a
stable id derived from route index, objective index, and kind. Validation is
separate from execution and rejects unknown kinds, malformed values, duplicate
ids, catalog mismatches, excluded quests, Training Center/off-island maps, and
Shanks travel.

`AmherstObjectiveHandlerRegistry` maps every current JSON kind to one typed
objective command. Handlers construct commands only. They do not move a
character, mutate inventory, change quest state, hit a reactor, or perform
combat.

Durable progress is stored atomically under `.runtime/agents/plans` by default.
Each character/plan snapshot records:

- stable objective id and `PENDING`, `RUNNING`, `SATISFIED`, `BLOCKED`,
  `FAILED`, or `CANCELLED` state;
- attempt count, reason code, message, and timestamps;
- capability-journal start/end correlation;
- a bounded plan journal for assignment, child handoff/result, retry,
  reconciliation, blocker, cancellation, terminal result, and plan completion.

Transient capability frames, map objects, positions, targets, and parent stage
memory are never persisted. On start and before assignment, authoritative live
quest, inventory, map, and progress state can satisfy missing durable progress
or reopen stale success. A successful child chain is persisted as satisfied
only after the same live reconciliation passes. This prevents a restart from
repeating quest rewards, reactor rewards, or item consumption merely because a
progress file is stale or absent.

The runner preserves locked focus, assigns one objective parent, waits for its
terminal result, and stops on cancellation, blockers, or non-retryable failure.
When no Amherst plan is active, the live Agent gate continues through the
ordinary reconstructed behavior exactly as before.

Phase 2 automated proof runs a small `1031 -> 1021 -> stop` composition,
including a fresh-runtime restart, stale-success reopening, cancellation and
resume, bounded child failure, and reward replay checks. Phase 3 owns the
guarded live-client smoke and the complete 25-objective, 18-quest Amherst
route. In-scope multi-hop travel is expanded through adjacent portals, and
portal entry still requires the Agent to navigate into the portal first.

`server.AGENT_LEGACY_DIALOGUE_ENABLED` controls the old NuTNNuT-derived Agent
chatter. It is `false` for the Amherst test run. The Amherst plan narrator is a
separate intentional channel: before each objective, the Agent states the
destination map, NPC, quest, combat/loot target, or reactor action using catalog
names.

Pio's reactor `0002001` progresses through four WZ hit states. The Agent moves
within 60 px, broadcasts a normal weapon swing, waits for the reactor hit
cooldown, and repeats until the box breaks. Quest `1008` requires one Old
Wooden Board `4031161` and one Old Screw `4031162`; one box may drop both.
Completing `1020` awards Relaxer `3010000`, which the final objective uses.
The completed plan retains the seated state instead of allowing the next
ordinary movement tick to replace the chair animation. NPC objectives face the
Agent toward the live NPC position and use the configured randomized
interaction pause before proceeding.
The showcase spawn is stationary and party-free; its reset happens before the
three-second countdown. A separate configured delay controls the pause between
automatic objectives. Navigation and NPC interaction wait for a grounded Agent
so a descending jump cannot turn into a frozen midair conversation.
