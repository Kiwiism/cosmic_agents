package server.partner;

import client.Character;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ActivePartnerSessionTest {
    @Test
    void simultaneousLifecycleOperationsAndTriggerCooldownAreExclusive() {
        ActivePartnerSession active = session();

        assertTrue(active.tryEnterSwitchOperation());
        assertFalse(active.tryEnterSwitchOperation());
        active.exitLifecycleOperation();
        assertTrue(active.tryEnterSwitchOperation());
        active.exitLifecycleOperation();

        assertTrue(active.tryAcquireSwitchCooldown(1_000L, 5_000L));
        assertFalse(active.tryAcquireSwitchCooldown(1_001L, 5_000L));
        assertTrue(active.tryAcquireCooldownNotice(1_001L));
        assertFalse(active.tryAcquireCooldownNotice(1_002L));
        assertTrue(active.tryAcquireSwitchCooldown(6_000L, 5_000L));
        assertTrue(active.tryAcquireCooldownNotice(6_001L));
    }

    private static ActivePartnerSession session() {
        PartnerLink link = new PartnerLink(
                5L, 1, 0, 10, 20, PartnerMode.SOLO_TAG,
                true, Instant.now(), Instant.now());
        PartnerSessionRuntime runtime = new PartnerSessionRuntime(
                7L, 5L, 10, ProfileLeaseRegistry.DETACHED_ACTOR,
                10, 20, PartnerMode.SOLO_TAG);
        runtime.activate();
        return new ActivePartnerSession(
                link, runtime, mock(Character.class), mock(Character.class), null);
    }
}
