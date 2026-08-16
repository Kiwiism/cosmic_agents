# Agent world activity ownership gaps

The intended composition is one selector above four self-contained session owners. The four systems
must not directly start one another.

```text
World activity coordinator (future: choose, travel, hand off, observe outcome)
  -> TownLife session
  -> Field/grind session
  -> Quest-plan session
  -> Economy session
```

Each child owns its local runtime only after typed admission. The coordinator retains the next-goal
decision, waits for a graceful terminal result, performs inter-map travel, and then requests the next
session. A child may propose an exit or successor but must not start another child directly.

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
2. Define a typed field outcome containing XP, kills, drops, duration, depletion/no-spawn evidence,
   and failure reason for the future coordinator.
3. Separate the 93-map observation catalog from autonomous suitability policy. It is evidence, not
   yet a selector for level, build, supplies, travel cost, crowd, or quest demand.
4. Add coordinator-owned travel and admission retry/defer behavior. Field activity correctly rejects
   an Agent that has not reached the requested map.
5. Live-soak dynamic joins/leaves, maps larger than six Agents, and ranged/melee mixtures.

## Economy / Free Market

The `simulation/economy-engine` branch has the strongest bounded ownership contract: typed
entry/defer/reject, bounded sessions, graceful drain/release, restart-safe checkpoints, protected
inventory authority, physical browsing/trading, and an explicit external farming port. It correctly
does not own questing, farming, or the next objective.

Remaining gaps:

1. It is not on `master`; reconcile it with the newer foreground/session APIs before merging.
2. Complete its documented 30-day 50-to-200 Agent soak, mid-stall restart, and paired experiments.
3. Wire its typed session port into the future world coordinator and keep FM travel external.
4. Provision legitimate store permits for seller experiments; the engine intentionally does not
   grant them administratively.

## Quest plans

The universal plan executor is checkpointed, resumes individual steps, arbitrates foreground
ownership, and delegates bounded TownLife and field visits through their public lease contracts.
This is good capability composition, but the top-level plan is still an executor rather than a full
external session contract.

Quest plans should gain:

1. `AgentPlanEntryRequest(requestId, callerId, planId, inputs)` and typed accepted/deferred/rejected
   admission results.
2. A persisted `AgentPlanSessionHandle`.
3. Explicit `ACTIVE`, `SUSPENDING`, `SUSPENDED`, `DRAINING`, and terminal phases.
4. Graceful suspend/exit at an atomic step boundary, with deadline and force paths.
5. A typed outcome with completion/failure, quest progress, last checkpoint, retryability, and
   suggested successors.
6. Caller-authorized resume/cancel so an ambient coordinator cannot steal a manually started run.
7. A durable handoff event after all child TownLife/field/interaction leases are released.

The plan continues to own sequencing only. Navigation, combat, shopping, TownLife, and field
formation remain child capabilities or sessions.

## Recommended integration order

1. Live-soak and tune the seven-town ambient baseline.
2. Add the top-level quest-plan entry/session/suspend/exit contract.
3. Add typed field outcomes and durable deployment state if field population becomes permanent.
4. Reconcile and merge the economy branch without weakening its inventory authority.
5. Introduce a small world activity coordinator that selects one goal, owns travel/handoff, and
   never ticks a child system's internals.
6. Add policy for choosing quest, grind, town, or economy only after every child exposes the same
   terminal-result shape.
