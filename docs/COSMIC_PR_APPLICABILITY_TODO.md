# Cosmic PR Applicability TODO

Backlog for deciding whether to import selected pull requests from
`P0nk/Cosmic` into this project later.

Scan date: 2026-07-01.

Source repository: https://github.com/P0nk/Cosmic

Current scan summary:

```text
Total PRs seen: 232
Open PRs: 22
Merged PRs: 146
Recent closed/unmerged PRs: 38
```

Notes:

- Do not apply PRs blindly.
- Prefer small, manually reviewed patches over merging PR branches directly.
- Current branch has reconstruction changes and local edits in files such as
  `Character.java`, `MapFactory.java`, `config.yaml`, `.gitignore`, and
  `Dockerfile`, so overlap needs careful review.
- Merged upstream PRs are assumed present in `cosmic/master` unless this fork
  diverged before them.

## Recommended First Review Batch

Review these first because they are small or directly relevant to uptime,
server correctness, agent farming, economy accuracy, or runtime hardening.

| PR | Status | Applicability | Decision | Notes |
|---|---|---:|---|---|
| [#318](https://github.com/P0nk/Cosmic/pull/318) Fix local address patterns in `IpAddresses` | open | high | accepted-modified | Manually anchored local-address regex patterns and switched matching to whole-string validation. |
| [#279](https://github.com/P0nk/Cosmic/pull/279) Fix mob skills not applying to players | open | high | accepted-modified | Verified current code already computes mob skill probability with `(float) iprop / 100`. |
| [#287](https://github.com/P0nk/Cosmic/pull/287) Drop rate fixes | open | high | accepted-modified | Manually ported million-scale drop chance and inclusive min/max quantity rolls in `MapleMap`. |
| [#348](https://github.com/P0nk/Cosmic/pull/348) Unbloat `queststatus` table + improve character save performance | open | high | accepted-modified | Manually added placeholder skip with extra preservation checks for completed count, expiration, custom data, progress, and medal maps. |
| [#277](https://github.com/P0nk/Cosmic/pull/277) Hired Merchant duplication and Store Remote Controller inventory checking | open | medium-high | accepted-modified | Manually added duplicate owner-state guards, rollback-safe world/channel registration, and Store Remote Controller requirement for remote access. |
| [#207](https://github.com/P0nk/Cosmic/pull/207) Fallback to scripts named after `MapId.PortalName` | closed/unmerged | medium | pending-later | Useful portal/script compatibility feature. Touches `MapFactory`; defer until map/catalog restructuring is stable. |

## Useful But Needs Review

| PR | Status | Applicability | Decision | Notes |
|---|---|---:|---|---|
| [#294](https://github.com/P0nk/Cosmic/pull/294) Fix omok `searchCombo` logic | open | medium | pending | Fixes minigame win detection/reset. Not agent-critical unless agents later use minigames. |
| [#330](https://github.com/P0nk/Cosmic/pull/330) Enable null to remove `server_message` | open | medium | accepted-modified | Added packet-level null-to-empty safety for server messages. |
| [#331](https://github.com/P0nk/Cosmic/pull/331) Quest mob count modifier | open | medium | split-before-use | Useful concept, but patch mixes quest-count config with unrelated WZ path behavior. If used, split manually. |
| [#274](https://github.com/P0nk/Cosmic/pull/274) Mob Rate | open | medium | reference-only | Similar to desired spawn-rate config. Use as reference; preferred design is global default plus per-map/per-set overrides. |
| [#295](https://github.com/P0nk/Cosmic/pull/295) Fix quest 6225 | open | medium | accepted-modified | Manually fixed NPC `2041023.js` gating for quest 6225/6315 paths. |
| [#281](https://github.com/P0nk/Cosmic/pull/281) Aran Combo Drain recovery | open | medium-high | accepted-modified | Manually capped Aran Combo Drain recovery by monster HP and half max HP, matching other drain-family safety. |

## Infrastructure / Optional

| PR | Status | Applicability | Decision | Notes |
|---|---|---:|---|---|
| [#342](https://github.com/P0nk/Cosmic/pull/342) Environment variable support in `config.yaml` | open | medium | pending-later | Useful for deployment. Touches config, Docker, and config classes. Avoid during current config churn. |
| [#327](https://github.com/P0nk/Cosmic/pull/327) `.env` DB support | open | medium | pending-later | Similar area as #342. Choose one coherent env/config approach, not both blindly. |
| [#339](https://github.com/P0nk/Cosmic/pull/339) Automatic DB backup | open | medium | pending-later | Useful for uptime/ops. Mostly Docker/scripts. |
| [#343](https://github.com/P0nk/Cosmic/pull/343) Dev Container | open | low-medium | optional | Dev convenience only. |
| [#344](https://github.com/P0nk/Cosmic/pull/344) Helm chart | open | low-medium | optional | Useful only if Kubernetes deployment is planned. |

## Defer / Not Directly Applicable Now

| PR | Status | Applicability | Decision | Notes |
|---|---|---:|---|---|
| [#221](https://github.com/P0nk/Cosmic/pull/221) Added Pyramid PQ | open | low-now | defer | Large feature, many files, high conflict risk. Revisit after agent restructure. |
| [#161](https://github.com/P0nk/Cosmic/pull/161) PostgreSQL database and Flyway migration | open | low-now | defer | Huge database refactor. Not suitable during current reconstruction. |
| [#302](https://github.com/P0nk/Cosmic/pull/302) Fix buffs | closed/unmerged | low-direct | do-not-apply-directly | Branch diff is enormous and noisy. If specific buff fixes are needed, extract manually after targeted review. |
| [#315](https://github.com/P0nk/Cosmic/pull/315) Revert item exp | closed/unmerged | medium-concept | handle-in-revert-batch | Relevant to existing revert-review list, but should be handled with planned Cosmic revert review, not as a PR merge. |
| [#347](https://github.com/P0nk/Cosmic/pull/347) Remove unsupported npm cache mount from web Dockerfile | closed/unmerged | low | skip-now | Web/Docker-specific. Not relevant unless adopting web stack. |
| [#346](https://github.com/P0nk/Cosmic/pull/346) Railway/web cost/security changes | closed/unmerged | low | skip-now | Large web/deploy footprint. Not relevant to current server/agent work. |

## Review Procedure Later

For each candidate:

1. Fetch PR ref:

```text
git fetch cosmic pull/<PR_NUMBER>/head:refs/remotes/cosmic/pr/<PR_NUMBER>
```

2. Inspect patch against upstream:

```text
git diff cosmic/master...cosmic/pr/<PR_NUMBER>
```

3. Check local overlap before editing:

```text
git status --short -- <changed-file>
git diff -- <changed-file>
```

4. Manually port only the needed patch.
5. Add or keep tests when available.
6. Run targeted tests/build.
7. Record final decision in this file:

```text
pending
accepted
accepted-modified
reference-only
deferred
rejected
```
