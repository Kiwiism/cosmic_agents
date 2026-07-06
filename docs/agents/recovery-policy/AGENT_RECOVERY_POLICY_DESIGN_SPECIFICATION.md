# Agent Recovery Policy Design Specification

Purpose:

```text
Define the portable policy package that decides how an Agent recovers from
danger, resource shortage, stuck movement, blocked objectives, failed NPC/quest
actions, death loops, and repeated plan failures.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent runtime until reconstructed Agent boundaries are stable.

## Design Rule

```text
Recovery decides the safest next intent.
Capabilities execute validated recovery actions.
Plan Runtime owns whether the plan continues, pauses, or fails.
```

Recovery Policy should prevent infinite loops and unsafe forced completion. It
should return structured recovery recommendations to Plan Runtime and Capability
Runtime.

## Goals

- Provide one shared recovery decision model for all capabilities.
- Keep Agents from looping forever on blocked objectives.
- Decide when to retry, use supplies, rest, retreat, resupply, postpone, ask for
  help, sidetrack, or fail a plan.
- Support Maple Island MVP fallbacks.
- Adapt to profile risk tolerance without violating hard plan constraints.
- Emit reasoned recovery events for profile learning and observability.
- Stay decoupled from Cosmic server classes.

## Non-Goals

- Do not directly mutate HP/MP, inventory, map, quest, or mesos.
- Do not force quest completion as normal recovery.
- Do not decide long-term plan selection.
- Do not hide real bugs by silently retrying forever.
- Do not make LLM calls inline.

## Recovery Inputs

Recovery Policy receives:

- agent id.
- active plan id and objective id.
- current capability result.
- recent failure history.
- live HP/MP/death state.
- inventory and potion state.
- meso state.
- map and safety state.
- quest state.
- profile risk/tolerance preferences.
- plan focus policy.
- catalog hints.
- server load/backpressure status.

## Recovery Outputs

Recovery Policy returns:

- decision type.
- reason code.
- retryable flag.
- recommended delay.
- recommended sidetrack plan/objective.
- required capability command, if any.
- whether to pause/postpone/fail active plan.
- evidence summary.

## Decision Types

```text
RETRY_SAME_ACTION
RETRY_WITH_ALTERNATE_TARGET
REFRESH_LIVE_STATE
REST_UNTIL_SAFE
USE_ITEM
RETREAT_TO_SAFE_MAP
RESUPPLY
CLEAR_INVENTORY
ASK_FOR_HELP
POSTPONE_OBJECTIVE
SIDETRACK_PLAN
FAIL_OBJECTIVE
FAIL_PLAN
REQUEST_HUMAN_REVIEW
REQUEST_LLM_REPLAN
```

## Common Recovery Scenarios

### Low HP / Low MP

Possible decisions:

- use potion if available and allowed.
- rest or sit until safe.
- retreat to safe map.
- postpone combat objective.
- fail if repeated death loop.

### Death

Possible decisions:

- wait for respawn.
- refresh map and quest state.
- return to objective if deaths below threshold.
- postpone if death count exceeds plan/profile tolerance.

### No Potions / No Mesos

Possible decisions:

- rest between fights.
- reduce combat risk.
- resupply if shop and meso are available.
- farm low-risk mobs for meso only if plan allows.
- postpone if no safe recovery exists.

### Navigation Stuck

Possible decisions:

- retry after map/position refresh.
- choose alternate approach point.
- choose alternate portal route.
- materialize only if simulation-tier policy allows.
- block with `NAVIGATION_STUCK` after bounded attempts.

### NPC / Quest Blocked

Possible decisions:

- refresh live quest state.
- revalidate requirements.
- approach alternate NPC placement/point.
- complete missing prerequisite objective.
- block manual-review/script-sensitive quest.
- never force-complete in normal runtime.

### Inventory Full

Possible decisions:

- use stackable item if safe.
- drop only non-protected trash if policy allows.
- sell/store through a sidetrack plan.
- postpone objective.
- block Maple Island MVP if no safe cleanup exists.

### Repeated No Target / No Drop

Possible decisions:

- change channel/map/region if allowed.
- clear filler mobs if spawn-pressure policy allows.
- switch to another objective temporarily.
- continue if profile is patient and risk is low.
- postpone after dry-streak threshold.

## Maple Island MVP Recovery Rules

First deterministic run:

- no economy cleanup.
- no sell-trash plan.
- no Shanks travel.
- no force quest APIs.
- interaction realism can be off.
- spawn-pressure clearing can be off at first.

Required fallbacks:

- low HP and no potion: rest/sit until safe.
- repeated death: block or postpone with `DEATH_LOOP`.
- missing NPC: block with `NPC_MISSING`.
- missing portal: block with `MISSING_PORTAL`.
- inventory full: block with `INVENTORY_FULL`.
- missing quest item source: block with `CATALOG_FACT_MISSING`.
- repeated no target mob: alternate safe map or block.
- objective loops too long: block with evidence, do not force-complete.

Optional debug-only fallback:

- force grant/complete may exist only in explicit test/debug mode, outside
  normal autonomous runtime, with audit and clear labels.

## Profile Influence

Profile can influence:

- retry patience.
- death tolerance.
- risk tolerance.
- willingness to rest versus buy supplies.
- willingness to ask for help.
- willingness to postpone.
- willingness to farm low-risk mobs for mesos.

Profile cannot override:

- forbidden actions.
- plan hard constraints.
- server validation.
- safety limits.
- manual-review gates.

## Recovery Memory

Recovery should track bounded history:

- failure count by objective.
- failure count by capability.
- death count by plan/window.
- stuck route/point ids.
- failed NPC approach points.
- dry-streak counters.
- recent recovery actions.

This prevents repeating the same bad choice.

## Relationship To Other Packages

Plan Runtime:

- asks recovery for next step when an objective blocks or fails retryably.
- owns final plan pause/fail/postpone state.

Capability Runtime:

- reports structured failures to recovery.
- executes recovery commands if Plan Runtime approves them.

Profile Platform:

- supplies tolerance and playstyle.
- consumes recovery outcomes for adaptation.

Catalog Platform:

- supplies safe maps, shops, item sources, routes, and objective alternatives.

Event Bus:

- carries recovery decisions and outcomes to observability/profile/economy.

## Success Criteria

Recovery Policy is ready when:

- every common blocker maps to a bounded decision.
- repeated failure cannot loop forever.
- Maple Island MVP has deterministic fallbacks.
- profile can influence but not bypass safety.
- every recovery decision has a reason code and evidence.
- Plan Runtime can pause/postpone/fail based on recovery output.
