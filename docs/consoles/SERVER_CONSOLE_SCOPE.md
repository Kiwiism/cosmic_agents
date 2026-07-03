# Server Console Scope

Purpose:

```text
Define the Server Console as the web management surface for runtime policy,
server settings, feature gates, rates, timers, behavior overrides, and
server-owned metadata overrides.
```

Server Console is separate from Database Console:

- Database Console owns concrete DB/content rows: characters, inventory, drops,
  shop rows, reward rows, Maker DB rows, and audit of those edits.
- Server Console owns behavior and policy: YAML-equivalent settings, runtime
  toggles, rates, map/spawn policy, command access, server JS/XML override
  activation, cache/reload control, and operational health.

`config.yaml` remains the fallback when Server Console is not installed.

## Top-Level Areas

Recommended left navigation:

```text
Overview
Analytics
Runtime & Infrastructure
Access & Security
Rates & Economy Pressure
World Behavior
Gameplay Systems
Commands
Technical Override Layers
Publish & Audit
```

Detailed pages can still keep legacy labels as aliases during implementation
(`Maps & Spawns`, `NPCs & Scripts`, `Commands & GM`, and similar), but the
left navigation should settle on the grouped model above so users do not have
to learn two different structures.

## Global UI Shell

Server Console should use one shell across all pages:

```text
┌────────────────────────────────────────────────────────────────────────────┐
│ Back Forward | Global Search / Command Palette | Env | Publish State       │
├──────────────┬─────────────────────────────────────────────┬───────────────┤
│ Left Nav     │ Page Header / Tabs / Filters / Main Editor  │ Right Dock    │
│              │                                             │               │
│              │                                             │ Details,      │
│              │                                             │ relationships,│
│              │                                             │ navigation    │
├──────────────┴─────────────────────────────────────────────┴───────────────┤
│ Publish Bar: unapplied changes | validation | preview diff | publish       │
└────────────────────────────────────────────────────────────────────────────┘
```

Shell rules:

- left nav is stable and only changes selected section.
- top bar always has back/forward history, global search, environment, and
  publish state.
- main workspace owns editing.
- right dock owns explanation, relationships, technical details, and navigation.
- bottom Publish Bar owns unapplied changes, validation, diff, publish,
  reload/restart status, and rollback entry points.

## Page Patterns

Use the same page patterns throughout the console.

Compact settings grid:

```text
Used for flat server settings, feature gates, rates, timers, and policy flags.
Cards show the current toggle/value at a glance. Selecting a card fills the
right dock with details and exposes advanced controls in the main workspace.
```

Entity catalog:

```text
Used for maps, mobs, bosses, NPCs, commands, events, PQs, transport routes,
and override records. Catalog pages support search, filter chips, list/grid
views where useful, badges, pagination, and right-dock preview.
```

Entity workspace:

```text
Used after opening one map, mob, NPC, command, PQ, or override record. The
workspace uses tabs for editable sections. Selecting any row inside the
workspace updates the right dock.
```

Diagnostic search:

```text
Used for cross-map portal search, spawn search, hook status, validation issues,
and audit search. These pages are for finding and explaining records; editing
opens the owning workspace.
```

Analytics page:

```text
Used for read-only metrics. Selecting a chart point, table row, or anomaly
updates the right dock with explanation and links to related settings.
```

Dangerous operation panel:

```text
Used for reloads, cache clears, heap dump, save all, shutdown, and similar
runtime actions. These require confirmation and write an audit event.
```

## Right Dock System

The right dock is the console's main explanation and navigation surface. It
should never be an afterthought or a duplicate of the editor.

Selection behavior:

- single click selects a card, row, chart point, map element, setting, warning,
  or audit event and updates the dock.
- double click or `Open` navigates to the owning workspace.
- dock links navigate to related pages without losing browser history.
- users can pin the dock to compare one selected entity while browsing another.
- every dock section is collapsible; sections default expanded except
  Technical Details, which defaults collapsed.
- ids, names, file paths, config paths, and source keys should be copyable.

Common dock layout:

```text
Right Dock
├─ Summary
│  ├─ name, id, icon/type, status badges
│  └─ one-sentence purpose or effect
├─ Effective Runtime Value
│  ├─ what the server is using now
│  └─ live/reload/restart status
├─ Original / Fallback Value
│  ├─ config.yaml, hardcoded default, WZ/XML/script default
│  └─ original source path when available
├─ Console Override
│  ├─ stored override value
│  ├─ pending unpublished edits
│  └─ validation state
├─ Source Chain
│  └─ console override -> YAML -> hardcoded -> WZ/XML/script
├─ Impact
│  ├─ affected players, agents, maps, mobs, NPCs, commands, or systems
│  └─ reload/restart/runtime risk
├─ Relationships / Navigation
│  └─ related pages and owning workspaces
├─ Warnings
│  └─ invalid, duplicate, risky, hook missing, or likely unintended behavior
├─ History / Audit
│  └─ last changes, publisher, publish batch, rollback link
└─ Technical Details
   ├─ hook/provider class
   ├─ config path/key
   ├─ cache/index version
   ├─ source file/url
   └─ database table/key if Server Console stores the override
```

Dock modes:

- Setting: shows value, source chain, description, validation, audit, and
  reload/restart behavior.
- Override: shows original/effective/override values side by side.
- Map: links to portals, spawns, NPCs, mobs, boss timers, metadata, and audit.
- Portal: links to source map, original target, effective target, restrictions,
  and block message.
- Spawnpoint: links to owning map, mob stat override, permanent spawn status,
  and related drop data.
- Mob/Boss: links to maps containing the mob, stat overrides, area boss timer,
  Database Console drop table, and quest usage.
- NPC: links to maps where NPC appears, script path, travel override, shops,
  rewards, and dialogue/catalog metadata when available.
- Event/PQ: links to enablement, level/party requirements, timers, rewards in
  Database Console, scripts, and audit.
- Command: links to command class, aliases, minimum GM level, dangerous-command
  rules, and command audit.
- Analytics Metric: explains metric source, aggregation window, query freshness,
  and links to related tuning pages.
- Publish Change: shows before/after diff, validation warnings, reload/restart
  requirement, and owning setting/workspace.
- Validation Issue: explains the issue, affected runtime behavior, and direct
  navigation to the broken setting.
- Audit Event: shows actor, timestamp, batch, changed paths, before/after, and
  rollback entry point.

Right dock navigation examples:

- selecting a map links to its portal overrides, spawn overrides, NPCs,
  contained mobs, area boss timers, entry message, and map audit.
- selecting a portal links to the source map workspace and target map workspace.
- selecting a spawnpoint links to the mob/boss stat page, map spawn tab, and
  Database Console drop table view.
- selecting an NPC links to all maps containing it, NPC travel override, script,
  and Database Console shop/reward pages when applicable.
- selecting an analytics row such as "highest meso generation map" links to map
  EXP/drop/spawn tuning and the audit trail for those settings.
- selecting a publish diff row links back to the edited setting and shows
  whether the change is live, reloadable, or restart-required.

## Consistency Rules

- Every editable field has a clear description, effective value, fallback
  source, validation state, and audit trail.
- Every game reference displays id plus name, and icon where applicable.
- Every catalog has search, additive filter chips, status badges, and top/bottom
  pagination when the result can be long.
- Every settings grid card shows the basic value in compact form, including
  stored value when disabled if toggling it on would re-use that value.
- Every high-impact change declares whether it applies live, needs reload, or
  needs restart.
- Cross-search pages do not own edits. They navigate to the owning workspace.
- Original values should always remain visible for override pages, especially
  maps, portals, mobs, NPC travel, event/PQ requirements, and command rules.
- Use consistent labels: `Fallback YAML`, `Console Override`, `Effective`,
  `Original`, `Unapplied`, `Pending Reload`, `Pending Restart`, and
  `Runtime Active`.
- Warnings should be shown before publish for duplicate entries, missing ids,
  illegal values, disabled hooks, event-map accidents, impossible portal targets,
  restart-required changes, and overrides that shadow a global default.

## Overview

Purpose:

```text
Show current runtime state and whether active server behavior matches the
published Server Console configuration.
```

Manage/show:

- server online/offline state.
- worlds/channels online.
- player count.
- agent count if agent bridge exposes it later.
- uptime.
- current config revision.
- unapplied config changes.
- pending restart-required changes.
- runtime-active overrides.
- recent reloads.
- warnings from invalid config or missing hooks.

## Analytics

Purpose:

```text
Show what the live server is doing without mixing monitoring screens into
configuration pages.
```

Analytics pages should be mostly read-only. They can link to Server Console
configuration pages when the data suggests a tuning problem.

Recommended pages:

```text
Analytics
├─ Server Health
├─ Population
├─ Server Demographics
├─ Progression
├─ Combat
├─ Map Health
├─ Quest Health
├─ Economy
├─ Item Economy
├─ Item Lifecycle
├─ World Activity
├─ Boss / Event Activity
├─ Social
├─ Agent Activity
├─ Abuse / Anomaly
└─ Fun Stats
```

Recommended analytics shell:

```text
+--------------------------------------------------------------------------------+
| Analytics > Page Name                                                           |
| Time Range: [1h] [24h] [7d] [30d] [Custom] | World | Channel | Player/Agent     |
+--------------------------------------------------------------------------------+
| KPI Row: main counters, deltas, warnings                                        |
+--------------------------------------------------------------------------------+
| Chart Area: primary trend / distribution / heatmap                              |
+------------------------------------------------------------+-------------------+
| Ranked Tables / Breakdowns / Drilldowns                    | Right Detail Dock |
+------------------------------------------------------------+-------------------+
| Export | Save View | Open Related Config | Open Audit                           |
+--------------------------------------------------------------------------------+
```

Analytics UI rules:

- every page has a time range selector.
- every page can filter by world/channel where applicable.
- every page can split player, agent, or combined activity where applicable.
- charts are read-only and drill into ranked tables.
- clicking a row opens the right dock with explanation, related entities, and
  links to relevant config pages.
- right dock technical section shows metric source, aggregation window, and last
  refresh.

Common chart types:

- line chart: trend over time.
- stacked area chart: source/sink composition over time.
- bar chart: ranked counts.
- histogram: level/wealth/session distributions.
- heatmap: map/time/channel intensity.
- Sankey or flow diagram: meso/item/source/sink flow, optional later.
- scatter plot: outlier/anomaly review.
- table with sparklines: compact ranked metric lists.

### Server Health

Live server/runtime metrics:

- online/offline.
- uptime.
- worlds/channels online.
- online player count.
- online agent count if agent bridge exposes it.
- peak online today.
- login attempts and login failures.
- current sessions.
- JVM heap used/max.
- non-heap memory.
- GC count/time.
- thread count.
- TimerManager queue/activity.
- DB pool active/idle/waiting.
- loaded maps.
- active event instances.
- save queue or save duration if instrumented.
- packet rate in/out if instrumented.

Useful alerts:

- DB pool saturation.
- memory growth.
- high GC time.
- failed saves.
- stuck event instances.
- channel overload.
- excessive map item count.

Recommended layout:

```text
KPI:
  Uptime | Online Players | Online Agents | Heap Used | DB Pool | Warnings

Charts:
  Memory over time: line chart
  DB pool active/idle/waiting: stacked area
  GC pause/time: line chart
  Packet rate: line chart if instrumented

Tables:
  Channels by load
  Slow saves / failed saves
  Recent server warnings
```

### Population

Live distribution:

- online players by world/channel.
- online agents by world/channel.
- players by current map.
- agents by current map.
- top active maps.
- town population.
- party count.
- guild online count.
- AFK/idle count if tracked.
- player vs agent split.
- login/logout rate.
- channel migration rate.

This page answers:

```text
Where is everyone right now?
```

Recommended layout:

```text
KPI:
  Online Players | Online Agents | Peak Today | Parties | Guilds Online

Charts:
  Online by world/channel: stacked bar
  Online trend: line chart
  Map population heatmap: heatmap/table
  Player vs agent split: donut or stacked bar

Tables:
  Top maps by population
  Town population
  Idle/AFK distribution if tracked
```

### Server Demographics

Longer-term character/account distribution.

Core demographics:

- total accounts.
- total characters.
- active accounts today / 7 days / 30 days.
- active characters today / 7 days / 30 days.
- highest level character.
- average level.
- median level.
- level percentiles: p50, p75, p90, p95, p99.
- character count by level range.
- character count by job.
- character count by job advancement.
- character count by world.
- character count by town/current region.
- player characters vs agent characters.
- GM/admin characters.
- islander characters if tagged/profiled later.

Recommended level buckets:

```text
1-7
8-10
11-20
21-30
31-50
51-70
71-120
121-160
161-200
```

Job distribution:

- beginner.
- warrior / fighter / page / spearman / later advancements.
- magician / FP / IL / cleric / later advancements.
- bowman / hunter / crossbowman / later advancements.
- thief / assassin / bandit / later advancements.
- pirate / brawler / gunslinger / later advancements.
- cygnus/aran/other jobs if enabled.

Progression metrics:

- levels gained per hour/day.
- average time to level 10 / 30 / 70 / 120.
- job advancement completion rates.
- quest completion counts by level range.
- deaths by level range.
- meso by level range.
- wealth by job.
- equipment power distribution, later if indexed.

Useful charts:

- level histogram.
- job pie/bar chart.
- world/channel distribution.
- active vs inactive accounts.
- new character creation over time.
- level progression over time.

Recommended layout:

```text
KPI:
  Accounts | Characters | Active 7d | Highest Level | Median Level

Charts:
  Level distribution: histogram
  Job distribution: stacked bar
  Active accounts over time: line chart
  Character creation over time: line chart
  Player vs agent demographics: stacked bar

Tables:
  Highest level characters
  Level buckets
  Job advancement counts
  Wealth by job/level bucket
```

This page answers:

```text
What kind of server population do we have?
```

### Progression

Progression metrics:

- level-up speed by job.
- level-up speed by map.
- level-up speed by player vs agent.
- EXP source split: mob, quest, PQ, event.
- average time to level 10 / 30 / 70 / 120.
- job advancement completion rates.
- class population trend over time.
- underrepresented jobs.
- overrepresented jobs.
- players delayed on job advancement.
- quest completion contribution to leveling.

Recommended layout:

```text
KPI:
  Level-ups Today | Avg Time to Lv30 | Most Used Training Map | Slowest Job

Charts:
  Level progression funnel: funnel chart
  Time-to-level by job: box plot or bar
  EXP source split: stacked area
  Class trend over time: line chart

Tables:
  Top leveling maps by bracket
  Slow progression outliers
  Job advancement delays
```

### Combat

Combat metrics:

- mob kills by map.
- mob kills by job.
- boss attempts vs clears.
- average boss clear time.
- player deaths by mob/map.
- damage taken by map.
- most lethal maps.
- most lethal mobs.
- potion consumption by map.
- miss rate by level/job/map.
- average damage output by job/level bracket.
- abnormal damage spikes.

Recommended layout:

```text
KPI:
  Kills/hour | Deaths/hour | Potions/hour | Boss Clears | Damage Spike Alerts

Charts:
  Kills over time: line chart
  Deaths by map/mob: bar chart
  Damage by job/level bracket: box plot
  Miss rate by level gap: line chart

Tables:
  Most lethal maps
  Most lethal mobs
  Boss attempts/clears
  Abnormal damage events
```

### Map Health

Map health metrics:

- maps with high traffic but low mob availability.
- maps with many ground drops.
- maps with many deaths.
- maps with high portal usage.
- maps with low/zero usage.
- maps where agents cluster too heavily.
- maps where spawn rates may be too low/high.
- maps with long-lived area bosses.
- maps with unusually high meso generation.

Recommended layout:

```text
KPI:
  Active Maps | Overcrowded Maps | Dead Maps | Drop Limit Warnings

Charts:
  Map activity heatmap: heatmap
  Spawn pressure by map: bar chart
  Portal flow: Sankey or ranked table
  Drops on ground over time: line chart

Tables:
  Overcrowded maps
  Underused maps
  Spawn-starved maps
  Maps with abnormal meso/EXP generation
```

### Quest Health

Quest metrics:

- most started quests.
- most completed quests.
- most forfeited quests.
- quests with high abandonment.
- quests with missing required item sources.
- quests where players stay blocked too long.
- quests completed by agents vs players.
- quest reward value injected into economy.
- dead-end quest detection.

Recommended layout:

```text
KPI:
  Quest Starts | Quest Completes | Abandonment Rate | Blocked Quest Alerts

Charts:
  Quest funnel: started -> progressed -> completed
  Abandonment by quest: bar chart
  Quest completion by level bracket: stacked bar
  Player vs agent quest completion: stacked bar

Tables:
  High-abandonment quests
  Blocked quests
  Missing-source requirements
  Reward injection by quest
```

### Economy

Meso and market-wide metrics:

- total meso in character inventories.
- total meso in storage.
- total meso in merchants.
- total meso held by players.
- total meso held by agents.
- total meso by world.
- total meso by level range.
- total meso by job.
- richest players.
- richest agents.
- wealth percentiles.
- top 1%, 5%, 10% wealth share.
- meso generated per minute/hour/day.
- meso spent per minute/hour/day.
- meso generated by source: mob, quest, event/PQ, NPC/script, GM command.
- meso spent by sink: NPC shop, travel, storage, Maker/craft, guild, wedding,
  tax.
- tax collected.
- trade volume.
- player-to-player, player-to-agent, agent-to-agent trade split.
- inflation/deflation indicators.

Useful alerts:

- meso generation spike.
- tax collection drop.
- abnormal wealth growth.
- suspected market manipulation.
- high agent wealth concentration.

Recommended layout:

```text
KPI:
  Total Meso | Meso/hour | Sinks/hour | Tax Collected | Inflation Index

Charts:
  Meso supply over time: line chart
  Sources vs sinks: stacked area
  Wealth distribution: histogram / Lorenz curve later
  Tax by source: stacked bar

Tables:
  Richest players
  Richest agents
  Top meso sources
  Top meso sinks
  Suspicious wealth growth
```

### Item Economy

Item-side metrics:

- item generated count.
- item destroyed/sold count.
- item traded count.
- item held count.
- top generated drops.
- top destroyed items.
- top traded items.
- rare item movement.
- scroll pass/fail statistics.
- equip stat distribution.
- price history by item.
- listing duration before sale.
- unsold listings.
- item source breakdown: drops, shops, quests, events, Maker, GM/admin.

Recommended layout:

```text
KPI:
  Items Created | Items Destroyed | Trade Volume | Rare Item Alerts

Charts:
  Item creation/destruction trend: line chart
  Top traded items: bar chart
  Price history: line chart
  Item liquidity: scatter plot

Tables:
  Top generated items
  Top destroyed/sold items
  Top traded items
  Rare item holders
```

### Item Lifecycle

Lifecycle metrics:

- item created by source.
- item destroyed by source.
- item traded count.
- item equipped count.
- item stored count.
- item dropped and expired count.
- scroll pass/fail by scroll.
- average equip stat distribution.
- high-value equip creation.
- godly-stat item creation if enabled.
- one-of-a-kind conflicts prevented.

Recommended layout:

```text
KPI:
  Created | Destroyed | Expired Drops | Scroll Success Rate | High-value Equips

Charts:
  Item lifecycle flow: Sankey later, stacked bars MVP
  Scroll pass/fail: stacked bar
  Equip stat distribution: histogram
  Item age before destruction/sale: histogram

Tables:
  High-value equip creations
  Most expired drops
  Scroll results by scroll id
  One-of-a-kind conflict warnings
```

### World Activity

Map and gameplay activity:

- mob kills per map.
- EXP generated per map.
- meso generated per map.
- drops generated per map.
- deaths per map.
- NPC interactions per map.
- portal usage.
- travel route usage.
- active grind maps.
- underused maps.
- maps with too many agents.
- maps with too many items.

Recommended layout:

```text
KPI:
  Mob Kills | EXP Generated | Meso Generated | Portal Uses | NPC Interactions

Charts:
  Activity by map: heatmap
  Portal usage flow: Sankey later, ranked table MVP
  EXP/meso by region: stacked bar
  NPC interactions over time: line chart

Tables:
  Top active maps
  Top travel routes
  Top NPC interactions
  Underused regions
```

### Boss / Event Activity

Boss, PQ, and event monitoring:

- boss spawn history.
- boss kill history.
- area boss uptime.
- area boss timer overrides active.
- expedition attempts.
- expedition clears/fails.
- PQ runs.
- PQ completion rate.
- event instance count.
- event participation.
- reward volume by event/PQ.

Recommended layout:

```text
KPI:
  Boss Spawns | Boss Kills | PQ Runs | Event Instances | Failed Runs

Charts:
  Boss uptime: timeline
  Boss attempts vs clears: stacked bar
  PQ completion rate: line/bar
  Event participation: line chart

Tables:
  Boss kill history
  Area boss timers
  PQ failure points
  Event reward volume
```

### Social

Social metrics:

- party creation count.
- party duration.
- party size distribution.
- guild growth.
- guild activity.
- buddy additions.
- fame changes.
- trade network graph.
- repeated trade partners.
- chat volume by channel/map if chat logging enabled.
- player-agent interactions later.

Recommended layout:

```text
KPI:
  Parties Created | Avg Party Duration | Guild Activity | Trades | Fame Changes

Charts:
  Party size distribution: histogram
  Guild activity: bar chart
  Trade network: graph later, ranked pairs MVP
  Chat volume: heatmap if enabled

Tables:
  Most active guilds
  Repeated trade partners
  Party activity by map
  Fame changes
```

### Agent Activity

Read-only server-side view of agent activity. Detailed control belongs to Agent
Console.

- online agents.
- agents by world/channel/map.
- agents by level range.
- agents by job.
- agent deaths.
- agent mob kills.
- agent EXP generated.
- agent meso generated/spent.
- agent tax paid.
- agent trade volume.
- agent map concentration.
- agents with abnormal stuck/death/wealth patterns.

Recommended layout:

```text
KPI:
  Online Agents | Simulated Agents | Agent Kills/hour | Agent Meso/hour | Stuck Alerts

Charts:
  Agents by map: heatmap
  Agent state distribution: stacked bar
  Agent progression funnel: funnel chart
  Agent economy impact: stacked area

Tables:
  Agent map clusters
  Stuck/death outliers
  Agent wealth growth
  Agent objective completion
```

### Abuse / Anomaly

Anomaly metrics:

- impossible meso growth.
- abnormal EXP gain.
- abnormal item creation.
- too many rare drops.
- repeated self-trades.
- mule-like wealth movement.
- high-frequency NPC shop abuse.
- command usage audit.
- GM item/meso creation.
- suspicious login locations/IPs.
- duplicate unique item ids.

Recommended layout:

```text
KPI:
  Open Alerts | High Severity | Suspicious Trades | GM Actions | Duplicate IDs

Charts:
  Alert volume over time: line chart
  Outlier scatter: wealth gain vs playtime
  Trade anomaly graph: graph later, table MVP

Tables:
  Alert queue
  Suspicious wealth movement
  GM command/item/meso actions
  Duplicate unique item warnings
```

### Fun Stats

Community-facing or admin-interest metrics:

- most killed mob today.
- most dangerous mob today.
- richest town.
- busiest map.
- most traveled portal.
- most popular job this week.
- rarest job.
- first level 30 / 70 / 120 / 200.
- biggest trade today.
- most expensive item sold.
- longest session.
- most deaths today.

Recommended layout:

```text
KPI:
  Busiest Map | Most Killed Mob | Biggest Trade | Longest Session

Charts:
  Daily highlights: cards
  Popular jobs: bar chart
  Busiest maps: bar chart
  Biggest trades: ranked table

Tables:
  Daily records
  Weekly records
  All-time records
```

Instrumentation recommendation:

```text
Server action
-> emit lightweight metric/event
-> update in-memory rolling counters
-> periodically aggregate to Server Console DB
```

Events to instrument:

- login/logout.
- character level up.
- job advancement.
- mob killed.
- EXP gained.
- meso gained.
- meso spent.
- tax collected.
- item generated.
- item destroyed.
- item traded.
- NPC shop purchase.
- travel fee.
- quest reward.
- event/PQ reward.
- boss spawned/killed.

## Worlds & Channels

Server-owned settings:

- world count.
- world names/flags/messages.
- channel count.
- max channels.
- max players per channel.
- channel load/capacity.
- channel locks.
- add/remove world.
- add/remove channel.
- world recommendation text.
- server message.
- event message.

Examples from current config/code:

- `WORLDS`.
- `WLDLIST_SIZE`.
- `CHANNEL_SIZE`.
- `CHANNEL_LOAD`.
- `CHANNEL_LOCKS`.
- per-world `flag`, `server_message`, `event_message`,
  `why_am_i_recommended`, `channels`.

Runtime notes:

- Some world/channel changes may require restart.
- If a GM command already exists, Server Console should call a server API rather
  than duplicating logic.

## Rates

Server-owned settings:

- EXP rate.
- meso rate.
- drop rate.
- boss drop rate.
- quest rate.
- fishing rate.
- travel rate.
- equip EXP rate.
- PQ bonus EXP rate.
- party bonus EXP rate.
- EXP split/leech interval.
- level-based rate multipliers.
- rate coupon behavior.

Examples:

- per-world `exp_rate`, `meso_rate`, `drop_rate`, `boss_drop_rate`,
  `quest_rate`, `fishing_rate`, `travel_rate`.
- `EQUIP_EXP_RATE`.
- `PQ_BONUS_EXP_RATE`.
- `PARTY_BONUS_EXP_RATE`.
- `EXP_SPLIT_LEVEL_INTERVAL`.
- `EXP_SPLIT_LEECH_INTERVAL`.
- `USE_SUPPLY_RATE_COUPONS`.
- `USE_STACK_COUPON_RATES`.
- `USE_ADD_RATES_BY_LEVEL`.

Database Console boundary:

- Reward/drop item rows remain Database Console.
- Effective rate multipliers and rate policies belong here.

## Login & Security

Server-owned settings:

- database connection config display/editing.
- PIC/PIN enablement.
- PIC/PIN bypass expiration.
- automatic registration.
- bcrypt migration.
- multiclient policy.
- account HWID limits.
- login-attempt lockout.
- IP validation.
- GM/admin account enforcement.
- timeout duration.
- Tailscale/WAN/LAN host config.

Examples:

- `ENABLE_PIC`.
- `ENABLE_PIN`.
- `AUTOMATIC_REGISTER`.
- `BCRYPT_MIGRATION`.
- `DETERRED_MULTICLIENT`.
- `MAX_ALLOWED_ACCOUNT_HWID`.
- `MAX_ACCOUNT_LOGIN_ATTEMPT`.
- `LOGIN_ATTEMPT_DURATION`.
- `USE_IP_VALIDATION`.
- `USE_ENFORCE_ADMIN_ACCOUNT`.
- `TIMEOUT_DURATION`.
- `HOST`, `LANHOST`, `LOCALHOST`, `TAILSCALEHOST`, `GMSERVER`.

Safety:

- DB credentials and host settings should be restart-required unless a safe
  reload path exists.

## Gameplay Rules

Server-owned settings:

- CPQ/MTS/family/Duey/fishing/map ownership feature gates.
- starter AP behavior.
- HP/MP randomization.
- novice EXP enforcement.
- job-level/SP enforcement.
- mob-level EXP range enforcement.
- merchant save behavior.
- item sort/storage sort behavior.
- chat logging.
- max HP/MP/stat caps if made configurable.
- item expiration policy toggle.

Examples:

- `USE_MTS`.
- `USE_CPQ`.
- `USE_FAMILY_SYSTEM`.
- `USE_DUEY`.
- `USE_FISHING_SYSTEM`.
- `USE_MAP_OWNERSHIP_SYSTEM`.
- `USE_PARTY_FOR_STARTERS`.
- `USE_AUTOASSIGN_STARTERS_AP`.
- `USE_AUTOASSIGN_SECONDARY_CAP`.
- `USE_RANDOMIZE_HPMP_GAIN`.
- `USE_ENFORCE_NOVICE_EXPRATE`.
- `USE_ENFORCE_MOB_LEVEL_RANGE`.
- `USE_ENFORCE_JOB_LEVEL_RANGE`.
- `USE_ENFORCE_JOB_SP_RANGE`.
- `USE_AUTOSAVE`.
- `USE_ENABLE_CHAT_LOG`.

## Maps & Spawns

Server-owned settings:

- global respawn interval.
- full respawn policy.
- world default mob spawn multiplier.
- world default max mobs per spawnpoint.
- per-map spawn multiplier override.
- per-map max mobs per spawnpoint override.
- per-map EXP rate override.
- per-map spawn enable/disable.
- map metadata overrides.
- portal policy and portal enable/disable.
- taxi/ferry/transport rules.
- map damage-over-time policy.
- item limit per map.
- map item expiration checks.
- hidden/player controller behavior if exposed as policy.

Examples:

- `RESPAWN_INTERVAL`.
- `USE_ENABLE_FULL_RESPAWN`.
- per-world `mob_rate`.
- per-world `max_mob_per_spawnpoint`.
- `MAP_DAMAGE_OVERTIME_INTERVAL`.
- `MAP_DAMAGE_OVERTIME_COUNT`.
- `ITEM_LIMIT_ON_MAP`.
- `ITEM_EXPIRE_CHECK`.
- `MAP_VISITED_SIZE`.

Recommended map-spawn override model:

```text
global default
-> world default
-> map override
-> spawnpoint override, optional later
```

Recommended map override UI:

```text
Server Console
-> World Behavior
-> Maps
```

Catalog page:

```text
Map Catalog

Search map id/name/region...
[+ Add Filter] [x] Overridden Only

| Map        | Region      | Overrides                         | Mobs | Portals | Status |
| 100000000  | Henesys     | [Portal] [Spawn Rate] [Map EXP]   | 6    | 8       | Active |
| 104000000  | Lith Harbor | [Portal]                          | 2    | 7       | Active |
| 105040300  | Sleepywood  | none                              | 0    | 5       | Global |
```

Catalog filters:

- map id/name.
- region/street.
- has any map override.
- map override enabled.
- has portal override.
- has spawn-rate override.
- has spawn-count override.
- has map EXP override.
- has boss/area boss timer override.
- has NPC travel override in map.
- contains mob id/name.
- contains NPC id/name.
- has disabled portals.

Map workspace:

```text
Map: 100000000 - Henesys

Override: [On/Off]

Tabs:
  Overview
  Portals
  Spawns
  EXP
  Entry Message
  Spawn Status
  NPCs
  Bosses
  Audit
```

Map-level override behavior:

```text
Global override applies to every map whose individual map override is off.
When map override is on, enabled sections on that map replace or patch the
global defaults for that section.
```

Section toggles:

- map override master toggle.
- portal override toggle.
- mob spawn rate override toggle.
- mob spawn count override toggle.
- map EXP rate override toggle.
- map mob HP multiplier override toggle.
- entry message override toggle.
- mob spawn status override toggle.
- map metadata override toggle, later.

Portal tab:

```text
| Portal | Type | Position   | Original Target        | Effective Target       | Status |
| east00 | 2    | 1180, 210  | 100020000 / west00     | 105040300 / sp         | Override |
| sp     | 0    | 320, 240   | 100000000 / sp         | Original               | Original |
```

Portal row should show:

- portal id/name.
- portal type.
- portal position.
- original target map and portal.
- effective target map and portal.
- enabled/disabled.
- override action: warp, block, original.
- restriction badges.
- block message if disabled.

Portal override behavior:

- `warp`: send player to override target map/portal.
- `block`: prevent travel and show configured block message.
- `original`: use original WZ/script portal behavior.

Blocked portal message:

```text
Each blocked portal override can define the message shown to the player.
If no message is configured, use a server default.
```

Spawn tab:

```text
Map Spawn Override: [On]
Mob Rate Override: [On]  1.40
Max Mobs Per Spawnpoint Override: [On]  3
Mob HP Multiplier Override: [On]  1.50

| Spawn | Mob Icon | Mob ID / Name       | Position    | Original Count | Effective Count | Status |
| 1     | [icon]   | 100100 - Snail      | -420, 215   | 1              | 2               | Override |
| 2     | [icon]   | 100101 - Blue Snail | -120, 215   | 1              | 1               | Original |
```

Spawn row should show:

- spawnpoint id/index.
- mob icon.
- mob id and name.
- x/y position.
- original mob count.
- effective mob count.
- original spawn rate.
- effective spawn rate.
- original HP.
- effective HP on spawn.
- area boss marker if applicable.
- quest mob marker if applicable.

Example override:

Example override:

```yaml
spawn_overrides:
  defaults:
    mob_rate: 1.0
    max_mob_per_spawnpoint: 2
  maps:
    100020000:
      mob_rate: 1.4
      max_mob_per_spawnpoint: 3
    104040000:
      mob_rate: 0.8
```

Recommended map EXP override model:

```yaml
map_exp_overrides:
  enabled: true
  default_multiplier: 1.0
  maps:
    100000000:
      enabled: true
      multiplier: 1.25
      indicator:
        mode: message
        enter_message: "Henesys has a 25% EXP field bonus."
    105040300:
      enabled: true
      multiplier: 1.5
```

Map EXP behavior:

- applied when the player gains monster EXP while currently in the map.
- should be displayed as effective EXP preview in combat/debug tooling.
- should stack order be explicit with world EXP, coupon EXP, party EXP, and
  quest EXP.
- should not affect quest EXP unless explicitly configured.
- map EXP multiplier stacks with the global/world EXP multiplier.

Recommended stacking order:

```text
base monster EXP
-> world EXP rate
-> map EXP multiplier
-> coupon/player modifiers
-> party/leech rules
```

UI note:

```text
Map EXP multiplier stacks with global/world EXP. Example: world EXP 2x and map
EXP 1.5x means monster EXP starts from 3x before coupon/player/party modifiers.
```

Entry message tab:

```text
Entry Message Override: [Off]

Mode:
  none
  server message
  popup/message box, later if safe

Message:
  "This field has boosted monster EXP and stronger monsters."
```

Default:

```text
No message is shown unless the map entry message override is enabled.
```

Indicator options:

- `message`: show a server message when entering the map.
- `map_effect`: play an existing map/effect if suitable.
- `existing_buff_icon`: use an existing skill/item buff icon only if it does
  not alter stats or confuse client behavior.
- `none`: no visible indicator.

Client limitation:

```text
A brand-new custom buff icon requires client edits. Without client edits, the
safe MVP is an enter/exit message or an existing effect. Applying a real buff
purely as an indicator is risky unless the buff has no unwanted gameplay stats.
```

### Map Mob Spawn Status Overrides

Purpose:

```text
Apply existing monster status effects to mobs when they spawn in selected maps,
mainly as a visible indicator or temporary map modifier.
```

Recommended page:

```text
Server Console
-> World Behavior
-> Maps
-> Spawns
-> Spawn Status
```

Supported status examples:

- WATK.
- MATK.
- WDEF.
- MDEF.
- ACC.
- AVOID.
- SPEED.
- WEAPON_ATTACK_UP.
- MAGIC_ATTACK_UP.
- WEAPON_DEFENSE_UP.
- MAGIC_DEFENSE_UP.

Do not use `SHOWDOWN` for map EXP indication unless explicitly desired, because
the current EXP calculation treats `SHOWDOWN` as a real EXP multiplier.

Current implementation constraint:

- `Monster.applyStatus(...)` and `Monster.applyMonsterBuff(...)` register
  status cancellation through `MobStatusService`.
- The scheduler stores `currentTime + duration`.
- Extremely large "infinite" durations can overflow and should not be used.

Recommended duration modes:

```text
duration_ms      # normal timed status
permanent_spawn  # server-owned status remains until mob dies/despawns
auto_rebuff      # reapply shortly before/after expiration while mob is alive
```

Recommended MVP:

```text
Use permanent_spawn for map-owned visual/status modifiers.
```

Implementation recommendation:

- Add a server-owned spawn status provider.
- Apply status after monster spawn.
- For `permanent_spawn`, store the status on the monster without registering a
  cancel task.
- Remove naturally when the monster dies/despawns.
- Broadcast the status to players who enter the map after it was applied through
  existing monster temporary-status encoding.

Fallback if avoiding permanent status changes:

- Use `auto_rebuff`.
- Pick a normal duration, for example 60 seconds.
- Reapply every 50-55 seconds while the monster is alive and the map override is
  still active.
- Avoid very short durations because they create excessive status packets.

Example:

```yaml
map_mob_spawn_status_overrides:
  enabled: true
  maps:
    100000000:
      enabled: true
      mode: permanent_spawn
      statuses:
        - status: WDEF
          value: 10
        - status: MDEF
          value: 10
        - status: SPEED
          value: -20
    105040300:
      enabled: true
      mode: auto_rebuff
      duration_ms: 60000
      rebuff_before_expire_ms: 5000
      statuses:
        - status: WEAPON_ATTACK_UP
          value: 20
```

Final per-map override shape:

```yaml
map_overrides:
  enabled: true
  global_spawn_defaults:
    mob_rate: 1.0
    max_mob_per_spawnpoint: 2
  maps:
    100000000:
      enabled: true

      portal_override:
        enabled: true
        portals:
          east00:
            action: warp
            target_map: 105040300
            target_portal: sp
          hidden00:
            action: block
            message: "This portal is closed right now."

      spawn_override:
        enabled: true
        mob_rate_enabled: true
        mob_rate: 1.4
        max_mob_per_spawnpoint_enabled: true
        max_mob_per_spawnpoint: 3

      mob_hp_override:
        enabled: true
        multiplier: 1.5

      map_exp_override:
        enabled: true
        multiplier: 1.25

      entry_message:
        enabled: false
        message: ""

      spawn_status:
        enabled: true
        mode: permanent_spawn
        statuses:
          - status: WDEF
            value: 10
          - status: MDEF
            value: 10
```

Global interaction rules:

- Global spawn rate/count defaults affect only maps whose map override is off.
- Per-map spawn rate/count affect only that map.
- Per-map mob HP multiplier affects only mobs spawned in that map.
- Per-map EXP multiplier affects only monster EXP earned in that map.
- Per-map EXP multiplier stacks with global/world EXP multiplier.
- Per-map permanent spawn statuses apply only to mobs spawned in that map.
- Entry message is off by default unless explicitly enabled per map.

Validation:

- status is supported by the client.
- value is within safe range.
- status does not secretly alter balance when intended only as visual.
- boss mobs are explicitly allowed before applying status to bosses.
- `SHOWDOWN` is blocked unless EXP multiplier behavior is intended.

Database Console boundary:

- It can show maps, mobs, NPCs, reactors, and portals for context.
- It should not own spawn policy, map behavior, transport rules, or map metadata
  overrides.

## Bosses

Server-owned settings:

- boss drop rate.
- expedition enable/disable.
- solo expedition enable/disable.
- daily expedition limits.
- boss entry reset policy.
- boss HP/stat override.
- boss damage/skill behavior override.
- boss map timer policy.
- area boss spawn timer override.
- reactor-triggered boss refresh time.

Current examples:

- per-world `boss_drop_rate`.
- `USE_ENABLE_SOLO_EXPEDITIONS`.
- `USE_ENABLE_DAILY_EXPEDITIONS`.
- `MOB_REACTOR_REFRESH_TIME`.
- `USE_DEADLY_DOJO`.
- bosslog daily/weekly reset behavior.

### Mob / Boss Stat Overrides

Purpose:

```text
Allow Server Console to override effective monster and boss stats while always
preserving and displaying the original WZ-loaded values for comparison.
```

Recommended page:

```text
Server Console
-> World Behavior
-> Mobs & Bosses
-> Stat Overrides
```

Supported override targets:

- individual mob id.
- boss mob id.
- map id + mob id, optional later.
- mob category/tag, optional later.

Server-side fields that can be overridden:

- level.
- HP / MP.
- EXP.
- physical attack.
- magic attack.
- physical defense.
- magic defense.
- accuracy.
- avoidability.
- speed.
- knockback / pushed value.
- undead flag.
- boss flag.
- friendly / damaged-by-mob flag.
- explosive reward flag.
- public reward / FFA loot flag.
- remove-after timer.
- first attack / auto-aggro-on-spawn flag.
- HP bar tag color/background, for boss HP bar mobs.
- elemental effectiveness.
- mob skill behavior, later if exposed safely.

Current server source fields:

- `maxHP`.
- `maxMP`.
- `exp`.
- `level`.
- `PADamage`.
- `MADamage`.
- `PDDamage`.
- `MDDamage`.
- `acc`.
- `eva`.
- `speed`.
- `pushed`.
- `undead`.
- `boss`.
- `damagedByMob`.
- `explosiveReward`.
- `publicReward`.
- `removeAfter`.
- `firstAttack`.
- `hpTagColor`.
- `hpTagBgcolor`.
- `elemAttr`.

Right dock requirement:

```text
Always show original values and effective values side by side.
```

Right dock sections:

- Summary:
  - mob id/name.
  - mob type: normal, area boss, expedition boss, event mob.
  - runtime status: Original, Override Active, Pending Reload, Hook Missing.
- Original WZ values:
  - values loaded from `wz/Mob.wz/{mobId}.img.xml`.
  - source file path.
- Effective server values:
  - final values after Server Console overrides.
  - highlight changed fields.
- Override rule:
  - override mode: exact value, multiplier, additive delta, or disabled.
  - scope: global mob id, map + mob id, category.
- Relationships:
  - maps where mob appears.
  - drop table link.
  - quest requirements.
  - area boss timer override link if applicable.
- Technical:
  - cache/index version.
  - runtime hook status.
  - reload requirement.

Recommended UI table:

```text
Mob/Boss Stat Overrides

| Mob        | Type      | Original HP | Effective HP | Original EXP | Effective EXP | Aggro | Status |
| 100100     | Normal    | 8           | 20           | 4            | 8             | No    | Active |
| 2220000    | Area Boss | 20,000      | 30,000       | 1,200        | 1,500         | Yes   | Active |
```

Recommended override model:

```yaml
monster_stat_overrides:
  enabled: true
  mobs:
    100100:
      hp:
        mode: exact
        value: 20
      exp:
        mode: multiplier
        value: 2.0
    2220000:
      hp:
        mode: multiplier
        value: 1.5
      exp:
        mode: exact
        value: 1500
      first_attack:
        mode: exact
        value: true
```

Mode types:

- exact: replace original value.
- multiplier: original value multiplied by value.
- additive: original value plus value.
- disabled: explicitly use original value.

Auto-aggro notes:

- Per-mob auto-aggro is represented by the WZ `firstAttack` field and loaded
  into `MonsterStats.firstAttack`.
- The server also has global nearby aggro behavior through
  `USE_AUTOAGGRO_NEARBY`.
- Packet-level control uses the monster controller aggro flag when assigning
  control to a client.

Recommended Server Console controls:

- show original `firstAttack`.
- allow `first_attack` override per mob.
- show global `USE_AUTOAGGRO_NEARBY`.
- keep global nearby aggro under `Gameplay Rules -> Skills & Buffs / Mob Aggro`
  or `World Behavior -> Mobs & Bosses`.

Validation:

- HP/MP/EXP cannot be negative.
- level should stay within accepted client/server range.
- speed should stay within sane movement range.
- boss flag changes should warn because they can affect boss drop rate, HP bar,
  quest behavior, and area boss classification.
- first attack changes should warn because they alter player safety in maps.
- map + mob overrides should warn if the mob appears in event/instance maps.
- HP bar tag changes should warn if the mob has no boss HP bar metadata.
- elemental overrides must use valid element/effectiveness values.

Client IMG requirement:

- Existing mob stat overrides do not require client IMG changes for gameplay.
- Client IMG/String changes are only needed for new mob IDs, visual/name
  changes, sprite changes, or client-facing metadata consistency.

### Area Boss Spawn Timer Override

Purpose:

```text
Allow the server to override respawn timing for field/area bosses separately
from normal mob respawn policy.
```

Recommended ownership:

- Server Console owns the timer policy.
- Database Console can show which mobs are area bosses and where they spawn.

Recommended override levels:

```text
global area boss default
-> mob id override
-> map id + mob id override
```

Example model:

```yaml
area_boss_spawn_timer_overrides:
  enabled: true
  default_ms: 3600000
  mobs:
    2220000:
      timer_ms: 1800000
      jitter_ms: 300000
    3220000:
      timer_ms: 2700000
  maps:
    100020101:
      mobs:
        2220000:
          timer_ms: 1200000
          jitter_ms: 180000
```

Recommended fields:

- enabled.
- mob id.
- map id, optional.
- base timer.
- random jitter.
- min timer.
- max timer.
- spawn-on-server-start yes/no.
- respawn-only-if-map-active yes/no.
- respawn-if-agent-only yes/no, if agent bridge exposes simulation ownership.
- announcement policy.
- kill log/reset policy.

Validation:

- mob id exists.
- mob is boss/area-boss tagged or explicitly allowed.
- map id exists if supplied.
- timer is positive.
- jitter does not make timer negative.
- override does not conflict with event/instance boss behavior.

## NPCs & Scripts

Server-owned settings:

- serverside NPC scriptability enable/disable.
- NPC JS override activation.
- NPC travel override.
- NPC metadata override.
- NPC behavior gates.
- NPC interaction cooldown.
- old-GMS PQ NPC behavior.

Examples:

- `USE_NPCS_SCRIPTABLE`.
- `NPCS_SCRIPTABLE`.
- `BLOCK_NPC_RACE_CONDT`.
- `USE_OLD_GMS_STYLED_PQ_NPCS`.

Database Console boundary:

- NPC rewards/shop rows are Database Console when they are concrete content.
- NPC script override activation, NPC travel behavior, and behavior policy are
  Server Console.

### NPC Travel Override

Purpose:

```text
Allow Server Console to add, remove, or replace NPC travel destinations without
directly editing each NPC JS script.
```

Current server behavior:

- Many travel NPCs are implemented as `scripts/npc/*.js`.
- The script builds a menu with `cm.sendSimple(...)`.
- The script charges mesos with `cm.gainMeso(-cost)`.
- The script moves the player with `cm.warp(mapId, portal)`.

Examples:

- `scripts/npc/1002000.js` is a Victoria Island travel/info NPC.
- `scripts/npc/1012000.js` is a regular cab script.
- `scripts/npc/1002007.js` is another regular cab variant with coupon handling.
- `scripts/npc/1002004.js` is a VIP cab to Ant Tunnel.

Use cases:

- Add Sleepywood to the original Victoria taxi destination list.
- Add Nautilus, Lith Harbor, or other destinations to selected taxi NPCs.
- Turn an otherwise plain/default-talk NPC into a travel NPC.
- Add event-only travel destinations.
- Add GM-only or level-gated travel options.
- Add meso-discount rules for beginners or specific jobs.
- Disable an original destination temporarily.

Recommended ownership:

- Server Console owns route availability, cost, requirements, and behavior.
- Database Console can show NPC location/context and map details.

Recommended override modes:

```text
append    # keep script destinations, add console destinations
replace   # ignore script destinations and use console-defined destinations
disable   # disable travel for this NPC
patch     # patch specific destination cost/requirements/enabled flag
```

Recommended config model:

```yaml
npc_travel_overrides:
  enabled: true
  npcs:
    1012000:
      mode: append
      routes:
        - label: Sleepywood
          target_map: 105040300
          target_portal: 0
          cost: 1200
          beginner_discount_percent: 90
          min_level: 0
          max_level: 255
          enabled: true
    9001105:
      mode: replace
      title: "Where would you like to go?"
      routes:
        - label: Henesys
          target_map: 100000000
          target_portal: 0
          cost: 0
          enabled: true
```

Recommended route fields:

- NPC id.
- mode: append, replace, disable, patch.
- source map constraint, optional.
- target map id.
- target portal id or portal name.
- display label override.
- cost.
- beginner discount.
- job-specific cost overrides.
- min/max level.
- required quest state.
- required item.
- consume item yes/no.
- allowed world/channel, optional.
- enabled.
- start/end timestamp for temporary travel.

Validation:

- NPC id exists.
- target map exists.
- target portal exists, or fallback portal is allowed.
- cost is non-negative.
- level range is valid.
- required item/quest exists.
- replacement does not accidentally hide important quest/event behavior.
- append mode does not duplicate an existing route unless explicitly allowed.

Recommended implementation:

```text
NPC JS script
-> calls server helper, for example cm.openTravelMenu("victoria_taxi")
-> helper resolves original script routes + Server Console overrides
-> helper renders menu
-> helper validates selection, cost, requirements
-> helper charges/consumes item
-> helper warps player
```

For NPCs that originally do nothing:

```text
NPCScriptManager
-> if no JS script exists or default talk would run
-> check NpcTravelOverrideProvider for replace-mode travel definition
-> open generic server-owned travel menu
```

This lets Server Console turn a default NPC into a travel NPC without adding a
new JS file, as long as the server has the generic travel provider hook.

MVP implementation path:

1. Add a reusable Java `NpcTravelProvider`.
2. Add `NPCConversationManager.openTravelMenu(keyOrNpcId)` helper.
3. Convert selected travel scripts to call the helper.
4. Add fallback hook for NPCs with no script if `npc_travel_overrides` has a
   replace-mode entry.
5. Add Server Console UI for route editing and validation.

Tradeoff:

- Editing JS files directly is fastest for one-off changes.
- A provider/hook is better for Server Console because it is auditable,
  portable, reloadable, and does not fork every travel NPC script.

## Events & PQ

Server-owned settings:

- event enable/disable.
- PQ enable/disable.
- PQ party-size requirements.
- PQ level requirements.
- PQ time limits.
- PQ lobby delay.
- event max queue.
- event recall.
- daily expedition gating.
- event end timestamp.
- stage EXP/meso tuning.
- event reload/restart operations.

Examples:

- `USE_CPQ`.
- `USE_ENABLE_RECALL_EVENT`.
- `EVENT_MAX_GUILD_QUEUE`.
- `EVENT_LOBBY_DELAY`.
- `EVENT_END_TIMESTAMP`.
- `MAX_EVENT_LEVELS`.
- `PQ_BONUS_EXP_RATE`.

Database Console boundary:

- PQ/event item reward pools can be Database Console.
- PQ/event requirements, limits, timings, and stage EXP/meso policy are Server
  Console.

## Commands & GM

Server-owned settings:

- command registry.
- command enabled/disabled.
- minimum GM level per command.
- public command enable/disable.
- command aliases.
- dangerous command confirmation policy.
- command logging.
- GM trade/storage/Duey/drop restrictions.
- GM hide/auto-hide policy.

Current command tree:

- `client.command.commands.gm0` through `gm6`.
- GM level is currently implied by package/registration.

Examples:

- `MINIMUM_GM_LEVEL_TO_TRADE`.
- `MINIMUM_GM_LEVEL_TO_USE_STORAGE`.
- `MINIMUM_GM_LEVEL_TO_USE_DUEY`.
- `MINIMUM_GM_LEVEL_TO_DROP`.
- `GM_NO_FAME_COOLDOWN`.
- `USE_AUTOHIDE_GM`.
- `BLOCK_GENERATE_CASH_ITEM`.
- `USE_WHOLE_SERVER_RANKING`.

Recommended override model:

```yaml
command_overrides:
  enabled: true
  commands:
    warp:
      enabled: true
      min_gm_level: 2
      log_usage: true
    item:
      enabled: true
      min_gm_level: 4
      block_cash_items: true
    killall:
      enabled: false
```

## Items & Equipment Policy

Server-owned settings:

- item expiration behavior.
- cash item creation blocking.
- cash item merchant/drop behavior.
- pet merchant/drop behavior.
- untradeable drop erase behavior.
- untradeable item trade policy.
- one-of-a-kind policy.
- scroll success policy.
- godly-stat policy.
- equipment stat caps.
- equip leveling policy.
- inventory slot policy.
- item suggestion/Owl/Cash Shop featured item policy.

Examples:

- `ITEM_EXPIRE_TIME`.
- `USE_ERASE_UNTRADEABLE_DROP`.
- `USE_ENFORCE_UNMERCHABLE_CASH`.
- `USE_ENFORCE_UNMERCHABLE_PET`.
- `BLOCK_GENERATE_CASH_ITEM`.
- `UNTRADEABLE_ITEMS_TRADEABLE`.
- `DISABLE_ONE_OF_A_KIND_CHECK`.
- `SCROLL_SUCCESS_BONUS_ENABLED`.
- `SCROLL_SUCCESS_BONUS`.
- `USE_PERFECT_SCROLLING`.
- `USE_PERFECT_GM_SCROLL`.
- `USE_ENHANCED_CHSCROLL`.
- `CHSCROLL_STAT_RATE`.
- `CHSCROLL_STAT_RANGE`.
- `GODLY_STATS_*`.
- `MAX_EQUIPMNT_STAT`.
- `MAX_EQUIPMNT_LVLUP_STAT_UP`.
- `ALWAYS_MAX_INVENTORY_SLOTS`.

Database Console boundary:

- Item metadata and concrete inventory/equipment rows are Database Console.
- Runtime item rules and caps are Server Console.

## Skills & Buffs

Server-owned settings:

- beginner skill upgrades.
- Aran starter skill behavior.
- Hero's Will reuse.
- crash/immunity behavior.
- Holy Shield dispel behavior.
- Holy Symbol sharing behavior.
- buff overwrite policy.
- everlasting buff policy.
- autoaggro behavior.

Examples:

- `USE_ULTRA_NIMBLE_FEET`.
- `USE_ULTRA_RECOVERY`.
- `USE_ULTRA_THREE_SNAILS`.
- `USE_FULL_ARAN_SKILLSET`.
- `USE_FAST_REUSE_HERO_WILL`.
- `USE_ANTI_IMMUNITY_CRASH`.
- `USE_UNDISPEL_HOLY_SHIELD`.
- `USE_FULL_HOLY_SYMBOL`.
- `USE_BUFF_MOST_SIGNIFICANT`.
- `USE_BUFF_EVERLASTING`.
- `USE_AUTOAGGRO_NEARBY`.

## Pets & Mounts

Server-owned settings:

- pet autopot behavior.
- autopot HP/MP ratios.
- equipment contribution to autopot thresholds.
- pet hunger.
- GM pet hunger bypass.
- mount hunger.
- pet expiration behavior.

Examples:

- `USE_COMPULSORY_AUTOPOT`.
- `USE_EQUIPS_ON_AUTOPOT`.
- `PET_AUTOHP_RATIO`.
- `PET_AUTOMP_RATIO`.
- `PETS_NEVER_HUNGRY`.
- `GM_PETS_NEVER_HUNGRY`.
- `PET_EXHAUST_COUNT`.
- `MOUNT_EXHAUST_COUNT`.
- `USE_ERASE_PET_ON_EXPIRATION`.

## Social Systems

Server-owned settings:

- guild creation requirements/costs.
- guild emblem/expansion costs.
- family rep rates.
- family max depth.
- wedding reservations.
- wedding bless EXP.
- fame cooldown behavior for GMs.

Examples:

- `CREATE_GUILD_MIN_PARTNERS`.
- `CREATE_GUILD_COST`.
- `CHANGE_EMBLEM_COST`.
- `EXPAND_GUILD_*`.
- `FAMILY_REP_PER_KILL`.
- `FAMILY_REP_PER_BOSS_KILL`.
- `FAMILY_REP_PER_LEVELUP`.
- `FAMILY_MAX_GENERATIONS`.
- `WEDDING_*`.
- `GM_NO_FAME_COOLDOWN`.

## Transport

Server-owned settings:

- travel rate.
- taxi rules.
- ferry rules.
- portal restrictions.
- map transfer policy.
- name/world transfer cooldowns.
- instant name change behavior.

Examples:

- per-world `travel_rate`.
- `NAME_CHANGE_COOLDOWN`.
- `WORLD_TRANSFER_COOLDOWN`.
- `INSTANT_NAME_CHANGE`.

Database Console boundary:

- Pending/completed transfer DB rows can be Database Console.
- Transport rules and cooldown policy are Server Console.

## Runtime Operations

Server-owned actions:

- reload drops.
- reload shops.
- reload portals.
- reload map.
- reload events.
- reload config, if implemented.
- clear quest cache.
- save all.
- heap dump.
- server health.
- show sessions.
- add/remove channel.
- add/remove world.
- shutdown.

Existing command examples:

- `ReloadDropsCommand`.
- `ReloadShopsCommand`.
- `ReloadPortalsCommand`.
- `ReloadMapCommand`.
- `ReloadEventsCommand`.
- `ClearQuestCacheCommand`.
- `SaveAllCommand`.
- `HeapDumpCommand`.
- `ServerHealthCommand`.
- `ShowSessionsCommand`.
- `ServerAddChannelCommand`.
- `ServerRemoveChannelCommand`.
- `ServerAddWorldCommand`.
- `ServerRemoveWorldCommand`.
- `ShutdownCommand`.

## Overrides

Server Console override types:

- YAML-equivalent config override.
- runtime config override.
- server Java behavior override flag.
- server JS override activation.
- server XML override activation.
- map metadata override.
- NPC metadata override.
- monster stat override.
- item metadata override.
- portal/transport override.
- NPC travel override.
- command access override.
- event/PQ requirement override.
- boss spawn timer override.

## Override Hierarchy

Recommended hierarchy for all Server Console overrides:

```text
Server Console
├─ Dashboard
│  ├─ Server Status
│  ├─ Active Overrides
│  ├─ Pending Reload / Restart
│  └─ Recent Publish / Audit
│
├─ Analytics
│  ├─ Server Health
│  ├─ Population
│  ├─ Server Demographics
│  ├─ Economy
│  ├─ Item Economy
│  ├─ World Activity
│  ├─ Boss / Event Activity
│  └─ Agent Activity
│
├─ Runtime & Infrastructure
│  ├─ YAML / Runtime Config
│  ├─ Database Connection
│  ├─ Network Hosts
│  ├─ Worlds
│  │  ├─ World Flags / Messages
│  │  ├─ World Rates
│  │  └─ World Channel Count
│  ├─ Channels
│  │  ├─ Channel Capacity
│  │  ├─ Add / Remove Channel
│  │  └─ Channel Locks
│  ├─ Runtime Timers
│  │  ├─ Respawn Interval
│  │  ├─ Ranking Interval
│  │  ├─ Coupon Interval
│  │  ├─ Purge Interval
│  │  └─ Server Time Update Interval
│  └─ Runtime Operations
│     ├─ Reload Drops / Shops / Portals / Maps / Events / Config
│     ├─ Clear Quest Cache
│     ├─ Save All
│     ├─ Heap Dump
│     ├─ Health / Sessions
│     └─ Shutdown
│
├─ Access & Security
│  ├─ Login Rules
│  │  ├─ Auto Register
│  │  ├─ Bcrypt Migration
│  │  ├─ Login Attempt Limits
│  │  └─ Login Timeout
│  ├─ PIC / PIN
│  ├─ Multiclient / HWID
│  ├─ IP Validation
│  └─ GM Security
│     ├─ GM Trade / Storage / Duey / Drop Minimum Level
│     ├─ GM Fame Cooldown
│     └─ Auto-hide GM
│
├─ Rates & Economy Pressure
│  ├─ EXP / Meso / Drop Rates
│  ├─ Boss Drop Rate
│  ├─ Quest Rate
│  ├─ Party EXP / Leech Rules
│  ├─ Equip EXP Rate
│  ├─ PQ Bonus EXP
│  ├─ Fishing Rate
│  ├─ Travel Rate
│  ├─ Coupon / Rate Stacking
│  └─ Level-based Rate Multipliers
│
├─ World Behavior
│  ├─ Maps
│  │  ├─ Map Catalog
│  │  ├─ Global Map Defaults
│  │  │  ├─ Global Spawn Defaults
│  │  │  ├─ World Spawn Defaults
│  │  │  └─ Default Map Rule Fallbacks
│  │  └─ Map Workspace
│  │     ├─ Map Master Override Toggle
│  │     ├─ Overview
│  │     ├─ Entry Message
│  │     ├─ Portal Overrides
│  │     │  ├─ Portal Warp Override
│  │     │  ├─ Portal Block Override
│  │     │  ├─ Block Message
│  │     │  └─ Portal Restrictions
│  │     ├─ Spawn Overrides
│  │     │  ├─ Spawn Rate
│  │     │  ├─ Spawn Count Per Spawnpoint
│  │     │  ├─ Mob HP Multiplier
│  │     │  ├─ Mob EXP Multiplier
│  │     │  └─ Permanent Mob Spawn Status
│  │     ├─ Mob Overrides In This Map
│  │     │  ├─ Per-mob Stat Multipliers
│  │     │  ├─ Per-mob Spawn Status
│  │     │  └─ Area Boss Timer Link
│  │     ├─ Map Metadata Override
│  │     ├─ Map Damage Rules
│  │     ├─ Map Item Limit / Drop Cleanup
│  │     └─ Audit
│  │
│  ├─ Portal Search
│  │  └─ Cross-map portal search and diagnostics. Editing opens the owning map workspace.
│  │
│  ├─ Spawn Search
│  │  └─ Cross-map spawn search and diagnostics. Editing opens the owning map workspace.
│  │
│  ├─ Mobs & Bosses
│  │  ├─ Mob / Boss Stat Override
│  │  ├─ Monster Metadata Override
│  │  ├─ Mob Aggro Override
│  │  │  ├─ Global Nearby Auto-aggro
│  │  │  └─ Per-mob FirstAttack
│  │  ├─ Area Boss Spawn Timer
│  │  ├─ Boss / Expedition Rules
│  │  ├─ Boss Entry / Reset Rules
│  │  └─ Reactor Boss Refresh
│  │
│  ├─ NPCs
│  │  ├─ NPC Scriptability
│  │  ├─ NPC JS Override Activation
│  │  ├─ NPC Travel Override
│  │  ├─ NPC Behavior Override
│  │  ├─ NPC Cooldown
│  │  └─ NPC Metadata Override
│  │
│  ├─ Events & PQ
│  │  ├─ Event / PQ Enablement
│  │  ├─ Party Size Requirement
│  │  ├─ Level Range Requirement
│  │  ├─ Time Limit
│  │  ├─ Lobby / Queue Rules
│  │  ├─ Recall / Daily Limits
│  │  └─ Stage EXP / Meso Policy
│  │
│  └─ Transport
│     ├─ Taxi Rules
│     ├─ Ferry / Ship Rules
│     ├─ Travel Timers
│     ├─ Portal Travel Rules
│     ├─ Name Change Cooldown
│     └─ World Transfer Cooldown
│
├─ Gameplay Systems
│  ├─ Feature Gates
│  │  ├─ CPQ
│  │  ├─ MTS
│  │  ├─ Family
│  │  ├─ Duey
│  │  ├─ Fishing
│  │  ├─ Map Ownership
│  │  └─ Chat Logging
│  │
│  ├─ Character Rules
│  │  ├─ Starter AP
│  │  ├─ HP / MP Gain
│  │  ├─ AP / Stat Caps
│  │  ├─ Job Level / SP Enforcement
│  │  ├─ Novice EXP Enforcement
│  │  ├─ Mob Level EXP Range Enforcement
│  │  └─ Inventory Slot Policy
│  │
│  ├─ Items & Equipment
│  │  ├─ Item Expiration
│  │  ├─ Item Metadata Override
│  │  ├─ Cash Item Policy
│  │  ├─ Pet Item Policy
│  │  ├─ Untradeable Policy
│  │  ├─ One-of-a-kind Policy
│  │  ├─ Equipment Stat Caps
│  │  ├─ Equipment Level-up Rules
│  │  └─ Godly Stat Rules
│  │
│  ├─ Scroll Policy
│  │  ├─ Scroll Success Bonus
│  │  ├─ Perfect Scrolling
│  │  ├─ GM Scroll Behavior
│  │  └─ Chaos Scroll Range / Rate
│  │
│  ├─ Skills & Buffs
│  │  ├─ Beginner Skill Boosts
│  │  ├─ Class Skill Toggles
│  │  ├─ Hero's Will Reuse
│  │  ├─ Holy Shield / Dispel Rules
│  │  ├─ Holy Symbol Sharing Rules
│  │  ├─ Buff Priority
│  │  └─ Everlasting Buffs
│  │
│  ├─ Pets & Mounts
│  │  ├─ Autopot
│  │  ├─ HP / MP Autopot Ratios
│  │  ├─ Pet Hunger
│  │  ├─ Mount Hunger
│  │  └─ Pet Expiration
│  │
│  └─ Social Systems
│     ├─ Guild Rules
│     ├─ Family Rules
│     ├─ Wedding Rules
│     └─ Fame Rules
│
├─ Commands
│  ├─ Command Registry
│  ├─ Command Enable / Disable
│  ├─ Minimum GM Level Per Command
│  ├─ Command Aliases
│  ├─ Dangerous Command Rules
│  └─ Command Audit / Logs
│
├─ Technical Override Layers
│  ├─ Java Behavior Flags
│  ├─ JS Override Activation
│  ├─ XML Override Activation
│  ├─ YAML Fallback Values
│  ├─ Runtime Override Values
│  └─ Hook Status
│
└─ Publish & Audit
   ├─ Unapplied Changes
   ├─ Validation
   ├─ Preview Diff
   ├─ Publish Batch
   ├─ Reload / Restart Requirements
   ├─ Config History
   └─ Rollback
```

Recommended source order:

```text
1. Server Console published override, if enabled and valid.
2. config.yaml value.
3. server hardcoded default.
4. WZ/XML/script default, where applicable.
```

Runtime status badges:

- `Runtime Active`.
- `Pending Restart`.
- `Pending Reload`.
- `Config Only`.
- `Hook Missing`.
- `Fallback YAML`.
- `Server JS`.
- `Server XML`.
- `Server Java`.
- `Console Override`.

## Audit & Publish

Server Console should not apply high-impact runtime changes silently.

Required flow:

```text
edit
-> validate
-> preview diff
-> publish
-> apply live, reload, or mark restart required
-> audit event
```

Validation examples:

- invalid map id.
- invalid mob id.
- invalid NPC id.
- negative timer.
- rate below zero.
- channel count exceeds configured maximum.
- command min GM level outside valid range.
- unknown command id.
- JS override references missing script.
- XML override references missing file/path.
- spawn override targets an event/instance map unintentionally.
- area boss timer override targets a normal mob without explicit allow flag.

Audit fields:

- actor.
- timestamp.
- environment.
- changed setting path.
- before value.
- after value.
- runtime status.
- reload/restart requirement.
- validation warnings acknowledged.
- publish batch id.
