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
- Derived-stat reconstruction now runs after the O(1) binding exchange and outside the profile locks; the recorded lock duration therefore measures the atomic exchange rather than WZ-backed equipment reconstruction.
- Canonical saves route stats, inventories, equipment, skills, keymap/macros/quickslots, quests, Monster Book, pets, locations, cooldowns, diseases, and profile progression through the attached profile owner ID.
- Actor-owned social/map/session state keeps the actor ID and a separate dirty signal. Account storage is not exchanged.
- Agent ticks use a scheduler-independent read/write transition barrier. Scoped callbacks and mailbox actions carry a transition generation and reject stale work.
- Buff, disease, cooldown, quest, item-expiry, and pet-hunger callbacks are owner/generation stamped. A profile transition drains in-flight callbacks before the O(1) binding exchange and rebuilds actor-bound schedules afterward.
- Activation precomputes copied skill, binding, inventory, and slot-limit presentation inputs by profile owner/version. Agent movement and combat caches are warmed before switching is enabled.
- Prepared presentation snapshots are discarded after successful release and failed activation, preventing copied inventory/macro state from accumulating across sessions.
- Presentation cancels old state, refreshes complete local state in deterministic packet-safe chunks, updates public appearance/effects, and records packet counts/bytes by category.
- Disconnect, channel transition, Agent removal, and the GM `!partnerprogram diag|recover [characterId]` surface route through `PartnerRecoveryService`.
- Channel, Cash Shop, and MTS transitions synchronously require canonical recovery before actor-keyed buff export or persistence. Failed saves and Agent teardown leave the runtime and leases available for a deterministic retry.
- Release saves both canonical owners and durably closes the session journal before tearing down the Agent. If teardown alone fails, retry skips duplicate saves/journal closure and completes the remaining cleanup.
- Server startup closes unfinished journals in canonical orientation. Asynchronous switch journal writes are generation-guarded so stale updates cannot overwrite newer transitions or a closed session.
- Dormant Solo profiles and headless Double actors consume and restore transient buff/disease storage with original timing. Cooldown and disease persistence now replaces both owner sets in one database transaction, including the empty-state case.
- Agent E now presents a state-driven menu: partner IGN/level/job, online/runtime status, and current mode are always visible; registration, activation, and mode actions only appear when relevant.
- Changing from Double to Solo restores and logs out the Agent before preloading the dormant Solo profile. Changing from Solo to Double restores canonical ownership before offering an immediate invite.
- Release is an idempotent reset for the registered pair. It closes a normal session, removes the exact registered Partner Agent across maps, clears stale pair leases, and recovers only that link's unfinished journals. Independently played characters are never force-disconnected.
- Agent party snapshots determine leadership from the party's canonical leader ID instead of dereferencing a live player, so offline party members cannot break Partner activation. A failed partial spawn also removes the newly created exact Partner Agent entry.

## Configuration and rollback

The feature is disabled by default under `adventurerPartner` in `config.yaml`. The dedicated switch cooldown defaults to 5000 ms. Enabling the feature requires `restoreCanonicalOnDisconnect: true`; startup rejects an unsafe configuration. Disabling the feature removes the Agent E menu and leaves the persistent link/session history intact.

## Stock v83 client constraint

Canonical names are deliberately not mutated. The human actor and Agent actor retain their actor IGNs while appearance, job, stats, equipment, skills, inventory, quests, Monster Book, pets, buffs, debuffs, and cooldowns follow the attached profile. Any client UI that caches the character name therefore continues to show the actor IGN without a custom client.

## Verification status

- Focused Partner/profile/persistence/Agent/login/deletion and shared bot-combat regression suite: 263 tests across 34 reports passed, 0 failures, 0 errors, 0 skipped.
- The focused suite includes two 1,000-iteration soaks (domain reversal and full Double coordinator), complete profile-bundle exchange, real presentation packet ordering/public-look calls, release retry fault injection, disconnect during presentation, Agent cache-rebuild failure, simultaneous triggers, stale profile-task rejection, SWAPPING recovery, login/deletion reservation races, mailbox barrier rejection, and Agent cache owner/version stamping.
- `mvnw.cmd -q -DskipTests package`: passed after the final production-code changes.
- `node --check scripts/npc/9000036.js`: passed.
- The state-driven Agent E follow-up regression group passed 60 tests across 15 reports with 0 failures, 0 errors, and 0 skipped. It covers menu states, direct mode transitions, idempotent reset, independently-online protection, same-map messaging, and offline-party snapshots.
- Liquibase changelog XML parsed successfully. Migration `026-adventurer-partner.sql` was applied against a disposable MySQL 8.4 schema; constraints, two-row quickslot migration, and link/session cascade behavior passed, and the disposable schema was removed. The existing `cosmic` database was not modified.
- Independent worktree catalogs were generated from the read-only WZ junction and verified: game 75/75, NPC 115/115, reactor 7/7, and derived Agent/LLM 51/51. `AgentCatalogServiceTest` then passed.
- A bounded expanded repository run produced 3,798 tests across 513 reports with 0 errors and 3 skips before entering the unrelated CPU-heavy `BotMovementSimulationLabTest`. Its single movement-fidget failure passed immediately in isolation. Earlier thread dumps likewise isolated `AgentNavigationGraphServiceTest` and `AgentPhysicsEngineTest` as navigation-graph bottlenecks. No navigation-graph files are changed by this branch.
- The requirement-to-evidence and live execution checklist is recorded in `ADVENTURER_PARTNER_PROGRAM_ACCEPTANCE_MATRIX.md`.
- Real v83 client acceptance, nearby-observer verification, live disconnect/save fault injection, and a long-running server/client memory soak still require the game client/server test environment. They must not be inferred from unit tests.
