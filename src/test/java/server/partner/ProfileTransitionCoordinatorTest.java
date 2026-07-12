package server.partner;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
