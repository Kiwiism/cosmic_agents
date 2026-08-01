# Hardening implementation status

Status: current engineering evidence as of 2026-08-01.

This document separates implemented safeguards from claims that still require integration, concurrency, or live-soak evidence. A checked-in implementation is not treated as proven at the 500-player/2,000-Agent target until its listed gate passes.

## Delivered safeguards

| Finding | Implemented protection | Remaining proof or limitation |
|---|---|---|
| Database credentials and network exposure | Production validation rejects blank, placeholder, and root credentials. The example deployment uses a dedicated account and no longer publishes MySQL on a host port by default. | Operators must provide a secret, non-root credential. A deliberately insecure local development configuration is rejected when the production profile is enabled. |
| Database administration bridge | Loopback binding, bounded request bodies, explicit production enablement, and production authentication are enforced. | Deployment owners must rotate the bridge token and keep the listener behind the host boundary. |
| Structured security evidence | Autoban input now records typed security events with severity, account/character identity, a privacy-preserving remote fingerprint, and evidence fields. Events are retained in a bounded live view and persisted asynchronously to `security_event`; the review service records reviewer, note, status, and review time. | An operator UI, retention/archival job, and deployment-specific alert delivery remain future work. |
| Packet-family controls | Global and per-family token buckets cover authentication/other, movement, combat, chat, and economy traffic, with evidence and bounded disconnect escalation. | This is a rate and family boundary, not full semantic validation for every opcode. New mutable packet handlers still require handler-specific authorization, range, ownership, and replay checks. |
| Economy mutation journal | Shop and player-trade mutations use ordered participant locks and durable PREPARED/COMMITTED/ROLLED_BACK/REVIEW_REQUIRED journal states. The coordinator snapshots mesos and inventories, restores both participants after an in-process mutation exception, and disconnects network clients whose earlier packets may now be stale. Startup marks interrupted operations for review. | This protects handled in-process failures, but it is not crash-safe atomic persistence. Prepared deltas, durable character writes, idempotency keys, and restart replay still need a single database transaction or an equivalent recoverable protocol. MTS remains production-disabled until its persistent listing and settlement flow is reconstructed. |
| Duey and MTS abuse boundaries | Duey claims are receiver-scoped, delete-before-grant, capacity checked, and fail closed. MTS validates listings, ownership, price/quantity, self-purchase, and conditional settlement; production use is disabled. MTS database-row reconstruction now has one tested mapper rather than five divergent copies. | Add database-backed concurrent-claim and crash-injection tests before enabling MTS. Persistent MTS listings still need a transactional ownership model. |
| Background abstract execution | Only explicitly allowlisted mutation-safe work can use BACKGROUND_ABSTRACT. A separate slow heartbeat, materialized-state fingerprint, outcome ledger, reconciliation, and transition tests prevent silent state drift. A synthetic gate round-trips 2,000 Agents through presentation, background-active, abstract, and rematerialized states. | The functional boundary and synthetic scale gate are implemented; populated live 1,000/1,500/2,000-Agent rematerialization and long-soak evidence is still required. |
| Idle map unloading | Guarded unloading is enabled for idle, unpinned maps after the configured retention period. Unit gates cover eligibility and 1,000 unload/reload cycles over 25 map IDs. | Complete 8-hour, 24-hour, and multi-day live lifecycle soaks before shortening retention or widening eligibility. |
| Economy abuse tests | Pickup ownership/range, shop validation, storage dirtiness, trade capacity/limits, Duey access, and MTS listing validation have focused tests. An opt-in disposable-MySQL gate covers concurrent journal writes, duplicate preparation, and stale-prepared restart reconciliation. | Run the MySQL gate in CI and add endpoint-level concurrency, replay, malformed-packet property tests, and process-crash injection across trade, shops, pickup, storage, MTS, and Duey. |
| Agent diagnostics | Scheduler details are projected from `AgentRuntimeRegistry`; shared read-only perception uses a bounded weak map cache rather than copying the same map view into every Agent. | Specialized bounded capture sessions remain separate intentionally. Migrate a capture only when it duplicates lifecycle ownership or cannot be disabled cleanly. |
| CI and supply-chain checks | Push and pull-request builds use JDK 21. CodeQL, dependency review, and Dependabot configuration are present. | Repository branch protection must require these checks. Mockito's inline mock maker currently emits a future-JDK dynamic-agent warning and should be migrated in test infrastructure before the JDK disallows dynamic attachment. |
| Privacy, retention, and AGPL | The deployment checklist covers minimization, retention, secrets, access requests, incident response, AGPL source availability, and third-party notices. | Operators remain responsible for publishing notices, selecting retention periods, honoring requests, and obtaining legal review for their deployment. |

## Deliberately proof-gated work

### True atomic economy settlement

The coordinator is rollback-capable for an exception caught in the running process. Do not describe it as crash-safe atomic settlement. The next transaction slice must define immutable before/after deltas, persist the prepared operation and durable character mutation in one database transaction where possible, make replay idempotent, and reconcile a process or host crash without duplicating or losing items or mesos. Only then should persistent MTS settlement be enabled.

### Legacy scheduler retirement

`CENTRAL_SHARDED` is the production default, but `LEGACY_PER_AGENT` remains a restart-time rollback mode. A cross-catalog contract gate now proves that every Explorer career resolves the same universal operation sequence and AP/SP profiles through level 30, and a two-recipient map fan-out test proves byte-identical delivery for the tested packet path. Removal still requires all live gates in [Agent Scheduler Parity Decision](AGENT_SCHEDULER_PARITY_DECISION.md): repeated five-job runs, two-client category parity, populated 1,000/1,500/2,000-Agent tests, long soaks, and a rollback rehearsal.

### Large server-class extraction

The safe extraction rule is seam-first and behavior-preserving: extract one cohesive policy or persistence boundary from a large class, add characterization tests, switch the caller, and stop. Do not perform a broad rewrite of `Character`, `PacketCreator`, `MapleMap`, `Server`, or navigation solely to reduce line count. MTS listing validation and MTS item-row mapping are completed examples; further slices should follow measured change hotspots and production defects.

## Opt-in real-MySQL gate

Point this only at a disposable test schema. The test does not read `config.yaml` and removes its own journal rows.

```powershell
$env:COSMIC_MYSQL_IT='true'
$env:COSMIC_MYSQL_IT_URL='jdbc:mysql://127.0.0.1:3306/cosmic_integration'
$env:COSMIC_MYSQL_IT_USER='cosmic_test'
$env:COSMIC_MYSQL_IT_PASSWORD='<test-secret>'
.\mvnw.cmd -q '-Dtest=JdbcEconomyTransactionJournalIntegrationTest' test
```

## Release evidence still required

1. Implement crash-safe, idempotent economy deltas and reconciliation; then run the opt-in MySQL journal gate in CI.
2. Run all five Explorer paths repeatedly through the same universal-plan and scheduler boundaries.
3. Run two-client consistency tests for movement, combat, loot, dialogue, transfers, death, and recovery.
4. Run populated 1,000, 1,500, and 2,000-Agent gates with observed/background transitions.
5. Capture 8-hour, 24-hour, and multi-day heap, GC, queue latency, database-pool, map-lifecycle, shutdown, and restart evidence.
6. Add endpoint-level real-MySQL concurrent settlement, replay, and crash-injection tests for every persistent economy endpoint.
7. Add semantic validation and malformed/replay tests to the remaining mutable packet handlers rather than relying only on family rate limits.
8. Add security-event retention, alert delivery, and an operator review surface appropriate to the deployment.
9. Rehearse restoration from journal review records and restart from the legacy scheduler before deleting either rollback path.
