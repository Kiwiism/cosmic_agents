package server.partner;

import client.Character;
import client.Client;
import client.profile.CharacterProfileRepository;
import config.AdventurerPartnerConfig;
import constants.skills.Beginner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PartnerRecoveryServiceTest {
    @Test
    void disconnectRoutesCanonicalRecoveryThroughLifecycleService() {
        AdventurerPartnerService service = mock(AdventurerPartnerService.class);
        ActivePartnerSession active = activeSession();
        Character actor = active.humanActor();
        when(service.activeSessionForActor(10)).thenReturn(Optional.of(active));
        PartnerRecoveryService recovery = new PartnerRecoveryService(
                service, new ProfileLeaseRegistry());

        recovery.onDisconnect(actor, false);

        verify(service).releaseActive(active, "Player disconnect recovery");
    }

    @Test
    void diagnosticsExposeBindingGenerationWithoutPrivateProfileData() {
        AdventurerPartnerService service = mock(AdventurerPartnerService.class);
        ActivePartnerSession active = activeSession();
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        assertTrue(leases.acquire(7L, active.runtime().profileToActorLeases()).acquired());
        when(service.activeSessionForActor(10)).thenReturn(Optional.of(active));
        PartnerRecoveryService recovery = new PartnerRecoveryService(service, leases);

        String diagnostics = recovery.diagnose(active.humanActor());

        assertTrue(diagnostics.contains("session=7"));
        assertTrue(diagnostics.contains("orientation=CANONICAL"));
        assertTrue(diagnostics.contains("leasesValid=true/true"));
    }

    @Test
    void disconnectDuringSwapStillQueuesRecoveryBehindTheLifecycleOperation() {
        AdventurerPartnerService service = mock(AdventurerPartnerService.class);
        ActivePartnerSession active = activeSession();
        active.runtime().beginSwap(0L);
        when(service.activeSessionForActor(10)).thenReturn(Optional.of(active));
        PartnerRecoveryService recovery = new PartnerRecoveryService(
                service, new ProfileLeaseRegistry());

        recovery.onDisconnect(active.humanActor(), false);

        verify(service).releaseActive(active, "Player disconnect recovery");
    }

    @Test
    void failedWorldExitRecoveryLeavesCallerInWorld() {
        AdventurerPartnerService service = mock(AdventurerPartnerService.class);
        ActivePartnerSession active = activeSession();
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        assertTrue(leases.acquire(7L, active.runtime().profileToActorLeases()).acquired());
        when(service.activeSessionForActor(10)).thenReturn(Optional.of(active));
        doThrow(new IllegalStateException("database unavailable"))
                .when(service).releaseActive(active, "Channel transition recovery");
        PartnerRecoveryService recovery = new PartnerRecoveryService(service, leases);

        boolean recovered = recovery.recoverBeforeWorldExit(
                active.humanActor(), "Channel transition recovery");

        assertTrue(!recovered);
        assertTrue(leases.isLeased(10));
        assertEquals(PartnerLifecycleStatus.ACTIVE, active.runtime().status());
    }

    @Test
    void disconnectDuringPresentationRefreshWaitsThenRestoresCanonicalOwnership() throws Exception {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.enabled = true;
        config.switchCooldownMs = 0L;
        AdventurerPartnerRepository repository = mock(AdventurerPartnerRepository.class);
        CharacterProfileRepository profiles = mock(CharacterProfileRepository.class);
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerRuntimeRegistry runtimes = new PartnerRuntimeRegistry();
        PartnerRosterQueryService.RuntimeAvailability availability =
                mock(PartnerRosterQueryService.RuntimeAvailability.class);
        PartnerAgentLifecycleBridge agents = mock(PartnerAgentLifecycleBridge.class);
        PartnerTriggerPolicy triggerPolicy = mock(PartnerTriggerPolicy.class);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        AtomicInteger humanOwner = new AtomicInteger(10);
        AtomicInteger dormantOwner = new AtomicInteger(20);
        Character human = dynamicCharacter(10, humanOwner);
        Character dormant = dynamicCharacter(20, dormantOwner);
        PartnerLink link = new PartnerLink(
                5L, 1, 0, 10, 20, PartnerMode.SOLO_TAG,
                true, Instant.now(), Instant.now());
        PartnerSessionRecord journal = new PartnerSessionRecord(
                7L, 5L, 10, 20, PartnerMode.SOLO_TAG,
                ProfileOrientation.CANONICAL, 0L, PartnerLifecycleStatus.ACTIVATING,
                Instant.now(), Instant.now(), null, null);
        when(repository.findActiveLinkForCharacter(10)).thenReturn(Optional.of(link));
        when(repository.findActiveLinkForCharacter(20)).thenReturn(Optional.of(link));
        when(repository.findCharacter(20)).thenReturn(Optional.of(
                new PartnerRosterCandidate(20, 1, 0, "Yoona", 17, 200)));
        when(repository.createSession(5L, 10, 20, PartnerMode.SOLO_TAG)).thenReturn(journal);
        when(profiles.loadDetached(20, 0, 1)).thenReturn(dormant);
        when(availability.isOnline(20)).thenReturn(false);
        when(availability.isLeased(20)).thenReturn(false);
        when(availability.canLoadCanonicalProfile(20)).thenReturn(true);
        when(triggerPolicy.validate(any(), any()))
                .thenReturn(new PartnerTriggerPolicy.Result(true, null));
        CountDownLatch inventoryRefreshEntered = new CountDownLatch(1);
        CountDownLatch finishInventoryRefresh = new CountDownLatch(1);
        AtomicInteger refreshCalls = new AtomicInteger();
        when(presentation.refresh(
                eq(human), eq(dormant), eq(PartnerMode.SOLO_TAG),
                any(Character.ProfileExchangeResult.class)))
                .thenAnswer(invocation -> {
                    if (refreshCalls.incrementAndGet() == 1) {
                        inventoryRefreshEntered.countDown();
                        assertTrue(finishInventoryRefresh.await(10, TimeUnit.SECONDS));
                    }
                    return new ProfilePresentationService.RefreshMetrics(4, 64L, 500L);
                });
        ProfileTransitionCoordinator transitions = new ProfileTransitionCoordinator(
                leases,
                presentation,
                new PartnerProfileCacheInvalidator(),
                (sessionId, orientation, generation, status, reason) -> { },
                (left, right) -> {
                    int previousHuman = humanOwner.get();
                    humanOwner.set(dormantOwner.get());
                    dormantOwner.set(previousHuman);
                    return new Character.ProfileExchangeResult(
                            humanOwner.get(), dormantOwner.get(), 1L, 1L, 100L);
                },
                ignored -> { });
        AdventurerPartnerService service = new AdventurerPartnerService(
                config,
                repository,
                profiles,
                leases,
                runtimes,
                new PartnerRosterQueryService(repository, availability),
                agents,
                triggerPolicy,
                transitions);
        PartnerRecoveryService recovery = new PartnerRecoveryService(service, leases);
        doAnswer(invocation -> {
            Character holder = invocation.getArgument(0);
            assertEquals(holder.getId(), holder.getProfileOwnerCharacterId());
            return null;
        }).when(profiles).saveCanonical(any(Character.class));
        ActivePartnerSession active = service.activate(human, PartnerMode.SOLO_TAG);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<AdventurerPartnerService.TriggerResult> switching = executor.submit(
                    () -> service.handleSwitchTrigger(human, Beginner.NIMBLE_FEET));
            assertTrue(inventoryRefreshEntered.await(10, TimeUnit.SECONDS));
            Future<?> disconnect = executor.submit(() -> recovery.onDisconnect(human, false));

            finishInventoryRefresh.countDown();

            assertTrue(switching.get(10, TimeUnit.SECONDS).switched());
            disconnect.get(10, TimeUnit.SECONDS);
            assertEquals(PartnerLifecycleStatus.CLOSED, active.runtime().status());
            assertEquals(10, humanOwner.get());
            assertEquals(20, dormantOwner.get());
            assertFalse(leases.isLeased(10));
            assertFalse(leases.isLeased(20));
            assertEquals(2, refreshCalls.get());
            verify(profiles).saveCanonical(human);
            verify(profiles).saveCanonical(dormant);
        } finally {
            finishInventoryRefresh.countDown();
            executor.shutdownNow();
        }
    }

    private static Character dynamicCharacter(int actorId, AtomicInteger profileOwnerId) {
        Character character = mock(Character.class);
        Client client = mock(Client.class);
        when(client.getChannel()).thenReturn(1);
        when(character.getId()).thenReturn(actorId);
        when(character.getProfileOwnerCharacterId()).thenAnswer(ignored -> profileOwnerId.get());
        when(character.getProfileBindingGeneration()).thenReturn(1L);
        when(character.getAccountID()).thenReturn(1);
        when(character.getWorld()).thenReturn(0);
        when(character.getClient()).thenReturn(client);
        when(character.profileTransitionBlockReason()).thenReturn(null);
        return character;
    }

    private static ActivePartnerSession activeSession() {
        Character human = mock(Character.class);
        Character partner = mock(Character.class);
        when(human.getId()).thenReturn(10);
        when(partner.getId()).thenReturn(20);
        when(human.getProfileOwnerCharacterId()).thenReturn(10);
        when(partner.getProfileOwnerCharacterId()).thenReturn(20);
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        runtime.activate();
        PartnerLink link = new PartnerLink(
                5L, 1, 0, 10, 20, PartnerMode.DOUBLE_PARTNER,
                true, java.time.Instant.now(), java.time.Instant.now());
        return new ActivePartnerSession(link, runtime, human, partner, null);
    }
}
