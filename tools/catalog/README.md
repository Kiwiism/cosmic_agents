# Catalog Tooling

This folder contains orchestration tools for offline catalog preparation.

These scripts do not modify runtime code, Agent behavior, BotClient behavior, or
configuration files.

## Verify All Catalogs

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-AllCatalogs.ps1
```

This runs the game, NPC, and Agent/LLM catalog verifiers and returns a combined
status.

Machine-readable compact output:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-AllCatalogs.ps1 -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `verifierCount`,
`returnedVerifierCount`, `checkCount`, `passCount`, `failCount`, `warnCount`,
`warningIds`, `failureIds`, `returnedCheckCount`, `nonPassingCheckCount`,
`verifierSummaries`, and `nonPassingChecks`. It omits the full nested verifier
rows while preserving the same default full output for existing callers.

## Write A Review Report

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-AllCatalogs.ps1 `
  -OutputPath tmp\catalog-verification-report.md
```

Use this report after WZ, SQL, script, or exporter updates before runtime
integration consumes refreshed catalog bundles.

## Show Current Catalog Status

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Get-CatalogStatus.ps1
```

This prints a compact status report for the current generated catalog folders,
the latest refresh run under `tmp/catalog-refresh/`, and the current combined
verification result.

For machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Get-CatalogStatus.ps1 -Json
```

Compact machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Get-CatalogStatus.ps1 -SummaryOnly -Json
```

The catalog status JSON includes `summaryOnly`, `rowsOmitted`,
`catalogDirectoryCount`, `existingCatalogDirectoryCount`, `verifierCount`,
`latestRefreshExists`, `returnedCatalogDirectoryCount`,
`returnedVerifierCount`, `returnedReportCount`, `returnedRunCount`, and
latest-refresh report counts so automation can show catalog health without
loading directory, verifier, and refresh-report rows.

## Show Runtime Readiness

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Get-CatalogRuntimeReadiness.ps1
```

This reports which generated lookup areas are ready for future runtime
integration, which are deferred until Agent boundaries exist, and which files
are missing. It is read-only and does not refresh catalogs.

Machine-readable form:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Get-CatalogRuntimeReadiness.ps1 -Json
```

Compact machine-readable form:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Get-CatalogRuntimeReadiness.ps1 -SummaryOnly -Json
```

The JSON output includes compact `statusCounts`, `categoryCounts`,
`deferredAreas`, `missingAreas`, and `nextActions` fields so handoff tools can
identify exact catalog-runtime areas still waiting for later implementation
without scraping Markdown. Summary mode also includes `summaryOnly`,
`rowsOmitted`, `checkCount`, `passCount`, `failCount`, `warnCount`,
`warningIds`, `failureIds`, `areaCount`, `returnedAreaCount`, `fileFactCount`,
`returnedFileFactCount`, `readyAreaIds`, `deferredAreaIds`, `missingAreaIds`,
`deferredAreaCount`, `missingAreaCount`, `nextActionCount`,
`returnedNextActionCount`, and `manifestOmitted`, and omits
the full manifest, file-fact rows, and area rows.

## Smoke-Test Runtime Lookups

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogQuerySmoke.ps1
```

This performs read-only representative lookups against the generated Agent/LLM
and NPC catalogs. It checks Maple Island MVP quest/map facts, NPC action and
placement facts, item source facts, and required action affordances. It does
not refresh catalogs or modify runtime files.

Machine-readable form:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogQuerySmoke.ps1 -Json
```

Compact machine-readable form:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogQuerySmoke.ps1 -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `checkCount`,
`passCount`, `failCount`, `warnCount`, `failureIds`, `warningIds`, and
`returnedCheckCount`. It omits detailed smoke-check rows while preserving the
same default full output for existing callers.

## Check Bundle Prep

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogBundlePrep.ps1
```

This checks whether the current generated catalog files are ready to be mapped
into the future portable bundle manifest. It does not parse the large generated
JSON files during the check; it verifies presence, size, category, and whether
each entry is required or intentionally deferred.

To write a review report and draft manifest:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogBundlePrep.ps1 `
  -OutputPath tmp\catalog-bundle-prep-report.md `
  -OutputManifestPath tmp\draft-catalog-bundle-manifest.json
```

The draft manifest includes SHA-256 hashes for generated catalog, index,
summary, report, and override files. Source-root hashes are opt-in because WZ
trees can be large:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogBundlePrep.ps1 `
  -OutputPath tmp\catalog-bundle-prep-report.md `
  -OutputManifestPath tmp\draft-catalog-bundle-manifest.json `
  -OutputSourceHashesPath tmp\catalog-source-hashes.json
```

For compact machine-readable bundle-prep status:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Test-CatalogBundlePrep.ps1 -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `checkCount`, `passCount`,
`failCount`, `warnCount`, `warningIds`, `failureIds`, `returnedEntryCount`,
`manifestOmitted`, `categoryCounts`, `statusCounts`, `requiredEntryKeys`,
`deferredEntryKeys`, and `missingRequiredKeys`. It omits the detailed entry rows
and embedded draft manifest from the report, while still allowing explicit
`-OutputManifestPath` writes when a draft manifest artifact is needed.

## Refresh All Catalogs

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Update-AllCatalogs.ps1
```

This snapshots the existing Agent/LLM catalog, runs the game, NPC, and
Agent/LLM exporters, writes the drop-source gap report, writes the combined
verification report, writes the catalog bundle prep report, writes a draft
manifest with generated file hashes, writes a source-hash report, writes the
catalog status and runtime-readiness reports, and writes an Agent/LLM catalog
diff.

For a quick smoke check without rerunning the exporters:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Update-AllCatalogs.ps1 -SkipExport
```

Machine-readable compact refresh summary:

```powershell
powershell -ExecutionPolicy Bypass -File tools\catalog\Update-AllCatalogs.ps1 -SkipExport -SummaryOnly -Json
```

The refresh summary JSON includes `summaryOnly`, `rowsOmitted`, `runDir`,
`summaryPath`, `reportCount`, `returnedReportCount`, `skipExport`,
`skipNpcApproach`, and `hadAgentLlmSnapshot`. Summary mode omits detailed
report rows while still writing the same refresh reports under the selected
report directory.

Generated review files are written under `tmp/catalog-refresh/`.
