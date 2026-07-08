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

Current proof-gap snapshot:

- `docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md`

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
| Catalog prep | Ready as contract and standalone verifier tooling | `docs/agents/catalog-platform/*`, `tools/game-catalog`, `tools/npc-catalog`, `tools/agent-llm-catalog`, `tools/catalog`, `docs/agents/catalog-overrides/drop-source-classifications.catalog.json` | Implement unified runtime bundle loader/query API after reconstruction boundaries are stable. Reactor/field-object catalog remains an optional deferred extension. |
| Maple Island MVP prep | Ready as first gameplay package | `docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md`, `docs/agents/MAPLE_ISLAND_MVP_DESIGN_SPECIFICATION.md`, `docs/agents/MAPLE_ISLAND_MVP_TECHNICAL_SPECIFICATION.md`, Amherst sub-phase scope/test docs, `docs/agents/plans/maple-island-mvp.plan.json` | Run Amherst smoke first, then full Maple Island MVP after reconstructed Plan/Capability runtime is stable. |
| Amherst inactive implementation prep | Ready as non-live contracts and unit-tested selectors | `server.agents.capabilities.quest.AmherstQuestCatalog`, `server.agents.capabilities.quest.AmherstScopePolicy`, `server.agents.capabilities.quest.GuardedAmherstTestResetHarness`, `server.agents.capabilities.reactor.*`, `docs/agents/plans/maple-island-amherst-subphase.plan.json` | Wire only after single-active-capability runtime, primitive wrappers, and live adapters exist. |
| Portable platform package specs | Ready as contracts | `docs/agents/PACKAGE_REGISTRY.md` and each package directory | Implement packages after reconstruction boundaries are stable. |
| Plan Card prep | Ready as contract | `docs/agents/llm-autonomy/PLAN_CARD_SYSTEM.md`, `docs/agents/plan-runtime/*` | Add schema/code after reconstruction. |
| Profile prep | Ready as contract | `docs/agents/profile-platform/*`, `docs/agents/social-relationship-runtime/*` | Add schema/store/adaptation implementation later. |
| Economy prep | Ready as contract | `docs/agents/llm-autonomy/ECONOMY_*`, `docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md` | Implement observation store after event bus/catalog/profile foundations. |
| Console planning | Ready as planning docs | `docs/consoles/DATABASE_CONSOLE_*`, `docs/consoles/SERVER_CONSOLE_SCOPE.md` | Keep console implementation modular. |
| Nutnnut-over-Cosmic review | Ready as decision record | `docs/NUTNNUT_OVER_COSMIC_REVIEW.md`, `docs/COSMIC_REVERT_REVIEW.md` | Apply only explicit approved reversions. |
| Reusable goal prompt | Ready as handoff artifact | `docs/agents/PRE_RECONSTRUCTION_GOAL_PROMPT.md` | Reuse for future safe-prep continuation threads. |
| Prep artifact verifier | Ready as local tooling | `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1` | Run after safe-prep batches and before claiming artifact readiness. |
| Baseline soak runbook | Ready as operator guide | `docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md` | Use to collect the missing server-baseline runtime evidence. |
| Current gap report | Ready as current-state handoff | `docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md` | Replace the gap with real baseline evidence before marking the broad goal complete. |

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
    prep-verifier-before-run.log
    baseline-status-before-run.log
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

## Catalog Verification Evidence

Standalone catalog verification is now part of the safe-prep gate. The combined
entry point is:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Test-AllCatalogs.ps1
```

Additional catalog-readiness entry points:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Get-CatalogRuntimeReadiness.ps1
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Test-CatalogBundlePrep.ps1
```

The pre-reconstruction verifier also runs the combined catalog gate, runtime
readiness gate, and bundle-prep gate:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1
```

Current standalone catalog checks cover:

- game knowledge catalog shape, source references, item/drop/shop/gachapon
  indexes, and reviewed non-mob drop source classifications.
- NPC catalog shape and interaction metadata.
- Agent/LLM catalog shape and runtime lookup expectations.
- generated catalog files mapped into future portable bundle categories.
- explicit deferred marker for the optional reactor/field-object catalog.

Reviewed non-mob drop source IDs are recorded in:

```text
docs/agents/catalog-overrides/drop-source-classifications.catalog.json
```

This keeps ambiguous drop-source data out of direct combat planning unless a
later live validation pass proves that the source is a normal mob target.

## Amherst Prep Evidence

The Amherst sub-phase now has inactive Java prep in addition to documentation.
This is not live behavior and does not satisfy the post-reconstruction gameplay
acceptance criteria by itself.

Prepared artifacts:

- static Amherst quest catalog.
- Amherst scope policy for covered quests, excluded legacy quests, later-map
  quests, forbidden maps, and Shanks/off-island travel.
- guarded test reset harness shell for allowlisted test Agents.
- reactor interaction request/result/target contracts.
- reactor scope policy for Pio quest `1008` in Amherst.
- reactor target selector and execution-port seam.
- Amherst sub-phase plan card ending at `1000000 Amherst`.

Verified focused tests:

```powershell
.\mvnw.cmd -q "-Dtest=server.agents.capabilities.reactor.*Test,server.agents.capabilities.quest.AmherstQuestCatalogTest" test
```

Post-reconstruction proof still required:

- capability runtime active-frame and handoff/resume stack.
- parity tests proving primitive NavigationCapability and CombatCapability wrap
  existing reconstructed behavior without behavior drift.
- live NPC, reactor, reset, inventory-use, loot, and quest-state adapters.
- full Amherst smoke run from a clean allowlisted test Agent.

Latest catalog bundle-prep status:

```text
READY_WITH_DEFERRED_ITEMS
Ready entries: 33
Missing required entries: 0
Deferred optional entries: 1
```

The deferred optional entry is `reactors`, which remains a future catalog
extension. Maple Island Pio's reactor-box requirement is still documented as a
special rule for the MVP package until a full reactor catalog is built.

Current local scaffold:

```text
logs/soak/baseline/baseline-20260707-0512
```

Current scaffold status:

```text
Baseline evidence verification: INCOMPLETE
Failures: 0
Warnings: 3
```

The scaffold is structurally valid and includes provenance logs, startup and
shutdown content, and a completed summary shape. The remaining warnings are
expected until a real server run fills interval serverhealth samples and the
checklist.

Current warning IDs:

```text
evidence:serverhealth
evidence:serverhealth-sample-count
evidence:checklist
```

Use the status helper to print the current warning messages and recommended
commands:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakStatus.ps1
```

Use the next-step helper to print the current required step ids and
per-checklist commands:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakNextSteps.ps1
```

Current next-step ids:

```text
add-serverhealth-sample
review-checklist
```

The compact prep, handoff, remaining-work, and goal-audit reports also mirror
the next required evidence command as `baselineSoakNextRequiredCommand`, with
the required ids under `baselineSoakRequiredNextStepIds`. Use those fields when
automation needs the current evidence task without loading the full next-step
row list.

The goal-audit summary additionally exposes `completionBlockerIds`,
`primaryRemainingExternalBlocker`,
`completionReadyExceptExternalEvidence`, and
`completionNextRequiredCommand`, plus `completionProgressEstimatePercent`.
When the only stable blocker is baseline soak evidence,
`completionReadyExceptExternalEvidence` is `true`, progress is estimated at
`95`, and the next command points at the remaining evidence-capture task.

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
- wire inactive Amherst prep classes into Agent tick/core runtime.
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

Current verifier interpretation:

```text
Pre-reconstruction prep verification: INCOMPLETE
```

Goal-audit completion handoff:

```text
primaryRemainingExternalBlocker: baseline-soak-evidence
completionReadyExceptExternalEvidence: true
completionProgressEstimatePercent: 95
```

Expected stable blocker:

- latest baseline evidence scaffold is still incomplete.

Possible transient warnings:

- unstaged Agent reconstruction changes under `src/main/java/server/agents`.
- unstaged Agent reconstruction test changes under `src/test/java/server/agents`.

Those transient warnings should be resolved or isolated before claiming
completion. They should not be folded into safe-prep commits unless explicitly
requested as reconstruction work.

See `docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md` for the exact
evidence collection sequence and current scaffold path.
