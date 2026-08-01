# Hardening implementation status

Status: current engineering evidence as of 2026-08-01.

This document separates implemented safeguards from claims that still require integration, concurrency, or live-soak evidence. A checked-in implementation is not treated as proven at the 500-player/2,000-Agent target until its listed gate passes.

## Delivered safeguards

| Finding | Implemented protection | Remaining proof or limitation |
|---|---|---|
| Database credentials and network exposure | Production validation rejects blank, placeholder, and root credentials. The example deployment uses a dedicated account and no longer publishes MySQL on a host port by default. | Operators must provide a secret, non-root credential. A deliberately insecure local development configuration is rejected when the production profile is enabled. |
| Database administration bridge | Loopback binding, bounded request bodies, explicit production enablement, and production authentication are enforced. | Deployment owners must rotate the bridge token and keep the listener behind the host boundary. |
| Structured security evidence | Autoban input records typed evidence with severity, account/character identity, a privacy-preserving remote fingerprint, and evidence fields. Events have a bounded live view, persistent review state, a daily reviewed-event retention job, a critical-event alert sink, and the GM6 `!securityevents` list/review interface. | The built-in alert delivery is a dedicated operator log. Deployments that require paging or a web dashboard must install an external sink or interface appropriate to their operations. |
| Packet-family controls | Global and per-family token buckets cover authentication/other, movement, combat, chat, and economy traffic. Duey now validates request envelopes and rejects rapid claim/remove replays; Storage rejects undefined actions, unopened sessions, invalid inventory types, and invalid slot boundaries through tested validation seams. | This is not proof for every mutable opcode. Remaining handlers need focused semantic/replay characterization when they change, and live two-client parity remains required. |
| Economy mutation journal | Shop and player-trade mutations use ordered participant locks. Their after-state and COMMITTED journal transition are persisted in one database transaction. Player trades carry a stable settlement key, committed retries are suppressed, rolled-back requests can be safely rearmed, injected commit failures roll back both state and journal, and startup classifies stale PREPARED rows as rolled back. | Crash-safe atomicity applies only to endpoints routed through `EconomyTransactionCoordinator`. The v83 shop protocol has no request nonce, so separate identical purchases remain distinct operations. Storage, Duey and disabled MTS retain their own persistence protocols. |
| Duey and MTS abuse boundaries | Duey claims are receiver-scoped, delete-before-grant, capacity checked, request-envelope checked, replay guarded, and fail closed. MTS validates listings, ownership, price/quantity, self-purchase, and conditional settlement; production use is disabled. MTS database-row reconstruction has one tested mapper. | Add database-backed process-crash tests before enabling MTS. Persistent MTS listings still need a transactional ownership model. |
| Background abstract execution | Only explicitly allowlisted mutation-safe work can use BACKGROUND_ABSTRACT. A separate slow heartbeat, materialized-state fingerprint, outcome ledger, reconciliation, and transition tests prevent silent state drift. A synthetic gate round-trips 2,000 Agents through presentation, background-active, abstract, and rematerialized states. | The functional boundary and synthetic scale gate are implemented; populated live 1,000/1,500/2,000-Agent rematerialization and long-soak evidence is still required. |
| Idle map unloading | Guarded unloading is enabled for idle, unpinned maps after the configured retention period. Unit gates cover eligibility and 1,000 unload/reload cycles over 25 map IDs. | Complete 8-hour, 24-hour, and multi-day live lifecycle soaks before shortening retention or widening eligibility. |
| Economy abuse tests | Pickup ownership/range, shop validation, Storage request semantics, trade capacity/limits, Duey envelope/replay behavior, and MTS listing validation have focused tests. The opt-in disposable-MySQL gate covers concurrent commits, committed retry idempotency, rolled-back retry, stale-prepared restart reconciliation, and failure injection between state persistence and journal commit. | Run the MySQL gate in CI and extend real-database crash injection to Storage, Duey, and MTS before those endpoints claim coordinator-level atomicity. |
| Agent diagnostics | Scheduler details are projected from `AgentRuntimeRegistry`; shared read-only perception uses a bounded weak map cache rather than copying the same map view into every Agent. | Specialized bounded capture sessions remain separate intentionally. Migrate a capture only when it duplicates lifecycle ownership or cannot be disabled cleanly. |
| CI and supply-chain checks | Push and pull-request builds use JDK 21. CodeQL, dependency review, and Dependabot configuration are present. Surefire loads Mockito explicitly as a Java agent, removing its future-JDK dynamic-attachment dependency. | Repository branch protection must require these checks. The JVM may still emit the unrelated class-data-sharing notice when the test agent extends the bootstrap path. |
| Privacy, retention, and AGPL | The deployment checklist covers minimization, retention, secrets, access requests, incident response, AGPL source availability, and third-party notices. | Operators remain responsible for publishing notices, selecting retention periods, honoring requests, and obtaining legal review for their deployment. |

## Deliberately proof-gated work

### Atomic economy settlement scope

The coordinator-covered Shop and player-trade paths now persist the complete participant mesos/inventory after-state and journal commit atomically. Stable request keys provide idempotent committed retries and restart reconciliation safely closes stale PREPARED rows. Do not generalize this claim to Storage, Duey, or MTS: they do not yet use this settlement boundary. Persistent MTS remains disabled.

### Legacy scheduler retirement

`CENTRAL_SHARDED` is the production default, but `LEGACY_PER_AGENT` remains a restart-time rollback mode. A cross-catalog contract gate now proves that every Explorer career resolves the same universal operation sequence and AP/SP profiles through level 30, and a two-recipient map fan-out test proves byte-identical delivery for the tested packet path. Removal still requires all live gates in [Agent Scheduler Parity Decision](AGENT_SCHEDULER_PARITY_DECISION.md): repeated five-job runs, two-client category parity, populated 1,000/1,500/2,000-Agent tests, long soaks, and a rollback rehearsal.

### Large server-class extraction

The safe extraction rule is seam-first and behavior-preserving: extract one cohesive policy or persistence boundary from a large class, add characterization tests, switch the caller, and stop. Do not perform a broad rewrite of `Character`, `PacketCreator`, `MapleMap`, `Server`, or navigation solely to reduce line count. MTS listing validation/mapping plus Duey and Storage request validators are completed examples; further slices should follow measured change hotspots and production defects.

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

1. Run all five Explorer paths repeatedly through the same universal-plan and scheduler boundaries.
2. Run two-client consistency tests for movement, combat, loot, dialogue, transfers, death, and recovery.
3. Run populated 1,000, 1,500, and 2,000-Agent gates with observed/background transitions.
4. Capture 8-hour, 24-hour, and multi-day heap, GC, queue latency, database-pool, map-lifecycle, shutdown, and restart evidence.
5. Put the disposable-MySQL gate in CI and add endpoint-level real-MySQL settlement/replay/crash tests for Storage, Duey, and MTS.
6. Continue semantic/replay validation for mutable handlers as characterization coverage identifies gaps.
7. Configure deployment-specific external alert delivery or a dashboard if the built-in alert log and GM review interface are insufficient.
8. Rehearse restoration and restart from the legacy scheduler before deleting scheduler or compatibility rollback paths.

## Boundary audit

- Economy durability remains in `server.economy`; packet handlers invoke it without owning database transaction policy.
- Security retention, alerting and review remain in `server.security`; the GM command is a thin operator adapter.
- Duey and Storage extraction moved only request-shape policy out of mutable handlers; endpoint behavior was not broadly rewritten.
- Live population, progression, packet-parity and long-soak gates were intentionally not claimed or simulated. Their absence prevents retirement of the legacy scheduler and compatibility paths.
