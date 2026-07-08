# Plan Runtime Prep Tools

This folder contains read-only prep tools for future Plan Runtime work.

These tools do not assign plans to Agents, execute objectives, call server
runtime APIs, or modify Agent/Bot/client/config files.

## Read A Plan Card

```powershell
powershell -ExecutionPolicy Bypass -File tools\plan-runtime\Get-PlanCardSummary.ps1
```

The default plan is:

```text
docs/agents/plans/maple-island-mvp.plan.json
```

The loader validates the file can be read as JSON, checks the required plan
envelope fields, summarizes route steps/objective kinds, counts quest/map
references, reports forbidden actions, and infers future capability dependencies
such as NPC quest interaction, navigation, inventory item use,
reactor/field-object interaction, controlled test injection, and plan control.

Write a review artifact:

```powershell
powershell -ExecutionPolicy Bypass -File tools\plan-runtime\Get-PlanCardSummary.ps1 `
  -OutputPath tmp\maple-island-plan-summary.md
```

Machine-readable form:

```powershell
powershell -ExecutionPolicy Bypass -File tools\plan-runtime\Get-PlanCardSummary.ps1 -Json
```

Compact machine-readable form for handoff/audit scripts:

```powershell
powershell -ExecutionPolicy Bypass -File tools\plan-runtime\Get-PlanCardSummary.ps1 -SummaryOnly -Json
```

Summary mode keeps root-level fields such as `checkCount`, `passCount`,
`failCount`, `warnCount`, `failureIds`, `warningIds`, `routeStepCount`,
`objectiveCount`, `uniqueMapCount`, `uniqueQuestCount`,
`forbiddenActionCount`, `capabilityDependencyCount`, `summaryOnly`,
`rowsOmitted`, `returnedCheckCount`, `returnedRouteStepCount`,
`returnedObjectiveCount`, `returnedObjectiveKindCount`, and
`returnedCapabilityDependencyCount`. It omits detailed check, route, and
objective rows while keeping objective-kind and capability-dependency summaries.

Runtime Plan Loader implementation still waits for reconstructed Agent
boundaries.
