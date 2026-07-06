# Pre-Reconstruction Completion Audit

Purpose:

```text
Record the evidence that safe pre-reconstruction prep is ready, identify the
proof source for each major requirement, and separate completed prep from
runtime soak evidence that must be collected after server runs.
```

This audit is intentionally conservative. It does not claim the Agent runtime
is implemented, scaled, or soak-tested. It records that the safe work which can
be done before Nutnnut bot-to-Agent reconstruction is stable has been
documented, specified, or implemented as default-preserving server diagnostics.

## Boundary

Before reconstruction is stable:

- no live Agent behavior should be changed.
- no BotClient behavior should be changed.
- `src/main/java/server/agents` and `src/main/java/server/bots` should not be
  edited for safe-prep batches unless explicitly requested.
- `config.yaml` values should not be changed unless explicitly requested.
- behavior-changing optimizations should wait for soak evidence.

## Audit Summary

| Requirement Area | Current State | Evidence | Remaining Proof |
| --- | --- | --- | --- |
| Server-only hardening and diagnostics | Ready for current phase | `docs/SERVER_HARDENING_DIAGNOSTICS.md`, `docs/SERVER_SCALE_TODO.md`, `docs/SERVER_PLAYER_SCALE_IMPLEMENTATION_PLAN.md` | Run baseline/player soak and archive `!serverhealth` / scale-health samples. |
| Agent scaling prep | Ready as strategy/specs | `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`, `docs/agents/AGENT_ENGINE_OPTIMIZATION.md`, simulation/background/perception specs | Implement after reconstruction, then run staged Agent soak. |
| Catalog prep | Ready as contract and partial tooling | `docs/agents/catalog-platform/*`, `tools/game-catalog`, `tools/npc-catalog`, `tools/agent-llm-catalog` | Implement unified bundle builder and validation reports. |
| Maple Island MVP prep | Ready as first gameplay package | `docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md`, `docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md`, `docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md`, `docs/agents/plans/maple-island-mvp.plan.json` | Implement after reconstructed Plan/Capability runtime is stable. |
| Portable platform package specs | Ready as contracts | `docs/agents/PACKAGE_REGISTRY.md` and each package directory | Implement packages after reconstruction boundaries are stable. |
| Plan Card prep | Ready as contract | `docs/agents/llm-autonomy/PLAN_CARD_SYSTEM.md`, `docs/agents/plan-runtime/*` | Add schema/code after reconstruction. |
| Profile prep | Ready as contract | `docs/agents/profile-platform/*`, `docs/agents/social-relationship-runtime/*` | Add schema/store/adaptation implementation later. |
| Economy prep | Ready as contract | `docs/agents/llm-autonomy/ECONOMY_*`, `docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md` | Implement observation store after event bus/catalog/profile foundations. |
| Console planning | Ready as planning docs | `docs/consoles/DATABASE_CONSOLE_*`, `docs/consoles/SERVER_CONSOLE_SCOPE.md` | Keep console implementation modular. |
| Nutnnut-over-Cosmic review | Ready as decision record | `docs/NUTNNUT_OVER_COSMIC_REVIEW.md`, `docs/COSMIC_REVERT_REVIEW.md` | Apply only explicit approved reversions. |
| Reusable goal prompt | Ready as handoff artifact | `docs/agents/PRE_RECONSTRUCTION_GOAL_PROMPT.md` | Reuse for future safe-prep continuation threads. |
| Prep artifact verifier | Ready as local tooling | `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1` | Run after safe-prep batches and before claiming artifact readiness. |
| Baseline soak runbook | Ready as operator guide | `docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md` | Use to collect the missing server-baseline runtime evidence. |

## Package Evidence

All major future Agent packages now have a registry entry and either design and
technical specifications or an established top-level contract.

Primary package index:

- `docs/agents/PACKAGE_REGISTRY.md`

Well-defined packages recorded there:

- Catalog Platform.
- NPC Catalog Package.
- Profile Platform.
- Economy Engine.
- Server Adapter Package.
- Maple Island MVP Package.
- Plan Runtime Package.
- Capability Runtime Package.
- NPC / Quest Interaction Capability Package.
- Runtime Event Bus.
- Recovery / Survival Policy.
- Agent Observability / Diagnostics.
- Interaction Realism Package.
- Agent Simulation Tier Runtime.
- Perception Runtime Package.
- Background Action Runtime.
- Agent Soak Test Harness.
- LLM Control Gateway Package.
- Agent Population Director.
- Portable Installer / Patcher.
- Quest / Combat Focus Policy Package.
- Social Relationship Runtime.

Current package registry state:

```text
Partially defined packages: none currently identified.
Backlog packages to promote: none currently identified.
```

## Server Diagnostics Evidence To Capture

The server has default-preserving diagnostics for the next evidence-gathering
phase. A real soak should archive samples from:

- `!serverhealth`.
- scale-health logs.
- slow-operation logs.
- save pressure and save section timing.
- map broadcast pressure.
- timer lane diagnostics.
- DB pool diagnostics.
- map active/idle/load diagnostics.
- runtime cache counts.
- heap dumps when trigger conditions are met.

Minimum baseline evidence package:

```text
logs/soak/baseline/
  <runId>/
    README.md
    evidence-checklist.md
    serverhealth-5min-samples.log
    scale-health.log
    slow-operations.log
    startup.log
    shutdown.log
    summary.json
```

Scaffold command:

```powershell
.\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -DurationMinutes 60 `
  -SampleIntervalMinutes 5
```

The scaffold only creates local evidence files and templates. It does not start
the server, change config, or modify runtime behavior.

Operator runbook:

- `docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md`

Verification command after filling the run folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId>
```

Optional JSON report:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Json > .\logs\soak\baseline\<runId>\verification.json
```

Audit-entry generator after verification:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakAuditEntry.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -OutputPath .\logs\soak\baseline\<runId>\audit-entry.md
```

Recommended `summary.json` fields:

```json
{
  "runId": "baseline-YYYYMMDD-HHmm",
  "stage": "server-baseline",
  "durationMinutes": 0,
  "onlinePlayerPeak": 0,
  "onlineAgentPeak": 0,
  "heapStartMb": 0,
  "heapEndMb": 0,
  "loadedMapStart": 0,
  "loadedMapEnd": 0,
  "dbWaitingMax": 0,
  "threadRejectedDelta": 0,
  "timerQueueMax": 0,
  "slowSaveCount": 0,
  "slowBroadcastCount": 0,
  "stuckLoginCount": 0,
  "shutdownClean": false,
  "restartClean": false,
  "notes": []
}
```

## Soak Evidence Stages

Use `docs/SOAK_TEST_CHECKLIST.md` as the operator checklist.

Required evidence stages:

1. baseline server/player run with no Agents.
2. small Agent run after reconstruction.
3. medium Agent run after reconstruction.
4. 1000-Agent run after simulation-tier optimization.
5. 2000-Agent long run after persistence/background shortcuts are implemented.

Do not use Agent soak results to prove server-only baseline health unless the
baseline stage also exists.

## Interpretation Rules

Evidence should be treated as incomplete when:

- a run did not record interval samples.
- startup or shutdown logs are missing.
- `!serverhealth` snapshots are missing.
- run duration is shorter than the stage requires.
- any failure was fixed manually without recording the root cause.
- Agent reconstruction code changed during the run.

A stage should be considered failed or inconclusive when:

- heap grows continuously without explanation.
- loaded maps only increase.
- DB waiting threads remain nonzero for repeated samples.
- ThreadManager rejected count increases.
- TimerManager queue grows continuously.
- stuck logged-in accounts require manual DB cleanup.
- shutdown/restart is not clean.

## Safe Next Work

The remaining work before implementation is evidence collection, not more
package-definition work.

Safe actions:

- run a short baseline server smoke/soak and archive the diagnostics.
- create the baseline evidence folder with
  `tools/soak/New-BaselineSoakEvidencePackage.ps1`.
- verify the filled baseline evidence folder with
  `tools/soak/Test-BaselineSoakEvidencePackage.ps1`.
- generate an audit-entry snippet with
  `tools/soak/New-BaselineSoakAuditEntry.ps1`.
- verify safe-prep artifact presence and scope guardrails with
  `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1`.
- update this audit with real run ids and summary numbers.
- keep compiling after safe-prep documentation batches.
- keep Agent implementation deferred until reconstruction boundaries are
  stable.

Unsafe before reconstruction:

- implement live Agent plan/capability behavior from these specs.
- add packet suppression or Agent-specific broadcast shortcuts.
- add Agent persistence shortcuts.
- add LLM command execution.
- add economy automation.

## Completion Claim Status

Safe preparation is ready as documentation, contracts, and server diagnostics.

Runtime evidence is not yet complete until actual soak runs are performed and
recorded. This is expected because the Agent soak stages depend on the
reconstructed Agent runtime and the server baseline requires an intentional
operator-run test window.
