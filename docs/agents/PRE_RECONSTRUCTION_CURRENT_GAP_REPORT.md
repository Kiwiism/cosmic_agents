# Pre-Reconstruction Current Gap Report

Purpose:

```text
Record the current proof gap before claiming the broad pre-reconstruction
preparation goal is complete.
```

Snapshot time:

```text
2026-07-07 05:00:03 +08:00
```

## Baseline Safe-Prep Result

At the last clean safe-prep snapshot, the artifact and scope verifier reported:

```text
Pre-reconstruction prep verification: INCOMPLETE
Failures: 0
Warnings: 1
```

That warning is expected at this stage:

```text
Baseline evidence root exists but has no run folders.
```

This means the documentation, package specs, guardrails, and verifier tooling
are present. The remaining proof gap is actual runtime evidence from an
intentional baseline server soak run.

Future verifier runs may report additional warnings if unrelated reconstruction
work is dirty in the worktree, especially under `src/test/java/server/agents`
or other Agent/Bot paths. Those warnings should be resolved, isolated, or
explicitly excluded from any safe-prep commit before claiming completion.

## What Is Complete Enough For Handoff

The following pre-reconstruction materials are present and ready to guide
post-reconstruction implementation:

- server-only hardening and scaling plans.
- Agent scaling strategy and simulation-tier specifications.
- catalog platform contracts and runtime knowledge expectations.
- Maple Island MVP sequence, handoff, and first plan card.
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

## Current Compile Caveat

Latest safe-prep verification found the expected missing baseline evidence, and
the safe-prep tooling changes themselves do not touch Agent runtime paths.

A compile check currently fails in existing Agent reconstruction code:

```text
src/main/java/server/agents/runtime/AgentIdlePhysicsRuntime.java
incompatible types: server.agents.runtime.AgentRuntimeEntry cannot be converted
to server.bots.BotEntry
```

This is reconstruction-scope work. Do not fix it as part of safe
pre-reconstruction prep unless explicitly requested.

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
  -Stream serverhealth `
  -Text "<paste serverhealth sample>"
```

Use the same sample appender for:

- `serverhealth`.
- `scale-health`.
- `slow-operations`.
- `startup`.
- `shutdown`.

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
