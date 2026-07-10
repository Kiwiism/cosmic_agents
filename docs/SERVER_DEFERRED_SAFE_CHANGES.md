# Server Deferred Safe Changes

These items are server-only and non-agent, but should remain deferred until soak logs or focused tests justify behavior changes.

## Character Deletion Transaction Consolidation

Status: completed in the current hardening worktree.

- DB-only deletion is grouped under one transaction.
- Server-memory cleanup runs after commit and cannot roll back committed SQL.
- Transaction rollback and post-commit failure behavior have focused tests.
- Real deletion of richly populated characters remains part of the manual
  gameplay/relogin gate; it is no longer an implementation TODO.

## Broadcast Optimization Follow-Up

Current state:

- Map broadcast slow warnings exist.
- Broadcast packet semantics are unchanged.

Why deferred:

- Broadcast paths are client-visible and easy to break subtly.
- Some specialized packets may be generated per recipient because content really differs by recipient.

Implementation plan:

1. Use slow broadcast logs to identify specific packet paths.
2. For each hot path, classify packet as:
   - same bytes for every recipient
   - different bytes for owner/GM/party/visibility
   - truly per-recipient
3. Cache packet bytes only for same-bytes cases.
4. Add a regression test or packet trace for each changed path.
5. Keep agent perception work out of this server-only pass.

## Static Runtime Cache Ownership

Current state:

- Scale health reports known runtime caches.
- Logout clears pending NPC/dressing-room state and script manager state.
- Event/map dispose diagnostics exist.

Remaining review targets:

- Guild/alliance/family maps.
- Buddy and messenger state.
- Merchant/shop owner registrations.
- Event instance character and map references.
- Debug/monitoring sets.
- New feature caches keyed by character/account/map/object id.

Current evidence:

- Empty messenger instances are removed from the world registry.
- Messenger member views are immutable snapshots and mutation is synchronized.
- World diagnostics expose account views, storages, families, messengers,
  player shops, and hired merchants.
- Transition buff/disease storage exposes retained-character counts.
- Idle-map unload remains default-off while the canary matrix is incomplete.

Implementation rule:

- Every runtime cache must document owner, expected max size, cleanup hook, and diagnostic count.
- Metadata caches from WZ/DB may be permanent if bounded by game data size.

## Config Review Without Value Changes

Current rule:

- Do not rewrite `config.yaml` values during hardening unless explicitly requested.

Recommended follow-up:

- Keep production-safety notes in docs unless the owner wants config comments added directly.
- Review risky toggles before public uptime tests:
  - dev/debug commands
  - dupe/delete/dressing-room commands
  - custom item/godly-stat behavior
  - high HP/MP cap
  - drop/spawn/rate overrides
  - local network/IP bypass behavior
