# Agent Reconstruction Completion Audit

Branch: `reconstruction/source-master-agent-base`

Audited commit: `4a951661eb` (`Document and enforce Agent Cosmic boundaries`)

## Architecture Result

The behavior-preserving reconstruction implementation is structurally complete:

- production and test `server.bots` packages are absent;
- 579 capability files own reusable gameplay behavior;
- 22 plan files compose capabilities into objectives;
- 29 command files translate external requests into Agent operations;
- 57 runtime files are limited to lifecycle, sessions, scheduling, tick
  orchestration, registries, state, and runtime adapters;
- 67 integration files define or implement Cosmic boundaries;
- all retained runtime classes are classified in
  `AGENT_RUNTIME_CLASSIFICATION.md` and checked automatically;
- operational Cosmic dependencies are inventoried in
  `AGENT_COSMIC_COUPLING.md` and checked automatically.

Runtime classifications:

| Classification | Classes |
| --- | ---: |
| `LEGITIMATE_RUNTIME_ORCHESTRATION` | 10 |
| `RUNTIME_SERVICE` | 20 |
| `RUNTIME_STATE` | 10 |
| `RUNTIME_ADAPTER` | 17 |

## Final Extraction Slices

- Recruitment moved to `commands.AgentRecruitService`.
- Performance aggregation moved to `monitoring.AgentPerformanceMonitor`.
- Unused `AgentRuntime` and `AgentRuntimeSnapshot` placeholders were deleted.
- Offline loading moved to `integration.cosmic.CosmicAgentOfflineLoadService`.
- Session persistence now uses `CharacterGateway.save`.
- Relog/logout/away requests moved to
  `commands.AgentSessionCommandCoordinator`.
- Support-skill packet layout moved to Cosmic integration.
- Buff item use and NPC shop lookup moved behind gateways.
- Capability timers and repeating Agent ticks were centralized through
  `AgentSchedulerRuntime` and `SchedulerGateway`.

All slices preserved existing ordering, delays, messages, packet bytes, handler
dispatch, state transitions, and side effects. No gameplay behavior change was
intended.

## Boundary Evidence

Current scans and `AgentCosmicBoundaryAuditTest` prove:

- no `server.bots` source/test package or import;
- no direct JDBC/database access outside Cosmic persistence integration;
- no direct `getClient()` or `saveCharToDB()` outside Cosmic integration;
- no packet codecs/opcodes, `UseItemHandler`, `ShopFactory`, or `TimerManager`
  outside Cosmic integration;
- generated packet, inventory, shop, persistence, client, map, and scheduler
  operations enter through Agent integration contracts.

Intentional Cosmic domain-model coupling remains documented for Character,
inventory/equipment, map entities, combat carriers/formulas, shop handles, WZ
data providers, KPQ event instances, and compatibility command/client types.

## Automated Verification

All verification ran in detached worktrees from committed reconstruction code,
with the workspace WZ and generated catalog fixtures linked read-only.

| Verification | Result |
| --- | --- |
| Per-slice compile and focused tests | PASS |
| Agent test suite excluding `BotMovementSimulationLabTest` | 2,312 unique tests accounted for; all pass after generated catalog fixtures were attached |
| `AgentCatalogServiceTest` with all four generated catalog roots | PASS, 10 tests |
| Maven package with tests skipped | PASS |
| Broader repository suite excluding the hanging movement simulation class | 4,242 tests; 4,239 pass, 3 known failures |

Known broader failures, unchanged from the branch baseline:

- `CombatFormulaProviderTest.shouldScalePhysicalSkillDamageFromBaseWeaponRange`
- `CombatFormulaProviderTest.shouldUseLuckySevenWatkScaledFormula`
- `CombatFormulaProviderTest.shouldUseDragonRoarFormulaBeforeApplyingSkillDamage`

## Remaining Validation Gaps

`BotMovementSimulationLabTest` contains nine tests. During the full Agent run,
`shouldResolveSlashRopeOscillationDumpByRefreshingPendingExitEdge` consumed a CPU
core indefinitely while rebuilding navigation jump edges. A thread dump placed
the loop in `AgentJumpProbeService.effectiveRightBoundaryX`. This reconstruction
did not modify that movement implementation, so the class was excluded from the
remaining suite rather than changing baseline behavior during architectural work.

Live-client validation was not run. Spawn/despawn, visible movement, map travel,
combat packets, loot, shop/trade, dialogue, and relog/logout still require the
existing manual parity checklist before a release claim.

## Completion Statement

The package reconstruction and ownership migration are complete. Release-level
behavior parity remains pending the movement-simulation investigation and live
client validation. Those are validation/follow-up milestones, not remaining
unclassified or misplaced Agent production ownership.
