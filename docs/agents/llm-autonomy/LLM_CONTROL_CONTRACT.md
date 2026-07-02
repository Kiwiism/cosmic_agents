# LLM Control Contract

The LLM controls agents by issuing typed commands. It does not directly mutate
server state. Every action command goes through validators and capability
executors.

## Control Levels

```text
Strategic:
  become level 30 cleric
  earn mesos through farming
  act as FM scout today

Task:
  complete Maple Island questline
  farm 50 Orange Mushroom Caps
  buy 200 blue potions below 300 mesos

Capability:
  navigate to map
  move near NPC
  accept quest
  attack mobs matching filter

Engine:
  actual movement, combat packets, delays, retries, cooldowns
```

The LLM should primarily use strategic and task commands.

## Command Envelope

All commands should use a shared envelope.

```json
{
  "commandId": "cmd-uuid",
  "issuedBy": "llm-director",
  "agentId": 123,
  "priority": "normal",
  "deadlineMs": null,
  "idempotencyKey": "agent-123-quest-1000-start",
  "type": "ASSIGN_TASK",
  "payload": {},
  "safety": {
    "allowScriptSensitive": false,
    "allowMarketSpendMesos": 100000,
    "allowTradeWithPlayers": false
  }
}
```

Priorities:

- `emergency`
- `high`
- `normal`
- `low`
- `background`

## Command Result

```json
{
  "commandId": "cmd-uuid",
  "status": "accepted",
  "agentId": 123,
  "taskId": "task-uuid",
  "estimatedDurationMs": 180000,
  "preconditions": {
    "met": true,
    "warnings": []
  }
}
```

Statuses:

- `accepted`
- `rejected`
- `queued`
- `running`
- `succeeded`
- `failed`
- `cancelled`
- `needs-planning`
- `blocked`
- `manual-review-required`

## Director Commands

These commands are safe orchestration primitives.

```text
ASSIGN_GOAL
ASSIGN_TASK
PAUSE_AGENT
RESUME_AGENT
CANCEL_TASK
SET_BEHAVIOR_PROFILE
REQUEST_STATUS
REQUEST_PLAN
REQUEST_BATCH_STATUS
```

Example:

```json
{
  "type": "ASSIGN_GOAL",
  "agentId": 12,
  "payload": {
    "goal": "reach-level",
    "targetLevel": 30,
    "preferredStyle": "quest-then-grind",
    "constraints": {
      "maxDeathRisk": "medium",
      "preserveMesos": 50000
    }
  }
}
```

## Capability Commands

Navigation:

```text
NAVIGATE_TO_MAP
NAVIGATE_TO_NPC
NAVIGATE_TO_ITEM_DROP
CHANGE_CHANNEL
GO_FREE_MARKET
RETURN_TO_TOWN
```

Combat:

```text
FARM_MOB
FARM_ITEM
DEFEND_SELF
CLEAR_NEARBY_THREATS
STOP_COMBAT
```

NPC/Quest:

```text
MOVE_TO_NPC_INTERACTION_RANGE
ACCEPT_QUEST
COMPLETE_QUEST
INTERACT_NPC
```

Inventory/Equipment:

```text
USE_ITEM
EQUIP_ITEM
EQUIP_BEST_UPGRADE
SELL_VENDOR_TRASH
BUY_SHOP_ITEM
DROP_ITEM
STORE_ITEM
```

Economy:

```text
SCAN_FM_ROOM
INSPECT_FM_SHOP
SEARCH_KNOWN_MARKET
BUY_MARKET_ITEM
LIST_MARKET_ITEM
UPDATE_PRICE_MEMORY
PLAN_PROFIT_TASK
```

Social:

```text
RESPOND_TO_CHAT
JOIN_PARTY
LEAVE_PARTY
FOLLOW_AGENT
ASSIST_AGENT
```

## Batch Control

The LLM should manage hundreds of agents through batch operations.

```json
{
  "type": "BATCH_ASSIGN",
  "payload": {
    "commands": [
      {
        "agentId": 12,
        "type": "ASSIGN_TASK",
        "payload": {
          "taskKind": "QUEST_CHAIN",
          "questlineId": "maple_island_basic"
        }
      },
      {
        "agentId": 51,
        "type": "ASSIGN_TASK",
        "payload": {
          "taskKind": "FM_SCOUT",
          "rooms": [1, 2, 3, 4, 5]
        }
      }
    ]
  }
}
```

## Validation Pipeline

Every action command should pass:

```text
permission check
rate-limit check
precondition check
capability validator
execution queue
progress monitor
result summarizer
memory update
```

Example for `COMPLETE_QUEST`:

```text
is quest in progress?
are requirements met?
is completion NPC known?
is NPC live on map?
is agent within range?
is NPC not do-not-auto-use?
is script risk acceptable?
execute completion through controlled capability
```

## LLM Tool Gateway

The gateway can be MCP-like, but the internal model should be stable even if MCP
is not used immediately.

Read-only tools:

```text
get_agent_state
get_agent_batch_status
search_map
find_npc
find_quest
find_item
find_drop_source
find_shop_selling
find_market_price
plan_route
inspect_inventory
inspect_quest_state
```

Action tools:

```text
submit_command
submit_batch_commands
cancel_command
pause_agent
resume_agent
update_profile_policy
```

Do not expose raw quest/shop/script functions to the LLM.

## Rate Limits

Recommended initial limits:

- LLM strategic review per active agent: 30 to 120 seconds.
- LLM idle review per agent: 2 to 5 minutes.
- Emergency review: immediate.
- FM shop scans: capped per room and per time window.
- Market buys: budget-limited and confidence-gated.
- Script-sensitive NPC actions: disabled unless explicitly allowed.
