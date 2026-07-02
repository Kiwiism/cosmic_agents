# Agent Engine Optimization

Design notes for reducing agent runtime cost after the agent reconstruction.
These are not current runtime behavior.

## Principle

Visible agents need presentation fidelity. Invisible agents need world-state
fidelity.

The server should remain authoritative. Agents should not become a second server;
they should request validated actions through capabilities/server adapters.

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
    BACKGROUND_ABSTRACT
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
```

### Combat

```text
PRESENTATION:
    attack packet path and visible skill effects

BACKGROUND_ACTIVE:
    cautious real map-state updates, reduced presentation

BACKGROUND_ABSTRACT:
    abstract combat rounds using shared combat formulas
```

### NPC And Quest

```text
PRESENTATION:
    move to randomized stop point, apply dialogue delay, execute quest action

BACKGROUND_ACTIVE:
    validate NPC/map/quest state, apply action with minimal visual work

BACKGROUND_ABSTRACT:
    validate requirements, apply start/complete directly through capability
```

### Shop

```text
PRESENTATION:
    walk to NPC, visible timing, buy/sell sequence

BACKGROUND_ACTIVE:
    validate shop access and inventory/mesos, transact with minimal visual work

BACKGROUND_ABSTRACT:
    direct validated transaction with realistic delay
```

### Dialogue And Cosmetic Behavior

```text
PRESENTATION:
    chat, emotes, fidgets allowed

BACKGROUND_ACTIVE:
    suppress most cosmetic output

BACKGROUND_ABSTRACT:
    no visible chat/emotes; optionally store memory/intention only
```

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
