# Profile Platform Prep Tools

This folder contains read-only prep tools for future Agent Profile runtime work.

These tools do not load profiles into live Agents, mutate character state, call
server runtime APIs, or touch Agent/Bot/client/config files.

## Verify Profile Templates

```powershell
powershell -ExecutionPolicy Bypass -File tools\profile-platform\Test-AgentProfileTemplates.ps1
powershell -ExecutionPolicy Bypass -File tools\profile-platform\Test-AgentProfileTemplates.ps1 -SummaryOnly -Json
```

The verifier checks the current data-only starter templates:

- `maple-island-mvp-tester`
- `islander`

It verifies required profile envelope fields, template ids, archetypes, trait
ranges, and Maple Island hard constraints such as blocking Shanks leave-island
travel.
Compact JSON sets `summaryOnly`, `rowsOmitted`, `checkCount`, `passCount`,
`warningIds`, `failureIds`, and `returnedCheckCount`, and omits detailed check
rows.

## Generate LLM-Safe Profile Summary

```powershell
powershell -ExecutionPolicy Bypass -File tools\profile-platform\New-AgentProfileSummary.ps1 `
  -ProfilePath docs\agents\profile-platform\templates\maple-island-mvp-tester.profile.json
```

The summary generator reads one profile/template JSON file and produces a
compact LLM-safe summary containing identity, hard constraints, plan
preferences, risk/social/economy preferences, build summary, and short notes.
It does not read live Agent state or mutate server data.

Machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File tools\profile-platform\New-AgentProfileSummary.ps1 `
  -ProfilePath docs\agents\profile-platform\templates\islander.profile.json `
  -Json
```

Compact machine-readable output for dashboards and goal checks:

```powershell
powershell -ExecutionPolicy Bypass -File tools\profile-platform\New-AgentProfileSummary.ps1 `
  -ProfilePath docs\agents\profile-platform\templates\islander.profile.json `
  -SummaryOnly -Json
```

Compact JSON sets `summaryOnly`, `rowsOmitted`, `llmNoteCount`,
`returnedLlmNoteCount`, `hardConstraintFieldCount`,
`returnedHardConstraintFieldCount`, `planPreferenceFieldCount`,
`returnedPlanPreferenceFieldCount`, and `detailBlocksOmitted`. Summary mode
keeps the identity summary and omits detailed constraints, preferences, traits,
build summary, and LLM notes.
