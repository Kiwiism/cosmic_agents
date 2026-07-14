# Adventurer Partner Program Acceptance Matrix

Base `cosmic_agents/master`: `28555684e8d867793251e646d421fc30498ad74e`

This matrix separates server-side automated evidence from behavior that can only
be proven with a real v83 client. An automated pass is not a substitute for a
live pass.

## Automated and structural evidence

| Requirement | Evidence | Status |
| --- | --- | --- |
| Agent E shows partner IGN/level/job, online/runtime status, current mode, and only state-relevant actions | `AdventurerPartnerNpcServiceTest`, `scripts/npc/9000036.js`; `node --check` | Automated/static pass; client layout pending |
| Direct mode toggle safely releases Double before preparing Solo, and releases Solo before offering a Double invite | `AdventurerPartnerServiceTest`, `AdventurerPartnerNpcServiceTest` | Automated pass |
| Release/reset is pair-scoped, idempotent, cleans an orphan Agent across maps, and does not disconnect an independently played character | `AdventurerPartnerServiceTest`, `CosmicPartnerAgentLifecycleBridge` | Automated boundary pass; live reset pending |
| Offline normal-party members cannot crash Double activation | `CosmicPartyGatewayTest.snapshotsOfflinePartyMembersWithoutDereferencingLivePlayers` | Regression pass |
| Headless Partner Agent does not remain permanently map-transitioning | `CosmicMapGatewayTest.serverControlledMapPlacementCompletesTheHeadlessTransition`, Partner bridge completion | Regression pass; live switch pending |
| Quest/card state swaps without replaying server notification or Quest Helper packets | `CosmicProfilePresentationServiceTest`; server profile exchange assertions | Automated packet pass; stock client panels refresh on relog; client-side auto-start/completion alerts remain |
| Solo skill-buff sharing is disabled by default, always merges party buffs when enabled, item-gates self buffs per receiving profile, supports equipment/carried-item rules, and applies weaker overlaps before stronger ones | `SoloTagBuffSharingServiceTest`, `AdventurerPartnerConfigTest`, `AdventurerPartnerNpcServiceTest` | Automated pass; live skill matrix pending |
| Job-change switch effect is local for the player and publicly broadcast for both Double actors | `CosmicProfilePresentationServiceTest` | Automated packet/broadcast pass; observer-client pending |
| Solo unprepared status and online/session Release/mode confirmations are state-driven | `AdventurerPartnerNpcServiceTest`, Agent E script | Automated/static pass; client dialogue pending |
| Same-account/world, self, deletion, online, lease, active-pair, and canonical-load eligibility | `PartnerRosterQueryServiceTest`, `AdventurerPartnerServiceTest`, `ProfileLeaseRegistryTest` | Automated pass |
| Symmetric persistent pair and session constraints | migration `026-adventurer-partner.sql`; disposable MySQL 8.4 migration/constraint run | Automated database pass |
| Solo activation uses one actor and one dormant profile | `AdventurerPartnerServiceTest`, `AdventurerPartnerLifecycleIntegrationTest` | Automated pass |
| Double activation uses the real Agent spawn/follow boundary | `AdventurerPartnerServiceTest`; `CosmicPartnerAgentLifecycleBridge` boundary audit | Automated boundary pass; live spawn pending |
| Actor IDs, positions, controllers, and map objects remain fixed | `CharacterProfileExchangeTest`, `AdventurerPartnerLifecycleIntegrationTest` | Automated pass |
| Complete owner bundle follows the binding | `CharacterProfileExchangeTest`: core stats, jobs/looks, mesos, skills, keymap, macros, quickslots, quests, Monster Book, pets, diseases, cooldowns, equipped/normal/cash inventories and slot limits | Automated pass |
| Canonical persistence owner is independent of actor | `CharacterProfileBindingTest`, lifecycle integration canonical-save assertions, owner-aware save paths | Automated pass |
| Stable lock ordering and exclusive leases | `ProfileTransitionLockManagerTest`, `ProfileLeaseRegistryTest` | Automated pass |
| Login/deletion cannot race Partner activation | `ProfileLeaseRegistryTest`, `PlayerLoggedinHandlerTest`, service activation tests | Automated pass |
| Atomic binding exchange is O(1) and derived reconstruction is outside profile locks | `Character.exchangeProfileBindings`, `ProfileTransitionCoordinatorTest` | Automated/structural pass |
| Agent barrier pauses/drains and resumes | `AgentTransitionBarrierStateTest`, `ProfileTransitionCoordinatorTest` | Automated pass |
| Stale mailbox/capability and profile-task callbacks are rejected | `AgentActionMailboxTest`, `CharacterProfileExchangeTest.staleCooldownCallbackCannotMutateTheNewlyAttachedProfile` | Automated pass |
| Agent cache identity includes profile owner and version | `AgentProfileCacheStampTest`, `PartnerProfileCacheInvalidator` | Automated pass |
| Agent cache/tick failure after exchange recovers forward | `ProfileTransitionCoordinatorTest.agentCacheRebuildFailureAfterExchangeRecoversForwardAndResumesBarrier` | Fault-injection pass |
| Simultaneous trigger requests execute one transition | `AdventurerPartnerServiceTest.simultaneousSwitchRequestsExecuteOnlyOneTransition` | Fault-injection pass |
| Presentation order, packet-safe inventory chunks, final action enable, and both public actor look refreshes | `InventoryPacketChunkerTest`, `CosmicProfilePresentationServiceTest` | Automated adapter pass; observer-client pending |
| Partner switching emits skill packets only for added, removed, level-changed, master-level-changed, or expiration-changed records; ordinary job advancement remains outside this adapter | `CosmicProfilePresentationServiceTest.partnerRefreshOnlySendsSkillRecordsThatDifferBetweenProfiles`; structural scope audit | Automated pass; live hitch comparison pending |
| A cross-job shared Solo buff registers its absent source skill client-side before `GIVE_BUFF`, retains it without switch churn, removes it on release, and never mutates canonical skills | `CosmicProfilePresentationServiceTest.soloSharedBuffSkillIsRegisteredOnceAndRemovedWhenSessionEnds`, release delegation assertion | Automated packet/state pass; Shadow Partner client-crash retest pending |
| Prepared presentation snapshots do not grow across sessions | `CosmicProfilePresentationServiceTest`, successful-release and failed-activation eviction tests | Automated pass |
| Presentation failure keeps the server binding authoritative | `ProfileTransitionCoordinatorTest.presentationFailureKeepsCommittedServerBinding` | Fault-injection pass |
| Disconnect during presentation waits for the transition and restores canonical ownership | `PartnerRecoveryServiceTest.disconnectDuringPresentationRefreshWaitsThenRestoresCanonicalOwnership` | Fault-injection pass |
| Save and Agent teardown failures retain leases/runtime for retry | `AdventurerPartnerServiceTest` release retry cases | Fault-injection pass |
| Channel, Cash Shop, and MTS transitions abort if canonical recovery fails | `PartnerRecoveryServiceTest`, handler integration paths | Automated pass |
| Restart never replays swapped bindings and closes open journals canonical | `JdbcAdventurerPartnerRepository.recoverOpenSessions`; disposable MySQL verification | Automated database/static pass |
| Repeated switching does not drift bindings, leases, journal generation, Agent barrier, or cache refresh | 1,000-transition Double coordinator soak and 1,000-reversal domain soak | Automated soak pass |
| Disabled-by-default and startup state is logged | `config.yaml`, `AdventurerPartnerConfigTest`, `Server.init` | Automated/static pass |

## Real-client acceptance still required

Run from:

```powershell
Set-Location 'C:\Users\user\Documents\Cosmic Agents-adventurer-partner-program'
.\launch.bat
```

Use two same-account characters with different jobs, appearances, equipment,
inventory density, skills, quests, Monster Book progress, pets, buffs, diseases,
and cooldowns. Capture server logs and client video/screenshots for every item.

1. Register through Agent E and verify every roster IGN, level, job, and rejection reason; then verify the compact header shows partner details, status, and mode.
2. Change directly to Solo Tag, verify it is prepared automatically, and switch with each configured beginner-family Nimble Feet skill.
3. Verify stats, look, equipment, every inventory tab, skills, keymap, macros, quickslots, quests, Monster Book, pets, effects, mesos, and item use.
   Confirm quest and Monster Book gameplay ownership follows the profile. Server-side replay notifications and Quest Helper activation must not occur; record any stock-client auto-start/completion alert caused by the swapped level/job/inventory. Their stock-client panels may require relogging to reconstruct silently.
4. Verify no reconnect, map load, camera jump, actor movement, or ordinary Nimble Feet buff.
5. End Solo while normal and swapped; reload both characters and compare canonical state.
6. Change directly to Double mode, accept the invite prompt, and verify the partner's own IGN/look and Follow behavior.
7. Switch while actors are near and far apart in the same map; verify both looks and Agent combat behavior after each switch.
   Confirm both actors broadcast the job-change switch effect, and verify a different-map attempt is rejected with the distance message.
8. Observe repeated switches from a second real client and verify public appearance, pets, buffs, and debuffs.
9. Exercise empty, typical, nearly full, and full inventories; normal/cash equipment; and melee/ranged/ammo profiles.
10. Exercise logout, channel change, Cash Shop, MTS, death, map change, and Agent removal while normal and swapped.
11. Disconnect before exchange, during inventory refresh, and after exchange; reload both canonical owners.
12. Force a save failure and Agent runtime exception in a controlled test environment; retry recovery and verify no loss or duplication.
13. Stop the server with an active swapped session, restart it, and verify canonical owners plus a recovered closed journal.
14. Run repeated invite/switch/release/re-invite cycles while monitoring map objects, scheduled tasks, heap, and logs.
15. Record switch pause, lock, cache, refresh, packet-count/byte, frame-hitch, and heap/task-growth measurements.
16. Configure a `SELF_BUFF_BOND` medal, buy it separately on both characters, and test neither/left-only/right-only/both eligibility in both enabled modes. Verify carried-but-unequipped is inactive. Exercise the partner-level caps with Magic Guard, Shadow Partner, weapon boosters, and overlapping party buffs; verify the strongest stat survives and remaining duration is preserved. Test each switch-skill medal, reward medal, duplicate-purchase rejection, and immediate effect removal after unequipping.

The feature does not satisfy the full Definition of Done until all 16 live items
pass. Stock v83 actor IGN caching remains the documented client limitation;
canonical names are never mutated.
