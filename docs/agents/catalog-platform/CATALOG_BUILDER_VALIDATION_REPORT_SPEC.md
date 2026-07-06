# Catalog Builder Validation And Report Specification

Purpose:

```text
Define the validation reports, source evidence, compatibility checks, and
accepted-gap workflow that every portable catalog bundle build must produce.
```

This is pre-reconstruction safe prep. It describes offline builder behavior and
does not wire catalogs into live Agent runtime.

## Design Rule

```text
Fast runtime lookup is only useful if the bundle can explain what it knows,
what it inferred, and what it could not prove.
```

Agents and LLM tools should never silently trust incomplete catalog data. Every
bundle must carry validation evidence, warnings, blockers, source hashes, and
accepted gaps.

## Goals

- Make catalog builds repeatable across Cosmic-like servers.
- Prove that generated catalogs and indexes match the declared manifest.
- Detect dangling references between maps, NPCs, quests, shops, mobs, drops,
  items, portals, reactors, scripts, and rewards.
- Mark script-sensitive or manual-review entries before Agent automation uses
  them.
- Provide machine-readable JSON reports and human-readable Markdown summaries.
- Allow known server-specific gaps to be explicitly accepted without hiding new
  regressions.
- Keep all validation offline and portable.

## Non-Goals

- Do not execute server actions.
- Do not prove live state availability.
- Do not guarantee that every script is automation-safe.
- Do not require the game server to run the builder.
- Do not block optional catalogs that the manifest declares as intentionally
  absent.

## Build Pipeline

Recommended offline pipeline:

```text
collect sources
  -> hash sources
  -> extract raw catalogs
  -> normalize rows
  -> merge overrides
  -> derive indexes
  -> generate LLM summaries
  -> validate catalogs
  -> validate indexes
  -> apply accepted-gap policy
  -> write reports
  -> write manifest
  -> verify bundle self-consistency
```

No runtime consumer should load a bundle that failed the final self-consistency
step.

## Required Output Files

```text
catalog-bundle/
  manifest.json
  reports/
    summary.md
    validation.md
    gaps.md
    validation.json
    gaps.json
    source_hashes.json
    compatibility.json
    index_coverage.json
```

Optional detailed reports:

```text
reports/details/
  maps.validation.json
  npcs.validation.json
  quests.validation.json
  shops.validation.json
  mobs.validation.json
  drops.validation.json
  items.validation.json
  portals.validation.json
  reactors.validation.json
  scripts.validation.json
  rewards.validation.json
  indexes.validation.json
  summaries.validation.json
```

## Severity Levels

```text
INFO
WARN
REVIEW
ERROR
BLOCKER
```

Meanings:

- `INFO`: useful build note.
- `WARN`: incomplete optional data or low-confidence inference.
- `REVIEW`: automation should require manual approval before using this row.
- `ERROR`: catalog/index is inconsistent but bundle can still be written for
  inspection.
- `BLOCKER`: bundle must not be marked runtime-ready.

Runtime-ready bundle rule:

```text
BLOCKER count must be 0.
ERROR count must be 0 unless every ERROR is listed in accepted gaps.
REVIEW rows are allowed but must be indexed as manual-review/action-risk flags.
```

## Validation Finding Schema

```json
{
  "schemaVersion": 1,
  "findingId": "quest-complete-npc-missing-placement:1001:2100",
  "severity": "ERROR",
  "category": "dangling-reference",
  "catalog": "quests",
  "key": {
    "questId": 1001,
    "npcId": 2100
  },
  "message": "Quest complete NPC has no known placement.",
  "sources": [
    {
      "kind": "wz",
      "path": "Quest.wz/Check.img/1001.xml"
    }
  ],
  "recommendedAction": "Add NPC placement override or mark quest blocked.",
  "acceptedGapId": null
}
```

## Report Summary Schema

```json
{
  "schemaVersion": 1,
  "bundleId": "cosmic-v83-main-20260707",
  "generatedAt": "2026-07-07T00:00:00+08:00",
  "runtimeReady": false,
  "counts": {
    "info": 100,
    "warn": 20,
    "review": 7,
    "error": 2,
    "blocker": 0
  },
  "catalogRowCounts": {
    "maps": 5262,
    "npcs": 1200,
    "quests": 900
  },
  "indexCoverage": {
    "id_to_item": 1.0,
    "item_to_drops": 0.92,
    "quest_to_complete_npcs": 0.98
  },
  "acceptedGapCount": 4,
  "newUnacceptedFindingCount": 2
}
```

## Required Validators

### Manifest Validators

Checks:

- manifest exists.
- schema version supported.
- builder version present.
- generated time present.
- source hashes present.
- declared catalogs exist.
- declared row counts match loaded row counts.
- required indexes exist.
- compatibility requirements are parseable.

### Source Hash Validators

Checks:

- WZ/XML root hash.
- SQL root hash.
- script root hash.
- override root hash.
- builder config hash.

Purpose:

- make rebuilds comparable.
- detect stale bundle use.
- help users understand why catalog output changed.

### Catalog Row Validators

Checks:

- required id fields are present.
- names exist when available.
- source evidence exists.
- duplicate IDs are reported.
- merged rows preserve source list.
- confidence is declared.
- `requiresLiveValidation` is present where needed.

### Index Validators

Checks:

- every index key points to an existing catalog row.
- every required catalog row is covered by its primary id index.
- reverse indexes are consistent with forward catalogs.
- top-N/summary indexes declare ranking basis.
- no full-scan-only query path is required for high-frequency Agent/LLM lookups.

### Map And Portal Validators

Checks:

- portal source map exists.
- portal destination map exists or is marked script/unknown.
- map region exists.
- map risk flags are generated.
- map NPC/mob/reactor/spawn indexes reference existing rows.
- route ETA edges reference valid portals or target points.
- travel services reference valid source and destination maps.

### NPC Validators

Checks:

- NPC id has name when available.
- NPC placements reference existing maps.
- NPC quest start/complete indexes reference existing quests.
- NPC shop/service indexes reference existing shops/services.
- interaction boxes and approach points exist or are marked missing.
- dialogue timing exists or is marked default-estimated.
- script-sensitive NPCs are in manual-review indexes.

### Quest Validators

Checks:

- quest id has name/metadata when available.
- start NPCs have placements or accepted gap.
- complete NPCs have placements, auto-complete flag, or accepted gap.
- prerequisite quest ids exist.
- required item ids exist.
- required mob ids exist.
- reward item ids exist.
- choose/random reward groups are represented.
- quest status override exists for blocked/review-needed quests.
- out-of-region or jump-quest/reactor requirements are flagged.

### Shop Validators

Checks:

- shop id exists.
- shop NPC exists.
- shop NPC has at least one placement or accepted gap.
- sold item ids exist.
- prices are non-negative.
- item order is stable.
- duplicate item rows are flagged.

### Mob And Drop Validators

Checks:

- mob ids exist.
- mob maps/spawns reference existing maps.
- drop item ids exist.
- drop rates are non-negative.
- quest-gated drops reference existing quest ids or are null.
- meso drop ranges are valid.
- global drops are distinguishable from mob-specific drops.
- boss/area-boss flags are present where known.

### Item And Reward Validators

Checks:

- item ids have name/type when available.
- reward sources reference existing quests, NPCs, PQs, events, boxes, or
  gachapon pools.
- equip stat ranges are represented when available.
- scroll/upgrade metadata is represented where known.
- item acquisition summaries have at least one source or are marked
  unobtainable/review.

### Reactor And Field Object Validators

Checks:

- reactor ids exist.
- reactor placements reference existing maps.
- reactor drops/rewards reference existing items/quests.
- quest reactor objectives are linked to reactor placements.
- jump-quest/field-object routes are marked manual-review until navigation graph
  support exists.

### Script And Manual-Review Validators

Checks:

- script files referenced by NPC/event/quest entries exist.
- direct-safe script actions are backed by a validator rule.
- unknown scripts are marked `REVIEW`.
- do-not-auto-use entries are indexed.
- script-sensitive actions are not exposed as safe LLM/Agent actions.

### LLM Summary Validators

Checks:

- LLM summary rows reference existing catalog rows.
- summary row sizes are bounded.
- summaries include confidence and source reasons.
- summaries do not include raw script bodies.
- summaries include manual-review/risk flags.

## Accepted Gaps

Accepted gaps are explicit waivers for known server-specific data issues.

Example:

```json
{
  "acceptedGapId": "victoria-lt30-old-map-quest-2001",
  "findingIdPattern": "quest-complete-npc-missing-placement:2001:*",
  "severityAllowed": ["ERROR"],
  "reason": "Old quest exists in WZ but is not reachable from v83 start maps.",
  "expiresOnSourceHashChange": true,
  "reviewOwner": "catalog-maintainer",
  "createdAt": "2026-07-07T00:00:00+08:00"
}
```

Rules:

- accepted gaps must be stored under `overrides/` or `reports/gaps.json`.
- accepted gaps must include reason and source.
- accepted gaps should expire when source hashes change unless explicitly
  pinned.
- accepted gaps reduce runtime blocker status only for matching findings.
- new unaccepted findings must remain visible.

## Runtime Readiness Gate

Bundle state:

```text
INSPECTION_ONLY
RUNTIME_READY_WITH_WARNINGS
RUNTIME_READY
BLOCKED
```

Rules:

- `BLOCKED`: any unaccepted blocker or error.
- `INSPECTION_ONLY`: reports exist but required indexes/catalogs are missing.
- `RUNTIME_READY_WITH_WARNINGS`: no blockers/errors, but warnings/review rows
  exist.
- `RUNTIME_READY`: no blockers/errors and warnings are below configured
  threshold.

The Catalog Runtime should refuse `BLOCKED` bundles by default.

## CI And Developer Workflow

Recommended commands later:

```text
catalog build --source <paths> --out <bundle>
catalog validate <bundle>
catalog report <bundle>
catalog diff <oldBundle> <newBundle>
catalog accept-gap <findingId> --reason <text>
```

CI gates:

- no missing manifest.
- no row-count mismatch.
- no unaccepted blocker/error findings.
- no high-frequency query missing required index.
- no LLM summary referencing missing rows.

## Human Report Layout

`reports/summary.md`:

- bundle id.
- source hashes.
- runtime readiness.
- counts by severity.
- changed row counts.
- top new issues.
- accepted gaps count.

`reports/validation.md`:

- grouped findings by severity and category.
- recommended actions.
- affected catalogs.

`reports/gaps.md`:

- accepted gaps.
- expired gaps.
- gaps requiring re-review after source hash change.

## Success Criteria

This contract is ready when:

- required reports and schemas are defined.
- validation severities are explicit.
- dangling-reference validators cover maps, NPCs, quests, shops, mobs, drops,
  items, portals, reactors, scripts, rewards, and indexes.
- accepted gaps have a controlled workflow.
- runtime readiness can be decided from machine-readable reports.
- future builder implementation can be ported to any Cosmic-like server without
  depending on live Agent runtime.
