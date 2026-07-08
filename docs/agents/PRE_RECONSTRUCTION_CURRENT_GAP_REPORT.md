# Pre-Reconstruction Current Gap Report

Purpose:

```text
Record the current proof gap before claiming the broad pre-reconstruction
preparation goal is complete.
```

Snapshot time:

```text
2026-07-07 11:20:00 +08:00
```

## Baseline Safe-Prep Result

At the latest safe-prep snapshot, the artifact and scope verifier reported:

```text
Pre-reconstruction prep verification: INCOMPLETE
Failures: 0
Warnings: 3
```

Those warnings are expected at this stage:

```text
evidence:serverhealth
evidence:serverhealth-sample-count
evidence:checklist
```

Since that snapshot, the catalog verifier gate has been expanded. The combined
catalog verifier now covers game knowledge, NPC metadata, and Agent/LLM runtime
lookup catalogs, and is called by the pre-reconstruction verifier. Catalog
runtime-readiness and bundle-prep checks are also part of the
pre-reconstruction gate.

Catalog verification entry points:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Test-AllCatalogs.ps1
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Get-CatalogRuntimeReadiness.ps1
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Test-CatalogBundlePrep.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1
```

Reviewed non-mob drop source classifications are recorded in:

```text
docs/agents/catalog-overrides/drop-source-classifications.catalog.json
```

This means the documentation, package specs, guardrails, catalog verifiers, and
safe-prep verifier tooling are present. The remaining proof gap is actual
runtime evidence from an intentional baseline server soak run. The reactor and
field-object catalog remains an intentional deferred catalog extension and is
not required for the current Maple Island MVP handoff.

Future verifier runs may report additional warnings if unrelated reconstruction
work is dirty in the worktree, especially under `src/test/java/server/agents`
or other Agent/Bot paths. Those warnings should be resolved, isolated, or
explicitly excluded from any safe-prep commit before claiming completion.

Current handoff helper interpretation:

```text
READY means all safe-prep artifacts and evidence are clean.
READY_WITH_WARNINGS means safe-prep can continue, but the report found expected
runtime-evidence gaps or Agent/Bot/config paths that must be excluded.
BLOCKED_FOR_SAFE_PREP_COMMIT means a forbidden or review-required path is
staged and must be unstaged or explicitly approved before a safe-prep-only
commit.
```

Because Agent reconstruction may be active in the same worktree, do not trust fixed counts
in this document. Use the handoff and commit-candidate helpers to
refresh the live status before a commit:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionHandoff.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1 -FailOnBlockers -FailOnReviewRequired
```

For a safe-prep-only commit, `Commit blockers` must be `0`, forbidden staged
paths must be empty, and any forbidden exclusions must be intentionally left
out of the commit.

## What Is Complete Enough For Handoff

The following pre-reconstruction materials are present and ready to guide
post-reconstruction implementation:

- server-only hardening and scaling plans.
- Agent scaling strategy and simulation-tier specifications.
- catalog platform contracts, runtime knowledge expectations, standalone
  catalog verifiers, and reviewed drop-source classification overrides.
- Maple Island MVP sequence, handoff, and first plan card.
- Maple Island Amherst sub-phase MVP and test plan as the first post-reconstruction smoke slice:
  `docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md` and
  `docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_TEST_PLAN.md`.
- Amherst sub-phase documentation and plan-card artifacts for the first
  post-reconstruction smoke slice.
- Agent Java prep under `src/main/java/server/agents` and
  `src/test/java/server/agents` is not counted as safe-prep completion in this
  lane. It must stay excluded from safe-prep commits unless explicitly
  requested as reconstruction/runtime work.
- portable Agent platform package boundaries.
- Plan Runtime and Capability Runtime specifications.
- NPC/Quest capability specifications.
- Agent Profile system specifications.
- Economy Engine specifications.
- Database Console and Server Console planning records.
- NuTNNuT-over-Cosmic decision records.
- pre-reconstruction goal prompt and safe-prep status map.
- baseline soak runbook and evidence collection scripts.

## Remaining Proof Gap

Before the goal can be honestly marked complete, collect at least one real
baseline server/player evidence package.

Current local scaffold:

```text
logs/soak/baseline/baseline-20260707-0512
```

The scaffold verifies as structurally valid but incomplete. It has provenance
logs and templates, but it still needs real startup, serverhealth,
scale-health, slow-operation, shutdown, checklist, and summary evidence from an
actual server run.

Required baseline proof:

- startup log.
- interval `!serverhealth` or equivalent server-health samples.
- scale-health samples.
- slow-operation samples, even if empty.
- shutdown log.
- completed `summary.json`.
- verifier result for that run.
- audit-entry snippet copied or referenced from the completion audit.

This baseline should not include live Agent reconstruction changes during the
run. It is meant to prove the current server baseline before Agent-specific
runtime work begins.

## Current Compile Status

Latest safe-prep verification found the expected missing baseline evidence.
The safe-prep tooling changes themselves do not touch Agent runtime paths.

Latest catalog verification status:

```text
Combined catalog verification: PASS
Game knowledge catalog: PASS
NPC catalog: PASS
Agent/LLM catalog: PASS
```

Latest pre-reconstruction verifier status after the catalog bundle-prep gate in
the current local worktree:

```text
Pre-reconstruction prep verification: INCOMPLETE
Failures: 0
Warnings: 1
```

The current verifier warnings are:

```text
Latest baseline evidence run baseline-20260707-0512 is INCOMPLETE with 3 warning(s).
```

Current baseline evidence warning IDs:

```text
evidence:serverhealth - serverhealth samples are empty or template-only.
evidence:serverhealth-sample-count - serverhealth sample count 0 is below expected minimum 1.
evidence:checklist - 12 checklist items remain unchecked.
```

Recommended status helper:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakStatus.ps1
```

Recommended next-step helper:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakNextSteps.ps1
```

The next-step helper currently reports:

```text
nextStepIds: add-serverhealth-sample, review-checklist
requiredNextStepIds: add-serverhealth-sample, review-checklist
nextRequiredCommand: powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 -RunPath "logs\soak\baseline\baseline-20260707-0512" -Target serverhealth -FromClipboard
uncheckedChecklistCommandCount: 12
```

The same next action is mirrored as `baselineSoakNextRequiredCommand` in:

- `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1 -SummaryOnly -Json`
- `tools/pre-reconstruction/Get-PreReconstructionHandoff.ps1 -SummaryOnly -Json`
- `tools/pre-reconstruction/Get-PreReconstructionRemainingWork.ps1 -SummaryOnly -Json`
- `tools/pre-reconstruction/Get-PreReconstructionGoalAudit.ps1 -SummaryOnly -Json`

The goal audit also exposes `completionBlockerIds`,
`primaryRemainingExternalBlocker`, `completionReadyExceptExternalEvidence`,
`completionNextRequiredCommand`, and `completionProgressEstimatePercent` for
compact handoffs. In the current safe-prep state, the stable external blocker
is `baseline-soak-evidence`, and progress is estimated at `95` while that
external evidence remains incomplete.

Use its `uncheckedChecklistCommands` output to mark reviewed checklist items
without hand-editing Markdown after the corresponding runtime evidence has
actually been collected.

Future verifier runs may also warn about reconstruction-side worktree dirt under
Agent/Bot paths. Those files should not be included in a safe-prep commit unless
explicitly requested as reconstruction work. With those isolated, the stable
safe-prep proof gap remains the incomplete baseline evidence package.

## Amherst Prep Implementation Gap

The Amherst prep classes are intentionally not a live runtime implementation.
They are current-state evidence for contracts and policy only.

Ready:

- covered Amherst quest catalog.
- Amherst quest/map/NPC travel scope policy.
- guarded reset harness shell.
- reactor target request/result model.
- reactor target selection by live reactor object attributes.
- Amherst plan-card route through `1000000 Amherst`.

Still missing after reconstruction:

- single-active-capability runtime with active frame, paused parent frame stack,
  child-result resume, timeout, cancellation, and audit state.
- primitive `NavigationCapability` wrapper over existing reconstructed
  movement/navigation behavior.
- primitive `CombatCapability` wrapper over existing reconstructed
  grind/combat behavior.
- objective capabilities that emit handoff requests instead of directly nesting
  lower-level behavior.
- live NPC quest interaction capability.
- live reset harness implementation for allowlisted test Agents.
- live reactor hit/touch adapter and drop observation.

The implementation rule is:

```text
Wrap existing reconstructed behavior first, prove parity, then add
Amherst-specific objective constraints.
```

Latest catalog bundle prep status:

```text
Catalog bundle prep: READY_WITH_DEFERRED_ITEMS
Ready entries: 33
Missing required entries: 0
Deferred optional entries: 1
```

The deferred optional entry is the future reactor/field-object catalog.

Latest compile check:

```text
.\mvnw.cmd -DskipTests compile
BUILD SUCCESS
```

## Exact Collection Sequence

Create the evidence package:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -RunId "baseline-YYYYMMDD-HHmm" `
  -DurationMinutes 60 `
  -SampleIntervalMinutes 5
```

During the run, append samples:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Target serverhealth `
  -Text "<paste serverhealth sample>"
```

Use the same sample appender for:

- `serverhealth`.
- `scale-health`.
- `slow-operations`.
- `startup`.
- `shutdown`.

To see the exact current serverhealth/checklist commands for the latest local
scaffold:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakNextSteps.ps1 -Json
```

After the run, update the summary:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Update-BaselineSoakSummary.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -DurationMinutes 60 `
  -OnlinePlayerPeak <number> `
  -OnlineAgentPeak 0 `
  -HeapStartMb <number> `
  -HeapEndMb <number> `
  -LoadedMapStart <number> `
  -LoadedMapEnd <number> `
  -DbWaitingMax <number> `
  -ThreadRejectedDelta <number> `
  -TimerQueueMax <number> `
  -SlowSaveCount <number> `
  -SlowBroadcastCount <number> `
  -StuckLoginCount <number> `
  -ShutdownClean <true-or-false> `
  -RestartClean <true-or-false>
```

Verify the run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId>
```

Write a JSON verifier result:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Json > .\logs\soak\baseline\<runId>\verification.json
```

Generate the audit entry:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakAuditEntry.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -OutputPath .\logs\soak\baseline\<runId>\audit-entry.md
```

Then copy or reference the audit entry in:

```text
docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md
```

## Completion Rule

Do not mark the broad pre-reconstruction goal complete until:

- `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1` reports `PASS`.
- the baseline evidence verifier reports a completed run.
- the completion audit records the real run id and summary.
- no live Agent/Bot source or config files are part of the safe-prep change.

Until then, the correct state is:

```text
Safe-prep artifacts ready; runtime evidence pending.
```
