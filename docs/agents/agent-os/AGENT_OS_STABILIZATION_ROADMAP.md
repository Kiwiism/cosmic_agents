# Agent OS Stabilization Roadmap

## Target contract

Every autonomous foreground action must be explainable as one chain:

```text
immutable authoritative snapshot
  -> selected goal
  -> versioned universal plan
  -> typed capability command
  -> terminal capability/objective result with evidence
```

Only the Cosmic adapter captures mutable server state or performs mutations.
The autonomy kernel, policy, plan runtime, and future LLM provider operate on
immutable contracts. TownLife remains a specialized activity schema, but it
uses the same foreground arbitration, capability, evidence, and policy
boundaries.

## Current kernel state

The universal executor is the authoritative lifecycle owner for versioned
plans. Each universal-plan step now records:

- the immutable Agent and map-perception snapshot used for the decision;
- the selected goal type;
- plan ID and version;
- step ID, operation, and declared capability IDs;
- a stable chain/plan/step/attempt correlation ID;
- terminal status, reason, and completion time.

The bounded record lives in `AgentAutonomyCycleState`; the four decision stages
also enter `AgentDecisionProvenanceState`. Snapshot capture remains in the
Cosmic integration adapter. The kernel cannot import mutable Cosmic types.

This is the first migration slice, not the final selector. Commands and tests
may still assign a plan explicitly. The next kernel slice must introduce a
policy-owned `GoalProposal -> PlanSelection` boundary and route automatic
progression through it.

## Known compatibility authorities to remove

| Current path | Temporary responsibility | Required migration |
|---|---|---|
| `AgentUniversalPlanRuntime` | Versioned plan lifecycle | Retain as the sole plan executor |
| `AgentMapleIslandLithHandoffRuntime` | Deferred Southperry transfer activation | Express activation as a universal successor/goal proposal |
| `AgentAmherstPlanRuntime` | Legacy Amherst checkpoint adapter | Remove after Maple Island parity and checkpoint migration |
| `AgentVictoriaPlanSessionRuntime` | Legacy Victoria checkpoint adapter | Remove after all five Explorer paths use universal plans |
| `AgentMapleIslandPlanRuntime` | Compatibility objective registration | Move registration to universal executor/goal selector |
| `AgentVictoriaTrainingObjectiveRuntime` | Compatibility training objective registration | Move to universal plan objective ownership |
| `AgentCareerObjectiveRuntime` | Career objective registration | Convert career selection to a goal proposal |
| `AgentSupplyProcurementRuntime` | Maintenance objective registration | Retain as remediation command, but let the remediation supervisor own suspension/resume |

No new feature-specific top-level runner should be added. New behavior must be
a policy proposal, plan card/step operation, typed capability, catalog overlay,
or presentation adapter.

## Recommended implementation order

### 1. Finish the deterministic autonomy kernel

Next slices:

1. Add immutable goal proposals with source, priority, eligibility, expiry,
   policy version, and evidence references.
2. Add deterministic arbitration with stable tie-breaking and rejection
   reasons.
3. Map the winning proposal to a versioned plan through a repository, never a
   feature runner.
4. Make plan-step executors submit typed capability invocations rather than
   directly coordinating feature runtimes.
5. Persist autonomy-cycle summaries and expose them through diagnostics.
6. Delete legacy Amherst/Victoria foreground registrations after parity gates.

Exit gate:

- one captured snapshot produces exactly one accepted goal or an explained
  no-op;
- that goal resolves to exactly one plan/version;
- every mutation is attributable to a capability command correlation ID;
- retries do not duplicate completed mutations;
- replaying identical snapshot plus policy/catalog versions produces the same
  selection.

### 2. Prove M1-A autonomous progression

Build a five-path evidence matrix for Warrior, Magician, Bowman, Thief, and
Pirate:

- Maple Island completion and Southperry transfer;
- legitimate Olaf and instructor interaction;
- first-job advancement and starter kit;
- AP/SP allocation at each level;
- instructor training quests;
- selected quests or grinding through level 30;
- supply monitoring and shopping;
- inventory/equipment decisions;
- death, navigation, logout/relog, and objective recovery.

Use seeded runs and store checkpoint plus decision/capability evidence. A test
command may reset state, but the run itself must use production contracts.

Exit gate: repeated clean and injected-failure runs reach level 30 for every
path without GM mutation after launch.

### 3. Complete the remediation supervisor

Model typed blockers:

- death;
- low HP/MP supplies or ammunition;
- full inventory;
- required equipment missing or unusable;
- insufficient mesos;
- blocked navigation.

The supervisor pushes a bounded remediation frame, suspends the parent
objective, validates postconditions, and resumes the same parent correlation.
Mutation receipts prevent duplicate quest rewards, purchases, sales, item use,
or advancement.

### 4. Harden navigation

Add:

- graph/portal/foothold route-coverage reports;
- deterministic graph warmup with readiness evidence;
- expected versus observed portal-arrival evidence;
- oscillation categories and counters;
- fall, rope, ladder, and landing recovery fixtures;
- map-specific catalog overlays instead of code branches.

Exit gate: all M1-A routes have coverage and deterministic warmup evidence;
navigation failure injection either recovers or yields a typed blocker.

### 5. Unify resource capabilities

Inventory, Supplies, Shopping, Equipment, and Acquisition keep separate
policies but exchange:

- `ResourceNeed`;
- quantity, urgency, quality, and budget constraints;
- item/meso/slot reservations;
- acquisition alternatives;
- mutation receipts and fulfillment evidence.

No capability may independently reserve or spend the same resource.

### 6. Finish combat and skill parity

Build the WZ-backed matrix for requirements, range, targets, timing, MP/item
cost, damage model, buffs, summons, utility, Teleport, Flash Jump, and
defensive behavior. Validate physical and magical results against server
combat rules and two-client packet evidence.

### 7. Make persistence comprehensive

Version and persist:

- career/build/profile assignment;
- active and suspended objectives;
- plan chain, step, attempt, and checkpoints;
- capability frame/checkpoint and mutation receipts;
- decision provenance and catalog/policy versions.

Startup reconciliation must be idempotent and include explicit migrations for
older records.

### 8. Complete background simulation tiers

Prove `OBSERVED_FULL <-> BACKGROUND_ACTIVE <-> BACKGROUND_ABSTRACT`
transitions. Rematerialization must preserve legal position, HP/MP, inventory,
equipment, mesos, quest counters, cooldowns, objective progress, and decision
correlation.

### 9. Add population allocation

Introduce world/channel/map capacity, fair admission, region leases,
party/cohort placement, alternative-map ranking, and backpressure. Allocation
produces a proposal/lease consumed by navigation; it does not teleport or
mutate Agents directly.

### 10. Establish release-grade validation

Run, in order:

1. deterministic unit/contract tests;
2. five-job repeated progression runs;
3. blocker and failure injection;
4. two-client packet consistency;
5. 100-Agent gameplay cohorts;
6. 500, 1,000, and 2,000-Agent live soaks;
7. 8-hour, 24-hour, then multi-day evidence.

Each stage has explicit correctness, memory, CPU, scheduler-lag, error-rate,
and recovery thresholds.

### 11. Retire compatibility and legacy paths

Delete a legacy path only after:

- parity evidence exists;
- persistence migration is proven;
- rollback is documented and tested;
- no command or checkpoint still references it.

Do not retain two permanent behavior engines.

### 12. Layer Social, Economy, and LLM control

LLM providers may submit goal, dialogue, or action proposals. They receive
bounded immutable context and never receive Cosmic mutation handles. The
deterministic kernel remains responsible for:

- policy and scope validation;
- budgets and rate limits;
- capability arbitration and resource locks;
- command execution;
- evidence and audit trails;
- fallback when the provider is unavailable or invalid.

Dialogue-only, dialogue-plus-decision, and deterministic-only modes therefore
share the same Agent OS contracts.
