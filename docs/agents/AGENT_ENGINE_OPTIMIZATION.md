# Agent Engine Optimization

Design notes for reducing agent runtime cost after the agent reconstruction.
These are not current runtime behavior.

Target:

```text
Run up to 2000 concurrent agents while keeping real player server work
responsive.
```

## Principle

Visible agents need presentation fidelity. Invisible agents need world-state
fidelity.

The server should remain authoritative. Agents should not become a second server;
they should request validated actions through capabilities/server adapters.

Core rule:

```text
Only make an agent expensive when the world can observe it.
```

Agents do not need to be simulated like full players every tick. They need
believable continuity, validated outcomes, and safe materialization when a real
player can observe or interact with them.

## Primary Bottlenecks For 2000 Agents

The scaling work should optimize these costs first:

- Movement and physics: foothold collision, jump arcs, portal traversal, stuck
  checks, and movement packet generation.
- Combat ticks: target scanning, hitbox checks, attack planning, damage rolls,
  mob retaliation, and potion/debuff handling.
- Mob simulation: controller assignment, aggro, respawn, loot generation, and
  map object updates.
- Packet broadcasting: movement, attack, skill, damage, chat, emote, and item
  drop packets.
- DB writes: inventory saves, quest saves, autosave, loot persistence, plan
  state, and decision logs.
- Inventory operations: pickup, stacking, filtering, selling, dropping, and
  quest item checks.
- Pathfinding: graph building, repeated route searches, same-map navigation,
  and portal route lookups.
- Scheduler pressure: thousands of timers, delayed actions, capability loops,
  and retry loops.
- Memory pressure: character state, plan state, perception snapshots, route
  caches, combat targets, dialogue state, and journals.
- Lock contention: map locks, inventory locks, player storage, channel/world
  structures, and shared caches.

## Simulation Tiers

Do not use viewport/screen visibility. MapleStory clients can use different
resolutions or custom behavior, and some clients may see most of a map. Use
same-map real-player presence instead.

### Tier 0 - Presentation Mode

Condition:

```text
At least one real player is in the same map as the agent.
```

Behavior:

- Full movement and physics fidelity.
- Normal movement ticks.
- Broadcast movement, attacks, effects, chat, and emotes.
- Normal NPC interaction delays and randomized stop spots.
- Full combat presentation.
- Full visible loot, shop, and NPC behavior.

Purpose:

```text
Anything a player can observe must look real.
```

### Tier 1 - Background Active Mode

Condition:

```text
No real player in the map, but the map is sensitive or pinned.
```

Examples:

- Event maps.
- Party quest maps.
- Boss maps.
- Free Market or shop maps.
- Maps with hired merchants or player shops.
- Maps with important shared-world state.

Behavior:

- Do not build or send visual broadcasts when no real players are present.
- Reduce movement tick rate.
- Use coarse but foothold-valid position updates.
- Reduce perception scan frequency.
- Suppress cosmetic chat, emotes, and fidget behavior.
- Preserve stronger real map-state consistency.
- Avoid aggressive abstraction for mobs, drops, event state, bosses, or shops.

Purpose:

```text
Save presentation cost while keeping sensitive map state safe.
```

### Tier 2 - Background Abstract Mode

Condition:

```text
No real player in the map, and the map is safe to abstract.
```

Behavior:

- No movement packet generation.
- No movement broadcasts.
- No attack/effect packet generation.
- No continuous physics.
- Use route ETA for navigation.
- Use simple ETA heuristics for same-map movement.
- Use abstract combat rounds where safe.
- Use direct validated NPC/shop/quest actions.
- Commit final state at arrival or objective completion.
- Materialize onto a valid foothold if a real player enters.

Purpose:

```text
Preserve believable outcomes and timing without simulating invisible visuals.
```

### Tier 3 - Strategic Offline Mode

Condition:

```text
Agent does not need to be loaded as a full active map character.
```

Behavior:

- Resolve plans in coarse time slices.
- Use expected EXP, meso, loot, potion use, death risk, and interruption chance.
- Do not run continuous movement, combat, perception, or broadcast logic.
- Materialize only when the agent enters an observable/shared interaction.

Purpose:

```text
Keep long-running population behavior progressing without occupying full map
runtime resources.
```

## Mode Selection

Recommended mode selection:

```text
if map has real player:
    PRESENTATION
else if map is event/boss/FM/shop/pinned/sensitive:
    BACKGROUND_ACTIVE
else:
    BACKGROUND_ABSTRACT
```

Suggested enum:

```java
enum AgentSimulationMode {
    PRESENTATION,
    BACKGROUND_ACTIVE,
    BACKGROUND_ABSTRACT,
    STRATEGIC_OFFLINE
}
```

## Transition Rule

When a real player enters a map containing an agent:

```text
switch to PRESENTATION
materialize agent at a believable foothold-valid position
resume full movement/combat/NPC presentation behavior
```

Background internal position may be virtual. Visible server position must be
valid for the map.

Materialization examples:

- Traveling agent: nearest valid foothold along the route.
- Combat agent: near a valid attack point or mob region.
- Shopping agent: near the shop NPC.
- Quest/NPC agent: near a randomized NPC approach point.
- Idle agent: current valid foothold or a nearby idle point.

## Background Route ETA

For invisible travel, do not run full physics if nobody can observe it. Store:

```text
routeStartTime
routeEtaMs
startPoint
targetPoint
routeId or route segment list
materialization policy
```

On query, an approximate virtual position can be derived:

```text
progress = elapsedMs / routeEtaMs
currentX = lerp(startX, targetX, progress)
currentY = virtual or foothold-adjusted y
```

Only commit real server position at important points:

- Arrival.
- Interruption.
- Player enters the map.
- Agent needs to interact with NPC, portal, mob, drop, or shop.

### Portal-To-Portal Catalog

Future derived catalog:

```text
generated_route_eta_catalog.json
```

Useful row fields:

```json
{
  "mapId": 10000,
  "from": {
    "type": "portal",
    "name": "sp",
    "x": 0,
    "y": 0
  },
  "to": {
    "type": "portal",
    "name": "out00",
    "x": 1077,
    "y": 480
  },
  "distancePx": 1180,
  "edgeKinds": ["walk"],
  "estimatedMsByProfile": {
    "speed100_jump100": 9500,
    "speed120_jump100": 7900,
    "speed140_jump120": 6600
  },
  "flags": []
}
```

Useful route targets:

- Portal to portal.
- Portal to NPC.
- Portal to shop NPC.
- Portal to mob region.
- Portal to randomized NPC interaction point.
- Current point to target point.

## Same-Map Background ETA Heuristic

For same-map movement to a mob, NPC, drop, portal, or objective target, a simple
background estimate can be used before route catalogs are available.

Use this only when no real player is in the map.

### Basic Formula

```text
dx = abs(target.x - current.x)
dy = abs(target.y - current.y)

effectivePx = dx + dy * verticalPenalty
etaMs = effectivePx / speedPxPerSec * 1000
etaMs += setupDelayMs
etaMs *= randomJitter
etaMs = clamp(minMs, maxMs)
```

Suggested vertical penalty:

```text
if dy < 40:       verticalPenalty = 1.0
else if dy < 120: verticalPenalty = 1.5
else if dy < 300: verticalPenalty = 2.5
else:             verticalPenalty = 4.0
```

### Slope Penalty Formula

Alternative formula:

```text
dx = abs(target.x - current.x)
dy = abs(target.y - current.y)
slope = dy / max(dx, 1)

effectivePx = dx + dy

if slope > 0.25: effectivePx *= 1.2
if slope > 0.5:  effectivePx *= 1.4
if slope > 1.0:  effectivePx *= 1.8
```

Additional modifiers:

```text
if target.y < current.y:
    effectivePx *= 1.25

if currentFoothold != targetFoothold:
    effectivePx += 200 to 600 px equivalent

etaMs += random(300, 1200)
etaMs *= personalityMovementMultiplier
```

### Recommended First Version

Use the simpler conservative version first:

```text
dx = abs(target.x - current.x)
dy = abs(target.y - current.y)

effectivePx = dx + dy * 2

if target.y < current.y:
    effectivePx *= 1.25

if currentFoothold != targetFoothold:
    effectivePx += 400

etaMs = effectivePx / speedPxPerSec * 1000
etaMs *= random(0.85, 1.25)
etaMs = clamp(500, 30000)
```

This is cheap, deterministic enough for planning, and conservative enough to
avoid unrealistic instant travel.

## Agent Runtime Cheat Methods

These are intentional optimizations. They are acceptable because agents are
server-controlled and only need player-grade fidelity when a real player can
observe or interact with them.

### 1. Path ETA Instead Of Physics

For hidden agents, do not run full walk/jump physics.

Use cataloged or estimated route duration:

```text
etaMs = routeDistancePx / speedPxPerMs
etaMs += verticalPenalty
etaMs += portalTransitionCost
etaMs += mapTraversalDifficultyCost
etaMs *= random(0.85, 1.25)
```

Then place the agent at the destination after the delay, validating the final
map, portal, and foothold before materialization.

### 2. Same-Map Travel Approximation

For hidden same-map travel to mobs, NPCs, portals, drops, or shop points, use
the same-map ETA heuristic instead of foothold stepping.

The runtime should keep a virtual position for progress reporting, but only
commit a real visible position when the action completes, is interrupted, or a
real player enters the map.

### 3. Virtual Combat

For maps without real players, resolve combat using expected DPS and survival
models instead of visible attacks.

Suggested model:

```text
effectiveDps =
    averageDamage
    * hitChance
    * attacksPerSecond
    * uptimeFactor
    * classEfficiency

killTimeMs = mobHp / effectiveDps * 1000 + repositionDelayMs

incomingDamageRate =
    mobTouchDamage
    * contactFrequency
    * avoidFailureRate
    * defenseModifier

expectedPotionUse = incomingDamage / potionHealAmount
```

The abstract resolver still validates that the mob is available, the agent can
hit it, the agent has enough HP/MP/potions, and the map is safe to abstract.

### 4. Spawn Pool Awareness

If an objective asks for a specific mob and that mob type is currently low, the
agent should kill useful filler mobs instead of idling.

Priority order:

```text
quest target mob
mobs dropping current quest items
mobs dropping future quest items
mobs with useful market drops
mobs blocking spawn rotation
nearest safe filler mob
```

This keeps mixed-spawn maps productive and avoids all agents waiting for the
same target type to respawn.

### 5. Virtual Loot Buffer

Do not create every hidden drop as a map item or DB row.

For background agents:

```text
lootBuffer[itemId] += count
mesoBuffer += amount
rareDropBuffer += materialized rare item
```

Materialize buffered loot only when needed:

- quest requirement check.
- shop sale.
- trade with player or agent.
- market listing.
- player inspection.
- agent despawn.
- shutdown checkpoint.

### 6. Inventory Compression

Common ETC and use-item quantities can be counters while an agent is not
player-observed.

Examples:

```text
4000000 snail shell -> count
4000016 orange mushroom cap -> count
mesos -> counter
rare equip/scroll -> real item object
```

The full inventory should be rebuilt or reconciled only at materialization
boundaries or durable checkpoints.

### 7. Eventual Agent Persistence

Agents should not use player-grade durability for every small mutation.

Immediate save triggers:

- level up.
- job advance.
- quest complete.
- player trade.
- rare item obtained.
- ownership/control change.
- despawn or shutdown.

Delayed save triggers:

- normal loot.
- meso changes.
- map movement.
- potion use.
- trash item sale.
- plan progress heartbeat.

Never save every tick:

- HP/MP fluctuation.
- current target.
- animation state.
- movement position.
- temporary objective step.

### 8. Plan-Level Simulation

Some hidden objectives can be resolved at the plan level.

Example:

```text
Objective: farm 50 snail shells
Inputs: map catalog, mob density, agent DPS, drop rate, travel overhead,
        potion policy, death risk, personality patience
Output: expected duration, EXP, meso, loot, potion use, failures, sidetracks
```

This avoids simulating every movement and kill when the detailed sequence has
no player-visible value.

### 9. Broadcast Suppression

If no real player can observe the action:

- do not build movement packets.
- do not build attack packets.
- do not build skill/effect packets.
- do not build damage packets.
- do not build item drop packets.
- do not emit cosmetic chat/emote/fidget packets.

Update authoritative state only through validated capability commits.

### 10. Cheap Human Variation

Realism should usually come from cheap policy choices, not expensive simulation.

Use random but bounded variation for:

- NPC stop point inside interaction box.
- dialogue-length delay.
- route variant.
- grind region.
- potion threshold.
- retreat threshold.
- sell timing.
- rest/chair behavior.
- sidetrack chance.
- social response delay.
- fatigue/recovery period.

### 11. Outcome Batching

For background farming, apply results in small batches rather than one kill at a
time.

Example:

```text
10 minute hidden farming slice:
    +EXP
    +mesos
    +lootBuffer deltas
    -expected potion use
    +quest kill/item progress
    possible death/interruption/rare event
```

Batching reduces DB writes, inventory churn, scheduler work, and repeated
capability calls.

### 12. Probability Smoothing

For common drops and routine meso gain, use expected values or low-frequency
sampling rather than rolling every invisible kill.

Recommended split:

```text
common drops:
    expected value with controlled variance

uncommon drops:
    batched random rolls

rare drops / scrolls / equips:
    explicit individual roll and journal event
```

This preserves market supply without spending CPU on thousands of tiny random
events.

### 13. Rare-Event Materialization

When a rare or story-worthy event happens, promote it into real agent state.

Examples:

- rare scroll/equip drop.
- level up.
- near-death escape.
- quest item completion.
- player-relevant market purchase.
- unusual social interaction.

Behavior:

```text
save milestone
write decision/event journal entry
allow profile/economy/plan reaction
optionally materialize if a real player can observe soon
```

This keeps the world interesting without making every routine kill expensive.

### 14. Anti-Perfect-Efficiency Tax

Background agents should not outperform visible/manual play simply because
their simulation is cheaper.

Apply small bounded penalties for:

- imperfect route choice.
- overkill/reposition time.
- missed mobs.
- fatigue.
- social distraction.
- inventory sorting.
- potion/rest delay.
- personality inefficiency.
- map crowding.

This makes background simulation believable and protects the economy.

### 15. Crowding Simulation

If many agents are farming the same map or region, reduce effective farming
efficiency even when no players are present.

Example modifiers:

```text
regionAgents <= recommendedCapacity:
    efficiency = 1.0

regionAgents > recommendedCapacity:
    efficiency = recommendedCapacity / regionAgents
    efficiency = clamp(efficiency, 0.35, 1.0)
```

Use map catalog metadata to decide capacity and region boundaries.

### 16. Map Region Allocation

Hidden agents should not virtually farm the same spawn point unless the map can
support it.

The runtime should assign agents to:

- grind regions.
- mob-density regions.
- NPC/shop regions.
- idle/social regions.
- portal/travel regions.

Allocation inputs:

- objective target.
- map crowding.
- agent profile.
- party/group behavior.
- expected mob/item value.
- safety.

### 17. Deferred Inventory Reconciliation

For hidden agents, inventory buffers may be allowed to exist outside exact client
inventory layout for a short time.

Strict reconciliation happens when:

- player inspects/trades with agent.
- agent opens a shop/market listing.
- agent needs a quest item.
- agent sells/drops items.
- agent despawns.
- server shutdown checkpoint.

Policy options:

```text
strict:
    validate capacity before every buffered item credit

relaxed:
    allow temporary buffer overflow, then resolve by stacking/selling/dropping
    at reconciliation boundary
```

For public servers, start with strict or mostly strict mode until the behavior
is well tested.

### 18. Visibility Grace Window

Do not switch simulation modes instantly on every player enter/leave event.

Recommended:

```text
player enters map:
    immediately stop starting new abstract actions
    materialize active agents safely

player leaves map:
    wait 5-30 seconds before demoting from presentation mode
```

This avoids mode flapping when players change channels, walk through a map, or
disconnect/reconnect.

### 19. Background Death Shortcut

Hidden combat should use death risk instead of full death sequence simulation.

If death occurs:

```text
apply EXP/meso/potion consequences
move agent to valid return/town map
set recovery/rest delay
write journal event
possibly change plan confidence
```

The visible death animation path is only needed when a real player can observe
it.

### 20. Potion And Rest Model

Hidden agents do not need per-hit autopot ticks.

Use a resource model:

```text
expectedHpLoss
expectedMpUse
availablePotions
potionThreshold
restPolicy
mesoForRestock
```

If supplies are low:

- rest on chair.
- return to town.
- buy potions.
- ask owner/party.
- switch to lower-risk plan.
- pause until recovery.

### 21. Quest Item Reservation

Direct sell/drop shortcuts must consult catalog and profile policy before
removing items.

Reservation inputs:

- current active quests.
- likely future quests.
- class/job path.
- crafting profile.
- economy value.
- personality carelessness.

Example:

```text
careful quester:
    reserves future quest items

merchant/farmer:
    reserves high-demand items for sale

careless grinder:
    may sell low-value future quest items
```

### 22. Agent Fairness Budget

Each hidden agent should have a maximum effective progress budget per time
window.

Budget examples:

- max EXP/hour by level/map/job band.
- max meso/hour by map/economy band.
- max rare-drop rolls/hour.
- max market actions/hour.
- max quest objective completions/hour.

This prevents background abstraction from creating impossible progression or
flooding the economy.

### 23. Debug Strict Mode

Add a test mode that forces agents through closer-to-real paths for comparison.

Example config:

```yaml
agents:
  simulation:
    strict_agent_simulation: true
```

Use strict mode to compare:

- visible combat result versus abstract combat result.
- real loot rolls versus buffered loot result.
- full movement time versus ETA movement time.
- full inventory mutation versus buffered reconciliation.

Strict mode is for validation, not the default 2000-agent runtime.

## Background Action Runtime Package

The cheat methods should be implemented as a separate package after
reconstruction, not mixed into the normal player-visible combat/loot path.

Package name:

```text
agent-background-action-runtime
```

Purpose:

```text
Resolve unobserved agent movement, combat, loot, NPC/shop actions, and plan
progress using validated low-fidelity simulation.
```

Two-path model:

```text
Presentation Path:
    real map combat
    real movement/physics
    real mob death
    real drops
    real packets/broadcasts
    used when real players can observe

Background Path:
    route ETA
    abstract combat
    direct loot credit
    loot/inventory buffers
    no presentation packets
    delayed/checkpoint persistence
    used when no real players can observe
```

Core components:

- `BackgroundActionRouter`: decides whether an objective can use background
  execution.
- `BackgroundNavigationResolver`: route ETA, same-map ETA, and arrival
  materialization.
- `BackgroundCombatResolver`: DPS/survival/potion/death-risk combat resolution.
- `BackgroundLootResolver`: drop/meso rolls, expected values, rare-event
  handling, and direct credit.
- `AgentLootBuffer`: compressed item/meso/result storage.
- `BackgroundInventoryCommitter`: validates and reconciles buffered inventory.
- `BackgroundQuestProgressService`: updates kill/item objective progress safely.
- `AgentMaterializationService`: converts virtual state into valid visible state.
- `AgentFairnessBudgetService`: caps progress and rare events.
- `BackgroundActionJournal`: records summarized decisions/outcomes.

Shared authoritative rules:

- same drop tables.
- same EXP/rate rules.
- same quest requirements.
- same inventory capacity rules at reconciliation.
- same combat formula baseline.
- same map/NPC/shop eligibility checks.
- same server-side validation before committing state.

Things it deliberately skips when safe:

- movement packets.
- attack packets.
- skill/effect packets.
- mob death packets.
- map item creation.
- pickup packets.
- item ownership timers.
- per-hit HP/MP mutation.
- per-kill DB writes.
- cosmetic chat/emotes/fidgets.

Transition safety:

```text
if real player enters map:
    stop starting new background actions
    finish, cancel, or materialize current background action
    validate map/foothold/HP/MP/inventory state
    switch agent to PRESENTATION mode
```

Implementation rule:

```text
The background path may skip presentation mechanics, but it must not skip
server validation or produce impossible final state.
```

## Mob Targeting

For mobs, route to a valid attack point, not the mob's exact center point.

```text
targetPoint = nearest valid attack point near mob
etaToEngageMs = travelEtaMs + random(300, 900)
```

After `etaToEngageMs`, background combat can switch to abstract combat rounds.

If the target mob no longer exists at arrival:

```text
rescan
choose a new target
or report objective blocked
```

## Capability Behavior By Tier

### Navigation

```text
PRESENTATION:
    full movement, physics, and movement broadcasts

BACKGROUND_ACTIVE:
    coarse foothold-valid updates, no broadcasts

BACKGROUND_ABSTRACT:
    route ETA or same-map ETA heuristic, scheduled arrival

STRATEGIC_OFFLINE:
    plan-level route duration and destination checkpoint
```

### Combat

```text
PRESENTATION:
    attack packet path and visible skill effects

BACKGROUND_ACTIVE:
    cautious real map-state updates, reduced presentation

BACKGROUND_ABSTRACT:
    abstract combat rounds using shared combat formulas

STRATEGIC_OFFLINE:
    expected-value combat outcome by time slice
```

### NPC And Quest

```text
PRESENTATION:
    move to randomized stop point, apply dialogue delay, execute quest action

BACKGROUND_ACTIVE:
    validate NPC/map/quest state, apply action with minimal visual work

BACKGROUND_ABSTRACT:
    validate requirements, apply start/complete directly through capability

STRATEGIC_OFFLINE:
    plan/objective state progress only; materialize for shared interactions
```

### Shop

```text
PRESENTATION:
    walk to NPC, visible timing, buy/sell sequence

BACKGROUND_ACTIVE:
    validate shop access and inventory/mesos, transact with minimal visual work

BACKGROUND_ABSTRACT:
    direct validated transaction with realistic delay

STRATEGIC_OFFLINE:
    market/economy intent only until transaction boundary
```

### Dialogue And Cosmetic Behavior

```text
PRESENTATION:
    chat, emotes, fidgets allowed

BACKGROUND_ACTIVE:
    suppress most cosmetic output

BACKGROUND_ABSTRACT:
    no visible chat/emotes; optionally store memory/intention only

STRATEGIC_OFFLINE:
    relationship/profile memory updates only
```

## DB Scaling Policy

Agents should have their own persistence lane after reconstruction.

Recommended save profiles:

```text
PLAYER_FULL:
    current player behavior

AGENT_FULL:
    full compatibility save for despawn, shutdown, inspection, or conversion

AGENT_CHECKPOINT:
    core character row, location, level/exp/job, mesos, dirty inventory,
    dirty quests, dirty skills, and plan checkpoint

AGENT_LIGHT:
    map/location, plan state, profile state, and buffered outcome summaries

AGENT_EPHEMERAL:
    no DB save except explicit milestone or despawn
```

Recommended queue rules:

- separate agent save queue from player saves.
- coalesce duplicate saves by character id.
- cap agent saves per second.
- jitter periodic checkpoints.
- skip unchanged sections using dirty flags.
- keep player logout/save work higher priority.

Suggested dirty flags:

```text
statsDirty
inventoryDirty
questDirty
skillsDirty
locationDirty
planDirty
profileDirty
marketDirty
relationshipDirty
```

Acceptable durability target:

```text
Players: near-zero progress loss.
Agents: may lose a few minutes of low-value background activity, but must not
        lose milestones such as level up, job advance, quest complete, rare
        item, or player trade.
```

## Runtime Tick Budget

Avoid one independent high-frequency timer per agent.

Preferred model:

```text
AgentEngineTick:
    classify agents by simulation mode
    process due work by priority
    respect per-tick CPU budget
    defer low-priority work under load
```

Suggested cadences:

```text
PRESENTATION:        100-250 ms for movement/combat presentation
BACKGROUND_ACTIVE:   500-1000 ms reduced decisions
BACKGROUND_ABSTRACT: 5-15 sec simulation steps
STRATEGIC_OFFLINE:   30-300 sec plan slices
```

Target population mix:

```text
100-200 presentation/high-fidelity agents
300-600 background-active agents
1000-1500 background-abstract or strategic agents
```

This mix is the practical path toward 2000 concurrent agents.

## Map Catalog Data For Scaling

Map catalog records should include scaling metadata, not only navigation data.

Useful fields:

- recommended agent capacity.
- maximum agent capacity.
- mob density regions.
- expected spawn counts by mob id.
- quest mob ids.
- filler mob ids.
- useful future quest drop mobs.
- safe grind regions.
- NPC interaction boxes.
- shop/NPC regions.
- portal regions.
- map traversal difficulty.
- sensitive-map flag.
- abstract-simulation eligibility.
- materialization-safe footholds.

These fields let agents distribute themselves and let the runtime choose
cheap-but-believable simulation modes.

## Do Not Do

Do not simply skip agent ticks when invisible:

```text
bad: if no players nearby, skip tick
```

That freezes agents and creates incorrect outcomes.

Use lower-fidelity simulation instead:

```text
visible = high frequency presentation
invisible = lower fidelity state simulation
```

## First Implementation Order

1. Detect real player presence per map.
2. Add `AgentSimulationMode` decision logic.
3. Suppress broadcasts/cosmetics when no real player is in the map.
4. Reduce invisible movement/perception cadence.
5. Add same-map background ETA heuristic.
6. Add route ETA/catalog support.
7. Add background navigation arrival/materialization.
8. Add background combat rounds.
9. Add background NPC/shop/quest execution.
10. Move to a sharded/budgeted agent scheduler.

## Safety Rules

- Runtime must validate live state before every committed action.
- Player-visible maps always use presentation mode.
- Sensitive maps should not use aggressive abstraction.
- Background simulation must materialize into valid visible state.
- Core server tasks and real player packet handling must stay higher priority
  than agent planning, cosmetic behavior, and LLM calls.
