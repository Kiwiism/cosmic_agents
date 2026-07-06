# NPC Quest Capability Design Specification

Purpose:

```text
Define the future capability package that lets reconstructed Agents validate,
start, and complete NPC-gated quests without replaying full dialogue, while
still obeying live server requirements and optional human-like presentation
delays.
```

This is a post-reconstruction package contract. It must not be wired into live
Agent behavior until the reconstructed Agent runtime, Capability Runtime, Plan
Runtime, Catalog Runtime, and Server Adapter boundaries are stable.

## Design Rule

```text
Catalog suggests what an NPC can do.
Capability validates what the Agent may do now.
Server Adapter commits the quest action.
Plan Runtime owns whether the objective is done.
```

The capability may skip visual dialogue replay, but it must not skip gameplay
requirements.

## Goals

- Provide direct, validated `quest-start` and `quest-complete` commands.
- Provide validation-only dry runs for planning and LLM tools.
- Use NPC catalog placement, action, dialogue timing, and interaction spot data.
- Check live map, NPC presence, range, quest state, requirements, inventory,
  reward choice policy, cancellation, and forbidden actions.
- Return structured blockers instead of looping or force-completing.
- Support Maple Island MVP special cases.
- Keep dialogue-length delay and random approach points outside plan cards.
- Keep the package portable across Cosmic-like servers through Server Adapter
  interfaces.

## Non-Goals

- Do not implement generic arbitrary NPC script automation.
- Do not replay uncontrolled dialogue menu options.
- Do not call `forceStart` or `forceComplete` in normal autonomous runtime.
- Do not decide the next plan objective.
- Do not own navigation pathfinding.
- Do not own reward valuation beyond asking Profile/Economy policy.
- Do not mutate server state without live validation immediately before commit.

## Capability Scope

Initial commands:

- `VALIDATE_NPC_QUEST_ACTION`
- `APPROACH_NPC_FOR_QUEST`
- `START_QUEST_AT_NPC`
- `COMPLETE_QUEST_AT_NPC`
- `AUTO_COMPLETE_QUEST`
- `SELECT_QUEST_REWARD`

Later commands:

- `START_DIALOGUE_OPTION_QUEST`
- `COMPLETE_DIALOGUE_OPTION_QUEST`
- `JOB_ADVANCE_QUEST`
- `EVENT_ENTRY_QUEST`
- `SCRIPTED_SERVICE_QUEST`

## Layer Responsibilities

Plan Runtime:

- chooses quest objective.
- passes quest id, NPC id, expected map, and objective context.
- handles retry, recovery, sidetrack, and completion.

NPC Quest Capability:

- resolves NPC catalog action.
- chooses/validates approach point through navigation.
- applies optional realism delay.
- validates quest requirements.
- executes start/complete through Server Adapter.
- returns structured result and evidence.

Catalog Runtime:

- supplies NPC placements, approach points, action rows, quest requirement
  summaries, dialogue timing, manual-review flags, and source references.

Server Adapter:

- reads live quest state.
- reads live inventory/level/job/map/NPC state.
- checks authoritative quest requirements.
- commits quest start/complete using normal server rules.

Profile/Economy:

- resolves reward choices.
- influences delay and approach variation.
- influences postpone decisions when requirements are hard or dangerous.

## Quest Action Types

### Quest Start

Required validation:

- Agent is alive and controllable.
- Agent is on expected map or can navigate there.
- NPC exists on live map.
- Agent is within interaction range after approach.
- Quest is not already completed.
- Quest is not already started unless idempotent start is allowed.
- Level/job/prerequisite/item/quest requirements are satisfied.
- Action is not marked manual-review or blocked.
- Plan forbidden actions do not block this NPC/action.

Success:

- live quest state becomes `STARTED`, or already started if idempotent.

### Quest Complete

Required validation:

- all Quest Start validations that apply to completion.
- Quest is started or marked auto-completable.
- Required items exist.
- Required mob counts/progress are complete.
- Required map/NPC/party/job/level conditions are satisfied.
- Inventory has enough free slots for rewards.
- Reward choice is resolved when required.
- Completion is not forbidden by active plan.

Success:

- live quest state becomes `COMPLETED`, or is already completed if idempotent.

### Auto-Complete Quest

Use only when catalog and live quest model both confirm the quest has no
required complete NPC or is intended to complete from current live state.

Required validation:

- quest id is explicitly cataloged as auto-complete or no-complete-NPC.
- live server requirement check passes.
- plan allows auto-complete for this quest.
- no manual-review flag blocks it.

Maple Island MVP known auto-complete assumptions:

- `1030`
- `8023`

These must remain explicit plan/catalog rules, not global assumptions.

## Maple Island MVP Special Rules

- Start at `10000 Mushroom Town`.
- Complete selected Maple Island questline and stop at `2000000 Southperry`.
- Quest `1046` may be started but must remain incomplete.
- Quest `1028` must not be completed.
- Quest `8142` is excluded.
- Todd quests `1018` and `1035` remain optional/manual-review unless enabled.
- Shanks `22000` may be used for quest `1026` completion.
- Shanks travel off Maple Island is forbidden.
- Pio `1008` requires reactor-box item handling outside this capability.
- Roger `1021` requires item-use handling outside this capability.
- Yoona `8020` requires scripted/granted shopping-guide item handling outside
  this capability.

## Interaction Realism

Plan objectives should stay clean. Realism is capability/runtime policy:

- `OFF`: no simulated delay, deterministic nearest valid approach.
- `LIGHT`: small delay and seeded approach variation.
- `FULL`: dialogue-length delay, profile reading speed, jitter, point
  reservation, anti-clustering, and failed-point memory.

The NPC Quest Capability asks Interaction Realism for:

- selected approach point.
- delay before action.
- whether this is first or repeated dialogue for this Agent.

## Reward Choice Policy

If completion requires choosing a reward, the capability asks a reward policy:

Inputs:

- quest id.
- available rewards.
- Agent job/build intent.
- inventory state.
- market value if economy is available.
- protected item policy.
- profile preference.

Outputs:

- selected reward id/index.
- reason code.
- confidence.
- manual review flag.

If no safe choice exists, return `REWARD_CHOICE_UNRESOLVED`.

## Blocker Categories

Use structured blockers:

- `NPC_MISSING`
- `NPC_OUT_OF_RANGE`
- `NPC_UNREACHABLE`
- `PLACEMENT_MISMATCH`
- `QUEST_NOT_AVAILABLE`
- `QUEST_ALREADY_COMPLETED`
- `QUEST_ALREADY_STARTED`
- `QUEST_REQUIREMENT_NOT_MET`
- `QUEST_PROGRESS_INCOMPLETE`
- `REQUIRED_ITEM_MISSING`
- `INVENTORY_FULL`
- `REWARD_CHOICE_UNRESOLVED`
- `MANUAL_REVIEW_REQUIRED`
- `SCRIPT_SENSITIVE`
- `FORBIDDEN_ACTION`
- `AGENT_STATE_BLOCKED`
- `CANCELLED`
- `TIMED_OUT`
- `SERVER_REJECTED`

## Failure Policy

Recommended handling:

- missing NPC: refresh map/NPC state once, then block.
- out of range: request navigation/approach retry.
- unreachable point: choose another approach point.
- unmet quest requirement: report exact unmet requirement to Plan Runtime.
- inventory full: request Recovery/Supply/Inventory plan if allowed.
- reward unresolved: ask Profile/Economy/manual policy.
- manual-review/script-sensitive: block and do not execute.
- server rejected after validation: refresh live state and return
  `SERVER_REJECTED`.

## Audit And Replay Safety

Every mutating quest action emits:

- agent id.
- plan id.
- objective id.
- action type.
- quest id.
- NPC id.
- map id.
- selected placement key.
- selected approach point id.
- delay applied.
- result status.
- reason code.
- changed quest/item/meso state summary.
- catalog confidence.
- live validation summary.

On resume after relog/restart:

- reload live quest and inventory state.
- mark already satisfied objectives complete.
- never repeat a completion purely from persisted local state.
- do not duplicate rewards.

## Success Criteria

The package is ready for Maple Island MVP when:

- validation-only dry run explains whether an Agent can start/complete a quest.
- direct start/complete uses normal server validation and commit paths.
- wrong NPC/map/range/quest state is rejected.
- Shanks travel is blocked while Shanks quest completion is allowed.
- `1046` start-only rule is enforced.
- `1028` completion is blocked.
- auto-complete quests are explicit and validated.
- reward choice blocker exists.
- interaction realism can be toggled off for deterministic tests.
- every result has reason codes and evidence.
