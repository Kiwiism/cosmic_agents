package net.server.channel.handlers;

import client.Character;
import constants.skills.Assassin;
import constants.skills.Marksman;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttackDamageCriticalPolicyTest {
    @Test
    void normalizesMagnitudeWithoutTreatingWireSignAsCriticalState() {
        assertEquals(1234, AbstractDealDamageHandler.normalizeClientDamage(1234));
        assertEquals(1234,
                AbstractDealDamageHandler.normalizeClientDamage(1234 | Integer.MIN_VALUE));
    }

    @Test
    void regularLineDoesNotReceiveCriticalDisplayFlag() {
        assertFalse(AbstractDealDamageHandler.shouldBroadcastCritical(0, true, 900, 1000));
        assertFalse(AbstractDealDamageHandler.shouldBroadcastCritical(0, false, 2000, 1000));
    }

    @Test
    void serverInferenceAndSnipeStillMarkCriticalLines() {
        assertTrue(AbstractDealDamageHandler.shouldBroadcastCritical(0, true, 1001, 1000));
        assertTrue(AbstractDealDamageHandler.shouldBroadcastCritical(
                Marksman.SNIPE, false, 1, 1000));
    }

    @Test
    void generalCriticalCapabilityRequiresAnActuallyLearnedPassive() {
        Character chiefBanditWithoutCriticalThrow = mock(Character.class);
        Character assassinWithCriticalThrow = mock(Character.class);
        when(assassinWithCriticalThrow.getSkillLevel(Assassin.CRITICAL_THROW)).thenReturn(1);

        assertFalse(AbstractDealDamageHandler.hasLearnedGeneralCriticalPassive(
                chiefBanditWithoutCriticalThrow));
        assertTrue(AbstractDealDamageHandler.hasLearnedGeneralCriticalPassive(
                assassinWithCriticalThrow));
    }
}
