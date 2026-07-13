package server.partner;

import client.Character;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void preparingSessionCoalescesFeedbackUntilItBecomesReady() {
        ActivePartnerSession ready = session();
        ActivePartnerSession preparing = ActivePartnerSession.preparing(
                ready.link(), ready.runtime(), ready.humanActor(),
                ready.partnerActorOrDormantProfile(), ready.agentEntry());

        assertFalse(preparing.isSwitchReady());
        assertTrue(preparing.tryScheduleSwitchPreparation());
        assertFalse(preparing.tryScheduleSwitchPreparation());
        assertTrue(preparing.tryAcquirePreparationNotice());
        assertFalse(preparing.tryAcquirePreparationNotice());
        assertTrue(preparing.markSwitchReady());
        assertTrue(preparing.isSwitchReady());
        assertFalse(preparing.tryAcquirePreparationNotice());
    }

    @Test
    void switchCooldownSaturatesInsteadOfWrapping() {
        ActivePartnerSession active = session();

        assertTrue(active.tryAcquireSwitchCooldown(Long.MAX_VALUE - 5L, 10L));
        assertEquals(1L, active.remainingSwitchCooldownMs(Long.MAX_VALUE - 1L));
        assertFalse(active.tryAcquireSwitchCooldown(Long.MAX_VALUE - 1L, 0L));
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
