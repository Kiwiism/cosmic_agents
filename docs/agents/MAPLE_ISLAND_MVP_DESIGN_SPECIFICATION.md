# Maple Island MVP Design Specification

This document defines the design for the first post-reconstruction Agent
milestone:

```text
Spawn one Agent with a Maple Island plan card.
Agent completes the selected Maple Island questline.
Agent stops at Southperry.
Agent does not use Shanks to leave Maple Island.
```

Technical implementation details live in:
`docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md`.

Route source: `docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md`.

Initial plan card: `docs/agents/plans/maple-island-mvp.plan.json`.

## Scope

In scope:

- Maple Island ordered questline only.
- Plan card runner and objective state.
- quest start/complete through validated server APIs.
- NPC interaction validation without full dialogue clicking.
- navigation to maps, portals, NPCs, mobs, and exact objective points.
- combat and loot for quest objectives.
- inventory reads and objective item use.
- HP/MP/death/stuck/no-progress recovery.
- minimal reactor handling for Pio `1008`.
- special scripted item handling for Yoona `8020`.
- deterministic first full run.

Out of scope:

- LLM autonomy.
- Free Market and economy.
- trading.
- job advancement.
- Victoria Island progression.
- party quests.
- generalized NPC dialogue replay.
- generalized shopping/resupply unless later proven necessary.

## Success Criteria

The MVP succeeds when:

- Agent starts from `10000 Mushroom Town`.
- Agent ends at `2000000 Southperry`.
- selected required Maple Island quests are complete.
- quest `1046` is started and remains incomplete.
- quest `1028` is not completed.
- Shanks `22000` is never used for off-island travel.
- optional-review Todd quests `1018` and `1035` are skipped by default.
- every objective has a journal/result row.
- failures become structured blockers, not loops.

## Current Capability Baseline

| Area | Current Level | MVP Gap |
| --- | --- | --- |
| Navigation | Mostly present | Need objective adapters for map, portal, NPC, mob, item, and exact point targets. |
| Combat | Mostly present | Need quest-focused targeting, live stop conditions, no-mob fallback, and recovery integration. |
| Looting | Mostly present | Need quest item priority, objective stop condition, and full-inventory blocker. |
| Item use | Partial | Need controlled objective item use for Roger's Apple and recovery items/chair if available. |
| NPC interaction | Missing/contracted | Need validation-only range/placement check and direct quest start/complete. |
| Quest capability | Missing/general incomplete | Need read, requirement check, start, complete, auto-complete special handling. |
| Plan runner | Missing | Need ordered objectives, dependencies, persistence, focus, fallbacks, and exit criteria. |
| Recovery | Partial/manual | Need HP/MP, death, no potion/no meso, stuck, no route, no mob, and full inventory handling. |
| Reactor interaction | Missing | Need minimal reactor-box handling for Pio `1008`. |

## Minimum Final Capability Set

Required:

- `PlanCardCapability`.
- `ObjectiveRunner`.
- `QuestCapability`.
- `NpcQuestInteractionCapability`.
- `NavigationCapability`.
- `PortalTravelCapability`.
- `CombatCapability`.
- `LootCapability`.
- `InventoryCapability`.
- `ItemUseCapability`.
- `RecoveryCapability`.
- `ReactorInteractionCapability`.
- `PlanJournalCapability`.

Not required:

- LLM command control.
- dynamic economy.
- Free Market.
- general shop automation.
- party/social behavior.
- job advancement.
- non-Maple-Island quest selection.
- arbitrary NPC dialogue option replay.

## Plan Card Model

The Maple Island plan card is ordered with live validation. It should describe
intent, route, constraints, and special cases. It should not embed low-level
movement, combat, or retry loops.

Plan sections:

- identity: `planId`, title, category, version.
- entry criteria: start map and region.
- focus policy: deterministic quest focus, emergency sidetrack only.
- quest policy: required, start-only, excluded, optional-review.
- route: ordered map/objective groups.
- special handling: scripted items, auto-complete, reactor boxes.
- forbidden actions: Shanks off-island travel.
- exit criteria.
- fallback policy.
- debug policy.

The plan card references catalogs for:

- NPC placements.
- NPC approach points.
- quest requirements.
- quest rewards.
- mob/drop sources.
- portal routes.
- special-case objective metadata.

## Objective Model

Each objective does one thing.

Objective kinds:

- `navigate-to-map`.
- `navigate-to-npc`.
- `navigate-to-point`.
- `quest-start`.
- `quest-complete`.
- `quest-chain`.
- `quest-if-available`.
- `quest-chain-if-available`.
- `kill-mob-count`.
- `loot-item-count`.
- `use-item`.
- `reactor-box-items`.
- `grant-scripted-item`.
- `auto-complete-quest`.
- `skip-optional-review`.
- `stop-plan`.

Objective statuses:

- `pending`.
- `active`.
- `satisfied-live`.
- `completed`.
- `skipped`.
- `blocked`.
- `failed`.
- `debug-resolved`.

Live server state is authoritative. Persisted objective state must be
reconciled against live quest/map/inventory state on every resume.

## Focus Mode

When active, the Agent enters `QUESTLINE_FOCUS`.

Allowed sidetracks:

- emergency recovery.
- death recovery.
- stuck recovery.
- inventory pressure blocker.
- low HP/MP survival pause.

Disallowed sidetracks:

- Free Market.
- normal social wandering.
- trade.
- off-island travel.
- job advancement.
- unrelated questing.

After a sidetrack, Agent returns to the active objective.

## Interaction Realism

Interaction realism is configurable:

```text
OFF
LIGHT
FULL
```

The first MVP run uses `OFF`.

When enabled later:

- choose randomized valid approach points from NPC catalog.
- apply dialogue-length delay estimates.
- apply per-Agent profile jitter.
- reserve nearby approach points briefly so Agents do not stack on the same
  coordinate.

Realism is presentation policy. It must not change the objective sequence.

## NPC Quest Interaction

Agents do not need to click through ordinary quest dialogue for MVP. They may
call controlled quest actions after validation.

Required validation:

- Agent exists and is alive.
- Agent is on the correct map.
- NPC exists in current map.
- NPC placement is known or accepted by fallback policy.
- Agent is inside interaction range or configured interaction box.
- quest is allowed by active plan.
- quest state allows start/complete.
- requirements are met.
- forbidden action rules are not violated.

Preferred execution:

```text
Quest.start(agent, npcId)
Quest.complete(agent, npcId)
```

Forbidden in normal runtime:

```text
forceStart
forceComplete
generic arbitrary grant item
```

Debug-only fallback may use force/grant behavior only under explicit debug
policy and must record `debug-resolved`.

## Quest Sequence

Canonical sequence lives in `docs/agents/MAPLE_ISLAND_MVP_SEQUENCE.md`.

Important decisions:

- Start at `10000 Mushroom Town`.
- Do Yoona before Mai.
- Skip old unreachable tutorial quests.
- Skip Todd `1018` and `1035` by default.
- Start Biggs `1046` but leave incomplete.
- Do not complete `1028`.
- Shanks `22000` can complete `1026`, but cannot travel off-island.

## Special Handling

### Roger's Apple `1021`

Flow:

```text
start quest
use item 2010007
complete quest
```

If apple is missing:

1. refresh quest/inventory state.
2. verify quest start reward behavior.
3. block as `missing-scripted-item`.
4. debug-only resolver may grant item `2010007`.

### Pio Reactor Boxes `1008`

Required items:

- `4031161`.
- `4031162`.

Agent must locate reactor boxes, move/hit/activate, loot items, and retry until
requirements are met or timeout is reached.

If reactor capability is not ready, block as:

```text
capability-missing: reactor-interaction
```

Debug-only resolver may grant the two known reactor items.

### Yoona Shopping Guide `8020`

Required item:

- `4031180`.

This is a known special scripted item. MVP may grant it through a controlled
`grant-scripted-item` objective. This is not a generic item grant feature.

### Auto-Complete Quests

Known candidates:

- `1030`.
- `8023`.

If no complete NPC exists and catalog marks auto-complete:

1. validate quest is active.
2. validate requirements.
3. use safe completion adapter if available.
4. otherwise block for review.

### Biggs `1046`

Start only. Completion is forbidden for MVP.

## Combat And Loot

For a required mob:

- target quest mob first.
- stop immediately when live quest kill count is satisfied.
- if target mob is scarce and map spawn is clogged, optionally kill filler mobs
  only when spawn-pressure policy is enabled.
- prefer filler mobs with future quest loot value only after deterministic run
  works.

First run configuration:

```text
spawnPressureClearing = OFF
futureQuestLootPriority = OFF
```

## Recovery And Fallback

Fallbacks are explicit and ordered.

### Low HP

1. Use HP potion if available and allowed.
2. Move to a safer position if possible.
3. Sit on chair if available.
4. Idle/rest until passive recovery reaches safe HP.
5. Resume objective.
6. If repeated near-death occurs, block as `survival-risk-too-high`.

### Low MP

1. Use MP potion if available.
2. Use basic attack if viable.
3. Rest if passive recovery can solve it.
4. Block if objective cannot proceed.

### No Potions And No Mesos

MVP does not require shop resupply. If no potions and no mesos:

1. rest/chair until full HP.
2. lower combat pace.
3. avoid high-risk mobs.
4. attempt objective only when HP is safe.
5. if deaths repeat, block as `insufficient-sustain`.

### Death

1. wait respawn.
2. refresh map, quest, inventory, HP/MP state.
3. recover HP.
4. navigate back to objective if allowed.
5. if deaths exceed threshold, block as `death-loop`.

### Stuck Navigation

1. recompute route.
2. try alternate portal/path.
3. try alternate approach point.
4. reset movement state.
5. block as `navigation-stuck`.

### Missing NPC

1. refresh live map life list.
2. verify correct map.
3. try alternate placement if catalog has one.
4. block as `missing-npc`.

### No Mob / Low Target Spawn

1. wait short cooldown.
2. search map.
3. kill filler mobs only if policy enabled.
4. block as `target-mob-unavailable`.

### Missing Loot

1. verify quest is active.
2. verify item source.
3. continue until timeout.
4. try alternate source if catalog has one.
5. block as `required-item-unavailable`.

### Full Inventory

1. never drop protected quest/future-quest items.
2. drop/vendor only if safe trash policy is implemented.
3. MVP default blocks as `inventory-full`.

## Debug-Only Resolver Policy

Debug resolvers are development tools, not normal Agent behavior.

Allowed debug resolvers:

- grant Roger apple `2010007` for `1021`.
- grant Yoona guide `4031180` for `8020`.
- grant Pio reactor items `4031161`, `4031162` for `1008`.
- debug-complete known no-complete-NPC quests only.

Never debug-resolve:

- Shanks off-island travel.
- completion of `1028`.
- completion of `1046`.
- arbitrary items.
- arbitrary mesos.
- whole-plan completion.

Every debug resolver must record objective id, quest/item id, reason, retry
history, config flag, and timestamp.

## Plan Journal

Every objective records:

- start/end timestamp.
- map id.
- NPC/mob/item/quest id.
- capability used.
- status.
- retries.
- fallbacks used.
- blockers.
- debug resolver usage.

## First Run Configuration

```yaml
agents:
  mapleIslandMvp:
    interactionRealism: OFF
    spawnPressureClearing: false
    futureQuestLootPriority: false
    allowDebugResolvers: false
    allowShopResupply: false
    maxObjectiveRetries: 3
    maxDeathsPerObjective: 3
```

