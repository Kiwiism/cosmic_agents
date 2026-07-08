# Pre-Reconstruction Prep Tools

Purpose:

```text
Verify that the safe pre-reconstruction planning artifacts and guardrails are
present before Agent runtime implementation begins.
```

Current tool:

- `Test-PreReconstructionPrep.ps1`
- `Test-PreReconstructionDocs.ps1`
- `Get-AgentPackageReadiness.ps1`
- `Get-PreReconstructionHandoff.ps1`
- `Get-PreReconstructionGoalAudit.ps1`
- `Get-PreReconstructionRemainingWork.ps1`
- `Get-SafePrepCommitCandidates.ps1`
- `Test-SafePrepDiffWhitespace.ps1`
- `Test-DatabaseConsoleBridgeDefault.ps1`
- `tools/catalog/Test-CatalogQuerySmoke.ps1`

## Usage

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1
```

Machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1 -Json
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionPrep.ps1 -SummaryOnly -Json
```

The JSON output includes a compact `summary` object with `nonPassCheckIds`,
safe-prep commit status, blocker count, forbidden exclusion count, and
review-required count for scripts that should not parse the full check list.
It also includes direct forbidden staged/unstaged counts and path lists for
Agent, bot, and config paths.
It also includes `safePrepDirectoryCandidateCount`; this must stay zero so a
safe-prep stage command cannot accidentally include an untracked directory.
The prep verifier cross-checks that value against the commit-candidate
reporter's own `directoryCandidates` count.
It also includes `remainingWorkStatus`, `remainingWorkCount`,
`remainingWorkWaiting`, `remainingWorkCategoryCounts`,
`remainingWorkTrackCounts`, and `remainingWorkPackageCounts` from the
remaining-work reporter. The summary also includes `planCardSummaryStatus`,
`planCardRouteStepCount`, `planCardObjectiveCount`, and
`planCardUniqueQuestCount`, and `planCardCapabilityDependencyCount` from the
read-only Plan Card summary loader. It also includes
`catalogRuntimeReadinessStatus`, `catalogReadyAreaIds`,
`catalogDeferredAreaIds`, and `catalogMissingAreaIds` from the catalog runtime
readiness reporter. It also includes `catalogBundlePrepStatus`,
`catalogBundleDeferredEntryKeys`, and `catalogBundleMissingRequiredKeys` from
the catalog bundle-prep verifier. It also includes
`packageReadinessStatus`, `packageReadinessCount`, and
`packageReadinessReadyCount` from the package-readiness reporter. The package
summary also includes `packageImplementationTrackStepCount` and
`packageImplementationTrackMissingReferenceCount` so scaling-first and gameplay
implementation order coverage can be checked without parsing the package
registry Markdown.
It also includes `baselineSoakLatestRunId`, `baselineSoakStatus`,
`baselineSoakWarningIds`, `baselineSoakFailureIds`,
`baselineSoakServerHealthSampleCount`,
`baselineSoakExpectedServerHealthSampleCount`,
`baselineSoakChecklistCheckedCount`, and `baselineSoakChecklistItemCount` so
the remaining baseline evidence gap can be shown without opening the soak
status report separately.
For lightweight consumers, the prep verifier also mirrors high-value
root-level prep verifier fields: `passCount`, `nonPassCheckIds`,
`completionReadyExceptExternalEvidence`,
`completionProgressEstimatePercent`,
`safePrepCommitStatus`,
`safePrepStageReady`, `safePrepCommitBlockers`,
`safePrepForbiddenExclusions`, `safePrepReviewRequired`,
`safePrepRecommendedVerificationCommandCount`, `directForbiddenStagedCount`,
`directForbiddenUnstagedCount`, `remainingWorkStatus`, `remainingWorkCount`,
`remainingWorkWaiting`, `planCardSummaryStatus`, `packageReadinessStatus`,
`catalogRuntimeReadinessStatus`, `catalogReadyAreaIds`,
`catalogDeferredAreaIds`, `catalogMissingAreaIds`, `catalogBundlePrepStatus`,
`catalogBundleDeferredEntryKeys`, `catalogBundleMissingRequiredKeys`,
`packageReadinessCount`, `packageReadinessReadyCount`,
`packageImplementationTrackStepIds`, `packageScalingFirstStepIds`,
`packageGameplayStepIds`, `packageTrackedInScalingDocsStepIds`,
`packageMissingReferenceStepIds`,
`baselineSoakLatestRunId`, `baselineSoakStatus`, `baselineSoakWarningIds`,
`baselineSoakFailureIds`, `baselineSoakServerHealthSampleCount`,
`baselineSoakExpectedServerHealthSampleCount`,
`baselineSoakChecklistCheckedCount`, `baselineSoakChecklistItemCount`,
`baselineSoakNextStepIds`, `baselineSoakRequiredNextStepIds`,
`baselineSoakNextStepCount`, `baselineSoakRequiredNextStepCount`, and
`baselineSoakNextRequiredCommand` while
keeping the original nested `summary` object.
It also mirrors `safePrepRecommendedVerificationCommands` so a compact prep
report can show the exact safe-prep verification commands without opening the
commit-candidate report separately.
When `-SummaryOnly` is used, the prep verifier sets `summaryOnly`,
`rowsOmitted`, and `returnedCheckCount`, and omits the full `checks` row list.
Text mode also prints only the compact status instead of every check row.

To check pre-reconstruction docs for stale helper command references and key
handoff/status helper mentions:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionDocs.ps1
```

Compact machine-readable docs consistency output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-PreReconstructionDocs.ps1 -SummaryOnly -Json
```

Summary mode includes `summaryOnly`, `rowsOmitted`, `checkCount`,
`passCount`, `failCount`, `warnCount`, `failureIds`, `warningIds`, and
`returnedCheckCount`. It omits detailed check rows while preserving the same
default full output for existing callers.

To summarize whether every well-defined Agent package has its primary docs and
verifier hook present:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-AgentPackageReadiness.ps1
```

Compact machine-readable package readiness output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-AgentPackageReadiness.ps1 -SummaryOnly -Json
```

The package-readiness JSON includes `summaryOnly`, `rowsOmitted`,
`returnedPackageCount`, `returnedImplementationTrackCount`,
`operationalPackageIdCount`, and `operationalPackageIds` so automation can
read package counts and safe-prep operational routing ids without loading every
package and implementation-track row.
It also mirrors compact implementation order lists:
`implementationTrackStepIds`, `scalingFirstStepIds`, `gameplayStepIds`,
`trackedInScalingDocsStepIds`, and `missingPackageReferenceStepIds`.

To generate a read-only handoff report that categorizes remaining work and
unsafe-to-commit paths, including docs consistency, catalog readiness, soak
status, package readiness, safe-prep commit status, staged/unstaged forbidden
counts, and any blocker unstage command:

This keeps the existing staged/unstaged forbidden counts visible while adding
package readiness to the same handoff surface.
The handoff also embeds the baseline soak next-step report so automation can
read `baselineSoakNextStepIds`, `baselineSoakNextStepCount`, and the full
`baselineSoakRequiredNextStepIds`, `baselineSoakRequiredNextStepCount`,
`baselineSoakNextRequiredCommand`, and the full `baselineSoakNextSteps` object
without scraping recommended command text.
For lightweight consumers, the JSON report also mirrors root-level handoff fields:
`safePrepCommitStatus`, `safePrepCommitBlockers`, `safePrepReviewRequired`,
`safePrepForbiddenExclusions`, `safePrepCommitCandidateCount`,
`safePrepDirectoryCandidates`, `catalogRuntimeReadinessStatus`,
`catalogReadyAreaIds`, `catalogDeferredAreaIds`, `catalogMissingAreaIds`,
`catalogBundlePrepStatus`, `catalogBundleDeferredEntryKeys`,
`catalogBundleMissingRequiredKeys`,
`baselineSoakStatus`,
`baselineSoakWarningIds`, `baselineSoakFailureIds`,
`baselineSoakWarningCount`, `baselineSoakFailureCount`,
`baselineSoakNextStepIds`, `baselineSoakNextStepCount`,
`baselineSoakRequiredNextStepIds`, `baselineSoakRequiredNextStepCount`,
`baselineSoakNextRequiredCommand`,
`completionReadyExceptExternalEvidence`, `completionProgressEstimatePercent`,
`packageReadinessStatus`, `packageReadinessReadyCount`,
`packageReadinessCount`, `packageImplementationTrackStepIds`,
`packageScalingFirstStepIds`, `packageGameplayStepIds`,
`packageTrackedInScalingDocsStepIds`, `packageMissingReferenceStepIds`,
`gitForbiddenStaged`, and
`gitForbiddenUnstaged` at the root level while keeping the original nested
`summary` object. Summary output also includes `gitForbiddenStagedPaths` and
`gitForbiddenUnstagedPaths` so a lightweight handoff can show the exact
Agent/bot/config paths that must stay out of a safe-prep commit.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionHandoff.ps1
```

Machine-readable handoff output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionHandoff.ps1 -Json
```

Compact machine-readable handoff output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionHandoff.ps1 -SummaryOnly -Json
```

When `-SummaryOnly` is used, the handoff report sets `summaryOnly`,
`rowsOmitted`, `categoryCount`, `remainingWorkCount`, `returnedCategoryCount`,
`returnedRemainingWorkCount`, and `returnedBaselineSoakNextStepCount`, and
omits row-heavy nested tool reports, categories, remaining-work rows, and
baseline soak next-step rows while preserving the root-level mirrors and the
compact `summary` object.

To run a top-level goal audit that checks the safe-prep boundary, package
readiness, remaining-work routing, catalog runtime prep, Maple Island MVP prep,
baseline soak evidence, and safe-prep commit readiness:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionGoalAudit.ps1
```

Machine-readable goal-audit output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionGoalAudit.ps1 -Json
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionGoalAudit.ps1 -SummaryOnly -Json
```

When another verifier is already running the full pre-reconstruction prep
check, use `-SkipPrepVerifier` to avoid duplicate heavy checks while still
auditing the rest of the handoff evidence:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionGoalAudit.ps1 -SkipPrepVerifier -Json
```

The goal audit also performs a direct staged forbidden-path check against
Agent, bot, and config paths so it can catch safe-prep commit blockers even if
another handoff summary was generated during an active reconstruction change.
Its JSON output includes a compact `summary` with pass/waiting/blocked/fail
counts, non-pass item ids, baseline soak evidence counts, recommended soak
commands, baseline soak next-step ids/counts, safe-prep commit status, and
direct staged forbidden-path counts.
For lightweight consumers, the same root-level goal audit fields are mirrored
as `passCount`, `waitingCount`, `blockedCount`, `failCount`,
`nonPassItemIds`, `completionBlockerIds`,
`primaryRemainingExternalBlocker`, `completionReadyExceptExternalEvidence`,
`completionNextRequiredCommand`, `completionProgressEstimatePercent`,
`baselineSoakStatus`, `baselineSoakNextStepIds`,
`baselineSoakNextStepCount`, `returnedBaselineSoakNextStepCount`, `safePrepCommitStatus`,
`safePrepCommitBlockers`, `safePrepWhitespaceStatus`,
`safePrepWhitespaceIssueCount`, `directForbiddenStagedCount`,
`directForbiddenUnstagedCount`, `directForbiddenStagedPaths`,
`directForbiddenUnstagedPaths`, `gitForbiddenStagedCount`,
`gitForbiddenUnstagedCount`, `gitForbiddenStagedPaths`, and
`gitForbiddenUnstagedPaths`.
When `-SummaryOnly` is used, the goal audit sets `summaryOnly`,
`rowsOmitted`, `itemCount`, and `returnedItemCount`, keeps the root-level
status/count fields, and omits the full requirement item rows plus the nested
baseline soak next-step object.

To list only the remaining-work backlog from the handoff report:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1
```

Filter by category or status:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -Id baseline-soak-evidence
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -Category "Agent gameplay"
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -Status waiting
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -ExcludeStatus ready
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -ExcludeStatus ready -SortBy category
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -ImplementationTrack gameplay
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -PackageId catalog-platform
```

Machine-readable remaining-work output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -Json
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 -SummaryOnly -Json
```

The remaining-work JSON includes `statusCounts`, `categoryCounts`,
`trackCounts`, and `packageCounts` so a
handoff can report backlog size by implementation state, implementation track, and package area
without parsing item text.
It also exposes `safePrepCommitStatus`, `safePrepCommitBlockers`,
`safePrepReviewRequired`, `safePrepStageReady`,
`safePrepRecommendedVerificationCommandCount`, `gitForbiddenStaged`,
`gitForbiddenUnstaged`, `baselineSoakNextStepIds`,
`baselineSoakRequiredNextStepIds`, `baselineSoakNextStepCount`,
`baselineSoakRequiredNextStepCount`, `baselineSoakNextRequiredCommand`,
`completionReadyExceptExternalEvidence`,
`completionProgressEstimatePercent`, and
`blockerUnstageCommand` at the top level so a
remaining-work report can be used as a safe-prep commit readiness summary
without digging into the embedded handoff object. It also mirrors
`safePrepRecommendedVerificationCommands`, `gitForbiddenStagedPaths`, and
`gitForbiddenUnstagedPaths` for compact review.
Use `-SummaryOnly` when only counts and filters are needed; it omits the item
rows with `rowsOmitted` set to true while keeping the total `count`,
`remainingWorkCount`, `readyCount`, `readyAfterReconstructionCount`,
`readyAfterReconstructionIds`, `readyAfterReconstructionTrackIds`,
`readyAfterReconstructionPackageIds`, `serverOnlyItemIds`,
`waitingForSoakEvidenceItemIds`, `waitingForAgentRuntimeBoundaryItemIds`,
`agentGameplayItemIds`, `agentScalingOptimisationItemIds`, `waitingCount`, `blockedCount`,
`clearCount`, `readyWithCautionCount`, `returnedItemCount`, `statusCounts`, and
`categoryCounts`, `trackCounts`, and `packageCounts`.
Use `-SortBy id`, `-SortBy category`, or `-SortBy status` to group the returned
items for review. The default `-SortBy handoff` preserves the handoff order.
Use `-ImplementationTrack` or `-PackageId` to hand off one implementation lane
or package without parsing the full backlog.
By default, this helper exits successfully even when the underlying handoff
status is `BLOCKED_FOR_SAFE_PREP_COMMIT`; use `-FailOnBlocked` when automation
should fail on that state.

To write a standalone remaining-work artifact for review:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 `
  -OutputPath .\logs\pre-reconstruction\remaining-work.md

powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-PreReconstructionRemainingWork.ps1 `
  -Json `
  -OutputPath .\logs\pre-reconstruction\remaining-work.json
```

To list files that are safe-prep commit candidates versus Agent/bot/config
paths that must be excluded:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1
```

Machine-readable commit-candidate output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1 -Json
```

Compact machine-readable output:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1 -SummaryOnly -Json
```

To make automation fail when forbidden or review-required paths are staged:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1 -FailOnBlockers
```

To make automation fail unless every dirty path is either safe-prep or an
excluded forbidden path:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Get-SafePrepCommitCandidates.ps1 -FailOnBlockers -FailOnReviewRequired
```

To check whitespace only inside the safe-prep lane, excluding active
Agent/bot/config runtime paths:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-SafePrepDiffWhitespace.ps1 -SummaryOnly -Json
```

The safe-prep whitespace check runs `git diff --check` only on tracked
safe-prep candidate paths and performs a lightweight trailing-whitespace and
final-newline check on untracked safe-prep files. It reports
`trackedSafePathCount`, `untrackedSafePathCount`, `issueCount`,
`trackedIssueCount`, `untrackedIssueCount`, and safe-prep commit boundary
counts without inspecting active Agent runtime files.

To verify that the Database Console live bridge default state matches the
current local-admin policy and remains loopback/token protected:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\pre-reconstruction\Test-DatabaseConsoleBridgeDefault.ps1 -SummaryOnly -Json
```

This check lets the exact Database Console bridge integration files stay in the
safe-prep lane only while confirming the bridge remains local-only,
token-protected, and explicitly disableable for server runs that should not
open the live admin bridge.

The commit-candidate report includes:

- safe docs/tools/isolated-console/server-planning candidates.
- top-level candidate counts for quick automation, including
  `safeCandidateCount`, `directoryCandidateCount`, `forbiddenExclusionCount`,
  `reviewRequiredCount`, `commitBlockerCount`, `stagedForbiddenCount`, and
  `unstagedForbiddenCount`.
- `safeStageReady`, which is true only when there are no commit blockers, no
  review-required paths, and no directory candidates.
- `recommendedVerificationCommands`, which lists the safe-prep verification
  commands to run after staging or before committing this lane.
- `summaryOnly`, `rowsOmitted`, and returned row counts, such as
  `returnedSafeCandidateCount` and `returnedDirectoryCandidateCount`, when
  compact output is requested.
- `directoryCandidates`, which must stay `0` because the reporter expands
  untracked directories with `git status --porcelain=v1 -uall`.
- grouped candidate counts, such as `docs-agents`, `database-console`, and
  `tools`.
- forbidden Agent/bot/config exclusions.
- paths outside the known safe-prep allowlist that need review.
- commit blockers, which are forbidden or review-required paths that are
  already staged.
- a generated `git add -- ...` command for the safe candidates only.
- the generated stage command uses explicit paths from
  `git status --porcelain=v1 -uall`, so untracked directories are expanded
  into leaf files before review.
- a generated `git restore --staged -- ...` command for staged forbidden or
  review-required blockers only.

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
- any dirty path under `src/main/java/server/agents`,
  `src/test/java/server/agents`, `src/main/java/server/bots`,
  `src/test/java/server/bots`, or config files is reported outside the
  concurrent safe-prep lane unless a later task explicitly changes scope.
- no unstaged production Agent/bot/config changes are present without a
  warning.
- whether baseline soak evidence has been collected.
- whether the newest baseline soak folder is complete or still only scaffolded.
- whether generated Agent/LLM and NPC catalogs can answer representative
  Maple Island MVP, item-source, and action-affordance lookups.
- whether the safe-prep commit-candidate report has zero staged blockers and
  no review-required paths.
- whether safe-prep candidate files have whitespace issues independently of
  any active Agent reconstruction diff outside this lane.
- whether the remaining-work reporter returns the expected implementation
  backlog categories, implementation tracks, and key package ids.
- whether every remaining-work package id is either registered in the Agent
  package registry or explicitly recognized as an operational safe-prep lane
  such as baseline soak, console work, or active reconstruction exclusion.
- whether the Maple Island MVP Plan Card summary loader returns the expected
  route, objective, quest-reference, and capability-dependency coverage.
- whether all well-defined Agent packages have primary docs and verifier hooks
  before runtime implementation starts.
- whether package implementation-track steps reference registered package ids
  or are explicitly tracked in scaling docs without dedicated packages.

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

The handoff report additionally groups the current state into:

- ready to implement after reconstruction.
- waiting for soak evidence.
- waiting for Agent runtime boundary.
- server-only.
- Agent gameplay.
- Agent scaling/optimisation.

The handoff JSON also includes a `remainingWork` array. Each item has an id,
category, status, title, evidence, and next action so future implementation
threads can filter the backlog without scraping Markdown.
The current backlog includes safe-prep commit review, baseline soak evidence,
catalog fast lookup validation, catalog runtime loader, NPC/quest catalog
runtime integration, Maple Island Amherst smoke, Agent scaling runtime,
profile/economy/LLM runtime, portable Agent installer runtime, server-only
diagnostics soak follow-up, console platform work, and forbidden Agent runtime
dirty paths that must stay excluded from safe-prep commits.
The `baseline-soak-evidence` remaining-work item includes numeric evidence
counts from the latest run, including serverhealth sample counts,
startup/shutdown line counts, and checklist checked/unchecked counts, plus the
current recommended soak command(s).
The explicit server-only diagnostics soak follow-up item keeps behavior changes
deferred until baseline evidence exists.

The commit candidate report is read-only. It does not stage, unstage, reset, or
modify files. Treat the generated stage command as a review aid; inspect it
before running it.
Its JSON output includes `safeCandidateGroupCounts`,
`hasSafeStageCommand`, and `hasBlockerUnstageCommand` so automation can show
review scope and command availability without parsing command text.

The handoff report shows both safe-prep candidate forbidden counts and direct git forbidden counts.
In an actively changing reconstruction worktree, these can briefly differ if
Agent files are staged or unstaged while the report is running; rerun the
handoff before committing.

Commit-candidate status values:

- `SAFE_CANDIDATES_ONLY`: every dirty path is within the safe-prep allowlist.
  Agent runtime, bot runtime, Agent capability, Agent test, and config paths
  are not included in this lane.
- `SAFE_CANDIDATES_WITH_FORBIDDEN_EXCLUSIONS`: safe candidates exist, but
  Agent/bot/config paths are dirty and must be excluded.
- `SAFE_CANDIDATES_WITH_REVIEW_ITEMS`: at least one dirty path is outside the
  known safe-prep allowlist and needs manual review.
- `BLOCKED_FOR_SAFE_PREP_COMMIT`: a forbidden or review-required path is staged
  and must be unstaged or explicitly approved before a safe-prep-only commit.

When `BLOCKED_FOR_SAFE_PREP_COMMIT` appears, review the `blockerUnstageCommand`
field in JSON output or the `Blocker Unstage Command` section in Markdown
output. The report only prints the command; it never changes the git index.
The optional `-FailOnBlockers` flag exits with code `1` only for actual commit
blockers, not for unstaged Agent reconstruction files that are merely excluded.
The optional `-FailOnReviewRequired` flag also exits with code `1` when any
path falls outside both the safe-prep allowlist and forbidden-path exclusion
list.
The handoff summary includes `packageReadinessStatus`,
`packageReadinessReadyCount`, and `packageReadinessCount`, and the full report
contains a `packageReadiness` object for package-centric implementation
handoffs.
The package-readiness JSON also mirrors `readyCount`, `waitingCount`,
`blockedCount`, and `failCount` at the root level so lightweight consumers can
read package state with the same count names used by the goal audit and
remaining-work helpers.
It includes `baselineSoakNextStepIds`, `baselineSoakNextStepCount`, and the
full `baselineSoakNextSteps` object, so the remaining external evidence work
can be routed by step id such as `add-serverhealth-sample` and
`review-checklist`.
It also includes `safePrepDirectoryCandidates` from the commit-candidate
report, so handoff consumers can confirm the generated stage command is made
from explicit leaf files.

Continue to use the normal safe-prep gates before committing. Agent runtime,
Agent capability, bot, BotClient, and config paths must stay excluded from a
safe-prep-only commit unless a later task explicitly changes scope:

```powershell
git diff --check
git diff --cached --name-only -- src/main/java/server/agents src/main/java/server/bots src/test/java/server/agents src/test/java/server/bots
git diff --cached --name-only -- config.yaml src/main/resources/config.yaml
.\mvnw.cmd -DskipTests clean compile
```
