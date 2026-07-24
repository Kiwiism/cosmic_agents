# Agent Engine Stabilization Milestone

This milestone establishes the control-plane boundaries required before adding
more progression, social, economy, market, guild, expedition, or LLM behavior.

## Runtime ownership

- `AgentForegroundActivityArbiter` selects one foreground owner from a registry.
  The scheduler no longer knows concrete plan or TownLife implementations.
- New progression assignments use `AgentUniversalPlanRuntime` and the single
  `AgentPlanExecutor`. Old Amherst/Victoria foreground adapters exist only to
  finish pre-universal sessions and are named `legacy-checkpoint-*`.
- A foreground transition is recorded in the bounded decision-provenance
  journal, so a stall can be explained by its selected owner.

## Mutation and concurrency boundary

- Plans, policies, personality, memory, coordination sessions, decision
  routing, and the LLM gateway may not call Cosmic mutation methods directly.
  The architecture test enforces this boundary.
- Mechanical mutation belongs inside executable capabilities and integration
  gateways. `AgentCapabilityRuntime` coordinates the common exclusive
  resources: movement, combat, inventory, NPC interaction, and social
  transaction.
- Resource leases are acquired atomically, are re-entrant for correlated
  parent/child capabilities, expire if abandoned, and release on every terminal
  path including cancellation and timeout.

## Versioning, interaction, and memory

- `AgentBehaviorVersionRouter` provides stable legacy, shadow, canary, and
  active routing by Agent and domain.
- `AgentDecisionProvenanceState` retains a bounded explanation trace. The trace
  has a deterministic replay verifier and stable fingerprint.
- `AgentInteractionSessionRegistry` supplies one bounded lifecycle for trade,
  party, buddy, guild, town encounter, expedition, and market negotiation.
- The coordination bus supports both dedicated technical messages and a
  structured message payload. Maple chat remains presentation, not protocol.
- Working, episodic, social, and economic memory share a bounded repository
  contract with TTL expiry. Persistence can be added behind the repository
  without changing policies.

## LLM boundary

`AgentReadOnlyLlmGateway` is dialogue-only. A provider receives immutable prompt
text and system instructions and returns text. It never receives `Character`,
`MapleMap`, `AgentRuntimeEntry`, a capability command gateway, or a mutation
handle. Decision support can later be introduced as a separate, version-routed
proposal interface; proposals must still pass deterministic policy validation
and capability/resource arbitration.

## Required gates

Run these before promoting a behavior version:

1. `AgentArchitectureBoundaryTest`
2. `AgentDecisionReplayTest`
3. `AgentPlanExecutorTest`
4. `AgentPlanReattachmentRuntimeTest`
5. `FileAgentPlanCheckpointStoreTest`
6. `AgentCapabilityResourceCancellationTest`
7. `AgentStabilizationScaleGateTest`

The 500-Agent JUnit gate validates bounded control-plane state. It does not
replace the live server soak: the operational gate must still run 500 Agents
through observed/unobserved maps, relog/resume, plan cancellation, TownLife,
combat, and disk/GC monitoring.
