# Pre-Reconstruction Safe Prep Status

Purpose:

```text
Track all safe work that can be completed before the Nutnnut bot-to-Agent
reconstruction is finished, without changing live Agent runtime behavior.
```

This document is the readiness map for the broad pre-reconstruction goal. It
ties together server hardening, catalogs, Maple Island MVP, portable platform
contracts, plan cards, profiles, economy, consoles, and NuTNNuT-over-Cosmic
review records.

Completion/evidence audit:

- `docs/agents/PRE_RECONSTRUCTION_COMPLETION_AUDIT.md`
- `docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md`
- `docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md`

Reusable goal prompt:

- `docs/agents/PRE_RECONSTRUCTION_GOAL_PROMPT.md`

## Hard Boundaries

Before reconstruction is stable:

- Do not edit live Agent behavior or Agent capability code under
  `src/main/java/server/agents` in this concurrent-prep lane unless a later
  task explicitly changes scope.
- Do not edit `src/main/java/server/bots`.
- Do not change BotClient behavior.
- Do not change live Agent navigation, combat, looting, shop, NPC, quest, or
  LLM behavior.
- Do not wire LLM, economy, profile learning, plan cards, NPC quest execution,
  or background simulation into live runtime.
- Do not change `config.yaml` values unless explicitly requested.
- Keep server changes default-preserving.
- Keep Agent-related work as docs, contracts, catalog prep, schemas, TODOs, or
  server-only hooks that do not live under `src/main/java/server/agents`.
- Any Java prep under `src/main/java/server/agents` or
  `src/test/java/server/agents` is outside this lane and must be excluded from
  safe-prep commits unless explicitly requested.

Allowed before reconstruction is stable:

- server-only diagnostics and hardening that preserve behavior.
- documentation.
- catalog builders and generated read-only catalog data.
- schema and interface contracts.
- implementation plans.
- review records.
- test/soak plans that do not alter live Agent behavior.
- verification tools that only inspect docs, git state, or evidence folders.

## Readiness Matrix

| Area | Status | Primary Evidence | Safe Next Work |
| --- | --- | --- | --- |
| Server-only hardening and scaling prep | Ready for current phase | `docs/SERVER_PLAYER_SCALE_IMPLEMENTATION_PLAN.md`, `docs/SERVER_SCALE_TODO.md`, `docs/SERVER_HARDENING_DIAGNOSTICS.md` | Run soak tests and use diagnostics to choose later behavior changes. |
| Agent scaling documentation | Ready as strategy and package specs, implementation waits | `docs/agents/AGENT_ENGINE_SCALING_TRACK.md`, `docs/agents/AGENT_ENGINE_OPTIMIZATION.md`, `docs/agents/simulation-tier-runtime/*`, `docs/agents/background-action-runtime/*`, `docs/agents/soak-test-harness/*` | Implement only after reconstruction and collect soak evidence. |
| Catalog and runtime knowledge prep | Ready as contract with standalone verifier gate | `docs/agents/catalog-platform/*`, `docs/agents/llm-autonomy/GAME_KNOWLEDGE_CATALOGS.md`, `tools/game-catalog`, `tools/npc-catalog`, `tools/agent-llm-catalog`, `tools/catalog`, `docs/agents/catalog-overrides/drop-source-classifications.catalog.json` | Implement unified runtime bundle loader after choosing builder entry point. |
| Maple Island MVP documentation | Ready as first gameplay slice | `docs/agents/MAPLE_ISLAND_MVP_HANDOFF.md`, MVP design/technical/sequence docs, `docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_MVP.md`, `docs/agents/MAPLE_ISLAND_AMHERST_SUBPHASE_TEST_PLAN.md`, `docs/agents/plans/maple-island-mvp.plan.json` | Implement only after Agent capability boundaries are stable. |
| Amherst Agent capability prep | Deferred outside concurrent-prep lane | Agent Java paths under `src/main/java/server/agents/capabilities/*` and `src/test/java/server/agents/capabilities/*` are now treated as forbidden exclusions by the safe-prep gate. The docs/plan-card artifacts remain safe. | Revisit only after reconstruction stabilizes or if explicitly requested as a separate Agent-runtime task. |
| Portable Agent architecture prep | Ready as contract | `docs/agents/POST_RECONSTRUCTION_AGENT_PLATFORM_SPECIFICATION.md`, `docs/agents/server-adapter/*` | Build installer/adapter code after reconstruction boundary is stable. |
| Plan Card system prep | Ready as contract | `docs/agents/llm-autonomy/PLAN_CARD_SYSTEM.md`, `docs/agents/plan-runtime/*`, `docs/agents/plans/maple-island-mvp.plan.json` | Add JSON schema files before coding. |
| Agent Profile system prep | Ready as contract | `docs/agents/profile-platform/*`, `docs/agents/llm-autonomy/AGENT_PROFILE_SCHEMA.md` | Add schema files and profile store after runtime boundary is stable. |
| Economy engine prep | Ready as contract | `docs/agents/llm-autonomy/ECONOMY_*`, `docs/agents/llm-autonomy/ADAPTIVE_ECONOMY_SYSTEM_PLAN.md` | Implement observation store only after event bus/profile/catalog foundation. |
| Database Console / Server Console planning | Ready as planning docs | `docs/consoles/DATABASE_CONSOLE_*`, `docs/consoles/SERVER_CONSOLE_SCOPE.md` | Keep console work modular and separate from Agent runtime. |
| NuTNNuT over Cosmic review records | Ready as decision log | `docs/NUTNNUT_OVER_COSMIC_REVIEW.md`, `docs/COSMIC_REVERT_REVIEW.md` | Apply only explicit approved reversions; leave bot-related items to reconstruction. |
| Reusable pre-reconstruction prompt | Ready as handoff artifact | `docs/agents/PRE_RECONSTRUCTION_GOAL_PROMPT.md` | Reuse when starting or resuming a broad safe-prep thread. |
| Verification | Ready for current server batch, runtime evidence pending | `docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md`, `docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md`, `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1`, `tools/catalog/Test-AllCatalogs.ps1`, `tools/soak/*`, recent clean compile and scope checks | Collect and archive baseline soak evidence, then repeat after every safe-prep batch. |

## Current Safe-Prep Completion State

Current compact verifier interpretation:

```text
completionProgressEstimatePercent: 95
completionReadyExceptExternalEvidence: true
stable blocker: baseline-soak-evidence
```

The remaining 5% is intentionally reserved for real baseline soak evidence.
The prep verifier, handoff report, remaining-work report, and goal audit expose
the same progress fields so future handoffs can read the status without
scraping this Markdown file.

### Completed Or Sufficiently Documented

- Server diagnostics foundation for player scaling:
  - save pressure.
  - save section timing.
  - save reasons.
  - broadcast pressure.
  - DB pool visibility.
  - timer lane visibility.
  - slow-operation thresholds in `!serverhealth`.
- Server behavior-changing optimizations are deferred and documented.
- Agent scaling tiers are documented:
  - visible/full simulation.
  - nearby/light simulation.
  - offscreen abstract simulation.
  - route ETA simulation.
  - combat/loot shortcut simulation.
  - DB write spreading and reduced persistence.
- Agent shortcut methods are documented for after reconstruction:
  - direct loot-to-inventory when unseen.
  - abstract combat resolution.
  - reduced physics ticks.
  - portal-to-portal ETA travel.
  - map population spreading.
  - deferred saves.
  - Agent DB separation.
  - reduced broadcast.
  - plan outcome simulation.
- Maple Island MVP is documented as the first vertical gameplay slice, with an
  Amherst sub-phase scope/test plan for the earliest post-reconstruction smoke
  run.
- Amherst Agent Java prep is currently outside the concurrent-prep boundary and
  must stay excluded from safe-prep commits. The safe portion that remains in
  this lane is the Amherst sub-phase documentation and plan card with explicit
  `reactor-hit` and `reactor-box-items` objectives.
- Profile, economy, LLM, catalog, and server-adapter contracts are documented.
- Standalone catalog verifiers are prepared for game knowledge, NPC, and
  Agent/LLM catalogs.
- A portable JSON Schema is prepared for the Catalog Bundle manifest.
- Portable JSON Schemas are prepared for Catalog Query requests and results.
- Portable JSON Schemas are prepared for Catalog Validation findings and
  summaries.
- A portable JSON Schema is prepared for Catalog accepted-gap review records.
- Portable JSON Schemas are prepared for Catalog source-hash, compatibility,
  and index-coverage reports.
- Portable JSON Schemas are prepared for Plan Cards, Plan Bundle manifests,
  Plan Progress, Objective Progress, Objective Results, Capability Commands,
  Capability Results, and Plan Events.
- A portable JSON Schema is prepared for the generic Agent Event Bus envelope.
- Portable JSON Schemas are prepared for Agent Event Bus subscriptions and
  replay queries.
- Portable JSON Schemas are prepared for LLM Control Commands and LLM Control
  Results.
- Portable JSON Schemas are prepared for Agent Profiles, Decision Journal
  entries, and Relationship Memory.
- Portable JSON Schemas are prepared for Agent Experience Events and bounded
  Profile Patches.
- Portable JSON Schemas are prepared for Economy Market Observations, Market
  Item State, and Economy Decision proposals.
- Portable JSON Schemas are prepared for Server Adapter live snapshots, action
  requests, and action results.
- Portable JSON Schemas are prepared for Portable Installer manifests, install
  plans, patch operations, and verify reports.
- Portable JSON Schemas are prepared for Agent Soak scenario manifests and run
  summaries.
- A portable JSON Schema and first seeded data-only preset are prepared for
  Agent Soak population presets.
- Portable JSON Schemas are prepared for Population Director world plans,
  population targets/cohorts, and map capacity policies.
- Portable JSON Schemas are prepared for Population Director snapshots,
  assignments, rebalance proposals, and economy demand signals.
- Portable JSON Schemas are prepared for Simulation Tier decisions and
  materialization plans.
- Portable JSON Schemas are prepared for Background Action requests, results,
  and virtual Agent state.
- `tools/agent-contracts/Test-AgentContracts.ps1` verifies the contract schema
  files and the current Maple Island MVP plan shape.
- `tools/agent-contracts/Test-PlanCardSafety.ps1` verifies Maple Island MVP
  exit criteria, forbidden actions, and the Shanks quest-complete vs travel
  distinction.
- `tools/agent-contracts/Test-ProfilePatchSafety.ps1` verifies profile patch
  hard-policy protection before future adaptation code consumes patches.
- `tools/agent-contracts/Test-PopulationDirectorContracts.ps1` verifies
  Population Director world-plan, target/cohort, map-capacity, snapshot,
  assignment, rebalance proposal, and demand-signal contracts.
- `tools/agent-contracts/Test-PortableInstallerContracts.ps1` verifies the
  Portable Installer manifest, install-plan, patch-operation, and verify-report
  contracts, including disabled-by-default install manifests.
- `tools/agent-contracts/Test-AgentScalingContracts.ps1` verifies Simulation
  Tier modes, materialization modes, Background Action kinds, result codes, and
  virtual mutation types before future scaling code consumes those contracts.
- `tools/soak/Test-SoakPopulationPreset.ps1` verifies data-only soak
  population presets before any future Agent runner consumes them.
- Baseline soak evidence helpers now support safe automation handoff modes:
  - `tools/soak/New-BaselineSoakEvidencePackage.ps1 -SummaryOnly -Json`
    creates evidence folders while returning package counts without per-file
    rows.
  - `tools/soak/Add-BaselineSoakSample.ps1 -DryRun -Json` validates pasted
    sample text and predicted sample counts before appending to evidence logs.
  - `tools/soak/Update-BaselineSoakSummary.ps1 -DryRun -SummaryOnly -Json`
    previews changed summary fields without writing `summary.json`.
  - `tools/soak/New-BaselineSoakAuditEntry.ps1 -SummaryOnly -Json` returns
    audit status without embedding the generated Markdown body.
- `tools/pre-reconstruction/Get-PreReconstructionHandoff.ps1` aggregates the
  current safe-prep state into ready/deferred/waiting categories and lists
  Agent/bot/config paths that must be excluded from a safe-prep commit.
- `tools/pre-reconstruction/Get-AgentPackageReadiness.ps1` verifies that every
  well-defined package in the package registry has its primary docs and
  verifier hooks present before runtime implementation starts.
- `tools/pre-reconstruction/Get-SafePrepCommitCandidates.ps1` lists docs/tools
  safe-prep commit candidates separately from forbidden Agent/bot/config paths.
- `tools/catalog/Test-AllCatalogs.ps1` is wired into
  `tools/pre-reconstruction/Test-PreReconstructionPrep.ps1` as a combined
  catalog gate.
- `tools/catalog/Get-CatalogStatus.ps1` summarizes generated catalog folders,
  latest refresh reports, and current verifier status for implementation
  handoff.
- `tools/catalog/Get-CatalogRuntimeReadiness.ps1` categorizes generated lookup
  areas as ready, deferred, or missing for post-reconstruction runtime
  integration.
- `tools/catalog/Test-CatalogBundlePrep.ps1` checks whether generated catalog
  files are ready to map into the future portable bundle manifest and can write
  a draft manifest without parsing the large generated JSON files.
- Reviewed non-mob drop sources are classified in
  `docs/agents/catalog-overrides/drop-source-classifications.catalog.json` so
  the runtime catalog can avoid treating scripted/reactor/area sources as
  direct combat targets until live validation proves otherwise.
- Database Console and Server Console boundaries are documented.
- NuTNNuT-over-Cosmic decisions are recorded separately from bot/Agent
  reconstruction work.

### Prepared But Not Yet Implemented

These are intentionally not live runtime features yet:

- catalog runtime loader.
- unified catalog bundle builder implementation.
- manifest and source hashing implementation.
- runtime catalog query API implementation.
- Plan Runtime.
- Capability Runtime.
- capability runtime active-frame/continuation stack.
- primitive NavigationCapability and CombatCapability wrappers around existing
  reconstructed movement/combat behavior.
- objective capabilities that request primitive handoffs and resume after child
  success.
- NPC/Quest interaction capability.
- Recovery policy.
- Event bus.
- Agent observability package.
- Agent simulation tier runtime.
- LLM gateway.
- profile store and adaptation runtime.
- economy observation store and market decision engine.
- portable installer/patcher.
- Agent soak test harness.

### Must Wait For Reconstruction Stability

- any edit to live Agent runtime behavior.
- any edit to legacy BotClient behavior.
- movement/combat/loot/shop/NPC/quest behavior changes for Agents.
- wiring inactive Amherst prep classes into live tick/runtime behavior before
  capability runtime gating exists.
- Agent save routing behavior.
- Agent background simulation.
- packet suppression for Agent presentation.
- LLM command execution.
- economy automation.
- profile learning that changes behavior.
- server adapter code that depends on reconstructed runtime entry points.

## Package Readiness

Use `docs/agents/PACKAGE_REGISTRY.md` as the package index.

Well-defined packages:

- Catalog Platform.
- NPC Catalog Package.
- Profile Platform.
- Economy Engine.
- Server Adapter Package.
- Maple Island MVP Package.
- Plan Runtime Package.
- Capability Runtime Package.
- NPC / Quest Interaction Capability Package.
- Runtime Event Bus.
- Recovery / Survival Policy.
- Agent Observability / Diagnostics.
- Interaction Realism Package.
- Agent Simulation Tier Runtime.
- Perception Runtime Package.
- Background Action Runtime.
- Agent Soak Test Harness.
- LLM Control Gateway Package.
- Agent Population Director.
- Portable Installer / Patcher.
- Quest / Combat Focus Policy Package.
- Social Relationship Runtime.

Partially defined packages that need specs before implementation:

- None currently identified.

Backlog packages to promote:

- None currently identified.

## Verification Rules For Every Safe-Prep Batch

Before committing:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionDocs.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-AgentPackageReadiness.ps1
powershell -ExecutionPolicy Bypass -File .\tools\catalog\Test-AllCatalogs.ps1
powershell -ExecutionPolicy Bypass -File .\tools\agent-contracts\Test-AgentContracts.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1 -FailOnBlockers
git status --short
git diff --check
git diff --name-only -- src/main/java/server/agents src/main/java/server/bots src/test/java/server/agents src/test/java/server/bots config.yaml src/main/resources/config.yaml
git diff --cached --name-only -- src/main/java/server/agents src/main/java/server/bots src/test/java/server/agents src/test/java/server/bots
git diff --cached --name-only -- config.yaml src/main/resources/config.yaml
.\mvnw.cmd -DskipTests clean compile
```

Required result:

- no Agent/bot source files staged unless explicitly requested.
- no config YAML staged unless explicitly requested.
- catalog verifier gate passes, or any warning is documented as reviewed
  source data.
- Agent platform contract verifier passes.
- required safe-prep docs and package specs still exist.
- compile passes, or a known unrelated reconstruction blocker is documented.
- docs state what was completed and what remains deferred.
- safe-prep commit candidate report has `commitBlockers` equal to `0` before
  making a safe-prep-only commit.

Current known verifier warning:

- Latest baseline soak evidence run `baseline-20260707-0512` is still
  `INCOMPLETE` with 3 warnings. This is expected until a fresh baseline server
  smoke/soak run is completed and archived.
  The current warning IDs are:
  - `evidence:serverhealth`
  - `evidence:serverhealth-sample-count`
  - `evidence:checklist`

Current safe-prep handoff and commit-candidate helpers:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionHandoff.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1
```

The handoff report is the authoritative machine-readable backlog for the
remaining pre-reconstruction goal. Its `remainingWork` array currently tracks:

- safe-prep commit candidates.
- baseline soak evidence.
- catalog fast lookup validation.
- catalog runtime loader.
- NPC/quest catalog runtime integration.
- Maple Island Amherst smoke.
- Agent scaling runtime.
- profile/economy/LLM runtime.
- portable Agent installer runtime.
- server-only diagnostics soak follow-up.
- Database Console and Server Console platform work.
- forbidden Agent runtime dirt that must stay excluded from safe-prep commits.

## Next Best Safe Work

Highest value before reconstruction finishes:

1. Run a baseline server smoke/soak and archive diagnostics using
   `docs/agents/PRE_RECONSTRUCTION_BASELINE_SOAK_RUNBOOK.md`.
2. Use `tools/soak/New-BaselineSoakEvidencePackage.ps1` to create the run
   folder and summary template before starting the baseline. Use
   `-SummaryOnly -Json` when a caller only needs run path and package counts.
3. Use `docs/agents/PRE_RECONSTRUCTION_CURRENT_GAP_REPORT.md` as the current
   proof-gap checklist before claiming the broad pre-reconstruction goal is
   complete.
4. Use `tools/soak/Add-BaselineSoakSample.ps1 -DryRun -Json` and
   `tools/soak/Update-BaselineSoakSummary.ps1 -DryRun -SummaryOnly -Json` to
   preview evidence changes before writing copied runtime output.
5. Keep server-only diagnostics stable and repeat compile/scope checks after
   every safe-prep batch.

Do not start implementation of Agent gameplay packages until the reconstruction
has stable entry points and the relevant package spec exists.
