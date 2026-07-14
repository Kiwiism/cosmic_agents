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
- Activation installs all union/buff-source skill mutations first, then precomputes copied skill, binding, inventory, and slot-limit presentation inputs by final profile owner/version. This ordering prevents the first switch from invalidating and rebuilding both inventory snapshots. Cached inventory entries remain reusable `Item` snapshots; every refresh creates fresh one-shot `ModifyInventory` operations because the packet encoder clears those wrappers after encoding. Agent movement and combat caches are warmed before switching is enabled.
- Prepared presentation snapshots are discarded after successful release and failed activation, preventing copied inventory/macro state from accumulating across sessions.
- Presentation cancels old state, refreshes complete local state in deterministic packet-safe chunks, updates public appearance/effects, and records packet counts/bytes by category. Local refresh packets are written in order and flushed once per switch.
- Solo and Double activation preload each profile with the other profile's missing cross-job, non-beginner, non-GM skill IDs. Missing records are marked as session-borrowed and sent in one multi-skill `UPDATE_SKILLS` packet. Borrowed records are invisible to ordinary server skill queries, but same-job and beginner records are still not synthesized: stock quest, mastery, and job-advance paths can legitimately mutate those raw records, and doing so safely would require promoting the restoration journal's original state. For the normal cross-job Partner use case, switches avoid skill add/remove packets; any original skills whose level/master-level/expiration differs between the two profiles are consolidated into one multi-skill packet. Normal `Character.changeJob` advancement behavior is unchanged.
- Borrowed union skills cannot be executed by players or Agents and cannot receive SP. They remain in the raw skill snapshot sent to the stock client, but normal server skill-level, mastery, and expiration queries treat them as unlearned. This prevents cross-job passive/stat leakage (for example a borrowed Berserk record) while retaining the client-side record required by transferred buffs such as Shadow Partner. A stale, already-open skill window receives a normal rejection message instead of triggering packet-edit autoban/disconnect. Successful SP assignment to an original skill during either active mode immediately updates the other profile's borrowed copy and the client-visible state; assigning the first point to a new cross-job skill adds that skill to the union. The borrowed marker follows the profile binding, not the actor.
- Solo preparation still pre-grants source skills needed by currently active transferable buffs, independent of bond-item eligibility; the medal is checked only when deciding whether a self buff transfers during a switch. `adventurer_partner_session_skills` records every union/buff grant's original state so release can cancel the borrowed buff and restore or delete the skill. Startup and link recovery also restore lingering grants transactionally, preventing a crash from making borrowed skills permanent. This specifically supports stock-client cross-job buffs such as Shadow Partner, whose attack path expects the skill to exist in the receiving character's client skill data.
- Disconnect, channel transition, Agent removal, and the GM `!partnerprogram diag|recover [characterId]` surface route through `PartnerRecoveryService`.
- Channel, Cash Shop, and MTS transitions synchronously require canonical recovery before actor-keyed buff export or persistence. Failed saves and Agent teardown leave the runtime and leases available for a deterministic retry.
- Release saves both canonical owners and durably closes the session journal before tearing down the Agent. If teardown alone fails, retry skips duplicate saves/journal closure and completes the remaining cleanup.
- Server startup restores every lingering session skill grant before closing unfinished journals in canonical orientation. Asynchronous switch journal writes are generation-guarded so stale updates cannot overwrite newer transitions or a closed session.
- Dormant Solo profiles and headless Double actors consume and restore transient buff/disease storage with original timing. Cooldown and disease persistence now replaces both owner sets in one database transaction, including the empty-state case.
- Agent E now presents a state-driven menu: partner IGN/level/job, online/runtime status, and current mode are always visible; registration, activation, and mode actions only appear when relevant.
- Changing from Double to Solo restores and logs out the Agent before preloading the dormant Solo profile. Changing from Solo to Double restores canonical ownership before offering an immediate invite.
- Release is an idempotent reset for the registered pair. It closes a normal session, removes the exact registered Partner Agent across maps, recovers only that link's unfinished journals, and releases stale pair leases only after durable recovery succeeds. Independently played characters are never force-disconnected.
- Agent party snapshots determine leadership from the party's canonical leader ID instead of dereferencing a live player, so offline party members cannot break Partner activation. A failed partial spawn also removes the newly created exact Partner Agent entry.
- Headless Agents now complete server-controlled map transitions immediately after placement, warp, or portal completion. They no longer remain permanently blocked by the real-client map acknowledgement flag.
- Switch presentation always sends the complete, optimized local and observer refresh; unsafe diagnostic switches that could deliberately leave the stock client stale were removed. The optional special effect is configurable, can be disabled, and is shown for both Double Partner actors on the controlling client even when map-wide broadcasting is disabled. The redundant server replay of the trigger-skill effect is also disabled by default to reduce visual clutter.
- A successful switch places every configured Partner trigger skill on the stock client's cooldown UI while one dedicated server gate enforces the exact configured duration. Native WZ cooldown entries are cleared from both attached profiles before eligibility and after each switch, so Nimble Feet or Agile Body cannot override the Partner cooldown. Cooling every trigger ID prevents a profile/keymap change from bypassing the lockout with another job family's trigger variant. Because v83 cooldown packets use whole seconds, the client display rounds a positive sub-second duration up. Rapid rejected casts emit at most one cooldown chat message per cooldown window; later casts remain handled without flooding chat or rejection logs.
- Double Partner invitation closes Agent E's conversation immediately on success. The Partner Agent announces that preparation has started, and later announces readiness, using shared party chat or a direct whisper when party chat is unavailable. Skill union and profile presentation inputs finish before readiness opens. Early Nimble Feet casts receive one themed loading message and later casts are silently handled. An optional bounded readiness delay can run after the NPC closes; the default is zero because stock v83 has no safe client-load acknowledgement. Completion clears the trigger cooldowns. The flow never fakes a map transition.
- Quest and Monster Book ownership still exchange in the authoritative in-memory profile and persist canonically. Switch presentation intentionally omits their normal gameplay delta packets because stock v83 treats every replay as a new quest/card notification and opens Quest Helper; the stock client has no silent bulk refresh packet. Their client panels refresh on the next normal character load.
- A second stock-client notification path remains for quests whose completion/auto-start conditions are evaluated locally when ordinary level/job/inventory packets change. Live examples are quests 1026, 2230, and the 2300-2310 Mushking chain. The server cannot suppress those client-generated alerts without either a client patch or deliberately hiding/desynchronizing the swapped quest items, so the server keeps the authoritative inventory correct.
- Agent E marks inactive Solo mode as `Unprepared`, confirms a live Partner Agent logout before changing to Solo, and confirms Release whenever a session, recovery state, or online Partner is present. Independently played characters remain protected from forced logout.
- Solo Tag medal effects are selected from ordered, YAML-configured levels; the last level whose partner-level, holder-level, and fame bounds match wins. Before each Solo transition, active skill buffs are captured by profile. Party buffs merge after the binding exchange; self-only buffs such as Magic Guard and Shadow Partner require the receiving profile to equip the configured bond medal and are capped by its selected level. The existing most-significant-buff resolver retains the strongest value when effects overlap.
- Double Partner uses the same medal levels and independent mode flag. It copies successfully cast self buffs immediately to the other live actor without a range or same-map requirement. Eligibility is directional and evaluated against the receiver's equipped medal. Party buffs retain their normal Cosmic behavior. Temporary union skills keep transferred buffs client-safe and are restored when the session ends.
- Weapon boosters never replace a different booster already active on the receiving profile. If multiple incoming booster sources conflict, all conflicting incoming boosters are skipped; a single booster still transfers when the recipient has none.
- A normal Double Partner release sends one farewell through party chat, with an owner whisper fallback, before the Partner Agent is removed.
- Agent E opens database shop `9000036`, seeded with the configured demo medals at 1,000,000 mesos. Prices can be edited in `shopitems`; a character cannot purchase a medal already owned or equipped.

## Configuration and rollback

The feature is disabled by default under `adventurerPartner` in `config.yaml`. The dedicated switch cooldown defaults to 5000 ms. Enabling the feature requires `RESTORE_CANONICAL_ON_DISCONNECT: true`; startup rejects an unsafe configuration. Disabling the feature removes the Agent E menu and leaves the persistent link/session history intact. Every key in this block uses the same uppercase snake-case convention as the main server settings.

Medal effects use ordered levels and independent mode flags. For example:

```yaml
MEDAL_EFFECTS:
    - ITEM_ID: 1142073
      EFFECT: SELF_BUFF_BOND
      SOLO_TAG_ENABLED: true
      DOUBLE_PARTNER_ENABLED: true
      LEVELS:
          - CONDITIONS:
                MIN_PARTNER_LEVEL: 70
            MAX_SKILL_LEVEL: 10
          - CONDITIONS:
                MIN_PARTNER_LEVEL: 95
            MAX_SKILL_LEVEL: 20
```

Verified thematic demo candidates from the read-only v83 WZ data are:

| Inventory type | Item | Eligibility behavior |
| --- | --- | --- |
| Equip | `1142073` Be My Friend Medal (default) | Must be equipped |
| Use | `2022109` The Breath of Nine Spirit | Must be carried |
| Setup | `3010116` The Spirit of Rock Chair | Must be carried |
| Etc | `4000144` Free Spirit | Must be carried |
| Cash | `5121000` Fighting Spirit | Must be carried |

Each effect has its own item ID. Equipment must be equipped; a configured non-equipment item only needs to be carried.

The complete `adventurerPartner` block uses uppercase snake-case keys (`ENABLED`, `NPC_ID`, `SOLO_TAG_ENABLED`, and so on), matching the main server configuration convention.

Presentation packet diffing, inventory chunking, copied static inputs, and the single local flush are always enabled. The old per-component `PRESENT_*` and `PUBLIC_PRESENTATION` diagnostics were removed because disabling a required category can desynchronize the stock client and nearby observers.

Safe presentation/readiness controls are:

```yaml
DOUBLE_PARTNER_READY_DELAY_MS: 0
SWITCH_EFFECT_ID: 8
SWITCH_EFFECT_BROADCAST: false
SWITCH_TRIGGER_EFFECT_ENABLED: false
```

`DOUBLE_PARTNER_READY_DELAY_MS` accepts 0-10000 ms. It is an optional minimum post-NPC settling window, not a client acknowledgement. `SWITCH_EFFECT_ID` accepts `-1` (disabled) or a byte-sized v83 effect ID other than incompatible effect `10`. The stock-safe default remains the original job-change effect (`8`); effect `10` crashes the tested v83 client. Avoid `9`, which is the quest-complete notification. Other IDs should be treated as experimental and verified against the exact client build. `SWITCH_EFFECT_BROADCAST: false` keeps both actor effects on the controlling client without adding map-wide visual clutter. Nimble Feet remains the safest trigger because all four character families have a beginner variant and it uses the special-move packet path; the Recovery family (`1001`, `10001001`, `20001001`, `20011001`) is technically compatible but would replace normal Recovery use while a session is active.

## Security and failure-path audit

- Profile ownership is authenticated from server-side link/account/world data, then revalidated after the canonical detached/Agent load. NPC selections and trigger skill IDs never establish ownership.
- Registration and session creation lock the relevant database rows; process-local reservations serialize normal login/deletion against activation. This deployment model assumes Cosmic's channels share one JVM. Running multiple independent server JVMs against one database would require a database-backed login lease check as an additional boundary.
- GM authority is actor/client scoped and is no longer part of the exchanged profile graph. A normal client cannot inherit a Partner character's GM level, and a GM controller does not lose recovery authority after a tag.
- Borrowed client-compatibility skills are excluded from ordinary server skill queries as well as final cast/Agent/SP policies, closing passive, mastery-book, expiry, dialogue, respec, and formula paths that do not consistently check the current job. Generic canonical skill mutation rejects a borrowed record; release/reset must restore it through the durable Partner skill journal instead. If the receiving profile already owns the same skill, preparation keeps that canonical level rather than temporarily overwriting real SP/mastery; only a missing borrowed copy is synthesized or synchronized.
- Failed activation now retains both profile leases and the open journal until temporary-skill restoration, Partner teardown/dormant cleanup, and durable journal closure all succeed. Agent E reset or startup recovery can therefore finish the work without opening a duplicate-login window.
- Successful release saves and canonicalizes first, restores temporary skills in memory and then in the database, closes the session journal only after that durable restore, tears down the Agent, closes runtime state, and releases leases last. A partial release is switch-locked and remains retryable instead of advertising the profiles as available. This ordering leaves no crash window where a closed journal can strand temporary skills.
- Same-map, alive, map-transition, Cash Shop/MTS, trade/shop/minigame, event/PQ, active conversation, unsupported periodic/map-bound buff, Agent-action, generation, and lease checks remain strict. `SAME_MAP_REQUIRED: false` is technically supported, but keeping it true avoids remote actor/map and event-context ambiguity and is the recommended production setting.
- Switch requests are lifecycle-serialized, generation-checked, cooldown-limited, and chat-coalesced. The cooldown deadline uses saturating arithmetic, so an extreme trusted configuration value cannot wrap into an immediately reusable switch.
- Quest and Monster Book client deltas remain intentionally omitted; authoritative data still switches and saves. This avoids replay-driven notifications but means those two stock-client panels are only fully refreshed by the next normal character load.
- Double Partner currently has to create the headless Agent before its profile can be prepared through the existing Agent lifecycle. Its transition barrier pauses Agent work immediately, and switching stays locked until preparation is complete. Hiding the spawn itself would require a new staged Agent lifecycle API and was not mixed into this low-risk pass.

## Stock v83 client constraint

Canonical names are deliberately not mutated. The human actor and Agent actor retain their actor IGNs while appearance, job, stats, equipment, skills, inventory, quests, Monster Book, pets, buffs, debuffs, and cooldowns follow the attached profile. Any client UI that caches the character name therefore continues to show the actor IGN without a custom client.

## Verification status

- The current hardening regression group passed 156 tests across 23 reports with 0 failures, 0 errors, and 0 skipped. It covers skill union/borrowed-skill isolation, SP synchronization, passive formula filtering, durable release ordering and fault retry, readiness gating, full optimized presentation, inventory packet batching, Agent build/dialogue safeguards, and cooldown-feedback coalescing.
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
