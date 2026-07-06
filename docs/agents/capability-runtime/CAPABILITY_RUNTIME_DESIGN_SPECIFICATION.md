# Capability Runtime Design Specification

Purpose:

```text
Define the common runtime layer that receives typed capability commands from
Plan Runtime, LLM Gateway, tests, or Agent runtime code and routes them through
validation, execution, result mapping, audit, and cancellation.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent runtime until the reconstructed Agent engine exposes stable boundaries.

## Design Rule

```text
Planner decides objective.
Capability validates and executes one action family.
Server Adapter validates live truth.
Result is structured, auditable, and cancellable.
```

Capabilities are the only normal path from plans/LLM/profile/economy into
mutating Agent actions.

## Goals

- Provide one command/result model for navigation, combat, looting, inventory,
  NPC, quest, shop, item-use, skill, recovery, social, and economy actions.
- Make every mutating action validate against live server state.
- Make every action return structured status and reason codes.
- Support cancellation, timeout, retry, and stuck detection consistently.
- Emit audit and observability events for debugging thousands of Agents.
- Keep catalog facts advisory and server validation authoritative.
- Keep capability implementations modular and testable.

## Non-Goals

- Do not decide which objective an Agent should do next.
- Do not own profile preferences or economy valuation.
- Do not own static catalog building.
- Do not directly expose raw Cosmic APIs to LLM or Plan Runtime.
- Do not force every capability to be implemented before Maple Island MVP.

## Capability Families

Minimum families:

- `NAVIGATION`
- `PORTAL_TRAVEL`
- `NPC_INTERACTION`
- `QUEST`
- `COMBAT`
- `LOOT`
- `INVENTORY`
- `ITEM_USE`
- `RECOVERY`
- `SHOP`
- `SKILL`
- `SOCIAL`
- `ECONOMY`
- `DIRECT_CONTROL`

Maple Island MVP requires:

- navigation to map/NPC/portal/point.
- portal travel verification.
- NPC quest interaction.
- quest read/start/complete.
- combat objective mode.
- loot objective mode.
- inventory read/free-slot/item-count.
- item use for Roger's apple.
- reactor item acquisition for Pio.
- recovery blockers.

## Capability Lifecycle

```text
RECEIVED
  -> VALIDATING_STATIC
  -> VALIDATING_LIVE
  -> QUEUED
  -> RUNNING
  -> WAITING
  -> SUCCEEDED
  -> FAILED_RETRYABLE
  -> BLOCKED
  -> CANCELLED
  -> TIMED_OUT
```

State meanings:

- `RECEIVED`: command accepted by gateway.
- `VALIDATING_STATIC`: check command shape, catalog facts, capability support.
- `VALIDATING_LIVE`: check live map, NPC, quest, inventory, HP/MP, cooldowns.
- `QUEUED`: waiting for scheduler/budget/action lock.
- `RUNNING`: capability is actively working.
- `WAITING`: waiting for movement, animation, server state, or cooldown.
- `SUCCEEDED`: live exit criteria are satisfied.
- `FAILED_RETRYABLE`: retry may succeed.
- `BLOCKED`: policy or live state prevents progress.
- `CANCELLED`: caller or higher-priority policy stopped command.
- `TIMED_OUT`: command exceeded timeout.

## Command Principles

Every command must contain:

- command id.
- agent id.
- requested capability family.
- command type.
- source: plan, LLM, manual, recovery, profile, economy, test.
- plan id and objective id when applicable.
- target ids and quantities.
- timeout.
- retry policy.
- validation mode.
- audit context.

Commands should not contain:

- raw `Character`.
- raw `MapleMap`.
- raw packet.
- direct script manager references.
- mutable server object references.

## Result Principles

Every result must contain:

- command id.
- status.
- reason code.
- message.
- whether live state changed.
- retryable flag.
- blocker type.
- duration.
- evidence map.
- next suggested action if known.

Results should be compact enough for logs and rich enough for plan/profile
journals.

## Validation Layers

### Static Validation

Checks:

- command type is known.
- required fields are present.
- ids are legal integers/strings.
- catalog supports the requested target if catalog is required.
- capability implementation is installed.
- target action is not forbidden by active plan.

### Live Validation

Checks:

- Agent still exists.
- Agent is in valid runtime state.
- map exists.
- NPC/mob/item/portal exists if required.
- Agent is in range or can navigate into range.
- quest state satisfies start/complete requirements.
- inventory has required item/free slot.
- HP/MP/death state is safe enough.
- cooldown/action lock allows action.
- server policy allows action.

### Commit Validation

Before mutation, validate again:

- state did not change while command was queued/running.
- target still exists.
- action remains legal.
- capability can commit through Server Adapter.

## Cancellation

Every long-running capability must support cancellation:

- navigation.
- combat/farming.
- shop visit.
- NPC approach.
- loot collection.
- recovery.
- direct movement.

Cancellation should:

- stop future work.
- clear internal command state.
- preserve safe Agent state.
- emit `CAPABILITY_CANCELLED`.
- return a structured result to the Plan Runtime.

## Timeouts

Timeouts should be command-specific:

- navigation to nearby point: short.
- navigation to far map: route ETA plus margin.
- NPC interaction: approach ETA plus delay.
- combat/loot objective: objective budget.
- shop visit: short.
- recovery: bounded retries.

Timeouts return `TIMED_OUT`, not an exception.

## Audit And Observability

Each command emits:

- received.
- validation failed, if any.
- started.
- progress milestone, for long commands.
- completed/blocked/cancelled/timed out.

Audit fields:

- agent id.
- plan id.
- objective id.
- capability family.
- command type.
- target ids.
- source.
- duration.
- reason code.
- live-state evidence.

## Relationship To Plan Runtime

Plan Runtime:

- creates or requests commands.
- owns objective progress.
- interprets capability result.
- decides retry/sidetrack/recovery.

Capability Runtime:

- validates command.
- executes or delegates execution.
- returns result.
- emits events.

Capability Runtime must not mark plan objectives complete by itself.

## Relationship To LLM Gateway

LLM Gateway may submit commands only through Capability Runtime or Plan Runtime.

Allowed direct LLM capability examples:

- navigate to exact point as a temporary sidetrack.
- inspect current capability status.
- cancel a command.

Disallowed:

- force quest complete.
- directly mutate inventory.
- spoof movement packet.
- bypass server validation.

## Success Criteria

Capability Runtime is ready when:

- all capability families share one command/result envelope.
- every mutating action has static, live, and commit validation hooks.
- commands can be cancelled.
- timeouts produce structured results.
- audit events are emitted.
- unknown/missing capabilities return `CAPABILITY_MISSING`.
- Plan Runtime can route Maple Island MVP objectives through capability
  commands without calling concrete implementations directly.
