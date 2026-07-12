package server.partner;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentTransitionBarrierState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class ProfileTransitionCoordinatorTest {
    @Test
    void soloTransitionCommitsBindingsLeasesAndPresentation() {
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerSessionRuntime session = soloSession();
        assertTrue(leases.acquire(7L, Map.of(10, 10, 20, 0)).acquired());
        Character human = characterWithOwner(10);
        Character dormant = characterWithOwner(20);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        Character.ProfileExchangeResult exchange = new Character.ProfileExchangeResult(20, 10, 1, 1, 500);
        when(presentation.refresh(human, dormant, PartnerMode.SOLO_TAG, exchange))
                .thenReturn(new ProfilePresentationService.RefreshMetrics(8, 128, 900));
        List<JournalEvent> journal = new ArrayList<>();
        ProfileTransitionCoordinator coordinator = coordinator(
                leases, presentation, journal, (left, right) -> exchange);

        ProfileTransitionCoordinator.TransitionResult result = coordinator.transition(
                session, human, dormant, null, 0L);

        assertTrue(result.committed());
        assertTrue(result.presentationComplete());
        assertEquals(ProfileOrientation.SWAPPED, session.bindings().orientation());
        assertEquals(10, leases.leaseForProfile(20).orElseThrow().actorCharacterId());
        assertEquals(0, leases.leaseForProfile(10).orElseThrow().actorCharacterId());
        assertEquals(PartnerLifecycleStatus.ACTIVE, journal.getLast().status());
        verify(human).resumeProfileRuntimeTasks();
        verify(human).suspendProfileRuntimeTasks();
        verify(dormant, times(2)).suspendProfileRuntimeTasks();
        verify(human).enterProfileTransitionWindow();
        verify(human).exitProfileTransitionWindow();
        verify(dormant).enterProfileTransitionWindow();
        verify(dormant).exitProfileTransitionWindow();
        verify(human).rebuildDerivedProfileStats();
        verify(dormant).rebuildDerivedProfileStats();
    }

    @Test
    void missingLeaseRejectsBeforeProfileExchange() {
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerSessionRuntime session = soloSession();
        Character human = characterWithOwner(10);
        Character dormant = characterWithOwner(20);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        List<JournalEvent> journal = new ArrayList<>();
        ProfileTransitionCoordinator.ProfileExchangeOperation exchange = mock(
                ProfileTransitionCoordinator.ProfileExchangeOperation.class);
        ProfileTransitionCoordinator coordinator = coordinator(leases, presentation, journal, exchange);

        ProfileTransitionCoordinator.TransitionResult result = coordinator.transition(
                session, human, dormant, null, 0L);

        assertFalse(result.committed());
        assertTrue(result.reason().contains("lease"));
        assertEquals(PartnerLifecycleStatus.ACTIVE, session.status());
        assertEquals(ProfileOrientation.CANONICAL, session.bindings().orientation());
        verify(exchange, never()).exchange(human, dormant);
        verify(presentation, never()).refresh(
                human, dormant, PartnerMode.SOLO_TAG, null);
    }

    @Test
    void presentationFailureKeepsCommittedServerBinding() {
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerSessionRuntime session = soloSession();
        assertTrue(leases.acquire(7L, Map.of(10, 10, 20, 0)).acquired());
        Character human = characterWithOwner(10);
        Character dormant = characterWithOwner(20);
        Character.ProfileExchangeResult exchange = new Character.ProfileExchangeResult(20, 10, 1, 1, 500);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        when(presentation.refresh(human, dormant, PartnerMode.SOLO_TAG, exchange))
                .thenThrow(new IllegalStateException("client refresh unavailable"));
        List<JournalEvent> journal = new ArrayList<>();
        ProfileTransitionCoordinator coordinator = coordinator(
                leases, presentation, journal, (left, right) -> exchange);

        ProfileTransitionCoordinator.TransitionResult result = coordinator.transition(
                session, human, dormant, null, 0L);

        assertTrue(result.committed());
        assertFalse(result.presentationComplete());
        assertEquals(ProfileOrientation.SWAPPED, session.bindings().orientation());
        assertTrue(journal.getLast().reason().contains("client refresh unavailable"));
    }

    @Test
    void failureAfterLiveBindingsChangedRecoversForwardAndCommitsAuthoritativeOrientation() {
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerSessionRuntime session = soloSession();
        assertTrue(leases.acquire(7L, Map.of(10, 10, 20, 0)).acquired());
        AtomicInteger humanOwner = new AtomicInteger(10);
        AtomicInteger dormantOwner = new AtomicInteger(20);
        Character human = characterWithDynamicOwner(humanOwner);
        Character dormant = characterWithDynamicOwner(dormantOwner);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        when(presentation.refresh(
                org.mockito.ArgumentMatchers.eq(human),
                org.mockito.ArgumentMatchers.eq(dormant),
                org.mockito.ArgumentMatchers.eq(PartnerMode.SOLO_TAG),
                org.mockito.ArgumentMatchers.any(Character.ProfileExchangeResult.class)))
                .thenReturn(new ProfilePresentationService.RefreshMetrics(5, 80L, 400L));
        List<JournalEvent> journal = new ArrayList<>();
        ProfileTransitionCoordinator coordinator = coordinator(
                leases, presentation, journal, (left, right) -> {
                    humanOwner.set(20);
                    dormantOwner.set(10);
                    throw new IllegalStateException("derived stat rebuild failed");
                });

        ProfileTransitionCoordinator.TransitionResult result = coordinator.transition(
                session, human, dormant, null, 0L);

        assertTrue(result.committed());
        assertTrue(result.presentationComplete());
        assertEquals(ProfileOrientation.SWAPPED, session.bindings().orientation());
        assertEquals(10, leases.leaseForProfile(20).orElseThrow().actorCharacterId());
        assertEquals(0, leases.leaseForProfile(10).orElseThrow().actorCharacterId());
        verify(human).rebuildDerivedProfileStats();
        verify(dormant).rebuildDerivedProfileStats();
    }

    @Test
    void agentCacheRebuildFailureAfterExchangeRecoversForwardAndResumesBarrier() {
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerSessionRuntime session = new PartnerSessionRuntime(
                9L, 6L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        session.activate();
        assertTrue(leases.acquire(9L, Map.of(10, 10, 20, 20)).acquired());
        AtomicInteger humanOwner = new AtomicInteger(10);
        AtomicInteger agentOwner = new AtomicInteger(20);
        Character human = characterWithDynamicOwner(humanOwner);
        Character agent = characterWithDynamicOwner(agentOwner);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentTransitionBarrierState barrier = new AgentTransitionBarrierState();
        when(entry.transitionBarrierState()).thenReturn(barrier);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        when(presentation.refresh(
                org.mockito.ArgumentMatchers.eq(human),
                org.mockito.ArgumentMatchers.eq(agent),
                org.mockito.ArgumentMatchers.eq(PartnerMode.DOUBLE_PARTNER),
                org.mockito.ArgumentMatchers.any(Character.ProfileExchangeResult.class)))
                .thenReturn(new ProfilePresentationService.RefreshMetrics(7, 112L, 700L));
        PartnerProfileCacheInvalidator invalidator = mock(PartnerProfileCacheInvalidator.class);
        doThrow(new IllegalStateException("agent tick cache fault"))
                .doNothing()
                .when(invalidator).invalidate(entry, agent);
        List<JournalEvent> journal = new ArrayList<>();
        ProfileTransitionCoordinator coordinator = new ProfileTransitionCoordinator(
                leases,
                presentation,
                invalidator,
                (sessionId, orientation, generation, status, reason) ->
                        journal.add(new JournalEvent(sessionId, orientation, generation, status, reason)),
                (left, right) -> {
                    humanOwner.set(20);
                    agentOwner.set(10);
                    return new Character.ProfileExchangeResult(20, 10, 1L, 1L, 200L);
                });

        ProfileTransitionCoordinator.TransitionResult result = coordinator.transition(
                session, human, agent, entry, 0L);

        assertTrue(result.committed());
        assertTrue(result.presentationComplete());
        assertEquals(ProfileOrientation.SWAPPED, session.bindings().orientation());
        assertEquals(10, leases.leaseForProfile(20).orElseThrow().actorCharacterId());
        assertEquals(20, leases.leaseForProfile(10).orElseThrow().actorCharacterId());
        assertFalse(barrier.isPaused());
        verify(invalidator, times(2)).invalidate(entry, agent);
        assertTrue(journal.getLast().reason().contains("agent tick cache fault"));
    }

    @Test
    void thousandDoubleTransitionsKeepBindingsLeasesBarrierAndJournalConsistent() {
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        PartnerSessionRuntime session = new PartnerSessionRuntime(
                11L, 12L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        session.activate();
        assertTrue(leases.acquire(11L, Map.of(10, 10, 20, 20)).acquired());
        AtomicInteger humanOwner = new AtomicInteger(10);
        AtomicInteger agentOwner = new AtomicInteger(20);
        Character human = characterWithDynamicOwner(humanOwner);
        Character agent = characterWithDynamicOwner(agentOwner);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentTransitionBarrierState barrier = new AgentTransitionBarrierState();
        when(entry.transitionBarrierState()).thenReturn(barrier);
        ProfilePresentationService presentation = mock(ProfilePresentationService.class);
        when(presentation.refresh(
                org.mockito.ArgumentMatchers.eq(human),
                org.mockito.ArgumentMatchers.eq(agent),
                org.mockito.ArgumentMatchers.eq(PartnerMode.DOUBLE_PARTNER),
                org.mockito.ArgumentMatchers.any(Character.ProfileExchangeResult.class)))
                .thenReturn(new ProfilePresentationService.RefreshMetrics(6, 96L, 600L));
        PartnerProfileCacheInvalidator invalidator = mock(PartnerProfileCacheInvalidator.class);
        List<JournalEvent> journal = new ArrayList<>();
        ProfileTransitionCoordinator coordinator = new ProfileTransitionCoordinator(
                leases,
                presentation,
                invalidator,
                (sessionId, orientation, generation, status, reason) ->
                        journal.add(new JournalEvent(sessionId, orientation, generation, status, reason)),
                (left, right) -> {
                    int previousHuman = humanOwner.get();
                    humanOwner.set(agentOwner.get());
                    agentOwner.set(previousHuman);
                    return new Character.ProfileExchangeResult(
                            humanOwner.get(), agentOwner.get(),
                            session.generation(), session.generation(), 100L);
                },
                ignored -> { });

        for (int transition = 0; transition < 1_000; transition++) {
            ProfileTransitionCoordinator.TransitionResult result = coordinator.transition(
                    session, human, agent, entry, session.generation());
            assertTrue(result.committed());
            assertTrue(result.presentationComplete());
        }

        assertEquals(ProfileOrientation.CANONICAL, session.bindings().orientation());
        assertEquals(10, humanOwner.get());
        assertEquals(20, agentOwner.get());
        assertEquals(10, leases.leaseForProfile(10).orElseThrow().actorCharacterId());
        assertEquals(20, leases.leaseForProfile(20).orElseThrow().actorCharacterId());
        assertEquals(1_000L, session.generation());
        assertEquals(1_000, journal.size());
        assertFalse(barrier.isPaused());
        verify(presentation, times(1_000)).refresh(
                org.mockito.ArgumentMatchers.eq(human),
                org.mockito.ArgumentMatchers.eq(agent),
                org.mockito.ArgumentMatchers.eq(PartnerMode.DOUBLE_PARTNER),
                org.mockito.ArgumentMatchers.any(Character.ProfileExchangeResult.class));
        verify(invalidator, times(1_000)).invalidate(entry, agent);
    }

    private static PartnerSessionRuntime soloSession() {
        PartnerSessionRuntime session = new PartnerSessionRuntime(
                7L, 5L, 10, 0, 10, 20, PartnerMode.SOLO_TAG);
        session.activate();
        return session;
    }

    private static Character characterWithOwner(int ownerId) {
        Character character = mock(Character.class);
        when(character.getProfileOwnerCharacterId()).thenReturn(ownerId);
        when(character.profileTransitionBlockReason()).thenReturn(null);
        return character;
    }

    private static Character characterWithDynamicOwner(AtomicInteger ownerId) {
        Character character = mock(Character.class);
        when(character.getProfileOwnerCharacterId()).thenAnswer(ignored -> ownerId.get());
        when(character.profileTransitionBlockReason()).thenReturn(null);
        return character;
    }

    private static ProfileTransitionCoordinator coordinator(
            ProfileLeaseRegistry leases,
            ProfilePresentationService presentation,
            List<JournalEvent> journal,
            ProfileTransitionCoordinator.ProfileExchangeOperation exchange) {
        PartnerJournalSink sink = (sessionId, orientation, generation, status, reason) ->
                journal.add(new JournalEvent(sessionId, orientation, generation, status, reason));
        return new ProfileTransitionCoordinator(
                leases,
                presentation,
                new PartnerProfileCacheInvalidator(),
                sink,
                exchange);
    }

    private record JournalEvent(long sessionId,
                                ProfileOrientation orientation,
                                long generation,
                                PartnerLifecycleStatus status,
                                String reason) {
    }
}
