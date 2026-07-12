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
- Partner presentation refreshes skills differentially by skill ID: it removes absent skills and sends new values only for additions or level/master-level/expiration changes. Identical records are left untouched, avoiding hundreds of redundant v83 skill packets. This optimization exists only in `CosmicProfilePresentationService`; normal `Character.changeJob` advancement behavior is unchanged.
- Solo preparation pre-grants each profile the other profile's exact learned self-buff skill states, plus the source skills for currently active transferable buffs, independent of bond-item eligibility; the medal is checked only when deciding whether a self buff transfers during a switch. Successful SP assignment to a self-buff skill during an active Solo session immediately updates the other profile's temporary copy, and buffs learned or cast later retain a switch-time grant fallback. Each grant updates `Character.skills`, sends the normal `UPDATE_SKILLS` packet, and is upserted into `skills`. `adventurer_partner_session_skills` records the original state so release can cancel the borrowed buff and restore or delete the skill. Startup and link recovery also restore lingering grants transactionally, preventing a crash from making borrowed skills permanent. This specifically supports stock-client cross-job buffs such as Shadow Partner, whose attack path expects the skill to exist in the receiving character's client skill data.
- Disconnect, channel transition, Agent removal, and the GM `!partnerprogram diag|recover [characterId]` surface route through `PartnerRecoveryService`.
- Channel, Cash Shop, and MTS transitions synchronously require canonical recovery before actor-keyed buff export or persistence. Failed saves and Agent teardown leave the runtime and leases available for a deterministic retry.
- Release saves both canonical owners and durably closes the session journal before tearing down the Agent. If teardown alone fails, retry skips duplicate saves/journal closure and completes the remaining cleanup.
- Server startup restores every lingering session skill grant before closing unfinished journals in canonical orientation. Asynchronous switch journal writes are generation-guarded so stale updates cannot overwrite newer transitions or a closed session.
- Dormant Solo profiles and headless Double actors consume and restore transient buff/disease storage with original timing. Cooldown and disease persistence now replaces both owner sets in one database transaction, including the empty-state case.
- Agent E now presents a state-driven menu: partner IGN/level/job, online/runtime status, and current mode are always visible; registration, activation, and mode actions only appear when relevant.
- Changing from Double to Solo restores and logs out the Agent before preloading the dormant Solo profile. Changing from Solo to Double restores canonical ownership before offering an immediate invite.
- Release is an idempotent reset for the registered pair. It closes a normal session, removes the exact registered Partner Agent across maps, clears stale pair leases, and recovers only that link's unfinished journals. Independently played characters are never force-disconnected.
- Agent party snapshots determine leadership from the party's canonical leader ID instead of dereferencing a live player, so offline party members cannot break Partner activation. A failed partial spawn also removes the newly created exact Partner Agent entry.
- Headless Agents now complete server-controlled map transitions immediately after placement, warp, or portal completion. They no longer remain permanently blocked by the real-client map acknowledgement flag.
- Switch presentation plays the v83 job-change effect locally for the human and broadcasts it for both human and Partner actors in Double mode.
- Quest and Monster Book ownership still exchange in the authoritative in-memory profile and persist canonically. Switch presentation intentionally omits their normal gameplay delta packets because stock v83 treats every replay as a new quest/card notification and opens Quest Helper; the stock client has no silent bulk refresh packet. Their client panels refresh on the next normal character load.
- A second stock-client notification path remains for quests whose completion/auto-start conditions are evaluated locally when ordinary level/job/inventory packets change. Live examples are quests 1026, 2230, and the 2300-2310 Mushking chain. The server cannot suppress those client-generated alerts without either a client patch or deliberately hiding/desynchronizing the swapped quest items, so the server keeps the authoritative inventory correct.
- Agent E marks inactive Solo mode as `Unprepared`, confirms a live Partner Agent logout before changing to Solo, and confirms Release whenever a session, recovery state, or online Partner is present. Independently played characters remain protected from forced logout.
- Optional Solo Tag buff sharing is disabled by default. Before each Solo transition, active skill buffs are captured by profile. When enabled, party buffs merge automatically after the binding exchange; self-only buffs such as Magic Guard, Shadow Partner, and weapon boosters require the receiving profile to qualify independently through the configured bond item. Equipment, including cash equipment, must be equipped; Use, Setup, Etc, and Cash items only need to be carried. The existing most-significant-buff resolver retains the strongest value when effects overlap.
- Weapon boosters never replace a different booster already active on the receiving profile. If multiple incoming booster sources conflict, all conflicting incoming boosters are skipped; a single booster still transfers when the recipient has none.
- Agent E can sell the configured bond item for the configured meso price. Granting and inventory-space checks complete before mesos are charged. The demo default is `1142073` (Be My Friend Medal) at 10,000,000 mesos.

## Configuration and rollback

The feature is disabled by default under `adventurerPartner` in `config.yaml`. The dedicated switch cooldown defaults to 5000 ms. Enabling the feature requires `RESTORE_CANONICAL_ON_DISCONNECT: true`; startup rejects an unsafe configuration. Disabling the feature removes the Agent E menu and leaves the persistent link/session history intact. Every key in this block uses the same uppercase snake-case convention as the main server settings.

Solo Tag buff sharing uses these independent settings:

```yaml
SOLO_TAG_BUFF_SHARING_ENABLED: false
SOLO_TAG_BUFF_SHARING_ITEM_ID: 1142073
SOLO_TAG_BUFF_SHARING_PRICE_MESOS: 10000000
```

Verified thematic demo candidates from the read-only v83 WZ data are:

| Inventory type | Item | Eligibility behavior |
| --- | --- | --- |
| Equip | `1142073` Be My Friend Medal (default) | Must be equipped |
| Use | `2022109` The Breath of Nine Spirit | Must be carried |
| Setup | `3010116` The Spirit of Rock Chair | Must be carried |
| Etc | `4000144` Free Spirit | Must be carried |
| Cash | `5121000` Fighting Spirit | Must be carried |

Only the single configured item ID is active at a time; the other entries are convenient category test candidates.

The complete `adventurerPartner` block uses uppercase snake-case keys (`ENABLED`, `NPC_ID`, `SOLO_TAG_ENABLED`, and so on), matching the main server configuration convention.

## Stock v83 client constraint

Canonical names are deliberately not mutated. The human actor and Agent actor retain their actor IGNs while appearance, job, stats, equipment, skills, inventory, quests, Monster Book, pets, buffs, debuffs, and cooldowns follow the attached profile. Any client UI that caches the character name therefore continues to show the actor IGN without a custom client.

## Verification status

- Focused Partner/profile/persistence/Agent/login/deletion and shared bot-combat regression suite: 263 tests across 34 reports passed, 0 failures, 0 errors, 0 skipped.
- The focused suite includes two 1,000-iteration soaks (domain reversal and full Double coordinator), complete profile-bundle exchange, real presentation packet ordering/public-look calls, release retry fault injection, disconnect during presentation, Agent cache-rebuild failure, simultaneous triggers, stale profile-task rejection, SWAPPING recovery, login/deletion reservation races, mailbox barrier rejection, and Agent cache owner/version stamping.
- `mvnw.cmd -q -DskipTests package`: passed after the final production-code changes.
- `node --check scripts/npc/9000036.js`: passed.
- The current state-driven/live-acceptance follow-up regression group passed 73 tests across 18 reports with 0 failures, 0 errors, and 0 skipped. It covers menu states, direct mode transitions, idempotent reset, independently-online protection, same-map messaging, offline-party snapshots, silent quest/card presentation, dual-actor effects, headless map-transition completion, asymmetric self-buff entitlement, automatic party-buff sharing, temporary client skill registration/removal, overlap ordering, and purchase safety.
- Liquibase changelog XML parsed successfully. Migration `026-adventurer-partner.sql` was applied against a disposable MySQL 8.4 schema; constraints, two-row quickslot migration, and link/session cascade behavior passed, and the disposable schema was removed. The existing `cosmic` database was not modified.
- Independent worktree catalogs were generated from the read-only WZ junction and verified: game 75/75, NPC 115/115, reactor 7/7, and derived Agent/LLM 51/51. `AgentCatalogServiceTest` then passed.
- A bounded expanded repository run produced 3,798 tests across 513 reports with 0 errors and 3 skips before entering the unrelated CPU-heavy `BotMovementSimulationLabTest`. Its single movement-fidget failure passed immediately in isolation. Earlier thread dumps likewise isolated `AgentNavigationGraphServiceTest` and `AgentPhysicsEngineTest` as navigation-graph bottlenecks. No navigation-graph files are changed by this branch.
- The requirement-to-evidence and live execution checklist is recorded in `ADVENTURER_PARTNER_PROGRAM_ACCEPTANCE_MATRIX.md`.
- Real v83 client acceptance, nearby-observer verification, live disconnect/save fault injection, and a long-running server/client memory soak still require the game client/server test environment. They must not be inferred from unit tests.
