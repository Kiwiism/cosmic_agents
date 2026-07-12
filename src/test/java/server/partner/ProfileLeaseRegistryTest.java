package server.partner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileLeaseRegistryTest {
    private ProfileLeaseRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProfileLeaseRegistry();
    }

    @Test
    void profileCanBelongToOnlyOneActiveSession() {
        assertTrue(registry.acquire(10L, Map.of(100, 1, 200, 2)).acquired());

        ProfileLeaseRegistry.LeaseResult conflict = registry.acquire(11L, Map.of(200, 3));

        assertFalse(conflict.acquired());
        assertTrue(conflict.rejectionReason().contains("Profile 200"));
    }

    @Test
    void actorCanHoldOnlyOneProfile() {
        assertTrue(registry.acquire(10L, Map.of(100, 1)).acquired());

        ProfileLeaseRegistry.LeaseResult conflict = registry.acquire(11L, Map.of(200, 1));

        assertFalse(conflict.acquired());
        assertTrue(conflict.rejectionReason().contains("Actor 1"));
    }

    @Test
    void ownedLeasesCanBeAtomicallyRebound() {
        assertTrue(registry.acquire(10L, Map.of(100, 1, 200, 2)).acquired());

        ProfileLeaseRegistry.LeaseResult rebound = registry.rebind(10L, Map.of(100, 2, 200, 1));

        assertTrue(rebound.acquired());
        assertEquals(2, registry.leaseForProfile(100).orElseThrow().actorCharacterId());
        assertEquals(1, registry.leaseForProfile(200).orElseThrow().actorCharacterId());
    }

    @Test
    void releaseRemovesEveryLeaseOwnedBySession() {
        assertTrue(registry.acquire(10L, Map.of(100, 1, 200, ProfileLeaseRegistry.DETACHED_ACTOR)).acquired());

        registry.releaseSession(10L);

        assertFalse(registry.isLeased(100));
        assertFalse(registry.isLeased(200));
    }

    @Test
    void loginReservationAndPartnerLeaseAreMutuallyExclusive() {
        assertTrue(registry.tryReserveForLogin(100));

        ProfileLeaseRegistry.LeaseResult activation = registry.acquire(10L, Map.of(100, 1));

        assertFalse(activation.acquired());
        assertTrue(activation.rejectionReason().contains("entering the world"));
        registry.releaseLoginReservation(100);
        assertTrue(registry.acquire(10L, Map.of(100, 1)).acquired());
        assertFalse(registry.tryReserveForLogin(100));
    }

    @Test
    void deletionReservationBlocksLoginAndPartnerActivation() {
        assertTrue(registry.tryReserveForDeletion(100));

        assertTrue(registry.isUnavailable(100));
        assertFalse(registry.isLeased(100));
        assertFalse(registry.tryReserveForLogin(100));
        assertFalse(registry.acquire(10L, Map.of(100, 1)).acquired());

        registry.releaseDeletionReservation(100);
        assertTrue(registry.tryReserveForLogin(100));
    }
}
