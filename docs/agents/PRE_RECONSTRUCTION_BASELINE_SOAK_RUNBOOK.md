# Pre-Reconstruction Baseline Soak Runbook

Purpose:

```text
Collect the missing server-baseline evidence before Agent reconstruction work is
used to prove scale or uptime.
```

This runbook is for the first evidence stage only:

- no Agents.
- no BotClient load.
- no Agent reconstruction changes during the run.
- current `config.yaml` values remain as-is.
- server behavior is observed, not tuned.

The baseline separates normal server/player health from future Agent-driven
load. Do not use later Agent soak results as proof of baseline health unless
this stage also exists.

## Required Inputs

- Clean or intentionally documented git state.
- Server can start locally.
- Database is available.
- Operator can log in with a normal player client.
- Evidence scaffold and verifier are available:
  - `tools/soak/New-BaselineSoakEvidencePackage.ps1`
  - `tools/soak/Test-BaselineSoakEvidencePackage.ps1`
  - `tools/soak/Add-BaselineSoakSample.ps1`
  - `tools/soak/Update-BaselineSoakSummary.ps1`
- Prep verifier is available:
  - `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1`

## Recommended Run Sizes

Use the shortest run that gives useful evidence for the current phase.

| Run Type | Duration | Sample Interval | Use When |
| --- | ---: | ---: | --- |
| smoke | 15 minutes | 5 minutes | quick local sanity after prep commits |
| baseline | 60 minutes | 5 minutes | normal pre-reconstruction evidence |
| extended | 4-24 hours | 5 minutes | before major server-only optimization decisions |

For the current pre-reconstruction goal, a 60-minute baseline is enough to
replace the verifier's expected "no baseline evidence yet" warning with real
run data.

## Step 1 - Pre-Run Verification

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1
```

Expected before the first run:

- `FAIL`: should be `0`.
- `WARN`: likely `1` because baseline evidence does not exist yet.
- no forbidden Agent/bot/config staged paths.

Record the output in the run folder after the scaffold is created.

## Step 2 - Create Evidence Folder

For a 60-minute baseline:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakEvidencePackage.ps1 `
  -RunId "baseline-YYYYMMDD-HHmm" `
  -DurationMinutes 60 `
  -SampleIntervalMinutes 5
```

The script creates:

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

Copy the pre-run prep verifier output into the run `README.md` or a note inside
`summary.json`.

## Step 3 - Start Server

Start the server normally with the current local configuration.

Do not:

- add Agent load.
- spawn bot/Agent waves.
- change `config.yaml` for the run.
- change timer/DB runtime overrides unless the run is explicitly a tuning run.

Copy startup output into:

```text
startup.log
```

Mark the startup checklist item in:

```text
evidence-checklist.md
```

## Step 4 - Capture Samples Every Interval

Every sample interval, capture:

1. `!serverhealth` output.
2. scale-health log lines since the last sample.
3. slow-operation warnings since the last sample.
4. any manual observation notes.

Paste `!serverhealth` samples into:

```text
serverhealth-5min-samples.log
```

Recommended helper:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Target serverhealth `
  -FromClipboard
```

Paste scale-health lines into:

```text
scale-health.log
```

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Target scale-health `
  -FromClipboard
```

Paste slow warnings into:

```text
slow-operations.log
```

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Add-BaselineSoakSample.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Target slow-operations `
  -FromClipboard
```

Each sample should include a timestamp.

## Step 5 - Exercise Normal Player Paths

During the run, exercise normal server/player behavior:

- login/logout cycles.
- channel changes.
- map travel across multiple regions.
- mob killing and drops.
- item pickup.
- inventory movement.
- NPC open/close.
- shop open/buy/sell.
- quest lookup and one safe quest action if available.
- party create/join/leave if another client is available.
- manual saveall if appropriate.
- clean shutdown at the end.

Do not use this baseline to test Agent behavior.

## Step 6 - Update Summary

Update `summary.json` with final values:

- `durationMinutes`.
- `sampleIntervalMinutes`.
- `onlinePlayerPeak`.
- `onlineAgentPeak`: must be `0`.
- heap start/end.
- loaded map start/end.
- `dbWaitingMax`.
- `threadRejectedDelta`: should be `0`.
- `timerQueueMax`.
- slow save/broadcast counts.
- `stuckLoginCount`: should be `0`.
- `shutdownClean`.
- `restartClean`.
- notes.

If a value is unknown, keep it conservative and add a note explaining why.

Recommended helper:

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

## Step 7 - Shutdown And Restart Check

At the end:

1. Shut down normally.
2. Copy shutdown output into `shutdown.log`.
3. Restart once.
4. Confirm no manual DB cleanup was needed.
5. Set `shutdownClean` and `restartClean` in `summary.json`.

If login says a character is already logged in after restart, record it as a
stuck login signal and do not hide it.

## Step 8 - Verify Evidence

Run:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId>
```

Optional JSON:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\Test-BaselineSoakEvidencePackage.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -Json > .\logs\soak\baseline\<runId>\verification.json
```

Generate an audit-ready Markdown entry:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\soak\New-BaselineSoakAuditEntry.ps1 `
  -RunPath .\logs\soak\baseline\<runId> `
  -OutputPath .\logs\soak\baseline\<runId>\audit-entry.md
```

Expected:

- `PASS` for a complete run.
- `INCOMPLETE` if samples or checklist entries are missing.
- `FAIL` if required files, JSON, or baseline invariants are wrong.

## Step 9 - Update Audit

After a complete run, update:

- `docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md`

Record:

- run id.
- duration.
- peak players.
- peak Agents, expected `0`.
- heap start/end.
- loaded map start/end.
- max DB waiting.
- thread rejected delta.
- stuck login count.
- shutdown/restart result.
- verifier result.
- any follow-up server-only TODOs.

Use `audit-entry.md` as the starting point for the audit update, then add any
human context that the generated summary cannot know.

## Pass Criteria

Baseline evidence is acceptable when:

- required evidence files exist.
- `summary.json` is valid.
- `onlineAgentPeak` is `0`.
- `threadRejectedDelta` is `0`.
- `stuckLoginCount` is `0`.
- serverhealth samples meet the declared duration/interval.
- startup and shutdown logs are filled.
- checklist is complete.
- shutdown and restart are clean.

## Investigate Before Proceeding

Investigate before using the run as proof if:

- heap only rises and never stabilizes.
- loaded map count only increases.
- DB waiting is repeatedly nonzero.
- timer queues keep growing.
- slow save warnings are frequent.
- slow broadcast warnings are frequent.
- accounts become stuck as logged in.
- shutdown requires manual cleanup.
- restart requires manual DB repair.

## What This Does Not Prove

This run does not prove:

- 2000-Agent capacity.
- Agent background simulation safety.
- Agent persistence shortcuts.
- LLM control safety.
- economy runtime correctness.
- Maple Island MVP execution.

Those need post-reconstruction stages.
