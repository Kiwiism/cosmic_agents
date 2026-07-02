# Maple Island MVP Quest Sequence

This is the selected first milestone route for the reconstructed Agent runtime.
The Agent starts in `10000 Mushroom Town`, completes reachable Maple Island
content, starts Biggs's Victoria Island story, then stops at Southperry without
using Shanks to leave the island.

## Route Rules

- Start map: `10000 Mushroom Town`.
- Final map: `2000000 Southperry`.
- Do Yoona before Mai at `1010000`.
- Start `1046 Biggs's Story on Victoria Island`, but leave it incomplete.
- Do not complete `1028 To Lith Harbor!`.
- Shanks `22000` may be used only for Maple Island quest completion, such as
  `1026`; do not use Shanks travel to leave Maple Island.
- Exclude old tutorial-map quests that are not reachable from `10000`.

## Special Handling

| Quest | Handling |
| --- | --- |
| `1008` Pio's Collecting Recycled Goods | Required items come from reactor boxes; Agent needs reactor-box interaction. |
| `1021` Roger's Apple | Agent must use apple item `2010007` before completing. |
| `1030` Maria's Map Reading | No complete NPC; treat as auto-complete after requirements. |
| `8020` Yoona's Quiz on Shopping : Start | Grant/spawn Cash Shop shopping guide item `4031180` for Agent. |
| `8023` Yoona's Quiz on Shopping 3 | No complete NPC; treat as auto-complete after requirements. |
| `1046` Biggs's Story on Victoria Island | Start only; leave active/incomplete at MVP exit. |

## Excluded Or Review

| Quest | Decision |
| --- | --- |
| `1028` To Lith Harbor! | Exclude; completion requires leaving Maple Island. |
| `1046` Biggs's Story on Victoria Island | Start only; do not complete in MVP. |
| `8142` Todd's How-to-Hunt | Exclude; old tutorial map and not reachable from `10000`. |
| `1018` Todd's How-to-Hunt | Optional review; NPCs are reachable, but quest may be legacy/tutorial-sensitive. |
| `1035` Todd's Hunting Method | Optional review; NPCs are reachable, but quest may be legacy/tutorial-sensitive. |

## Ordered Sequence

### 1. Mushroom Town

Map: `10000 Mushroom Town`

1. Talk to Heena `2101`.
   - Start `1000`.
2. Talk to Sera `2100`.
   - Complete `1000`.
   - Start `1001`.
3. Talk to Heena `2101`.
   - Complete `1001`.
4. Do follow-up Heena/Sera quest if available:
   - `1031`.

### 2. Snail Garden

Move: `10000 -> 20000`

1. Talk to Roger `2000`.
   - Start `1021`.
2. Use Roger's Apple.
   - Use item `2010007`.
3. Talk to Roger `2000`.
   - Complete `1021`.

### 3. Nina And Sen

Move: `20000 -> 30000`

1. Talk to Nina `2102`.
   - Start `1003`.

Move: `30000 -> 30001`

2. Talk to Sen `2001`.
   - Complete `1003`.
   - Start `1004`.

Move: `30001 -> 30000`

3. Talk to Nina `2102`.
   - Complete `1004`.
4. Continue Nina/Sen follow-up quests if available:
   - `1032`
   - `1033`
   - `1034`

### 4. Todd And Peter Review Stop

Move: `30000 -> 40000`

Todd `2004` and Peter `2002` are reachable in `40000`, but `1018` and `1035`
should stay optional until verified in the client. The MVP plan should skip them
by default and mark them as `optional-review`.

### 5. Sam

Move: `40000 -> 50000`

1. Talk to Sam `2005`.
   - Handle reachable Sam quests:
     - `1019`
     - `1029`
     - `1037`
2. Complete required kill or loot objectives from those quests using live quest
   requirements.

### 6. Amherst

Move: `50000 -> 1000000`

1. Talk to Maria `2103`.
   - Handle Maria quests:
     - `1005`
     - `1025`
     - `1026`
     - `1030`
     - `1037`
     - `1038`
2. Talk to Lucas `12000`.
   - Handle Lucas quests:
     - `1006`
     - `1022`
     - `1027`
     - `1040`
     - `8031`
3. Talk to Rain `12101`.
   - Handle Rain quiz chain:
     - `1009`
     - `1010`
     - `1011`
     - `1012`
     - `1013`
     - `1014`
     - `1015`
4. Talk to Pio `10000`.
   - Handle Pio quests:
     - `1008`
     - `1020`
5. For `1008`, open reactor boxes and collect:
   - `4031161`
   - `4031162`

### 7. Yoona Before Mai

Move: `1000000 -> 1010000`

1. Talk to Yoona `20100`.
   - Handle Yoona quests before Mai:
     - `1039`
     - `8020`
     - `8021`
     - `8022`
     - `8023`
     - `8024`
     - `8025`
2. For `8020`, grant/spawn item `4031180` for the Agent.
3. For `8023`, use auto-complete handling if no complete NPC is present.

### 8. Mai

Map: `1010000 Entrance to Adventurer Training Center`

1. Talk to Mai `12100`.
   - Handle Mai quests:
     - `1016`
     - `1017`
     - `1027`
     - `1041`
     - `1042`
     - `1043`
     - `1044`
2. Complete required kill or loot objectives using live quest requirements.

### 9. Southperry

Move: `1010000 -> 1020000 -> 2000000`

1. Talk to Biggs `20002`.
   - Start/complete reachable Biggs quest:
     - `1007`
   - Start but do not complete:
     - `1046`
2. Talk to Shanks `22000` only if needed to complete Maple Island delivery:
   - Complete:
     - `1026`
   - Do not choose travel/off-island flow.
3. Stop at Southperry.

## Exit Criteria

The MVP plan is complete when:

- Agent is in `2000000 Southperry`.
- Required selected Maple Island quests are completed.
- `1046` may be active but incomplete.
- `1028` is not completed.
- Agent has not used Shanks `22000` to leave Maple Island.
- Optional-review Todd quests are either skipped or explicitly enabled by a
  later plan revision.
