package server.partner;

import client.Character;
import client.Client;
import client.BuffStat;
import client.Skill;
import client.profile.CharacterProfileRepository;
import config.AdventurerPartnerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.packet.Packet;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTransitionBarrierState;
import server.StatEffect;
import tools.PacketCreator;
import tools.Pair;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class AdventurerPartnerServiceTest {
    private AdventurerPartnerConfig config;
    private AdventurerPartnerRepository repository;
    private CharacterProfileRepository profiles;
    private ProfileLeaseRegistry leases;
    private PartnerRuntimeRegistry runtimes;
    private PartnerRosterQueryService.RuntimeAvailability availability;
    private PartnerAgentLifecycleBridge agents;
    private PartnerTriggerPolicy triggerPolicy;
    private ProfileTransitionCoordinator transitions;
    private PartnerSessionSkillService sessionSkills;
    private AdventurerPartnerService service;
    private Character player;
    private Character partner;
    private PartnerLink link;
    private PartnerSessionRecord journal;

    @BeforeEach
    void setUp() {
        config = new AdventurerPartnerConfig();
        config.ENABLED = true;
        repository = mock(AdventurerPartnerRepository.class);
        profiles = mock(CharacterProfileRepository.class);
        leases = new ProfileLeaseRegistry();
        runtimes = new PartnerRuntimeRegistry();
        availability = mock(PartnerRosterQueryService.RuntimeAvailability.class);
        agents = mock(PartnerAgentLifecycleBridge.class);
        triggerPolicy = mock(PartnerTriggerPolicy.class);
        transitions = mock(ProfileTransitionCoordinator.class);
        sessionSkills = mock(PartnerSessionSkillService.class);
        service = new AdventurerPartnerService(
                config,
                repository,
                profiles,
                leases,
                runtimes,
                new PartnerRosterQueryService(repository, availability),
                agents,
                triggerPolicy,
                transitions,
                sessionSkills);

        player = character(10, 10, 1, 0);
        partner = character(20, 20, 1, 0);
        link = new PartnerLink(
                5L, 1, 0, 10, 20, PartnerMode.DOUBLE_PARTNER,
                true, Instant.now(), Instant.now());
        journal = new PartnerSessionRecord(
                7L, 5L, 10, 20, PartnerMode.SOLO_TAG,
                ProfileOrientation.CANONICAL, 0L, PartnerLifecycleStatus.ACTIVATING,
                Instant.now(), Instant.now(), null, null);

        when(repository.findActiveLinkForCharacter(10)).thenReturn(Optional.of(link));
        when(repository.findActiveLinkForCharacter(20)).thenReturn(Optional.of(link));
        when(repository.findCharacter(20)).thenReturn(Optional.of(
                new PartnerRosterCandidate(20, 1, 0, "Yoona", 17, 200)));
        when(repository.createSession(5L, 10, 20, PartnerMode.SOLO_TAG)).thenReturn(journal);
        when(availability.isOnline(20)).thenReturn(false);
        when(availability.isLeased(20)).thenReturn(false);
        when(availability.canLoadCanonicalProfile(20)).thenReturn(true);
    }

    @Test
    void soloActivationLeasesBothProfilesAndReleaseClosesCanonicalSession() throws Exception {
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);

        ActivePartnerSession active = service.activate(player, PartnerMode.SOLO_TAG);

        assertEquals(PartnerMode.SOLO_TAG, active.runtime().mode());
        assertTrue(leases.holds(7L, 10));
        assertTrue(leases.holds(7L, 20));
        assertEquals(ProfileLeaseRegistry.DETACHED_ACTOR,
                leases.leaseForProfile(20).orElseThrow().actorCharacterId());
        assertTrue(runtimes.findByHumanActorId(10).isPresent());
        verify(transitions).prepareSessionSkills(
                7L, PartnerMode.SOLO_TAG, player, partner);
        assertTriggerCooldownsReset();

        service.release(player, "test release");

        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        assertTrue(runtimes.findByHumanActorId(10).isEmpty());
        assertEquals(PartnerLifecycleStatus.CLOSED, active.runtime().status());
        verify(profiles).saveCanonical(player);
        verify(profiles).saveCanonical(partner);
        verify(profiles).storeTransientStateForLogout(partner);
        verify(repository).closeSession(
                7L, ProfileOrientation.CANONICAL, 1L,
                PartnerLifecycleStatus.CLOSED, "test release");
        verify(sessionSkills).restore(7L, player, partner);
        verify(transitions).discardPreparedProfiles(player, partner);
    }

    @Test
    void assigningSpToSelfBuffUpdatesOtherSoloProfile() throws Exception {
        config.SOLO_TAG_BUFF_SHARING_ENABLED = true;
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        Skill shadowPartner = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(shadowPartner.getId()).thenReturn(4111002);
        when(shadowPartner.getMaxLevel()).thenReturn(30);
        when(shadowPartner.getEffect(11)).thenReturn(effect);
        when(shadowPartner.getAction()).thenReturn(true);
        when(effect.isSkill()).thenReturn(true);
        when(effect.isOverTime()).thenReturn(true);
        when(effect.getDuration()).thenReturn(180_000);
        when(effect.getStatups()).thenReturn(List.of(
                new Pair<>(BuffStat.SHADOWPARTNER, 50)));
        when(effect.isPartyBuff()).thenReturn(false);
        when(player.getSkillLevel(shadowPartner)).thenReturn((byte) 11);
        when(player.getMasterLevel(shadowPartner)).thenReturn(0);
        when(player.getSkillExpiration(shadowPartner)).thenReturn(-1L);
        service.activate(player, PartnerMode.SOLO_TAG);

        service.onSkillPointAssigned(player, shadowPartner);

        ArgumentCaptor<SoloTagBuffSharingService.SkillGrant> grant =
                ArgumentCaptor.forClass(SoloTagBuffSharingService.SkillGrant.class);
        verify(sessionSkills).grant(org.mockito.ArgumentMatchers.eq(7L), grant.capture());
        assertEquals(partner, grant.getValue().recipient());
        assertEquals(4111002, grant.getValue().skill().getId());
        assertEquals(11, grant.getValue().level());
    }

    @Test
    void doubleActivationPausesSpawnedAgentUntilRuntimeAndLeaseAreReady() {
        PartnerSessionRecord doubleJournal = new PartnerSessionRecord(
                8L, 5L, 10, 20, PartnerMode.DOUBLE_PARTNER,
                ProfileOrientation.CANONICAL, 0L, PartnerLifecycleStatus.ACTIVATING,
                Instant.now(), Instant.now(), null, null);
        when(repository.createSession(5L, 10, 20, PartnerMode.DOUBLE_PARTNER))
                .thenReturn(doubleJournal);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentTransitionBarrierState barrier = new AgentTransitionBarrierState();
        when(entry.transitionBarrierState()).thenReturn(barrier);
        when(agents.spawnFollowing(player, 20, "Yoona"))
                .thenReturn(new PartnerAgentLifecycleBridge.SpawnedPartner(partner, entry));

        ActivePartnerSession active = service.activate(player, PartnerMode.DOUBLE_PARTNER);

        assertEquals(PartnerMode.DOUBLE_PARTNER, active.runtime().mode());
        assertEquals(20, leases.leaseForProfile(20).orElseThrow().actorCharacterId());
        assertFalse(barrier.isPaused());
        assertTriggerCooldownsReset();
        verify(agents).spawnFollowing(player, 20, "Yoona");
        verify(profiles).restoreTransientState(partner);
    }

    @Test
    void agentTeardownFailureRetainsSessionAndRetriesWithoutRepeatingDurableCloseOrSaves() {
        PartnerSessionRecord doubleJournal = new PartnerSessionRecord(
                8L, 5L, 10, 20, PartnerMode.DOUBLE_PARTNER,
                ProfileOrientation.CANONICAL, 0L, PartnerLifecycleStatus.ACTIVATING,
                Instant.now(), Instant.now(), null, null);
        when(repository.createSession(5L, 10, 20, PartnerMode.DOUBLE_PARTNER))
                .thenReturn(doubleJournal);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentTransitionBarrierState barrier = new AgentTransitionBarrierState();
        when(entry.transitionBarrierState()).thenReturn(barrier);
        when(agents.spawnFollowing(player, 20, "Yoona"))
                .thenReturn(new PartnerAgentLifecycleBridge.SpawnedPartner(partner, entry));
        doThrow(new IllegalStateException("teardown failed"))
                .doNothing()
                .when(agents).release(any());
        ActivePartnerSession active = service.activate(player, PartnerMode.DOUBLE_PARTNER);

        assertThrows(IllegalStateException.class,
                () -> service.release(player, "fault injection"));

        assertTrue(active.isJournalClosed());
        assertEquals(PartnerLifecycleStatus.ACTIVE, active.runtime().status());
        assertTrue(leases.isLeased(10));
        assertTrue(leases.isLeased(20));
        assertFalse(barrier.isPaused());

        service.release(player, "teardown retry");

        assertEquals(PartnerLifecycleStatus.CLOSED, active.runtime().status());
        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        verify(profiles, times(1)).saveCanonical(player);
        verify(profiles, times(1)).saveCanonical(partner);
        verify(repository, times(1)).closeSession(
                8L, ProfileOrientation.CANONICAL, 1L,
                PartnerLifecycleStatus.CLOSED, "fault injection");
    }

    @Test
    void failedCanonicalLoadClosesJournalAndReleasesLeases() throws Exception {
        when(profiles.loadDetached(20, 0, 1)).thenThrow(new SQLException("load failed"));

        assertThrows(IllegalStateException.class,
                () -> service.activate(player, PartnerMode.SOLO_TAG));

        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        verify(repository).closeSession(
                7L, ProfileOrientation.CANONICAL, 0L,
                PartnerLifecycleStatus.FAILED, "Canonical profile load failed");
        verify(transitions).discardPreparedProfiles(player, null);
    }

    @Test
    void failureAfterPresentationPrecomputeDiscardsPreparedSnapshots() throws Exception {
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        doThrow(new IllegalStateException("precompute failed"))
                .when(transitions).prepareProfiles(player, partner);

        assertThrows(IllegalStateException.class,
                () -> service.activate(player, PartnerMode.SOLO_TAG));

        verify(transitions).discardPreparedProfiles(player, partner);
        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
    }

    @Test
    void onlinePartnerIsRejectedBeforeSessionCreationOrLoading() {
        when(availability.isOnline(20)).thenReturn(true);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.activate(player, PartnerMode.SOLO_TAG));

        assertTrue(failure.getMessage().contains("adventuring independently"));
    }

    @Test
    void saveFailureRetainsRuntimeAndLeasesUntilAReleaseRetrySucceeds() throws Exception {
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        ActivePartnerSession active = service.activate(player, PartnerMode.SOLO_TAG);
        doThrow(new IllegalStateException("save failed"))
                .doNothing()
                .when(profiles).saveCanonical(player);

        assertThrows(IllegalStateException.class,
                () -> service.release(player, "fault injection"));

        assertTrue(leases.isLeased(10));
        assertTrue(leases.isLeased(20));
        assertTrue(runtimes.findByHumanActorId(10).isPresent());
        assertEquals(PartnerLifecycleStatus.ACTIVE, active.runtime().status());
        verify(repository).updateSession(
                7L, ProfileOrientation.CANONICAL, 1L,
                PartnerLifecycleStatus.ACTIVE, "Canonical save failed: save failed");

        service.release(player, "retry after fault injection");

        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        assertTrue(runtimes.findByHumanActorId(10).isEmpty());
        assertEquals(PartnerLifecycleStatus.CLOSED, active.runtime().status());
        verify(repository).closeSession(
                7L, ProfileOrientation.CANONICAL, 2L,
                PartnerLifecycleStatus.CLOSED, "retry after fault injection");
    }

    @Test
    void simultaneousSwitchRequestsExecuteOnlyOneTransition() throws Exception {
        config.SWITCH_COOLDOWN_MS = 0L;
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        when(triggerPolicy.validate(any(), any()))
                .thenReturn(new PartnerTriggerPolicy.Result(true, null));
        CountDownLatch transitionEntered = new CountDownLatch(1);
        CountDownLatch allowTransition = new CountDownLatch(1);
        when(transitions.transition(any(), any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    transitionEntered.countDown();
                    assertTrue(allowTransition.await(10, TimeUnit.SECONDS));
                    return new ProfileTransitionCoordinator.TransitionResult(
                            true, true, 1L, 1L, 0L,
                            ProfilePresentationService.RefreshMetrics.none(), null);
                });
        service.activate(player, PartnerMode.SOLO_TAG);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<AdventurerPartnerService.TriggerResult> first = executor.submit(
                    () -> service.handleSwitchTrigger(player, config.TRIGGER_SKILL_IDS.getFirst()));
            assertTrue(transitionEntered.await(10, TimeUnit.SECONDS));

            AdventurerPartnerService.TriggerResult simultaneous =
                    service.handleSwitchTrigger(player, config.TRIGGER_SKILL_IDS.getFirst());

            assertTrue(simultaneous.handled());
            assertFalse(simultaneous.switched());
            assertTrue(simultaneous.message().contains("lifecycle operation"));
            allowTransition.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS).switched());
            verify(transitions, times(1)).transition(any(), any(), any(), any(), anyLong());
        } finally {
            allowTransition.countDown();
            executor.shutdownNow();
            service.release(player, "simultaneous trigger test cleanup");
        }
    }

    @Test
    void registrationRequiresACompleteNonConsumingCanonicalDryLoad() throws Exception {
        PartnerRosterCandidate candidate = new PartnerRosterCandidate(
                20, 1, 0, "Yoona", 17, 200);
        when(repository.findActiveLinkForCharacter(10)).thenReturn(Optional.empty());
        when(repository.findActiveLinkForCharacter(20)).thenReturn(Optional.empty());
        when(repository.findRosterCandidates(1, 0, 10)).thenReturn(List.of(candidate));
        when(profiles.loadDetachedForValidation(20, 0, 1)).thenReturn(partner);
        when(repository.registerLink(10, 20, PartnerMode.SOLO_TAG)).thenReturn(link);

        PartnerLink registered = service.register(player, 20);

        assertEquals(link, registered);
        verify(profiles).loadDetachedForValidation(20, 0, 1);
        verify(repository).registerLink(10, 20, PartnerMode.SOLO_TAG);
    }

    @Test
    void registrationDoesNotCreateLinkWhenCanonicalDryLoadFails() throws Exception {
        PartnerRosterCandidate candidate = new PartnerRosterCandidate(
                20, 1, 0, "Yoona", 17, 200);
        when(repository.findActiveLinkForCharacter(10)).thenReturn(Optional.empty());
        when(repository.findActiveLinkForCharacter(20)).thenReturn(Optional.empty());
        when(repository.findRosterCandidates(1, 0, 10)).thenReturn(List.of(candidate));
        when(profiles.loadDetachedForValidation(20, 0, 1))
                .thenThrow(new SQLException("corrupt profile"));

        assertThrows(IllegalStateException.class, () -> service.register(player, 20));

        verify(repository, never()).registerLink(10, 20, PartnerMode.SOLO_TAG);
    }

    @Test
    void rosterRejectsAnOtherwiseEligibleCharacterWhoseCanonicalDryLoadFails() throws Exception {
        PartnerRosterCandidate candidate = new PartnerRosterCandidate(
                20, 1, 0, "Yoona", 17, 200);
        when(repository.findActiveLinkForCharacter(10)).thenReturn(Optional.empty());
        when(repository.findActiveLinkForCharacter(20)).thenReturn(Optional.empty());
        when(repository.findRosterCandidates(1, 0, 10)).thenReturn(List.of(candidate));
        when(profiles.loadDetachedForValidation(20, 0, 1))
                .thenThrow(new SQLException("corrupt profile"));

        List<PartnerRosterEntry> roster = service.roster(player);

        assertEquals(1, roster.size());
        assertFalse(roster.getFirst().eligible());
        assertTrue(roster.getFirst().rejectionReason().contains("could not be loaded"));
    }

    @Test
    void changingActiveDoubleModeReleasesAgentAndPreparesSoloTag() throws Exception {
        PartnerSessionRecord doubleJournal = new PartnerSessionRecord(
                8L, 5L, 10, 20, PartnerMode.DOUBLE_PARTNER,
                ProfileOrientation.CANONICAL, 0L, PartnerLifecycleStatus.ACTIVATING,
                Instant.now(), Instant.now(), null, null);
        when(repository.createSession(5L, 10, 20, PartnerMode.DOUBLE_PARTNER))
                .thenReturn(doubleJournal);
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        when(entry.transitionBarrierState()).thenReturn(new AgentTransitionBarrierState());
        PartnerAgentLifecycleBridge.SpawnedPartner spawned =
                new PartnerAgentLifecycleBridge.SpawnedPartner(partner, entry);
        when(agents.spawnFollowing(player, 20, "Yoona")).thenReturn(spawned);
        service.activate(player, PartnerMode.DOUBLE_PARTNER);

        ActivePartnerSession solo = service.changeToSoloTag(player);

        assertEquals(PartnerMode.SOLO_TAG, solo.runtime().mode());
        assertTrue(runtimes.findByHumanActorId(10).isPresent());
        verify(agents).release(spawned);
        verify(repository).updatePreferredMode(5L, PartnerMode.SOLO_TAG);
        verify(profiles).loadDetached(20, 0, 1);
    }

    @Test
    void changingActiveSoloModeReleasesDormantProfileBeforeSelectingDouble() throws Exception {
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        service.activate(player, PartnerMode.SOLO_TAG);

        service.changeToDoublePartner(player);

        assertTrue(runtimes.findByHumanActorId(10).isEmpty());
        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        verify(repository).updatePreferredMode(5L, PartnerMode.DOUBLE_PARTNER);
        verify(profiles).saveCanonical(player);
        verify(profiles).saveCanonical(partner);
    }

    @Test
    void releaseResetCleansExactOrphanAgentAndScopedOpenSessions() {
        when(agents.hasPartnerAgent(10, 20)).thenReturn(true);
        when(agents.releasePartnerAgent(10, 20)).thenReturn(true);
        when(repository.recoverOpenSessionsForLink(5L, "Agent E reset")).thenReturn(1);
        leases.acquire(99L, java.util.Map.of(10, 10, 20, 20));

        AdventurerPartnerService.ReleaseResult result =
                service.releaseOrReset(player, "Agent E reset");

        assertTrue(result.orphanAgentReleased());
        assertEquals(1, result.recoveredSessions());
        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        verify(agents).releasePartnerAgent(10, 20);
        verify(repository).recoverOpenSessionsForLink(5L, "Agent E reset");
    }

    @Test
    void releaseResetDoesNotDisconnectIndependentlyOnlinePartner() {
        when(availability.isOnline(20)).thenReturn(true);

        AdventurerPartnerService.ReleaseResult result =
                service.releaseOrReset(player, "Agent E reset");

        assertTrue(result.partnerOnlineIndependently());
        assertFalse(result.changedRuntimeState());
        verify(agents, never()).release(any());
        verify(agents).releasePartnerAgent(10, 20);
    }

    @Test
    void unregisterAutomaticallyReleasesActiveSessionBeforeDisablingLink() throws Exception {
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        service.activate(player, PartnerMode.SOLO_TAG);

        service.unregister(player);

        assertTrue(runtimes.findByHumanActorId(10).isEmpty());
        verify(repository).disableLink(5L);
        verify(profiles).saveCanonical(player);
        verify(profiles).saveCanonical(partner);
    }

    @Test
    void overviewDistinguishesIndependentLoginFromRecoveryRequired() {
        when(availability.isOnline(20)).thenReturn(true);
        assertEquals(
                AdventurerPartnerService.PartnerPresence.ONLINE_INDEPENDENTLY,
                service.overview(player).orElseThrow().presence());

        when(agents.hasPartnerAgent(10, 20)).thenReturn(true);
        assertEquals(
                AdventurerPartnerService.PartnerPresence.RECOVERY_REQUIRED,
                service.overview(player).orElseThrow().presence());
    }

    private static Character character(int actorId, int profileOwnerId, int accountId, int world) {
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        when(client.getChannel()).thenReturn(1);
        when(character.getId()).thenReturn(actorId);
        when(character.getProfileOwnerCharacterId()).thenReturn(profileOwnerId);
        when(character.getAccountID()).thenReturn(accountId);
        when(character.getWorld()).thenReturn(world);
        when(character.getClient()).thenReturn(client);
        when(character.isProfileTransitioning()).thenReturn(false);
        when(character.isProfileSaving()).thenReturn(false);
        return character;
    }

    private void assertTriggerCooldownsReset() {
        for (int skillId : config.TRIGGER_SKILL_IDS) {
            verify(player).removeCooldown(skillId);
        }
        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(player, times(config.TRIGGER_SKILL_IDS.size())).sendPacket(packets.capture());
        for (int skillId : config.TRIGGER_SKILL_IDS) {
            byte[] expected = PacketCreator.skillCooldown(skillId, 0).getBytes();
            assertTrue(packets.getAllValues().stream()
                    .anyMatch(packet -> java.util.Arrays.equals(expected, packet.getBytes())));
        }
    }
}
