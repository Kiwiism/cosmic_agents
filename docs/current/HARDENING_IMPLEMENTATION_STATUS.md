# Hardening implementation status

Status: current engineering evidence as of 2026-08-01.

This document separates implemented safeguards from claims that still require integration, concurrency, or live-soak evidence. A checked-in implementation is not treated as proven at the 500-player/2,000-Agent target until its listed gate passes.

## Delivered safeguards

| Finding | Implemented protection | Remaining proof or limitation |
|---|---|---|
| Database credentials and network exposure | Production validation rejects blank, placeholder, and root credentials. The example deployment uses a dedicated account and no longer publishes MySQL on a host port by default. | Operators must provide a secret, non-root credential. A deliberately insecure local development configuration is rejected when the production profile is enabled. |
| Database administration bridge | Loopback binding, bounded request bodies, explicit production enablement, and production authentication are enforced. | Deployment owners must rotate the bridge token and keep the listener behind the host boundary. |
| Structured security evidence | Autoban input now records typed security events with severity, account/character identity, a privacy-preserving remote fingerprint, and evidence fields. | Events are bounded in memory and written to the security log. Durable security-event querying and an operator adjudication UI remain future work. |
| Packet-family controls | Global and per-family token buckets cover authentication/other, movement, combat, chat, and economy traffic, with evidence and bounded disconnect escalation. | This is a rate and family boundary, not full semantic validation for every opcode. New mutable packet handlers still require handler-specific authorization, range, ownership, and replay checks. |
| Economy mutation journal | Shop and player-trade mutations use ordered participant locks and durable PREPARED/COMMITTED/REVIEW_REQUIRED journal states. Startup marks interrupted operations for review. | The journal is a write-ahead evidence boundary, not yet a database transaction that can roll in-memory inventory and mesos back after an arbitrary mid-mutation exception. MTS remains production-disabled until its persistent listing and settlement flow is transactionally reconstructed. |
| Duey and MTS abuse boundaries | Duey claims are receiver-scoped, delete-before-grant, capacity checked, and fail closed. MTS validates listings, ownership, price/quantity, self-purchase, and conditional settlement; production use is disabled. | Add database-backed concurrent-claim and crash-injection tests before enabling MTS. |
| Background abstract execution | Only explicitly allowlisted mutation-safe work can use BACKGROUND_ABSTRACT. A separate slow heartbeat, materialized-state fingerprint, outcome ledger, reconciliation, and transition tests prevent silent state drift. | The functional boundary is implemented; populated 1,000/1,500/2,000-Agent rematerialization and long-soak evidence is still required. |
| Idle map unloading | Guarded unloading is enabled for idle, unpinned maps after the configured retention period, with lifecycle tests. | Complete 8-hour, 24-hour, and multi-day lifecycle soaks before shortening retention or widening eligibility. |
| Economy abuse tests | Pickup ownership/range, shop validation, storage dirtiness, trade capacity/limits, Duey access, and MTS listing validation have focused tests. | Add real-database concurrency, replay, malformed-packet property tests, and crash/failure injection across trade, shops, pickup, storage, MTS, and Duey. |
| Agent diagnostics | Scheduler details are projected from `AgentRuntimeRegistry`; shared read-only perception uses a bounded weak map cache rather than copying the same map view into every Agent. | Specialized bounded capture sessions remain separate intentionally. Migrate a capture only when it duplicates lifecycle ownership or cannot be disabled cleanly. |
| CI and supply-chain checks | Push and pull-request builds use JDK 21. CodeQL, dependency review, and Dependabot configuration are present. | Repository branch protection must require these checks. Mockito's inline mock maker currently emits a future-JDK dynamic-agent warning and should be migrated in test infrastructure before the JDK disallows dynamic attachment. |
| Privacy, retention, and AGPL | The deployment checklist covers minimization, retention, secrets, access requests, incident response, AGPL source availability, and third-party notices. | Operators remain responsible for publishing notices, selecting retention periods, honoring requests, and obtaining legal review for their deployment. |

## Deliberately proof-gated work

### True atomic economy settlement

Do not describe the current journal as rollback-capable atomic settlement. The next transaction slice must define immutable before/after deltas, persist the prepared operation and durable character mutation in one database transaction where possible, make replay idempotent, and reconcile a crash without duplicating or losing items or mesos. Only then should persistent MTS settlement be enabled.

### Legacy scheduler retirement

`CENTRAL_SHARDED` is the production default, but `LEGACY_PER_AGENT` remains a restart-time rollback mode. Removal requires all gates in [Agent Scheduler Parity Decision](AGENT_SCHEDULER_PARITY_DECISION.md): two-client packet parity, repeated five-job plans, populated 1,000/1,500/2,000-Agent tests, long soaks, and a rollback rehearsal.

### Large server-class extraction

The safe extraction rule is seam-first and behavior-preserving: extract one cohesive policy or persistence boundary from a large class, add characterization tests, switch the caller, and stop. Do not perform a broad rewrite of `Character`, `PacketCreator`, `MapleMap`, `Server`, or navigation solely to reduce line count. The MTS listing validator is one such extracted seam; further slices should follow measured change hotspots and production defects.

## Release evidence still required

1. Run all five Explorer paths repeatedly through the same universal-plan and scheduler boundaries.
2. Run two-client consistency tests for movement, combat, loot, dialogue, transfers, death, and recovery.
3. Run populated 1,000, 1,500, and 2,000-Agent gates with observed/background transitions.
4. Capture 8-hour, 24-hour, and multi-day heap, GC, queue latency, database-pool, map-lifecycle, shutdown, and restart evidence.
5. Add real-MySQL concurrent settlement, replay, and crash-injection tests for every persistent economy endpoint.
6. Rehearse restoration from journal review records and restart from the legacy scheduler before deleting either rollback path.
