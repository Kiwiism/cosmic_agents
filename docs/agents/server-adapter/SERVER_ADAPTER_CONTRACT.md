# Server Adapter Contract

The server adapter is the only layer that knows about a specific Cosmic-like
server implementation.

Catalog runtime, profile runtime, LLM tooling, and portable Agent logic should
talk to this adapter instead of server classes directly.

## Goals

- Hide server-specific runtime classes.
- Provide live state snapshots.
- Execute validated actions.
- Expose capability-safe operations.
- Make the portable Agent platform usable by other Cosmic-based servers.

## Non-Goals

- Do not expose raw packet sending.
- Do not expose raw quest/shop/script mutation APIs.
- Do not let LLM call adapter actions directly.
- Do not expose server locks or mutable collections.

## Service Shape

```java
public interface AgentServerAdapter {
    ServerIdentity getServerIdentity();

    LiveAgentState getAgentState(int agentId);
    LiveMapState getMapState(int mapId);
    LiveInventoryState getInventoryState(int agentId);
    LiveQuestState getQuestState(int agentId);
    LiveEconomyState getEconomyState(int agentId);

    ValidationResult validate(AgentAction action);
    ActionResult execute(ValidatedAgentAction action);
}
```

## Server Identity

```json
{
  "serverFamily": "cosmic",
  "gameVersion": "v83",
  "worldId": 0,
  "adapterVersion": "1.0.0",
  "capabilities": [
    "navigation",
    "combat",
    "npc",
    "quest",
    "shop",
    "market-scan"
  ]
}
```

## Live Agent State

```json
{
  "agentId": 123,
  "characterId": 456,
  "name": "Mira",
  "mapId": 100000000,
  "channelId": 1,
  "position": {
    "x": 120,
    "y": 45,
    "footholdId": 11
  },
  "level": 14,
  "jobId": 200,
  "hp": 900,
  "maxHp": 1000,
  "mp": 500,
  "maxMp": 700,
  "mesos": 120000,
  "busyState": "idle",
  "alive": true
}
```

## Live Map State

```json
{
  "mapId": 100000000,
  "channelId": 1,
  "npcs": [
    {
      "npcId": 1012100,
      "objectId": 999,
      "x": 100,
      "y": 45,
      "footholdId": 11
    }
  ],
  "mobs": [],
  "drops": [],
  "playersNearby": 3,
  "agentsNearby": 7,
  "portals": []
}
```

## Action Model

Actions should be intent-level:

```json
{
  "type": "MOVE_TO_NPC",
  "agentId": 123,
  "payload": {
    "npcId": 1012100,
    "mapId": 100000000,
    "approachPoint": {
      "x": 120,
      "y": 45,
      "footholdId": 11
    }
  }
}
```

Avoid low-level packet or key actions.

## Validation

Validation should check:

- agent exists
- agent is alive
- agent is not busy in incompatible state
- map is loaded or loadable
- live NPC/mob/item/portal exists when needed
- position/range is valid
- inventory/quest requirements are met
- action is allowed by server policy
- action is allowed by Agent policy
- action is not script-sensitive unless explicitly enabled

Validation result:

```json
{
  "status": "valid",
  "warnings": [],
  "requiredLiveChecks": ["npc-present", "range-valid"]
}
```

## Execution Result

```json
{
  "status": "succeeded",
  "agentId": 123,
  "actionType": "COMPLETE_QUEST",
  "effects": {
    "questCompleted": 1000,
    "itemsAdded": [],
    "itemsRemoved": [],
    "expGained": 120
  },
  "events": []
}
```

Statuses:

- `queued`
- `running`
- `succeeded`
- `failed`
- `cancelled`
- `blocked`
- `manual-review-required`

## Portable JSON Contracts

These schemas are prep-only contracts. They define the portable data envelopes
that future adapter code should emit or accept after reconstruction is stable:

- `docs/agents/server-adapter/live-agent-snapshot.schema.json`
  - live state snapshot for one Agent, including map, position, vitals,
    inventory/quest/buff summaries, nearby summaries, and timestamp.
- `docs/agents/server-adapter/server-action-request.schema.json`
  - validated intent-level server action request from plan, capability, LLM,
    recovery, test, or system sources.
- `docs/agents/server-adapter/server-action-result.schema.json`
  - adapter action result with status, reason code, live-state-change marker,
    optional post-action snapshot, effects, and evidence.
- `docs/agents/server-adapter/portable-install-manifest.schema.json`
  - portable installer ownership manifest for copied files, marker-blocked
    patches, config keys, marker prefix, and disabled-by-default install state.
- `docs/agents/server-adapter/portable-install-plan.schema.json`
  - dry-run install/update/uninstall plan for copied files, patch operations,
    config changes, status, and risks.
- `docs/agents/server-adapter/portable-patch-operation.schema.json`
  - anchor-based, marker-blocked patch operation contract.
- `docs/agents/server-adapter/portable-install-verify-report.schema.json`
  - installer verify report contract for marker, file, config, and compile
    checks.

The adapter implementation must still re-check live server state before
executing anything. Catalog facts, LLM commands, and plan objectives are never
trusted as direct permission to mutate server state.

## Porting Checklist

To port the platform to another server:

- Implement `AgentServerAdapter`.
- Map server characters/agents to `agentId`.
- Provide live map snapshots.
- Provide live inventory snapshots.
- Provide live quest snapshots.
- Implement validation without trusting catalog blindly.
- Implement action execution for supported capabilities.
- Declare unsupported capabilities in `ServerIdentity`.
- Run catalog bundle compatibility checks.
