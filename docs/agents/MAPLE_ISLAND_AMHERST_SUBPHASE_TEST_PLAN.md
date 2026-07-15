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

The implemented entry point is `AmherstTestResetService.configuredHarness()`.
It is disabled by default and reads JVM system properties:

```text
-Dagents.amherst.reset.enabled=true
-Dagents.amherst.reset.characterIds=7
-Dagents.amherst.reset.characterNames=AmherstTestAgent
```

An id or exact name must be allowlisted, and the live port verifies any supplied
id/name pair against the same online Agent. The reset API is not registered as
a normal Agent command or capability.

The service should support these modes:

| Mode | Purpose |
| --- | --- |
| `clean-lv1-start` | Reset the Agent as if newly created: level 1 beginner at `10000 Mushroom Town`. |
| `quest-scenario` | Warp and reset only the relevant quest/items for one capability test. |
| `amherst-ready` | Place the Agent in `1000000 Amherst` with prerequisite state seeded for Amherst-only tests. |
| `amherst-mvp-clean` | Reset all Amherst-slice quest IDs and start from `10000 Mushroom Town`. |

`amherst-ready` seeds all covered pre-Amherst quests as fixture-complete and
places `1037 Help Hunt the Snails` in a started state with its ten-snail
progress ready to report. It does not grant Amherst quest rewards.

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
| Equipped items | Preserve the test Agent's currently equipped baseline, including gender-specific starter gear and cash-cover slots; do not replace it with a hard-coded outfit. |
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
| Quest state read | Query `1031`; assert `NOT_STARTED`; start it; assert `STARTED`; complete it; assert `COMPLETED`. |
| NPC quest interaction | Start `1031` at Heena `2101`, complete it at Sera `2100`. |
| Navigation | Navigate `10000 -> 20000 -> 30000 -> 30001 -> 30000 -> 50000 -> 1000000` with arrival checks. |
| Item use | Reset `1021`; run Roger's scripted start, consume `2010007`, run scripted completion, and verify the 10 EXP plus potion rewards. |
| Combat objective | Reset `1037`; start at Sam `2005`; kill 10 Snails `100100`; verify progress; complete at Maria `2103`. |
| Kill and required loot | Reset `1035`; start at Todd `2004`; kill Tutorial Jr. Sentinel `9300018`; loot `4031802`; complete at Peter `2002`. |
| Tutorial contact immunity | Overlap the Agent with Tutorial Jr. Sentinel `9300018`; verify no HP loss or knockback from touch damage. |
| Robin tutorial quiz | Reset `1036`; start and complete Robin the Walking Encyclopedia at Robin `2003`; verify 40 EXP. |
| Loot/item turn-in | Reset `1038`; complete Maria-to-Lucas letter handoff and verify quest item movement. |
| Quiz/dialogue | Reset Rain chain; complete `1009`; optionally continue to `1010`; verify deterministic answer path. |
| Reactor interaction | Warp Amherst; reset `1008`; start at Pio `10000`; approach within 60 px; visibly swing four times with cooldown; loot one `4031161` and one `4031162`; complete. |
| Scope safety | Attempt route past Amherst, Shanks/off-island travel, or `1028`; expect forbidden/blocker result. |
| Journal/observability | Assert every objective records start, end, status, retry count, and blocker/reason when applicable. |

## Recommended Test Order

Run smoke tests in this order:

1. Reset harness.
2. Capability runtime frame-stack handoff/resume.
3. Navigation wrapper parity.
4. Combat wrapper parity.
5. `1031` NPC quest flow.
6. `1021` Roger apple item use.
7. `1035` Tutorial Jr. Sentinel kill, loot, and Peter turn-in.
8. `1036` Robin tutorial quiz.
9. `1037` kill 10 Snails.
10. `1038` quest-item delivery.
11. `1009` Rain quiz.
12. `1008` Pio start, four reactor hits, loot, completion, and `1020` follow-up.
13. Scope safety blockers.
14. Full Amherst sub-phase plan.

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

- `1018` remains excluded because it requires the legacy Wizet Plain Suit.
- `1035` is the clean replacement visible on map `40000` and is required.
- `1000`, `1001`, `1003`-`1006`, `1025`, `1029`, and `1030` require the
  unequipped Wizet Plain Suit `1042003`; `8031` depends on that legacy chain.
  They are excluded from the clean-character run.
- `1019`, `1022`, `1026`, `1027`, and `1040` are not Amherst completion goals
  because they complete beyond Amherst or depend on later segments.
- Reactor debug grant is useful as a fallback test, but it must be clearly
  separated from the normal reactor capability test.

## Phase 2 Automated Evidence

Phase 2 now has automated tests for all objective parent types and the plan
runtime. The tests run through `AgentCapabilityRuntime`, assert that the parent
is suspended while each child owns the top frame, and use a mutable gateway
fixture only to model normal Cosmic results. No objective test directly marks
its target quest complete or grants its target reward.

Covered objective fixtures:

| Pattern | Automated fixture |
| --- | --- |
| NPC delivery | `1031` in the minimal plan |
| Item use | `1021` and item `2010007` |
| Combat | `1035` with required loot `4031802`; `1037` with ten `100100` kills |
| Quest-item delivery | `1038` |
| Quiz | `1009` |
| Reactor and loot | `1008`, items `4031161` and `4031162` |
| Scope | `1028`, map `1010000`, off-island map, Shanks `22000` |
| Runtime failure | child retry exhaustion, parent timeout, cancellation |

The card tests load the authoritative Amherst JSON, verify stable generated
ids, instantiate one typed command for every declared objective kind, and
exercise structured validation errors. Persistence tests round-trip the atomic
JSON snapshot and prove idempotent transitions. Reconciliation tests distinguish
started, completed, available, and unavailable quest state and cover inventory
and kill-progress evidence.

The minimal plan proof executes `1031` and `1021` in order before a
verified stop. It restarts from a fresh `AgentRuntimeEntry` and the saved file,
resumes at the first live-unsatisfied objective, and asserts that already paid
quest completions are not called again. Cancellation is persisted and the same
objective can be safely attempted on a later start.

Still required as Phase 3 live smoke:

- visible movement and portal packet behavior in the v83 client;
- Rain dialogue presentation and answer flow;
- Pio reactor animation, drop ownership, and pickup packets;
- full reset-to-Amherst execution of all 18 selected clean-character quests;
- adjacent-map expansion or route-planner support for plan-card jumps that do
  not have a direct portal.

## Manual Live Plan Command

GM level 6 can operate the guarded live test through:

```text
!amherst run AmherstRun
!amherst reset <AgentIGN>
!amherst start <AgentIGN>
!amherst next <AgentIGN>
!amherst retry <AgentIGN>
!amherst status <AgentIGN>
!amherst list <AgentIGN> [page]
!amherst journal <AgentIGN> [count]
!amherst cancel <AgentIGN>
!amherst resume <AgentIGN>
```

`!amherst run AmherstRun` is the showcase path. It spawns the configured
Agent without follow mode or party membership, immediately applies the guarded
clean reset at the starting position, waits three seconds, and then runs every
objective automatically. The Agent must already exist and be controllable by
the command issuer. Resetting before the countdown prevents the Agent from
walking toward the owner and then snapping back to the start.

The clean reset preserves the Agent's equipped baseline as loaded at reset
time. Inventory rewards, quest items, mesos, skills, stats, and quest progress
are still cleared. Set the intended starter appearance and equipped items
before starting the showcase.

The named character must already be a spawned Agent controlled by the command
issuer. `reset` remains disabled unless the existing Amherst reset feature flag
and exact character id/name allowlist permit it. A successful reset clears the
Agent fixture and only then deletes that character's durable Amherst progress.

`start` and `resume` use manual execution mode. Start authorizes exactly one
live-unsatisfied objective. After its terminal result, the runner reports the
result and overall count, previews the next objective, and remains paused until
`next` or `retry` grants one more objective. Repeated ticks cannot advance a
paused manual plan. There is intentionally no force-skip command.

The owning player receives messages for:

- objective number, description, map, and expected child sequence;
- child capability start, result, retry, and blocker;
- objective terminal status and reason;
- Agent level and EXP before and after each objective;
- satisfied-objective total and next objective;
- cancellation, plan error, and final completion.

With `server.AGENT_LEGACY_DIALOGUE_ENABLED: false`, NuTNNuT-derived ambient,
status, combat, supply, trade, and welcome-back lines are suppressed. The
`server.AGENT_AMHERST_DEBUG_MESSAGES_ENABLED` flag controls detailed yellow
runner output. `server.AGENT_AMHERST_INTENTION_CHAT_ENABLED` independently
controls the Agent's map-chat announcement of the map, NPC, quest name,
kill/loot target, or reactor action it is about to perform.

The assigned `maple-island-quester` behavior profile defines a randomized
pause after the Agent reaches and faces an NPC but before it interacts. It also
defines a separate randomized pause after an automatic objective completes and
before the next objective is announced and assigned. Manual authorization does
not add the between-objective delay. The executable profile is
`src/main/resources/agents/profiles/maple-island-quester.profile.json`; use
zero-valued ranges there for deterministic local timing tests. Delay maximums
must remain below the 30-second child-capability timeout.

NPC and navigation arrival cannot complete while the Agent is airborne. The
capability yields to movement physics until the Agent lands, then faces the NPC
and starts the profile-owned interaction pause.

After the final objective equips the Relaxer state, the Amherst plan gate keeps
ordinary follow and fidget movement from replacing the chair animation. The
Agent resumes ordinary ticks after it stands.

Use `!amherst sit <AgentIGN>` to force and rebroadcast Relaxer `3010000` for an
early chair-render test. Use `!amherst stand <AgentIGN>` to clear that state.

`list` is paginated in groups of ten. `reset` prints the complete objective
list only when Amherst debug messages are enabled. `status`
also reports session mode, current child capability, objective count, required
quest count, map, Agent level/EXP, and last runner error. `journal` prints at most the newest 20
durable events.
