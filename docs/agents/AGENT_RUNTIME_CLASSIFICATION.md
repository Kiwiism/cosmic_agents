# Agent Runtime Classification

Status: current for `reconstruction/source-master-agent-base` after the command,
monitoring, offline-load, and session-command ownership extractions.

The runtime package is limited to live session state, lifecycle, scheduling,
tick orchestration, and cross-capability coordination. Reusable gameplay behavior
belongs in capabilities; objective workflows belong in plans; external requests
belong in commands; and operational Cosmic calls belong in integration adapters.

Classifications:

- `LEGITIMATE_RUNTIME_ORCHESTRATION`: coordinates lifecycle or tick phases across
  multiple runtime services/capabilities.
- `RUNTIME_SERVICE`: focused lifecycle, session, scheduling, or tick service.
- `RUNTIME_STATE`: live mutable session state, registry, configuration, or state
  access contract.
- `RUNTIME_ADAPTER`: adapts `AgentRuntimeEntry` or runtime registries to a focused
  capability/integration contract without owning reusable gameplay behavior.

| Runtime class | Classification | Responsibility and retention reason |
| --- | --- | --- |
| `AgentChatOrchestratorContext` | `RUNTIME_ADAPTER` | Adapts one live runtime entry to the dialogue orchestrator callback contract. |
| `AgentCommonTickRuntime` | `RUNTIME_ADAPTER` | Wires common-tick service hooks to capability and integration implementations. |
| `AgentCommonTickService` | `RUNTIME_SERVICE` | Preserves ordered execution of systems shared by every live Agent tick. |
| `AgentHeartbeatService` | `RUNTIME_SERVICE` | Applies runtime heartbeat cadence without owning client mutation. |
| `AgentIdleModeTickService` | `RUNTIME_SERVICE` | Dispatches the idle tick branch and reports whether the tick was consumed. |
| `AgentInteractionRuntime` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Public server entry point coordinating registration, spawn, chat, relogin, and tick dispatch. |
| `AgentLeaderSafetyCoordinator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Coordinates inactive-leader recovery across sessions and recovery capabilities. |
| `AgentLeaderSessionResolver` | `RUNTIME_SERVICE` | Resolves the current live leader for a scheduled Agent tick. |
| `AgentLeaderSessionService` | `RUNTIME_SERVICE` | Implements leader-session selection rules independently of server lookup. |
| `AgentLeaderStateRuntime` | `RUNTIME_ADAPTER` | Adapts live entry identity storage to leader read/write operations. |
| `AgentLifecycleService` | `RUNTIME_SERVICE` | Owns spawn, registration, relogin, dismissal, and removal sequencing through hooks. |
| `AgentLifecycleStatusCoordinator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Schedules lifecycle status checks against the live session. |
| `AgentLiveModeTickRuntime` | `RUNTIME_ADAPTER` | Wires live-mode phase hooks to movement, combat, shop, loot, and navigation capabilities. |
| `AgentLiveModeTickService` | `RUNTIME_SERVICE` | Preserves ordered live-mode branch dispatch and tick-consumption semantics. |
| `AgentLiveTickContextRuntime` | `RUNTIME_ADAPTER` | Builds a live tick context from runtime identity and target snapshots. |
| `AgentLiveTickContextService` | `RUNTIME_SERVICE` | Validates and assembles immutable inputs for one live tick. |
| `AgentLiveTickGateRuntime` | `RUNTIME_ADAPTER` | Wires live tick gates to capability implementations and instrumentation. |
| `AgentLiveTickGateService` | `RUNTIME_SERVICE` | Applies trade, idle, and map-change gates before active mode execution. |
| `AgentMapTransitionRuntime` | `RUNTIME_ADAPTER` | Connects tracked map transitions to grounding and movement reset capabilities. |
| `AgentModeService` | `RUNTIME_SERVICE` | Coordinates mutually exclusive follow, grind, move, farm, and patrol runtime modes. |
| `AgentModeState` | `RUNTIME_STATE` | Stores cross-capability active mode and follow-target identity. |
| `AgentModeStateRuntime` | `RUNTIME_ADAPTER` | Exposes mode state held by the live runtime entry. |
| `AgentMovementOnlyModeCoordinator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Coordinates movement-only dispatch when normal live AI phases are unavailable. |
| `AgentMovementOnlyTickCoordinator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Runs one movement-only tick through lifecycle, movement, and map-transition seams. |
| `AgentRandom` | `RUNTIME_SERVICE` | Supplies shared nondeterministic delay selection used by runtime and capabilities. |
| `AgentRegistrationCoordinator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Connects manual/spawned registration to scheduler, lifecycle, and initial mode setup. |
| `AgentRuntimeCleanupService` | `RUNTIME_SERVICE` | Removes sessions and clears their scheduled/capability state in legacy order. |
| `AgentRuntimeConfig` | `RUNTIME_STATE` | Holds runtime tick and failure configuration used by scheduling infrastructure. |
| `AgentRuntimeEntry` | `RUNTIME_STATE` | Live mutable Agent session container composed from capability-specific state objects. |
| `AgentRuntimeHandle` | `RUNTIME_STATE` | Minimal runtime identity contract used by generic lifecycle services. |
| `AgentRuntimeIdentityState` | `RUNTIME_STATE` | Stores the live Agent character and current leader reference. |
| `AgentRuntimeRegistry` | `RUNTIME_STATE` | Owns active session registration and leader/Agent lookup indexes. |
| `AgentScheduledTaskRuntime` | `RUNTIME_ADAPTER` | Exposes scheduled-task cancellation on a live runtime entry. |
| `AgentScheduledTaskState` | `RUNTIME_STATE` | Stores and cancels the scheduled tick handle for one session. |
| `AgentSchedulerRuntime` | `RUNTIME_SERVICE` | Shared delayed-callback facade preserving existing random delay behavior. |
| `AgentSession` | `RUNTIME_STATE` | Stable public session identity contract for future entry-state replacement. |
| `AgentSessionControlRuntime` | `RUNTIME_ADAPTER` | Adapts session registry and leader-safety services for session command coordination. |
| `AgentSessionLifecycleRuntime` | `RUNTIME_ADAPTER` | Provides live session lookup and lifecycle dispatch to capabilities and commands. |
| `AgentSpawnPlacementCoordinator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Wires spawned-session normalization to movement, death, party, and map tracking services. |
| `AgentSpawnPlacementService` | `RUNTIME_SERVICE` | Preserves spawn normalization/reset ordering through explicit hooks. |
| `AgentSpawnPositionService` | `RUNTIME_SERVICE` | Resolves lifecycle spawn grounding with the movement grounding capability. |
| `AgentTargetSnapshotCoordinator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Captures the current follow/combat/navigation target snapshot for tick dispatch. |
| `AgentTickCadenceStateRuntime` | `RUNTIME_ADAPTER` | Exposes movement/AI cadence counters held by the runtime tick state. |
| `AgentTickCoreRuntime` | `RUNTIME_ADAPTER` | Wires core tick service hooks to plans, capabilities, and monitoring. |
| `AgentTickCoreService` | `RUNTIME_SERVICE` | Preserves top-level one-Agent tick phase ordering and early-return behavior. |
| `AgentTickFailurePolicy` | `RUNTIME_SERVICE` | Converts repeated tick failures into per-session idle/disable decisions. |
| `AgentTickFailureRuntime` | `RUNTIME_ADAPTER` | Applies failure decisions to live session and capability state through gateways. |
| `AgentTickFailureState` | `RUNTIME_STATE` | Stores the per-session failure count and failure-window timestamp. |
| `AgentTickFailureStateRuntime` | `RUNTIME_ADAPTER` | Exposes failure state held by the live runtime entry. |
| `AgentTickOrchestrator` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Executes one guarded Agent update and isolates failure handling per session. |
| `AgentTickPreflightRuntime` | `RUNTIME_ADAPTER` | Wires preflight checks to identity, lifecycle, and heartbeat integrations. |
| `AgentTickPreflightService` | `RUNTIME_SERVICE` | Validates session/character readiness and determines AI cadence eligibility. |
| `AgentTickRuntime` | `LEGITIMATE_RUNTIME_ORCHESTRATION` | Public callback target connecting scheduled sessions to the core tick runtime. |
| `AgentTickScheduler` | `RUNTIME_SERVICE` | Scheduling contract used by lifecycle registration. |
| `AgentTickState` | `RUNTIME_STATE` | Stores tick timestamps, heartbeat, follow-idle, and cadence counters. |
| `AgentTickStateRuntime` | `RUNTIME_ADAPTER` | Exposes tick state held by the live runtime entry. |
| `AgentTrackedMapChangeTickService` | `RUNTIME_SERVICE` | Detects map changes and dispatches the runtime map-transition handler once. |

## Extracted Ownership

- Recruitment coordination: `server.agents.commands.AgentRecruitService`.
- Session request coordination: `server.agents.commands.AgentSessionCommandCoordinator`.
- Performance aggregation: `server.agents.monitoring.AgentPerformanceMonitor`.
- Offline character loading: `server.agents.integration.cosmic.CosmicAgentOfflineLoadService`.
- Persistence, spawn, and relogin operations: Cosmic integration gateways/coordinators.
- One-shot and repeating scheduling: runtime policy over
  `integration.SchedulerGateway`, implemented by `CosmicSchedulerGateway`.
- Script objectives: `server.agents.plans.AgentScriptTaskCoordinator`.
- Movement, combat, recovery, follow, loot, supplies, dialogue, and other reusable
  gameplay behavior: their corresponding capability packages.

`AgentRuntime` and `AgentRuntimeSnapshot` were unused placeholders and have been
deleted. This inventory is checked by `AgentRuntimeClassificationTest`; adding or
removing a runtime class requires updating this document.

Remaining intentional Cosmic domain-model dependencies are inventoried in
`AGENT_COSMIC_COUPLING.md` and operational boundaries are enforced by
`AgentCosmicBoundaryAuditTest`.
