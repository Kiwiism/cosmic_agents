# LLM Gateway Design Specification

Purpose:

```text
Define the future package that lets an LLM safely inspect, plan for, and direct
Agents through typed tools and validated command queues.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
LLM decides intent.
Agent engine executes behavior.
Server validates reality.
```

The gateway is a boundary, not a shortcut. It converts LLM tool calls into
bounded read requests, plan proposals, profile patches, or command submissions.
It never exposes raw Cosmic objects, packet functions, quest/script internals,
or direct server mutation.

## Goals

- Let an LLM manage many Agents through typed, auditable tools.
- Provide read-only tools for catalog, profile, perception, economy, plan, and
  Agent status lookup.
- Provide controlled action tools that submit commands to Agent runtime queues.
- Support batch status and batch assignment for hundreds of Agents.
- Enforce permissions, budgets, rate limits, idempotency, schema validation,
  and manual-review gates.
- Keep LLM reasoning separate from capability execution.
- Make every LLM action explainable in logs and future Agent Console pages.

## Non-Goals

- Do not let the LLM spoof packets or press client keys.
- Do not let the LLM directly call quest, shop, script, combat, or inventory
  mutation functions.
- Do not let the LLM bypass capability validators.
- Do not run LLM calls every Agent tick.
- Do not require MCP specifically; the internal gateway model should work with
  MCP, REST, local queues, or future plugin transports.
- Do not require full economy automation before budgets and validation exist.

## Gateway Layers

```text
External LLM/tool transport
  -> Gateway tool schema validation
  -> Permission and rate-limit policy
  -> Read-only query services or command queue
  -> Capability/plan/profile/economy validators
  -> Agent runtime execution
  -> Result summarizer and audit log
```

## Tool Classes

### Read-Only Tools

Purpose:

- Help LLM understand the world and Agent state without mutation.

Examples:

- `get_agent_state`
- `get_agent_batch_status`
- `get_agent_profile_summary`
- `get_agent_plan_state`
- `get_agent_perception`
- `search_map`
- `find_npc`
- `find_quest`
- `find_item`
- `find_drop_source`
- `find_shop_selling`
- `find_market_price`
- `plan_route`
- `inspect_inventory`
- `inspect_quest_state`

### Proposal Tools

Purpose:

- Let LLM suggest plans, profile changes, or economy strategies that still need
  engine validation.

Examples:

- `propose_plan`
- `propose_plan_reorder`
- `propose_profile_patch`
- `propose_economy_action`
- `propose_population_assignment`

### Command Tools

Purpose:

- Submit typed commands to Agent runtime queues.

Examples:

- `submit_command`
- `submit_batch_commands`
- `cancel_command`
- `pause_agent`
- `resume_agent`
- `assign_plan`
- `assign_goal`
- `navigate_to_point`

Command tools return accepted/queued/rejected status, not guaranteed immediate
completion.

## Control Levels

Preferred LLM control level:

```text
Strategic -> Task -> Plan/Objectives -> Capability command -> Engine execution
```

The LLM should usually assign goals, tasks, or plans. Capability-level commands
are allowed only when the command is typed, bounded, and safe.

Direct navigation to an exact point should be represented as a temporary
sidetrack objective, not as a raw movement override.

## Permission Model

Permission dimensions:

- issuer identity.
- environment: dev, soak, production.
- tool class: read, proposal, command, emergency.
- agent scope: one Agent, group, all Agents.
- action risk: safe, gameplay, economy, social, script-sensitive, emergency.
- budget: mesos, market spend, command count, batch size.
- manual-review requirement.

Default posture:

- read-only tools enabled first.
- proposal tools enabled second.
- low-risk command tools enabled after validators exist.
- economy/social/script-sensitive tools disabled until explicitly allowed.

## Rate Limits

Initial policy:

- batch status summaries preferred over per-Agent polling.
- strategic review per active Agent: 30 to 120 seconds.
- idle review per Agent: 2 to 5 minutes.
- emergency review: immediate but audited.
- market scans capped by room/time window.
- market buys capped by confidence and mesos budget.
- profile patches capped per Agent/time window.
- batch commands capped by count and risk class.

## Command Queue

The gateway submits commands into a queue owned by Agent runtime, Plan Runtime,
or Capability Runtime depending on command type.

Queue requirements:

- idempotency key.
- priority.
- deadline.
- cancellation.
- status polling.
- result summary.
- failure/blocker reason.
- audit trail.

The queue must be bounded and must not allow LLM work to starve player-visible
server work.

## Batch Control

The LLM can direct groups by:

- selecting Agents by profile, level, job, map, plan state, or role.
- assigning group goals.
- assigning plan cards.
- pausing or resuming groups.
- asking for batch status summaries.
- rebalancing population roles.

Batch operations must support:

- dry run.
- partial acceptance.
- per-Agent rejection reasons.
- max batch size.
- budget rollup.
- rate-limit rollup.

## Safety And Manual Review

Manual-review gates should trigger for:

- script-sensitive NPC actions.
- high-value item transfer.
- market buys above budget/confidence threshold.
- profile hard-policy changes.
- group commands affecting too many Agents.
- commands in sensitive maps.
- commands that conflict with profile hard constraints such as islanders never
  leaving Maple Island.

## Audit And Explainability

Every LLM interaction should record:

- tool name.
- issuer.
- request id.
- agent or group scope.
- input summary.
- validation result.
- accepted/rejected/queued status.
- command ids.
- budget consumed.
- safety warnings.
- result summary.

Decision journal links should let future analysis answer:

- What did the LLM ask for?
- Why was it allowed or rejected?
- What did the Agent actually do?
- What changed in profile, plan, inventory, economy, or relationship state?

## Relationship To Packages

Catalog Platform:

- read-only game knowledge tools.

Perception Runtime:

- current state summaries and batch status.

Profile Platform:

- profile summaries and validated profile patch proposals.

Plan Runtime:

- plan assignment, plan proposal, sidetrack command creation, plan state.

Capability Runtime:

- validates and executes command-level actions.

Economy Engine:

- market summaries, buy/sell proposals, budgets, manipulation-risk signals.

Event Bus:

- command, result, audit, rejection, and LLM summary events.

Observability:

- command counts, latencies, rejection reasons, LLM cost, queue pressure.

Server Adapter:

- final authority for live-state validation and mutation through capabilities.

## Success Criteria

The package is ready when:

- tool classes are explicit.
- read-only, proposal, and command paths are separated.
- command envelopes, results, status, and audit are defined.
- permissions and rate limits are defined.
- batch control has dry-run and partial acceptance rules.
- direct navigation-to-point is represented as a controlled objective.
- LLM cannot bypass validators or mutate server state directly.
- implementation can be transported over MCP, REST, local queue, or plugin
  without changing core contracts.
