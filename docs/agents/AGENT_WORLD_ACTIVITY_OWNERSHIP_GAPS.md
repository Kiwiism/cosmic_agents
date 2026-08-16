# Agent world activity ownership and handoff baseline

The intended composition is one selector above four self-contained session owners. The four systems
must not directly start one another.

```text
World activity coordinator (choose, travel, hand off, observe outcome)
  -> TownLife session
  -> Field/grind session
  -> Quest-plan session
  -> Economy session
```

Each child owns its local runtime only after typed admission. A selector retains the next-goal
decision; the handoff coordinator waits for a graceful terminal result, delegates inter-map travel,
and then requests the next session. A child may propose an exit or successor but must not start
another child directly.

## Shared lifecycle and handoff

The neutral `runtime.activity.session` package now defines the common activity kind, lifecycle
phase, admission, graceful-exit, snapshot, terminal-outcome, transfer, and handoff contracts. The
two-phase coordinator follows this sequence:

1. request a safe source exit;
2. observe that the exact source session released ownership;
3. let the external transfer port complete travel;
4. request destination admission;
5. retry explicit deferrals without replaying completed travel.

It fails closed if source ownership changes, a deadline expires, travel fails, or destination
identity does not match. It never calls a child tick or selects a goal. TownLife, field, quest, and
economy adapters are confined to the adapter package, and architecture tests prevent children from
starting sibling systems.

## TownLife

The per-Agent core has typed caller/request identity, admission, one foreground owner, graceful and
forced exit, visit leases, checkpoint restoration, interaction pause/resume, terminal events, and
map-local profiles. The external ambient baseline now adds seven-town pool deployment, 50-percent
population targeting, town/activity quotas, long dwell, rotation outcomes, chair provisioning,
standby modes, narration, and operator controls without moving population ownership into the core.

Remaining gaps:

1. Run a live seven-town soak and correct profile coordinates that the movement executor cannot
   reach; authored scenic spots are intentionally absent in the four new profiles.
2. Add separate interior profiles/excursion owners for Sleepywood hotel/sauna and future multi-map
   town activities. TownLife itself should remain map-local.
3. Persist ambient deployment roster/rotation state if seamless server-restart continuation is
   required. Individual visits are checkpointed; the observation deployment is process-local.
4. Replace placeholder social narration only when the general chat system is ready.

## Managed field/grind

`AgentFieldActivityRuntime` already supplies typed entry, a session handle, map/admission validation,
foreground arbitration, checkpointing, suspension, graceful drain, and terminal lifecycle events.
The field allocator owns formation/territory and combat owns attacks; the level 15-25 observation
harness is an external pool owner rather than combat logic.

Remaining gaps:

1. Persist observation membership/rotation only if it becomes permanent world population.
2. Populate collected-drop evidence in the typed field outcome; kill, level/EXP, duration, live-spawn,
   objective progress, and failure evidence are already projected.
3. Separate the 93-map observation catalog from autonomous suitability policy. It is evidence, not
   yet a selector for level, build, supplies, travel cost, crowd, or quest demand.
4. Live-soak dynamic joins/leaves, maps larger than six Agents, and ranged/melee mixtures.

## Economy / Free Market

The economy engine is now merged into the integration baseline and retains its bounded ownership
contract: typed entry/defer/reject, bounded sessions, graceful drain/release, restart-safe checkpoints, protected
inventory authority, physical browsing/trading, and an explicit external farming port. It correctly
does not own questing, farming, or the next objective.

Remaining gaps:

1. Complete its documented 30-day 50-to-200 Agent soak, mid-stall restart, and paired experiments.
2. Keep FM travel external to its standard activity-session adapter.
3. Provision legitimate store permits for seller experiments; the engine intentionally does not
   grant them administratively.

## Quest plans

The universal plan executor is checkpointed, resumes individual steps, arbitrates foreground
ownership, and delegates bounded TownLife and field visits through their public lease contracts.
Its top-level contract now includes caller/request identity, a persisted session handle, explicit
active/suspending/suspended/draining/terminal phases, safe-step-boundary suspend and exit, deadline
and force paths, caller-authorized resume/cancel, and typed terminal outcomes with the last cursor,
retryability, inputs, and suggested successors.

Remaining gaps:

1. Add quest-specific progress evidence to the generic plan outcome without coupling the executor
   to individual quest packs.
2. Journal world handoff state if handoffs must survive a server restart in mid-travel. Child
   sessions are checkpointed; the small coordinator is currently caller-owned and in-memory.

The plan continues to own sequencing only. Navigation, combat, shopping, TownLife, and field
formation remain child capabilities or sessions.

## Completed integration order

1. The seven-town ambient baseline remains isolated behind explicit TownLife admission.
2. Quest plans expose a top-level owned session and safe lifecycle boundaries.
3. Field sessions expose terminal kill, EXP, spawn, and objective evidence.
4. Economy is reconciled with the current foreground arbiter without weakening inventory authority.
5. All four child systems expose common snapshots, exits, admissions, and terminal outcomes through
   adapters.
6. The small world handoff coordinator owns release/transfer/admission order and no child internals.

Autonomous goal-selection policy remains deliberately separate. It should rank quest, grind,
TownLife, or economy using these outcomes only after live soak data is available; the handoff layer
must remain policy-free.

## Integration validation

Focused TownLife, field, universal-plan, economy, foreground arbitration, configuration, architecture,
and handoff suites pass after the merge. The full suite executes 5,973 tests. Its integration-caused
failures were corrected during this rollout.

Two movement-lab assertions remain red and reproduce unchanged at rollback commit `849926b50f`:

- `BotMovementSimulationLabTest.shouldFollowOwnerUsingFormationOffsetOnFlatGround`
- `BotMovementSimulationLabTest.shouldCommitJohnSecondJumpOnTheNextAiTickAfterLanding`

They are pre-existing navigation/physics baseline work, not economy or world-activity ownership
regressions, and are intentionally not altered in this integration commit.
