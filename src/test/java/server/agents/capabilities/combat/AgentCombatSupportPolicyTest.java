package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import constants.skills.Cleric;
import constants.skills.SuperGM;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AgentCombatSupportPolicyTest {
    @Test
    void shouldRejectDragonRoarAtOrBelowHalfHp() {
        Character bot = characterWithHp(100, 200);

        assertFalse(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 20, 6, true));
    }

    @Test
    void shouldAllowDragonRoarAboveHalfHpWithEnoughTargets() {
        Character bot = characterWithHp(101, 200);

        assertTrue(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 6, 6, false));
    }

    @Test
    void shouldAllowDragonRoarBelowTargetThresholdWithNearbyHealer() {
        Character bot = characterWithHp(101, 200);

        assertTrue(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 1, 6, true));
    }

    @Test
    void shouldUseLegacyDragonRoarTargetThresholdWithoutNearbyHealer() {
        Character bot = characterWithHp(101, 200);

        assertFalse(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 9, false));
        assertTrue(AgentCombatSupportPolicy.canUseDragonRoarPlan(bot, 10, false));
    }

    @Test
    void shouldApplyLegacyHealThreshold() {
        Character healthy = characterWithHp(101, 200);
        Character hurt = characterWithHp(99, 200);

        assertFalse(AgentCombatSupportPolicy.needsHeal(healthy, 0.5));
        assertTrue(AgentCombatSupportPolicy.needsHeal(hurt, 0.5));
    }

    @Test
    void shouldRecognizeClericAndSuperGmHealSkills() {
        Character cleric = mock(Character.class);
        when(cleric.getSkillLevel(Cleric.HEAL)).thenReturn(1);
        Character gm = mock(Character.class);
        when(gm.getSkillLevel(SuperGM.HEAL_PLUS_DISPEL)).thenReturn(1);
        Character other = mock(Character.class);

        assertTrue(AgentCombatSupportPolicy.hasHealSkill(cleric));
        assertTrue(AgentCombatSupportPolicy.hasHealSkill(gm));
        assertFalse(AgentCombatSupportPolicy.hasHealSkill(other));
    }

    @Test
    void shouldConsiderOnlyReadyPartySupportBuffs() {
        assertTrue(AgentCombatSupportPolicy.shouldConsiderSupportBuff(true, false, false));
        assertFalse(AgentCombatSupportPolicy.shouldConsiderSupportBuff(false, false, false));
        assertFalse(AgentCombatSupportPolicy.shouldConsiderSupportBuff(true, true, false));
        assertFalse(AgentCombatSupportPolicy.shouldConsiderSupportBuff(true, false, true));
    }

    @Test
    void shouldPreserveSkillBuffTickPreflightPriorityAndMessages() {
        assertEquals(AgentCombatSupportPolicy.SkillBuffTickDecision.ATTACK_COOLDOWN,
                AgentCombatSupportPolicy.skillBuffTickDecision(true, false, false, false, false));
        assertNull(AgentCombatSupportPolicy.SkillBuffTickDecision.ATTACK_COOLDOWN.legacyDebugSummary());

        assertEquals(AgentCombatSupportPolicy.SkillBuffTickDecision.DISABLED,
                AgentCombatSupportPolicy.skillBuffTickDecision(false, false, false, false, false));
        assertEquals("skill buffs disabled",
                AgentCombatSupportPolicy.SkillBuffTickDecision.DISABLED.legacyDebugSummary());

        assertEquals(AgentCombatSupportPolicy.SkillBuffTickDecision.IDLE,
                AgentCombatSupportPolicy.skillBuffTickDecision(false, true, false, false, false));
        assertEquals("idle (not following or grinding)",
                AgentCombatSupportPolicy.SkillBuffTickDecision.IDLE.legacyDebugSummary());

        assertEquals(AgentCombatSupportPolicy.SkillBuffTickDecision.NO_BUFF_SKILLS,
                AgentCombatSupportPolicy.skillBuffTickDecision(false, true, true, false, false));
        assertEquals("no buff skills in cache",
                AgentCombatSupportPolicy.SkillBuffTickDecision.NO_BUFF_SKILLS.legacyDebugSummary());

        assertEquals(AgentCombatSupportPolicy.SkillBuffTickDecision.READY,
                AgentCombatSupportPolicy.skillBuffTickDecision(false, true, false, true, true));
        assertNull(AgentCombatSupportPolicy.SkillBuffTickDecision.READY.legacyDebugSummary());
    }

    @Test
    void shouldTickSupportHealingOnlyWhenLegacyPreflightAllowsIt() {
        assertTrue(AgentCombatSupportPolicy.shouldTickSupportHealing(
                false, true, true, false, 2001002, false));
        assertTrue(AgentCombatSupportPolicy.shouldTickSupportHealing(
                false, true, false, true, 2001002, false));
        assertFalse(AgentCombatSupportPolicy.shouldTickSupportHealing(
                true, true, true, false, 2001002, false));
        assertFalse(AgentCombatSupportPolicy.shouldTickSupportHealing(
                false, false, true, false, 2001002, false));
        assertFalse(AgentCombatSupportPolicy.shouldTickSupportHealing(
                false, true, false, false, 2001002, false));
        assertFalse(AgentCombatSupportPolicy.shouldTickSupportHealing(
                false, true, true, false, 0, false));
        assertFalse(AgentCombatSupportPolicy.shouldTickSupportHealing(
                false, true, true, false, 2001002, true));
    }

    @Test
    void shouldCastSupportHealForPartyNeedOrUndeadTargetsOnly() {
        assertTrue(AgentCombatSupportPolicy.shouldCastSupportHeal(true, false));
        assertTrue(AgentCombatSupportPolicy.shouldCastSupportHeal(false, true));
        assertTrue(AgentCombatSupportPolicy.shouldCastSupportHeal(true, true));
        assertFalse(AgentCombatSupportPolicy.shouldCastSupportHeal(false, false));
    }

    @Test
    void shouldPreserveSupportCastReadinessOrder() {
        AtomicBoolean costChecked = new AtomicBoolean(false);

        assertEquals(AgentCombatSupportPolicy.SupportCastReadiness.MISSING_SKILL_LEVEL,
                AgentCombatSupportPolicy.supportCastReadiness(0, true, () -> {
                    costChecked.set(true);
                    return true;
                }));
        assertFalse(costChecked.get());

        assertEquals(AgentCombatSupportPolicy.SupportCastReadiness.DEAD,
                AgentCombatSupportPolicy.supportCastReadiness(1, false, () -> {
                    costChecked.set(true);
                    return true;
                }));
        assertFalse(costChecked.get());

        assertEquals(AgentCombatSupportPolicy.SupportCastReadiness.CANNOT_PAY_COST,
                AgentCombatSupportPolicy.supportCastReadiness(1, true, () -> {
                    costChecked.set(true);
                    return false;
                }));
        assertTrue(costChecked.get());

        assertEquals(AgentCombatSupportPolicy.SupportCastReadiness.READY,
                AgentCombatSupportPolicy.supportCastReadiness(1, true, () -> true));
    }

    @Test
    void shouldDetectNearbyHealSkillAlly() {
        Character bot = characterAt(1, new Point(100, 100), true);
        Character healer = characterAt(2, new Point(130, 110), true);
        when(healer.getSkillLevel(Cleric.HEAL)).thenReturn(1);
        Character farHealer = characterAt(3, new Point(220, 100), true);
        when(farHealer.getSkillLevel(Cleric.HEAL)).thenReturn(1);
        Character nonHealer = characterAt(4, new Point(120, 100), true);
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(bot, nonHealer, healer, farHealer));

        assertTrue(AgentCombatSupportPolicy.hasNearbyHealSkillAlly(bot, 60, 30));
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(bot, nonHealer, farHealer));
        assertFalse(AgentCombatSupportPolicy.hasNearbyHealSkillAlly(bot, 60, 30));
    }

    @Test
    void shouldFilterNearbyPartyMembersByAliveIdentityVerticalAndDistance() {
        Character bot = characterAt(1, new Point(100, 100), true);
        Character nearby = characterAt(2, new Point(130, 110), true);
        Character dead = characterAt(3, new Point(130, 110), false);
        Character tooHigh = characterAt(4, new Point(130, 160), true);
        Character tooFar = characterAt(5, new Point(220, 100), true);
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(bot, nearby, dead, tooHigh, tooFar));

        assertTrue(AgentCombatSupportPolicy.nearbyPartyMembers(bot, 60, 30).contains(nearby));
        assertFalse(AgentCombatSupportPolicy.nearbyPartyMembers(bot, 60, 30).contains(dead));
        assertFalse(AgentCombatSupportPolicy.nearbyPartyMembers(bot, 60, 30).contains(tooHigh));
        assertFalse(AgentCombatSupportPolicy.nearbyPartyMembers(bot, 60, 30).contains(tooFar));
    }

    @Test
    void shouldDetectPartyMemberNeedingHealInsideSkillBounds() {
        Character bot = characterAt(1, new Point(100, 100), true);
        Character hurtInside = characterAt(2, new Point(115, 100), true);
        when(hurtInside.getHp()).thenReturn(40);
        when(hurtInside.getCurrentMaxHp()).thenReturn(100);
        Character hurtOutside = characterAt(3, new Point(250, 100), true);
        when(hurtOutside.getHp()).thenReturn(40);
        when(hurtOutside.getCurrentMaxHp()).thenReturn(100);
        when(bot.getPartyMembersOnSameMap()).thenReturn(List.of(hurtInside, hurtOutside));

        assertTrue(AgentCombatSupportPolicy.hasPartyMemberInBoundsNeedingHeal(
                bot, new Rectangle(100, 90, 50, 30), 60, 30, 0.5));
        assertFalse(AgentCombatSupportPolicy.hasPartyMemberInBoundsNeedingHeal(
                bot, new Rectangle(160, 90, 50, 30), 60, 30, 0.5));
    }

    private static Character characterWithHp(int hp, int maxHp) {
        Character chr = mock(Character.class);
        when(chr.isAlive()).thenReturn(true);
        when(chr.getHp()).thenReturn(hp);
        when(chr.getCurrentMaxHp()).thenReturn(maxHp);
        return chr;
    }

    private static Character characterAt(int id, Point position, boolean alive) {
        Character chr = mock(Character.class);
        when(chr.getId()).thenReturn(id);
        when(chr.getPosition()).thenReturn(position);
        when(chr.isAlive()).thenReturn(alive);
        return chr;
    }
}
