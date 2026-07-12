# Adventurer Partner Program Implementation Notes

Base `cosmic_agents/master` commit:

```text
28555684e8d867793251e646d421fc30498ad74e
```

## Re-audited production boundaries

- `client.Character` remains the compatibility facade and currently owns profile state directly.
- `Character.loadCharFromDB` and `Character.saveCharToDB` are the canonical load/save entry points.
- `Inventory.attachPersistenceOwner` already separates an inventory's live callback target from its item state.
- `AgentLifecycleService` and `CosmicAgentSpawnCoordinator` are the supported Agent spawn/follow integration boundaries.
- `AgentRuntimeEntry.sessionGeneration` and scoped tasks reject callbacks from removed Agent sessions.
- `AgentTickScheduler` exposes pause/resume only for the optional central scheduler; Partner transitions therefore require a scheduler-independent quiescence barrier.
- `SpecialMoveHandler` owns the ordinary Nimble Feet execution path and must delegate only the configured Partner trigger interception.
- Agent E is `scripts/npc/9000036.js`; its existing `sendDefault()` behavior must remain available when the feature is disabled or the player leaves the Partner menu.
- Liquibase table changes are registered in `src/main/resources/db/changelog-tables.xml`.

The removed snapshot/row-replacement Double Agent POC is not used or restored.

## Implemented production model

- Partner links are symmetric, canonical-ID pairs. Registration and mode changes are database-serialized and require an inactive pair.
- Active sessions lease both profile owner IDs before loading or spawning the Partner. Login and deletion paths reject leased profiles.
- Login, deletion, and Partner activation now share an exclusive profile reservation boundary, closing the eligibility-check/use race before a lease exists.
- Solo Tag keeps the Partner profile in a detached, dormant holder. Double Partner uses the existing headless Agent lifecycle and Follow runtime.
- `CharacterProfileBinding` carries canonical owner, binding generation, and mutation version independently of actor ID.
- Profile exchange keeps actor IDs, clients/controllers, map membership, object IDs, positions, footholds, and movement fixed while exchanging the profile-owned object graph in O(1).
- Canonical saves route stats, inventories, equipment, skills, keymap/macros/quickslots, quests, Monster Book, pets, locations, cooldowns, diseases, and profile progression through the attached profile owner ID.
- Actor-owned social/map/session state keeps the actor ID and a separate dirty signal. Account storage is not exchanged.
- Agent ticks use a scheduler-independent read/write transition barrier. Scoped callbacks and mailbox actions carry a transition generation and reject stale work.
- Buff, disease, cooldown, quest, item-expiry, and pet-hunger callbacks are owner/generation stamped. A profile transition drains in-flight callbacks before the O(1) binding exchange and rebuilds actor-bound schedules afterward.
- Activation precomputes copied skill, binding, inventory, and slot-limit presentation inputs by profile owner/version. Agent movement and combat caches are warmed before switching is enabled.
- Presentation cancels old state, refreshes complete local state in deterministic packet-safe chunks, updates public appearance/effects, and records packet counts/bytes by category.
- Disconnect, channel transition, Agent removal, and the GM `!partnerprogram diag|recover [characterId]` surface route through `PartnerRecoveryService`.
- Channel, Cash Shop, and MTS transitions synchronously require canonical recovery before actor-keyed buff export or persistence. Failed saves and Agent teardown leave the runtime and leases available for a deterministic retry.
- Release saves both canonical owners and durably closes the session journal before tearing down the Agent. If teardown alone fails, retry skips duplicate saves/journal closure and completes the remaining cleanup.
- Server startup closes unfinished journals in canonical orientation. Asynchronous switch journal writes are generation-guarded so stale updates cannot overwrite newer transitions or a closed session.
- Dormant Solo profiles and headless Double actors consume and restore transient buff/disease storage with original timing. Cooldown and disease persistence now replaces both owner sets in one database transaction, including the empty-state case.

## Configuration and rollback

The feature is disabled by default under `adventurerPartner` in `config.yaml`. The dedicated switch cooldown defaults to 5000 ms. Enabling the feature requires `restoreCanonicalOnDisconnect: true`; startup rejects an unsafe configuration. Disabling the feature removes the Agent E menu and leaves the persistent link/session history intact.

## Stock v83 client constraint

Canonical names are deliberately not mutated. The human actor and Agent actor retain their actor IGNs while appearance, job, stats, equipment, skills, inventory, quests, Monster Book, pets, buffs, debuffs, and cooldowns follow the attached profile. Any client UI that caches the character name therefore continues to show the actor IGN without a custom client.

## Verification status

- Focused Partner/profile/Agent/login/deletion and shared bot-combat regression suite: 243 tests across 30 reports passed, 0 failures, 0 errors, 0 skipped.
- The focused suite includes 1,000 repeated binding-and-lease reversals, release retry fault injection, post-exchange authoritative recovery, SWAPPING disconnect recovery, login/deletion reservation races, mailbox barrier rejection, and Agent cache owner/version stamping.
- `mvnw.cmd -q -DskipTests package`: passed after the final production-code changes.
- `node --check scripts/npc/9000036.js`: passed.
- Liquibase changelog XML parsed successfully. Migration `026-adventurer-partner.sql` was applied against a disposable MySQL 8.4 schema; constraints, two-row quickslot migration, and link/session cascade behavior passed, and the disposable schema was removed. The existing `cosmic` database was not modified.
- The full repository suite produced 513 suite reports before stalling after `AgentNavigationGraphFallbackTest`; isolated `AgentNavigationGraphServiceTest` also timed out without producing a report. No navigation-graph files are changed by this branch.
- Real v83 client acceptance, nearby-observer verification, live disconnect/save fault injection, and a long-running server/client memory soak still require the game client/server test environment. They must not be inferred from unit tests.
