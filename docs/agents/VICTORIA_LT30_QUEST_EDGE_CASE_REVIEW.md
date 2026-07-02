# Victoria <30 Quest Edge Case Review

This review is for post-Maple-Island progression, after Agents reach Lith Harbor
and begin branching by job/profile toward level 30.

The goal is not to disable every quest that looks odd in raw WZ data. The goal
is to classify each edge case into a policy bucket so the Agent can either run
it, postpone it, or mark it for later manual review.

Durable catalog outputs:

```text
docs/agents/catalog-overrides/victoria-lt30-quest-status.catalog.json
docs/agents/catalog-overrides/VICTORIA_LT30_QUEST_STATUS_CATALOG.md
```

The JSON file is the fast lookup source for future catalog/runtime loading. The
Markdown file is the human review view of the same status decisions.

## Current Decision Policy

### Completion Outside Victoria

If a quest starts in Victoria but completes outside Victoria, do not mark it as
blocked.

Policy:

```text
take quest if it is otherwise useful and safe
keep it active
postpone completion until Agent has a plan that travels to the target region
```

Catalog status:

```text
postpone-outside-current-region
```

Runtime behavior:

```text
Quest accepted
Objective waits in background/postponed state
Scheduler may resume it when region becomes relevant
```

### Reactor Quests

If a quest item comes from `reactordrops`, do not mark it as a dead end.

Policy:

```text
requires-reactor-capability
```

Reliable source:

```text
src/main/resources/db/data/131-reactordrops-data.sql
```

Strong signal:

```text
reactordrops.questid == questId
```

Weaker signal:

```text
reactordrops.questid == -1
```

`questid == -1` means the item can drop from reactors generally, but it does
not prove that the quest requires reactor interaction.

Runtime behavior after capability exists:

```text
lookup reactor source
navigate to map containing reactor
validate reactor exists/live
hit/open reactor
loot required item
repeat until requirement met
```

### Jump Quests

If a quest item or completion path requires a jump quest map, do not mark it as
impossible.

Policy:

```text
requires-jump-quest-nav-graph
```

Runtime behavior after navigation support exists:

```text
load jump quest route graph
navigate platform/rope/hazard route
collect/complete objective
return to normal plan flow
```

Until jump-quest navigation exists, these should be:

```text
postpone-until-capability-ready
```

## Known Capability Buckets

### Requires Reactor Capability

Known from `reactordrops` quest-specific or item-linked data:

| Quest | Name | Reactor Signal |
| --- | --- | --- |
| `1008` | Pio's Collecting Recycled Goods | `4031161`, `4031162` from reactors |
| `2067` | Research on Plant Fossils | `reactor 1012000 -> 4031150`, quest `2067` |
| `2074` | Find the Maple History Book | `reactor 9102000 -> 4031157`, `reactor 9102001 -> 4031158`, quest `2074` |
| `2169` | The Large Pearl | `reactor 1202002 -> 4031843`, quest `2169` |

These should move from `likely-blocked` to:

```text
requires-reactor-capability
```

### Requires Jump Quest Navigation

Known or likely jump-quest style quests:

| Quest | Name | Notes |
| --- | --- | --- |
| `2050` | Sabitrama and the Diet Medicine | Pink Anthurium style objective; needs jump-quest route handling. |
| `2052` | John's Pink Flower Basket | John's flower basket style objective; needs jump-quest route handling. |
| `2055` | Shumi's Lost Coin | Subway jump quest style objective. |

These should move from `likely-blocked` to:

```text
requires-jump-quest-nav-graph
```

### Postpone Outside Current Region

Quests that leave Victoria or lead to another region should not block the
Victoria progression plan. They can stay active and be resumed when the Agent
travels there later.

Examples from the current scan:

| Quest | Name | Notes |
| --- | --- | --- |
| `2074` | Find the Maple History Book | Includes sources/completion outside ordinary Victoria route; also has reactor parts. |
| `2075` | Find the Maple History Book 2 | Starts outside reachable Victoria scan, completes back at Jay. |
| `2254` | Karcasa of the Desert | Completion target outside Victoria; postpone until desert travel. |
| `8053` | Traveling Around Maple 1 | Travel/world-tour style quest. |
| `8059` | Traveling Around Maple 7 | Travel/world-tour style quest. |

Catalog status:

```text
postpone-outside-current-region
```

## Remaining Review Bucket

After accepting the policies above, the remaining quests to flag for later
review are mostly:

- script-only or auto-complete ambiguity.
- quest items with no known source after checking mob/shop/reactor/quest reward.
- NPC placements missing from current catalog even though the NPC should exist.
- event/admin quests.
- pet/NX/cash/event quests.
- possible old-version quests.

Current preliminary Victoria-area `<30` review had:

```text
Victoria area candidates: 183
Catalog-OK: 104
Manual review: 11
Likely blocked by raw catalog: 68
```

After applying the accepted policies:

```text
reactor quests: move to requires-reactor-capability
jump quests: move to requires-jump-quest-nav-graph
outside-region completions: move to postpone-outside-current-region
remaining unknowns: keep manual-review-required
```

## Remaining Quests To Review Later

These should remain flagged until their scripts, item source, NPC placement, or
quest chain behavior is verified.

### Script / Auto-Complete Ambiguity

| Quest | Name | Reason |
| --- | --- | --- |
| `2071` | Stranger's Request | No complete NPC and no auto-pre-complete flag in raw scan. |
| `2117` | Shawn the Excavator's Request | No complete NPC and no auto-pre-complete flag in raw scan. |
| `2216` | Information from Mr. Pickall | No complete NPC in raw scan. |
| `2217` | Information from Shumi | No complete NPC in raw scan. |
| `2218` | Information from Nella | No complete NPC in raw scan. |
| `2219` | Information from Jake | No complete NPC in raw scan. |
| `2232` | Find a Junior! | No complete NPC in raw scan. |
| `2233` | Raise the Rep! | No complete NPC in raw scan. |
| `2234` | Enjoy the Entitlement! | No complete NPC in raw scan. |

### Quest Item Source Or Chain Needs Review

These may be valid quest-chain item rewards or script grants, but the catalog
must prove that before Agents run them automatically.

| Quest | Name | Current Concern |
| --- | --- | --- |
| `2006` | Getting Arcon's Blood | `4031005` source unclear. |
| `2007` | Making Sparkling Rock | `4031004` source unclear. |
| `2008` | Delivering the Weird Medicine | `4031006` source unclear. |
| `2016` | Mother's Gold Watch | `4031007` source unclear. |
| `2066` | Delivering a Box of Fossil | `4031148`, `4031149` source unclear. |
| `2069` | Transporting Drake's Skull | `4031151` source unclear. |
| `2070` | Progress on Fossil Research | `4031152` source unclear; some requirements may be chain/script-based. |
| `2073` | Camila's Gem | `4031156` source unclear and completion NPC placement needs validation. |
| `2076` | Estelle's Special Sauce | `4031154` source unclear. |
| `2092` | I Need to Find My Daughter 1 | `4031174` source unclear. |
| `2093` | I Need to Find My Daughter 2 | `4031173` source unclear. |
| `2153` | The Old Snail | `4161035` source unclear. |

### NPC Placement / Reachability Needs Review

These may be valid, but current NPC placement/catalog reachability does not
prove completion.

| Quest | Name | Current Concern |
| --- | --- | --- |
| `2078` | The Path of a Bowman | Athena Pierce placement not resolved by current scan. |
| `2080` | The Path of a Magician | Grendel placement not resolved by current scan. |
| `2123` | A Special Assignment | Complete NPC not in reachable Victoria scan. |
| `2162` | The Half-written Letter | Completion NPC placement/source needs review. |
| `2164` | Athena Pierce's Gift | Start/complete placement/source needs review. |
| `2167` | Sea Firefly1 | Completion outside current reachable Victoria scan. |
| `2168` | Sea Firefly2 | Start/complete path needs review. |
| `2172` | What is it that Bart saw? | NPC path/source needs review. |
| `2174` | Report to Muirhat | NPC path/source needs review. |
| `2175` | Disciples of the Black Magician | NPC path/source needs review. |
| `2180` | Find Fresh Milk | Item/source behavior needs review. |
| `2181` | Porchay's Letter | Completion path needs review. |
| `2183` | A Banquet for the Whalians | Completion path needs review. |
| `2185` | Dress for Kyrin | Completion path/source needs review. |
| `2186` | Help Me Find My Glasses | Item/source behavior needs review. |
| `2210` | Take the Gold Pouch to Muirhat | NPC/path source needs review. |
| `2211` | Deliver the Tattered Map to Black Bark | NPC/path source needs review. |
| `2221` | Conclusion | NPC/path source needs review. |
| `2224` | Rage, Resentment, and Revenge | NPC/path source needs review. |
| `2226` | Arwen's Apology | NPC/path source needs review. |
| `2230` | A Mysterious Small Egg | Quest/script source needs review. |
| `2264` | Mr. Lim and the Subway | Quest/script source needs review. |

### Event / Pet / Admin / Seasonal

These should not be part of normal autonomous progression unless explicitly
enabled by event policy.

| Quest | Name | Reason |
| --- | --- | --- |
| `1047` | Designated Monster Effect | No complete NPC; likely special/tutorial/admin behavior. |
| `1048` | Job Recommendation | Maple Administrator / special flow. |
| `1049` | Becoming a Warrior | Maple Administrator / special flow. |
| `1050` | Becoming a Magician | Maple Administrator / special flow. |
| `1051` | Becoming a Bowman | Maple Administrator / special flow. |
| `1052` | Becoming a Thief | Maple Administrator / special flow. |
| `1053` | Becoming a Pirate | Maple Administrator / special flow. |
| `1200` | Moon Bunny's Rice Cake | PQ/event-like; no complete NPC in raw scan. |
| `1201` | First Time Together | PQ/event-like; no complete NPC in raw scan. |
| `4646` | Pet Instructor Test | Pet-specific; gate behind pet capability. |
| `8700` | 2006 Easter : Easter Basket | Seasonal event. |
| `8800` | Anniversary : Birthday Present (Red) | Seasonal event. |
| `8801` | Anniversary : Cody's Quest | Seasonal event. |
| `8804` | Independence Day : Cody's Barbecue Party | Seasonal event. |
| `8821` | Thanksgiving : Turkey Yellow Egg hunt | Seasonal event. |
| `8833` | New Year's Wishes 1 | Seasonal event. |
| `8834` | New Year's Wishes 2 | Seasonal event. |

### Mob Source Needs Review

These were flagged because the target mob was not found on reachable Victoria
maps by the current catalog scan. Some may be field boss/manual spawn cases.

| Quest | Name | Current Concern |
| --- | --- | --- |
| `2105` | DANGER! <1-G. Mushroom> | Mob `9101000` not found on reachable Victoria maps. |
| `2107` | DANGER! <3-Z. Mushroom> | Mob `9101001` not found on reachable Victoria maps. |
| `2111` | DANGER! <1-G. Mushroom> | Mob `9101000` not found on reachable Victoria maps. |
| `2113` | DANGER! <3-Z. Mushroom> | Mob `9101001` not found on reachable Victoria maps. |
| `2146` | Monstrous Tree Stumpy | Stumpy source/spawn needs field boss handling. |
| `28275` | [Hunt] You Were Bitten by a Green Mushroom? | Needs validation against current mob/map catalog. |
| `28283` | For the peace of Victoria Island... | Needs validation against current mob/map catalog. |

## Catalog Statuses To Add

Add these statuses to the quest edge-case catalog:

```text
normal
postpone-outside-current-region
requires-reactor-capability
requires-jump-quest-nav-graph
requires-field-boss-policy
requires-pet-capability
requires-pq-capability
requires-event-policy
requires-script-review
requires-chain-source-review
requires-npc-placement-review
manual-review-required
disabled-for-agents
```

## Runtime Rule

The Agent should not fail a plan because a quest is outside the current region.

Instead:

```text
if quest completion is outside current region:
  keep quest active
  mark objective postponed
  continue choosing other plans in current region
```

The Agent should only block when:

- the quest is selected as required by the active plan.
- the required capability is missing.
- no safe postpone/skip policy exists.
- the catalog marks it `manual-review-required` or `disabled-for-agents`.
