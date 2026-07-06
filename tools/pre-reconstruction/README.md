# Pre-Reconstruction Prep Tools

Purpose:

```text
Verify that the safe pre-reconstruction planning artifacts and guardrails are
present before Agent runtime implementation begins.
```

Current tool:

- `Test-PreReconstructionPrep.ps1`

## Usage

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1
```

Machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1 -Json
```

## What It Checks

- required safe-prep docs exist.
- baseline soak runbook exists.
- required package specs exist.
- Maple Island MVP handoff, sequence, and plan card exist.
- catalog, profile, economy, scaling, server adapter, console, and review docs
  exist.
- soak evidence scaffold/verifier scripts exist.
- newest baseline evidence run verifies as complete when a run folder exists.
- no forbidden Agent/bot/config paths are staged.
- no unstaged production Agent/bot/config changes are present without a
  warning.
- whether baseline soak evidence has been collected.
- whether the newest baseline soak folder is complete or still only scaffolded.

## Status Meaning

- `PASS`: all required artifacts exist and no warnings are present.
- `INCOMPLETE`: required artifacts exist, but evidence or local state still
  needs review.
- `FAIL`: required artifacts are missing or forbidden files are staged.

This tool does not:

- compile the server.
- start the server.
- run soak tests.
- modify files.
- prove Agent runtime implementation is complete.

Continue to use the normal safe-prep gates before committing:

```powershell
git diff --check
git diff --cached --name-only -- src/main/java/server/agents src/main/java/server/bots src/test/java/server/agents src/test/java/server/bots
git diff --cached --name-only -- config.yaml src/main/resources/config.yaml
.\mvnw.cmd -DskipTests clean compile
```
