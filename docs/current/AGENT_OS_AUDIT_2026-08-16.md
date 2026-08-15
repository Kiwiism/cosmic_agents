# Agent OS architecture audit — 2026-08-16

## Completed rollout

1. Preserved and committed the existing ranged-hunt, recovery, quest-route, and
   TownLife baseline before structural work.
2. Removed tracked runtime artifacts and stopped tracking the local database
   console secret file; added repository line-ending and crash-dump rules.
3. Made Victoria hunt catalog exports deterministic and compiled authored
   primary/fallback maps into one immutable hunt objective contract.
4. Added typed navigation traversal commands/results so route execution no
   longer communicates rejection through nulls and booleans.
5. Consolidated foreground plan authority behind the universal plan adapter,
   while retaining old Amherst checkpoint reattachment.
6. Consolidated combat decision frames and target candidate discovery under
   capability-owned boundaries.
7. Split pure ranged-spacing policy from live execution and made the combat
   loop consume the authoritative attack transaction result.
8. Moved ranged tactical commitments out of `AgentRuntimeEntry` and into the
   capability state registry.
9. Extracted graph-build profiling from the navigation graph construction
   service without changing generated edges or cache compatibility.
10. Split TownLife session orchestration from its bounded activity state
    machine.
11. Scoped optional TownLife decision controllers and encounter mutation locks
    by town.
12. Added architecture regression gates, ran focused subsystem suites, and
    attempted the aggregate project suite. The aggregate run reached its
    ten-minute bound during integration coverage; its completed reports exposed
    a configuration-boundary violation that was corrected and re-tested.

## Boundary assessment

- Plans own objectives and ordering; combat, navigation, inventory, and
  TownLife expose execution boundaries.
- Combat target selection is still a large runtime, but candidate discovery,
  decision state, ranged spacing policy, and attack commitment now have
  distinct owners.
- Navigation path search and edge execution have typed boundaries. Graph
  generation remains intentionally centralized because changing its mechanics
  needs map-coverage evidence, not a mechanical file split.
- TownLife entry/exit is externally requested, session orchestration owns the
  lease, and activity execution is local to TownLife. Town-specific policy can
  be installed without replacing all towns.

## Compatibility paths retained deliberately

The following are not dead code and were not removed:

- `LEGACY_PER_AGENT` scheduler mode is the documented restart-time rollback.
  Its live parity and soak gates remain incomplete.
- `SYNTHETIC` mob reaction is deprecated and the default is `PHYSICS`, but
  removal still needs recorded five-job combat parity and rollback evidence.
- Old Amherst checkpoints remain readable through the universal foreground
  adapter; removing that reader would strand existing snapshots.

These paths should be deleted only after their existing evidence/runbook gates
pass. Hidden experimental behavior should not be promoted merely to make it
reachable; it needs a named policy owner, diagnostics, tests, and a rollback
condition first.

## Remaining highest-value work

1. Decompose graph construction by edge provider (walk, drop, jump, rope,
   portal) while preserving byte-equivalent graph fixtures.
2. Move the remaining direct capability state fields out of
   `AgentRuntimeEntry` in small behavior-owned aggregates.
3. Split combat scoring/selection tracing from `AgentCombatTargetRuntime` and
   replace the remaining `legacy*` scoring names after parity tests prove they
   are the authoritative policy.
4. Complete scheduler and mob-physics live gates before deleting rollback
   modes.
5. Add repeatable multi-Agent map soaks for combat, navigation, and TownLife;
   unit tests do not establish long-run efficiency or packet presentation
   parity.
