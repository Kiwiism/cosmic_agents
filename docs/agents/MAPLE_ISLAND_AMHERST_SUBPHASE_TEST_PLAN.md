# Maple Island Amherst Sub-Phase Test Plan

This document defines the reset harness and capability smoke tests for the
Maple Island Amherst sub-phase MVP.

Related scope document:

```text
docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md
```

## Goal

Before running the full Amherst sub-phase plan, verify each required capability
in isolation with a clean and repeatable test agent.

The full run must use the capability runtime active-frame model:

```text
one active capability frame
explicit handoff to child primitive capability
resume parent objective after child terminal result
objective advances only after parent verifies success
```

The preferred workflow is:

1. Reset the same test Agent to a known clean state.
2. Run one capability smoke test.
3. Assert live game state and journal output.
4. Reset again before the next test.
5. Run the full Amherst sub-phase only after the capability tests pass.

## Reset Harness

Add a test-only reset service or command, guarded by test configuration and an
Agent allowlist.

Suggested name:

```text
AgentTestRunResetService
```

The service should support these modes:

| Mode | Purpose |
| --- | --- |
| `clean-lv1-start` | Reset the Agent as if newly created: level 1 beginner at `10000 Mushroom Town`. |
| `quest-scenario` | Warp and reset only the relevant quest/items for one capability test. |
| `amherst-ready` | Place the Agent in `1000000 Amherst` with prerequisite state seeded for Amherst-only tests. |
| `amherst-mvp-clean` | Reset all Amherst-slice quest IDs and start from `10000 Mushroom Town`. |

### Clean Level 1 Reset

`clean-lv1-start` should reset:

| State | Reset Behavior |
| --- | --- |
| Map | Move to `10000 Mushroom Town`. |
| Level/job/EXP | Restore level 1 beginner baseline. |
| Stats | Restore beginner baseline or deterministic test baseline. |
| HP/MP | Restore to full. |
| Meso | Set to controlled baseline, preferably `0` unless a test needs otherwise. |
| Inventory | Remove non-starter items and Maple Island quest items. |
| Equipped items | Restore starter-only equipment and weapon. |
| Quest status | Remove/reset all selected Amherst-slice quest statuses and progress. |
| Runtime state | Clear movement, combat, loot, pending actions, task queue, cooldowns, and stale objective state. |
| Map drops | Remove loose drops from previous tests where possible. |
| Reactors | Reset current-map reactors when a reactor test starts. |

Prefer resetting one fixed test Agent in place so `agent_id` and character links
remain stable. Disposable generated test characters are acceptable for pure
JUnit-style tests, but less useful for repeated live smoke runs.

## Test Rule

Fixture setup may directly reset or seed state.

The capability under test must use the normal game path.

Examples:

| Scenario | Allowed |
| --- | --- |
| Reactor test setup | Warp to Amherst, reset `1008`, reset reactors. |
| Reactor test action | Hit/open reactor boxes, loot `4031161` and `4031162`, complete through normal quest flow. |
| Combat test setup | Reset `1037`, place Agent near Snail map. |
| Combat test action | Start quest, kill 10 Snails, complete at Maria. |
| Debug resolver test | Grant Pio reactor items, but only in a separately named debug-only test. |

Do not force-complete the quest that the current smoke test is meant to prove.

## Capability Smoke Tests

| Capability | Smoke Test |
| --- | --- |
| Reset harness | Run `clean-lv1-start`; assert map `10000`, level 1, clean quest state, no stale Maple Island quest items. |
| Capability runtime frame stack | Start a fake objective that requests navigation; assert parent pauses, navigation child runs, child success resumes parent, parent verifies and succeeds. |
| Navigation wrapper parity | Run legacy movement tick and `NavigationCapability` tick from identical setup; compare position, move-target state, physics/navigation state, and arrival result. |
| Combat wrapper parity | Run legacy grind/combat tick and `CombatCapability` tick from identical setup; compare selected target, attack plan route where observable, cooldown, consumed tick, and movement target result. |
| Quest state read | Query `1000`; assert `NOT_STARTED`; start it; assert `STARTED`; complete it; assert `COMPLETED`. |
| NPC quest interaction | Start `1000` at Heena `2101`, complete at Sera `2100`, start `1001`, complete at Heena. |
| Navigation | Navigate `10000 -> 20000 -> 30000 -> 30001 -> 30000 -> 50000 -> 1000000` with arrival checks. |
| Item use | Reset `1021`; start Roger's Apple at Roger `2000`; use item `2010007`; complete at Roger. |
| Combat objective | Reset `1037`; start at Sam `2005`; kill 10 Snails `100100`; verify progress; complete at Maria `2103`. |
| Loot/item turn-in | Reset `1005`/`1006`; complete Maria/Lucas/Maria letter handoff and verify quest item movement. |
| Quiz/dialogue | Reset Rain chain; complete `1009`; optionally continue to `1010`; verify deterministic answer path. |
| Reactor interaction | Warp Amherst; reset `1008`; start at Pio `10000`; reset reactors; hit boxes; loot `4031161` and `4031162`; complete. |
| Auto-complete | Reset `1030`; start at Maria `2103`; complete through explicit auto-complete special handling. |
| Scope safety | Attempt route past Amherst, Shanks/off-island travel, or `1028`; expect forbidden/blocker result. |
| Journal/observability | Assert every objective records start, end, status, retry count, and blocker/reason when applicable. |

## Recommended Test Order

Run smoke tests in this order:

1. Reset harness.
2. Capability runtime frame-stack handoff/resume.
3. Navigation wrapper parity.
4. Combat wrapper parity.
5. `1000`/`1001` NPC quest flow.
6. `1021` Roger apple item use.
7. `1037` kill 10 Snails.
8. `1005`/`1006` quest-item delivery.
9. `1009` Rain quiz.
10. `1008` Pio reactor.
11. `1030` Maria auto-complete.
12. Scope safety blockers.
13. Full Amherst sub-phase plan.

This order keeps failures small: each later test depends on fewer unproven
systems, and the full Amherst run only starts once the core capability set has
already passed in isolation.

## Full Amherst Sub-Phase Smoke

After the capability smoke tests pass, run the Amherst sub-phase plan from a
fresh `amherst-mvp-clean` reset.

Expected final state:

| State | Expected |
| --- | --- |
| Map | `1000000 Amherst` |
| Completed covered quests | Selected Amherst roster from the sub-phase scope document |
| Later-map quests | Not started unless explicitly selected as setup; not required for completion |
| Off-island travel | Not used |
| Shanks travel | Not used |
| Journal | Contains complete objective history with no unresolved retry loops |

Required capability trace shape:

```text
objective-start
capability-handoff-requested
child-capability-started
child-capability-succeeded
parent-capability-resumed
objective-succeeded
```

The full smoke is not accepted if it completes by direct scripted mutation
outside the capability runtime.

## Notes

- `1018` and `1035` remain excluded because the original Maple Island MVP marked
  them legacy/tutorial-sensitive.
- `1019`, `1022`, `1026`, `1027`, and `1040` are not Amherst completion goals
  because they complete beyond Amherst or depend on later segments.
- Reactor debug grant is useful as a fallback test, but it must be clearly
  separated from the normal reactor capability test.
