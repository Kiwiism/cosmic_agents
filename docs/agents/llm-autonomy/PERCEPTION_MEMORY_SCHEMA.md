# Perception And Memory Schema

The LLM should receive compact summaries, not raw packets or full server object
graphs.

## Perception Snapshot

Key:

```text
agentId + timestamp
```

Example:

```json
{
  "schemaVersion": 1,
  "agentId": 123,
  "timestampMs": 123456789,
  "location": {
    "mapId": 100000000,
    "mapName": "Henesys",
    "channel": 1,
    "x": 120,
    "y": 45,
    "footholdId": 11
  },
  "status": {
    "hpPercent": 0.92,
    "mpPercent": 0.64,
    "level": 14,
    "jobId": 200,
    "mesos": 120000,
    "busyState": "idle",
    "danger": "low"
  },
  "nearby": {
    "npcs": [],
    "mobs": [],
    "drops": [],
    "players": [],
    "agents": [],
    "portals": []
  },
  "tasks": {
    "activeTaskId": null,
    "lastResult": null,
    "blockedReason": null
  }
}
```

## Nearby Entity Summaries

NPC:

```json
{
  "npcId": 1012100,
  "name": "Chief Stan",
  "distance": 87,
  "canInteract": true,
  "knownTypes": ["quest-start", "quest-complete"],
  "risk": "safe"
}
```

Mob:

```json
{
  "mobId": 100100,
  "name": "Orange Mushroom",
  "level": 8,
  "distance": 150,
  "estimatedThreat": "low",
  "isTargetRelevant": true
}
```

Drop:

```json
{
  "itemId": 4000004,
  "name": "Orange Mushroom Cap",
  "distance": 120,
  "questRelevant": true,
  "marketRelevant": false
}
```

Portal:

```json
{
  "portalName": "east00",
  "distance": 220,
  "toMapId": 100010000,
  "routeKnown": true
}
```

## Memory Buckets

Memory should be split by purpose so the LLM can query only what it needs.

### Task Memory

```json
{
  "agentId": 123,
  "taskKind": "QUEST_CHAIN",
  "taskKey": "maple_island_basic",
  "attempts": 3,
  "successes": 2,
  "failures": [
    {
      "reason": "unreachable-npc",
      "mapId": 261040000,
      "npcId": 2111020,
      "timestampMs": 123
    }
  ]
}
```

### Route Memory

```json
{
  "fromMapId": 100000000,
  "toMapId": 100010000,
  "agentProfileKey": "mage-low-level",
  "successRate": 0.96,
  "averageDurationMs": 28000,
  "failureReasons": []
}
```

### NPC Memory

```json
{
  "npcId": 1012100,
  "mapId": 100000000,
  "preferredApproachPoints": [],
  "failedApproachPoints": [],
  "lastInteractionMs": 123,
  "averageDelayMs": 2400
}
```

### Combat Memory

```json
{
  "mapId": 100010100,
  "mobId": 100100,
  "classGroup": "mage",
  "levelBand": [10, 15],
  "deathCount": 0,
  "potionUsePerMinute": 1.2,
  "expPerMinute": 2400,
  "lootValuePerMinute": 500
}
```

### Economy Memory

Defined in `ECONOMY_SYSTEM_SCHEMA.md`.

## Summarization Levels

Use different detail levels depending on LLM task.

```text
urgent:
  death risk, stuck, low pots, trade invite, task blocked

active:
  current task progress, nearby relevant objects, next planned step

strategic:
  level, mesos, equipment, goals, market opportunities, long-term memory

batch:
  compressed status for many agents
```

Batch status example:

```json
{
  "agents": [
    {
      "agentId": 12,
      "role": "quester",
      "state": "running",
      "mapId": 100000000,
      "task": "maple_island_basic",
      "blocked": false
    }
  ]
}
```

## Memory Retention

Suggested retention:

- urgent event memory: 24 to 72 hours
- route performance: long-lived, decayed
- market observations: 7 to 30 days by item liquidity
- failed approach points: long-lived until catalog changes
- chat/social memory: short and privacy-limited
- quest completion memory: permanent per agent
