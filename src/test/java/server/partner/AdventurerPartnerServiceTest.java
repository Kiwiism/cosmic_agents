package server.partner;

import client.Character;
import client.Client;
import client.profile.CharacterProfileRepository;
import config.AdventurerPartnerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTransitionBarrierState;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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
    private ProfileTransitionCoordinator transitions;
    private AdventurerPartnerService service;
    private Character player;
    private Character partner;
    private PartnerLink link;
    private PartnerSessionRecord journal;

    @BeforeEach
    void setUp() {
        config = new AdventurerPartnerConfig();
        config.enabled = true;
        repository = mock(AdventurerPartnerRepository.class);
        profiles = mock(CharacterProfileRepository.class);
        leases = new ProfileLeaseRegistry();
        runtimes = new PartnerRuntimeRegistry();
        availability = mock(PartnerRosterQueryService.RuntimeAvailability.class);
        agents = mock(PartnerAgentLifecycleBridge.class);
        transitions = mock(ProfileTransitionCoordinator.class);
        service = new AdventurerPartnerService(
                config,
                repository,
                profiles,
                leases,
                runtimes,
                new PartnerRosterQueryService(repository, availability),
                agents,
                new PartnerTriggerPolicy(),
                transitions);

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

        service.release(player, "test release");

        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        assertTrue(runtimes.findByHumanActorId(10).isEmpty());
        assertEquals(PartnerLifecycleStatus.CLOSED, active.runtime().status());
        verify(profiles).saveCanonical(player);
        verify(profiles).saveCanonical(partner);
        verify(repository).closeSession(
                7L, ProfileOrientation.CANONICAL, 1L,
                PartnerLifecycleStatus.CLOSED, "test release");
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
        when(agents.spawnFollowing(player, "Yoona"))
                .thenReturn(new PartnerAgentLifecycleBridge.SpawnedPartner(partner, entry));

        ActivePartnerSession active = service.activate(player, PartnerMode.DOUBLE_PARTNER);

        assertEquals(PartnerMode.DOUBLE_PARTNER, active.runtime().mode());
        assertEquals(20, leases.leaseForProfile(20).orElseThrow().actorCharacterId());
        assertFalse(barrier.isPaused());
        verify(agents).spawnFollowing(player, "Yoona");
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
    }

    @Test
    void onlinePartnerIsRejectedBeforeSessionCreationOrLoading() {
        when(availability.isOnline(20)).thenReturn(true);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> service.activate(player, PartnerMode.SOLO_TAG));

        assertTrue(failure.getMessage().contains("online"));
    }

    @Test
    void saveFailureStillReleasesLeasesAndClosesFailedSession() throws Exception {
        when(profiles.loadDetached(20, 0, 1)).thenReturn(partner);
        ActivePartnerSession active = service.activate(player, PartnerMode.SOLO_TAG);
        doThrow(new IllegalStateException("save failed")).when(profiles).saveCanonical(player);

        assertThrows(IllegalStateException.class,
                () -> service.release(player, "fault injection"));

        assertFalse(leases.isLeased(10));
        assertFalse(leases.isLeased(20));
        assertTrue(runtimes.findByHumanActorId(10).isEmpty());
        assertEquals(PartnerLifecycleStatus.FAILED, active.runtime().status());
        verify(repository).closeSession(
                7L, ProfileOrientation.CANONICAL, 1L,
                PartnerLifecycleStatus.FAILED, "fault injection: save failed");
    }

    @Test
    void registrationRequiresACompleteNonConsumingCanonicalDryLoad() throws Exception {
        PartnerRosterCandidate candidate = new PartnerRosterCandidate(
                20, 1, 0, "Yoona", 17, 200);
        when(repository.findActiveLinkForCharacter(10)).thenReturn(Optional.empty());
        when(repository.findActiveLinkForCharacter(20)).thenReturn(Optional.empty());
        when(repository.findRosterCandidates(1, 0, 10)).thenReturn(List.of(candidate));
        when(profiles.loadDetachedForValidation(20, 0, 1)).thenReturn(partner);
        when(repository.registerLink(10, 20, PartnerMode.DOUBLE_PARTNER)).thenReturn(link);

        PartnerLink registered = service.register(player, 20);

        assertEquals(link, registered);
        verify(profiles).loadDetachedForValidation(20, 0, 1);
        verify(repository).registerLink(10, 20, PartnerMode.DOUBLE_PARTNER);
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

        verify(repository, never()).registerLink(10, 20, PartnerMode.DOUBLE_PARTNER);
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
}
