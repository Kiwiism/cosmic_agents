# Soak Evidence Tools

Purpose:

```text
Prepare repeatable evidence folders for server baseline and future Agent soak
runs without changing server behavior.
```

Current tool:

- `New-BaselineSoakEvidencePackage.ps1`
- `Test-BaselineSoakEvidencePackage.ps1`
- `Add-BaselineSoakSample.ps1`
- `Update-BaselineSoakSummary.ps1`
- `New-BaselineSoakAuditEntry.ps1`
- `Get-BaselineSoakStatus.ps1`
- `Get-BaselineSoakNextSteps.ps1`
- `Set-BaselineSoakChecklistItem.ps1`
- `Test-SoakPopulationPreset.ps1`
- `Test-AgentSchedulerLiveGatePreflight.ps1`

`Test-SoakPopulationPreset.ps1` verifies data-only Agent soak population
presets under `docs/agents/soak-test-harness/presets`. It checks required
fields, ratio totals, duplicate ids, and simple capacity invariants. It does
not create Agents or connect to the server.
Use `-SummaryOnly -Json` for compact automation output. The report includes
`checkCount`, `passCount`, `warningIds`, `failureIds`, and
`returnedCheckCount`, and omits detailed check rows when summary mode is used.

`Test-AgentSchedulerLiveGatePreflight.ps1` is a read-only safety check for the
centralized scheduler live gate. It verifies the expected branch and clean
worktree, packaged server artifact, shared WZ directory junction, free server
ports, running MapleStory client, explicitly pinned disposable database, and
an external runtime/cache root. The shared WZ target remains policy-level
read-only. The tool prints the exact scheduler JVM arguments and
`COSMIC_AGENT_POPULATION_FILE` redirect without starting the server or writing
runtime data.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-AgentSchedulerLiveGatePreflight.ps1 `
  -ExpectedDatabaseName cosmic_scheduler_soak_20260714 `
  -AllowConfigOverride `
  -AllowClientLaunchAfterServer
```

Use `-Json` or `-SummaryOnly -Json` for automation. A normal `cosmic` database
is rejected; the live gate must use an explicitly named disposable database.
`-AllowConfigOverride` permits exactly one dirty path, `config.yaml`, so the
operator can point this worktree at that database without committing the local
credential/database override. Any other pending path still fails preflight.
By default a running MapleStory process is required. Use
`-AllowClientLaunchAfterServer` only when the v83 client must be launched after
the server reaches the listening state; the operator must still launch and
verify the client before recording live evidence.

For a populated stage, pass `-MinimumTargetAgents <count>`. The preflight then
requires the external `population.json`, validates its required fields and
Agent records, rejects duplicate character ids or case-insensitive names, and
verifies that `enabled` plus `multiplier` produce at least the requested target:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-AgentSchedulerLiveGatePreflight.ps1 `
  -ExpectedDatabaseName cosmic_scheduler_soak_20260714 `
  -MinimumTargetAgents 500 `
  -AllowConfigOverride `
  -AllowClientLaunchAfterServer
```

This is a read-only roster check. It does not create backing characters or
query their Agent-only account eligibility. Confirm the live eligible count
with `@agentpop status` after startup. Population presets under
`docs/agents/soak-test-harness/presets` describe desired distributions; they
are not runtime rosters.

## Baseline Evidence Workflow

Use this before a server-only baseline smoke/soak run.

```powershell
.\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -DurationMinutes 60 `
  -SampleIntervalMinutes 5
```

If local PowerShell script execution is disabled, run it with a process-local
policy bypass:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -DurationMinutes 60 `
  -SampleIntervalMinutes 5
```

Add `-Json` to return `runId`, `runPath`, `summaryPath`,
`expectedServerHealthSampleCount`, `packageFileCount`,
`missingPackageFileCount`, and the generated `packageFiles` list for
automation. `DurationMinutes` and `SampleIntervalMinutes` must both be `1` or
greater so the expected server-health sample count is meaningful.
Use `-SummaryOnly -Json` to keep package counts while omitting the per-file
`packageFiles` rows; compact output sets `summaryOnly`, `rowsOmitted`, and
`returnedPackageFileCount`.

This creates:

```text
logs/soak/baseline/<runId>/
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

The script records:

- run id.
- current branch.
- current commit.
- short git status.
- `config.yaml` SHA-256 hash.
- Java version.
- OS/user/machine metadata.
- pre-run prep verifier output.
- pre-run baseline evidence status output.

It does not:

- start the server.
- stop the server.
- edit `config.yaml`.
- modify server runtime behavior.
- modify Agent or bot code.
- generate Agent load.

To quickly inspect whether baseline evidence exists and what the next operator
step should be:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakStatus.ps1
```

Machine-readable form:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakStatus.ps1 -Json
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakStatus.ps1 -SummaryOnly -Json
```

The status helper also reports the latest verifier warnings/failures and
recommended follow-up commands, such as appending `!serverhealth` samples or
listing unchecked checklist items.

The JSON output includes `recommendedCommands` and a compact `summary` object
for pre-reconstruction handoff tooling. The summary includes the latest run id,
verification status, warning ids, failure ids, warning/failure counts,
readiness, `latestEvidenceSummary`, and the number of recommended commands.
For lightweight consumers, the report also mirrors root-level soak status fields:
`latestWarningIds`, `latestFailureIds`, `recommendedCommandCount`,
`serverHealthSampleCount`, `expectedServerHealthSampleCount`,
`checklistCheckedCount`, `checklistUncheckedCount`, and `checklistItemCount`.
When `-SummaryOnly` is used, the status helper sets `summaryOnly`,
`rowsOmitted`, `returnedRunCount`, `returnedWarningCount`,
`returnedFailureCount`, and `returnedRecommendedCommandCount`, and omits the
detailed warning, failure, command, summary, and run rows.
`latestEvidenceSummary` reports serverhealth sample counts, expected sample
counts, startup/shutdown line counts, and checklist checked/unchecked counts.
Common unresolved warning ids include
`evidence:serverhealth`, `evidence:serverhealth-sample-count`, and
`evidence:checklist`.

To get an operator-facing next-step report for the latest baseline run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakNextSteps.ps1
```

Machine-readable form:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakNextSteps.ps1 -Json
powershell -ExecutionPolicy Bypass -File .\tools\soak\Get-BaselineSoakNextSteps.ps1 -SummaryOnly -Json
```

The next-step helper is read-only. It combines the latest verifier result,
evidence summary counts, unchecked checklist items, and exact follow-up
commands without editing logs, config, server files, or Agent runtime files.
Its JSON output includes the selected `runId`, `latestRunId`, `runPath`,
`latestRunPath`,
`checklistItemCount`, checked/unchecked checklist counts, and ordered
`nextSteps` so handoff tooling can show `checked/total` progress without
inferring values from Markdown.
For lightweight consumers, the report also mirrors `nextStepIds`,
`requiredNextStepIds`, `nextStepCount`, `requiredNextStepCount`,
`nextRequiredCommand`, `serverHealthSampleCount`,
`expectedServerHealthSampleCount`, `checklistCheckedCount`,
`checklistUncheckedCount`, `checklistItemCount`, and
`uncheckedChecklistItemCount` at the root level. Full output also includes
`uncheckedChecklistCommands`, a per-unchecked-item command list for marking
reviewed checklist lines without hand-editing Markdown.
When `-SummaryOnly` is used, the next-step helper sets `summaryOnly`,
`rowsOmitted`, `returnedNextStepCount`, and
`returnedUncheckedChecklistItemCount`, and
`returnedUncheckedChecklistCommandCount`, and omits detailed `nextSteps`,
`uncheckedChecklistItems`, and `uncheckedChecklistCommands` rows while
preserving the root-level ids and counts.

## During The Run

Recommended baseline flow:

1. Create the evidence folder with the script.
2. Start the server normally.
3. Copy startup logs into `startup.log`.
4. Every sample interval, paste `!serverhealth` output into
   `serverhealth-5min-samples.log`.
5. Copy scale-health log lines into `scale-health.log`.
6. Copy slow-operation warnings into `slow-operations.log`.
7. Exercise normal player/server scenarios from `docs/SOAK_TEST_CHECKLIST.md`.
8. Shut down cleanly.
9. Copy shutdown logs into `shutdown.log`.
10. Update `summary.json` with final numbers and notes.

To append timestamped samples without manually editing the files:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Target serverhealth `
  -FromClipboard
```

Supported targets:

- `serverhealth`
- `scale-health`
- `slow-operations`
- `startup`
- `shutdown`

Samples can come from `-Text`, `-InputPath`, `-FromClipboard`, or pipeline
input.
Add `-Json` to return `targetPath`, `sampleCount`, and
`appendedCharacterCount` for automation that wants to confirm the sample was
written without parsing console text.
Use `-DryRun` to validate the target file, input text, character count, and
predicted sample count without appending anything. Dry-run JSON returns
`status` as `DRY_RUN`, `dryRun`, `appended`, `sampleCountBefore`, and the
predicted `sampleCount`.
Use `-SummaryOnly -Json` for consistency with the other soak helpers. The
sample appender already omits raw sample text from JSON, so compact output
keeps the same root-level count fields and sets `summaryOnly` plus
`rowsOmitted`.

To update `summary.json` without hand-editing JSON:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Update-BaselineSoakSummary.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -OnlinePlayerPeak 1 `
  -OnlineAgentPeak 0 `
  -ThreadRejectedDelta 0 `
  -StuckLoginCount 0 `
  -ShutdownClean true `
  -RestartClean true `
  -Note "Baseline completed without manual DB cleanup."
```

Add `-Json` to return `summaryPath`, `changedFields`,
`changedFieldCount`, and the updated `summary` object for automation.
Use `-DryRun` to preview the changed fields and projected summary without
writing `summary.json`; dry-run output sets `status` to `DRY_RUN`, `dryRun`,
and `updated=false`.
Use `-SummaryOnly -Json` to keep changed-field counts while omitting the full
updated summary object; compact output sets `summaryOnly` and
`summaryOmitted`.

To list and mark checklist items without hand-editing Markdown:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Set-BaselineSoakChecklistItem.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -List
```

Machine-readable checklist status:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Set-BaselineSoakChecklistItem.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -List `
  -SummaryOnly `
  -Json
```

The JSON output includes checked and unchecked counts plus the parsed checklist
items, which lets handoff tooling show exactly what still needs review.
When `-SummaryOnly` is used, the checklist helper sets `summaryOnly`,
`rowsOmitted`, and `returnedItemCount`, preserves the root counts and matched
or changed counts, and omits detailed checklist item rows.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Set-BaselineSoakChecklistItem.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Pattern "Startup logs"
```

Use `-Uncheck` to clear matched items, or `-All` to mark all checklist items
after a completed reviewed run.

## Pass / Fail Interpretation

Use:

- `docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md`
- `docs/SOAK_TEST_CHECKLIST.md`

Do not treat this baseline as proof of Agent scale. Agent stages require the
reconstructed Agent runtime and the dedicated Agent soak harness.

After the run folder has been filled, verify it:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId>
```

To write machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Json > .\logs\soak\baseline\<runId>\verification.json
```

For compact automation output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -SummaryOnly `
  -Json
```

When `-SummaryOnly` is used, the verifier sets `summaryOnly`,
`rowsOmitted`, `checkCount`, `passCount`, `warningIds`, `failureIds`, and
`returnedCheckCount`, preserves `evidenceSummary`, and omits detailed check
rows.

To generate an audit-ready Markdown entry from a filled run folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakAuditEntry.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -OutputPath .\logs\soak\baseline\<runId>\audit-entry.md
```

Add `-Json` to return `auditStatus`, `verificationStatus`, `outputPath`, and
`markdownCharacterCount` after the Markdown is generated. Use
`-SummaryOnly -Json` to keep the status fields while omitting the generated
Markdown body from JSON; summary output sets `summaryOnly` and
`markdownOmitted`.

Copy the generated entry into:

- `docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md`

The verifier reports:

- `PASS` when required files exist and summary/log signals look complete.
- `INCOMPLETE` when the folder is structurally valid but still missing run
  evidence.
- `FAIL` when required files, JSON, or baseline invariants are wrong.

Important checks:

- required files exist.
- `summary.json` has required fields.
- `onlineAgentPeak` is zero for the server baseline.
- `threadRejectedDelta` and `stuckLoginCount` are zero.
- `serverhealth-5min-samples.log` has enough samples for the declared
  duration and interval.
- `startup.log` and `shutdown.log` are filled.
- `evidence-checklist.md` has checked items, ideally all checked.

## Example With Explicit Run Id

```powershell
.\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -RunId "baseline-20260707-local-smoke" `
  -DurationMinutes 30 `
  -SampleIntervalMinutes 5
```

## Testing The Scaffold Without Writing Logs

Use a temporary output folder:

```powershell
$tmp = Join-Path $env:TEMP "cosmic-baseline-soak-test"
Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
.\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -RunId "baseline-test" `
  -OutputRoot $tmp
```

Execution-policy-safe form:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -RunId "baseline-test" `
  -OutputRoot $tmp
```

Then verify the temp package:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath (Join-Path $tmp "baseline-test")
```
