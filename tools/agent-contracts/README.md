# Agent Contract Tooling

This folder contains read-only contract verifiers for the post-reconstruction
Agent platform packages. The scripts validate docs, JSON schemas, package
contracts, population presets, plan safety rules, profile patch safety rules,
portable installer contracts, and Agent scaling contracts.

These tools do not modify runtime code, Agent behavior, BotClient behavior, or
configuration files.

## Verify All Contracts

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-contracts\Test-AgentContracts.ps1
```

Machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-contracts\Test-AgentContracts.ps1 -Json
```

Compact machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-contracts\Test-AgentContracts.ps1 -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `checkCount`, `passCount`,
`failCount`, `warnCount`, `nonPassingCheckCount`, `nonPassingCheckIds`, and
`returnedCheckCount`. It omits the full check row list so pre-reconstruction
handoff tools can use the verifier without loading hundreds of pass rows.

## Focused Verifiers

- `Test-PlanCardSafety.ps1` checks Maple Island plan card safety rules.
- `Test-ProfilePatchSafety.ps1` checks profile patch safety constraints.
- `Test-PopulationDirectorContracts.ps1` checks population director contracts.
- `Test-PortableInstallerContracts.ps1` checks portable installer contracts.
- `Test-AgentScalingContracts.ps1` checks simulation tier, materialization,
  background action, and soak-test contract coverage.

Each focused verifier also supports compact machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File tools\agent-contracts\Test-PlanCardSafety.ps1 -SummaryOnly -Json
```

Focused summary mode includes `summaryOnly`, `rowsOmitted`, `checkCount`,
`passCount`, `failCount`, `warnCount`, `failureIds`, `warningIds`, and
`returnedCheckCount`, with detailed check rows omitted.
