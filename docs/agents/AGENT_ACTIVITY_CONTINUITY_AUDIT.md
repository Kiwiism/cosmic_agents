# Agent activity continuity audit

Date: 2026-08-18

## Scope and conclusion

The four main systems have useful local lifecycle boundaries, but the world-level lifecycle is not
yet continuous or autonomous. Questing, Hunting, TownLife, and Commerce can each own and drain a
session. The Activity Host also guarantees one execution owner per tick. However, the World
Director, persistent handoff coordinator, and common session adapters are currently library
components used by tests rather than a live coordinator that connects all four systems.

Current scripted and observation commands remain usable. The crowded-field visitor lease added in
this change deliberately does not transfer foreground ownership from Questing to Hunting: it is a
temporary map-coordination lease, so an existing quest plan continues to advance normally.

## Ownership map

| System | Live owner | Durable local state | Graceful exit | Restart restore | Live world handoff |
|---|---|---:|---:|---:|---:|
| Questing | `AgentUniversalPlanRuntime` | Yes | Step boundary | Yes | No |
| Hunting | `AgentFieldActivityRuntime` plus map-local `AgentFieldRuntime` | Per-Agent visit only | Target/loot-safe boundary | Per-Agent visit | No |
| TownLife | `AgentTownLifeRuntime` | Yes | Current activity/interaction boundary | Yes | No |
| Commerce | `AgentCommerceSessionRuntime` | Yes | Protected market-operation boundary | Registry-specific | No |

`AgentActivityBootstrap` registers all four as primary controllers. Precedence orders already-active
controllers; it is not an admission policy. `AgentActivityAdmissionCoordinator` is the admission
guard used by Questing, managed Hunting, party quests, and the observable Commerce scenario.

## Crowded-field quest visitor behavior

1. A questing Agent entering an open field session presents its current required mob IDs.
2. The field coordinator first uses unused compatible station capacity.
3. If compatible capacity is occupied and preemption is enabled, a pure policy ranks incumbent
   general hunters by local mob opportunity, visitor travel distance, and restoration cost.
4. Active combat targets, active loot, young leases, cooldowns, quest visitors, support roles, and
   stations occupied by a real player cannot be displaced.
5. One selected incumbent navigates normally to a spawn-free safe spot and pauses. The visitor gets
   a temporary station lease; Questing still owns its plan and combat objective.
6. Relevant damage renews the visitor lease. Objective change/completion, map departure, or the
   progress timeout releases it.
7. The incumbent resumes grinding with its previous station as a preference. The planner can still
   reject that preference if live geometry or occupancy changed.

The feature is gated by
`server.agents.field.AgentFieldPolicyConfig.QUEST_VISITOR_PREEMPTION_ENABLED`. Observation sessions
remain closed to quest visitors regardless of the gate.

## Conflicts and continuity gaps

### P0: the World Director is not connected to live runtime

`AgentWorldDirector` selects proposals, but no production runtime constructs or ticks it. Hunting,
TownLife, and Questing do not publish world proposals; Commerce has a proposal policy but no live
consumer. Agents therefore do not autonomously choose and hand off among all four systems.

### P0: the persistent handoff state machine has no live driver or rollback

`AgentPersistentActivityHandoffCoordinator` and the four session adapters are only instantiated by
tests. No registration/tick service restores and advances in-flight handoffs. In addition, a target
admission rejection after the source has released ends in `FAILED`; the coordinator has no source
resume/rollback port. A future live rollout would risk leaving an Agent idle after a partial
handoff unless rollback is added first.

### P0: a Questing `field-visit` child conflicts with primary admission

`AgentFieldVisitPlanStepExecutor` starts a managed Hunting visit while the universal Questing plan
is active. Managed Hunting calls the primary admission coordinator, which asks Questing to drain.
That is correct for independent Hunting, but incorrect for a child visit owned by the quest step.
Current level-15 quest combat avoids this conflict by retaining Questing ownership and using the
ordinary combat loop/map visitor overlay. Before converting those plans to typed field visits, add
an explicit delegated-child activity contract rather than bypassing admission ad hoc.

### P1: planned TownLife is implemented as overlapping primary state

The TownLife plan step starts TownLife directly and pauses the foreground clock. This works because
TownLife has higher host precedence, but both Questing and TownLife remain active primary sessions.
The arrangement is intentional compatibility behavior, not the standardized handoff model. It can
conflict with an independent TownLife request or with future world-level ownership assertions.

### P1: restart restoration can rehydrate several primary owners

`AgentRegistrationCoordinator` independently restores Questing, TownLife, and Hunting checkpoints,
then reattaches the plan. It does not reconcile conflicting retained sessions before the first
tick. Precedence prevents two controllers from executing simultaneously, but a lower-priority
session can remain retained and silently starved. Commerce restoration is separate again.

### P1: map coordination is intentionally ephemeral

The per-Agent Hunting visit is checkpointed, but group formation, live cells, station assignments,
quest visitor displacement, and incumbent cooldowns are map-instance state. They disappear on
restart. This is safe—the Agent falls back to local admission/replanning—but it is not a seamless
continuation and can temporarily change formation after recovery.

### P1: Commerce admission is not exposed through one common live facade

The durable per-Agent Commerce registry exists, but preparation, foreground admission, destination
travel, and entry are assembled by callers such as the observation scenario. The common Economy
session adapter is not the registry's live entry boundary. Production autonomous Commerce needs one
idempotent facade that performs preflight, admission, registry preparation, and cleanup on failure.

The audit found that the Commerce observation harness reached `DatabaseConnection`, `TimerManager`,
and live clients directly. This change moves those calls behind the economy Cosmic adapter, shared
Agent scheduler, and client/map gateways instead of expanding the boundary allowlist.

### P2: terminal outcomes are not consumed uniformly

Each subsystem exposes terminal evidence, but there is no shared live outcome inbox that the World
Director uses to choose retry, alternate quest, safe grinding, or TownLife. A failed plan can expose
successor IDs and Commerce can retain an outcome awaiting acknowledgement, yet no common policy
guarantees the next activity.

### P2: no runtime invariant reports retained-owner conflicts

The host records the selected controller, but it does not emit a warning when more than one
independent primary session remains active. Without an invariant projection, a starved restored
session can look like ordinary idleness.

## Recommended continuation order

1. Define delegated child activities for Questing-owned Hunting and TownLife visits.
2. Add source rollback/resume to the handoff state machine and test every failure phase.
3. Add a registration-time retained-owner reconciler before plan reattachment.
4. Build one live handoff registry/driver and persist its active handoff ID per Agent.
5. Wire proposal producers for all four systems into a live World Director service.
6. Add a common terminal-outcome inbox with retry, alternate-work, safe-Hunting, and TownLife
   fallback policies.
7. Route autonomous Commerce through one idempotent admission facade.
8. Add invariant metrics for multiple retained primaries, handoff age, failed rollback, and time
   without an execution owner.
9. Only then migrate existing quest hunt steps from ordinary quest combat to delegated typed
   Hunting visits.

This ordering keeps the proven level-15 plan behavior intact while closing the lifecycle gaps from
the inside out.
