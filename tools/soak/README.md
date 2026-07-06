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
- `New-BaselineSoakAuditEntry.ps1`

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

It does not:

- start the server.
- stop the server.
- edit `config.yaml`.
- modify server runtime behavior.
- modify Agent or bot code.
- generate Agent load.

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

To generate an audit-ready Markdown entry from a filled run folder:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakAuditEntry.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -OutputPath .\logs\soak\baseline\<runId>\audit-entry.md
```

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
