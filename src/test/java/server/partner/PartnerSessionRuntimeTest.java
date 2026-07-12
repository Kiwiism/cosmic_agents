package server.partner;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerSessionRuntimeTest {
    @Test
    void doublePartnerBindingReversalKeepsActorIdsFixed() {
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        runtime.activate();

        PartnerSessionRuntime.TransitionToken token = runtime.beginSwap(0L);
        runtime.commitSwap(token);

        assertEquals(10, runtime.playerActorCharacterId());
        assertEquals(20, runtime.partnerActorCharacterId());
        assertEquals(ProfileOrientation.SWAPPED, runtime.bindings().orientation());
        assertEquals(Map.of(20, 10, 10, 20), runtime.profileToActorLeases());
        assertEquals(1L, runtime.generation());
        assertEquals(PartnerLifecycleStatus.ACTIVE, runtime.status());
    }

    @Test
    void soloTagKeepsInactiveProfileDetached() {
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, ProfileLeaseRegistry.DETACHED_ACTOR,
                10, 20, PartnerMode.SOLO_TAG);
        runtime.activate();

        PartnerSessionRuntime.TransitionToken token = runtime.beginSwap(0L);
        runtime.commitSwap(token);

        assertEquals(Map.of(20, 10, 10, ProfileLeaseRegistry.DETACHED_ACTOR),
                runtime.profileToActorLeases());
    }

    @Test
    void staleGenerationCannotBeginOrCommitTransition() {
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        runtime.activate();

        assertThrows(IllegalStateException.class, () -> runtime.beginSwap(1L));
        PartnerSessionRuntime.TransitionToken token = runtime.beginSwap(0L);
        PartnerSessionRuntime.TransitionToken stale = new PartnerSessionRuntime.TransitionToken(
                token.sessionId(), token.generation() + 1, token.before(), token.after());

        assertThrows(IllegalStateException.class, () -> runtime.commitSwap(stale));
        runtime.abortSwap(token);
        assertTrue(runtime.isCurrentGeneration(1L));
    }

    @Test
    void releaseRestoresCanonicalOrientationBeforeClosing() {
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        runtime.activate();
        PartnerSessionRuntime.TransitionToken token = runtime.beginSwap(0L);
        runtime.commitSwap(token);

        long releaseGeneration = runtime.beginRelease();
        runtime.restoreCanonicalForRelease(releaseGeneration);
        runtime.close(releaseGeneration, PartnerLifecycleStatus.CLOSED);

        assertEquals(ProfileOrientation.CANONICAL, runtime.bindings().orientation());
        assertEquals(PartnerLifecycleStatus.CLOSED, runtime.status());
    }

    @Test
    void failedReleaseCanReturnToActiveCanonicalStateForRetry() {
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        runtime.activate();

        long releaseGeneration = runtime.beginRelease();
        runtime.restoreCanonicalForRelease(releaseGeneration);
        runtime.abortRelease(releaseGeneration);

        assertEquals(ProfileOrientation.CANONICAL, runtime.bindings().orientation());
        assertEquals(PartnerLifecycleStatus.ACTIVE, runtime.status());
        assertEquals(releaseGeneration, runtime.generation());
    }

    @Test
    void repeatedBindingAndLeaseReversalRemainsExclusiveAndReturnsCanonical() {
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, 20, 10, 20, PartnerMode.DOUBLE_PARTNER);
        ProfileLeaseRegistry leases = new ProfileLeaseRegistry();
        assertTrue(leases.acquire(7L, runtime.profileToActorLeases()).acquired());
        runtime.activate();

        for (int iteration = 0; iteration < 1_000; iteration++) {
            PartnerSessionRuntime.TransitionToken token = runtime.beginSwap(runtime.generation());
            assertTrue(leases.rebind(7L, runtime.profileToActorLeases(token.after())).acquired());
            runtime.commitSwap(token);
            assertTrue(leases.holds(7L, 10));
            assertTrue(leases.holds(7L, 20));
        }

        assertEquals(ProfileOrientation.CANONICAL, runtime.bindings().orientation());
        assertEquals(1_000L, runtime.generation());
        assertEquals(Map.of(10, 10, 20, 20), runtime.profileToActorLeases());
    }
}
