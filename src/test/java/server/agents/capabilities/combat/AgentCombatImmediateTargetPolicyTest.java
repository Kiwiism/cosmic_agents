package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import client.Skill;
import client.SkillFactory;
import client.inventory.WeaponType;
import java.awt.Point;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import server.StatEffect;
import server.life.Monster;

class AgentCombatImmediateTargetPolicyTest {
    @Test
    void shouldRejectImmediateProjectileTargetWithoutAmmoOrLivingTarget() {
        Character agent = agentAt(100, 100);
        Monster target = monsterAt(200, 100, true);

        assertFalse(AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(agent, target, true, 0));
        assertFalse(AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(agent, monsterAt(200, 100, false),
                false, 0));
        assertFalse(AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(null, target, false, 0));
    }

    @Test
    void shouldAcceptBasicRangedProjectileWhenClientHitboxIntersectsTarget() {
        Character agent = agentAt(100, 100);
        Monster target = monsterAt(250, 100, true);

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<SkillFactory> skills = Mockito.mockStatic(SkillFactory.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(agent))
                    .thenReturn(WeaponType.BOW);
            attacks.when(() -> AgentAttackExecutionProvider.determineBasicWeaponRoute(WeaponType.BOW))
                    .thenReturn(AgentAttackRoute.RANGED);
            attacks.when(() -> AgentAttackExecutionProvider.shouldDegenerateRangedAttack(
                    eq(WeaponType.BOW), any(Point.class), any(Point.class))).thenReturn(false);

            assertTrue(AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(agent, target, false, 0));
        }
    }

    @Test
    void shouldRejectBasicRangedProjectileWhenDegenerateRangedAttackIsRequired() {
        Character agent = agentAt(100, 100);
        Monster target = monsterAt(250, 100, true);

        try (MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<SkillFactory> skills = Mockito.mockStatic(SkillFactory.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(agent))
                    .thenReturn(WeaponType.BOW);
            attacks.when(() -> AgentAttackExecutionProvider.determineBasicWeaponRoute(WeaponType.BOW))
                    .thenReturn(AgentAttackRoute.RANGED);
            attacks.when(() -> AgentAttackExecutionProvider.shouldDegenerateRangedAttack(
                    eq(WeaponType.BOW), any(Point.class), any(Point.class))).thenReturn(true);

            assertFalse(AgentCombatImmediateTargetPolicy.isImmediateProjectileTarget(agent, target, false, 0));
        }
    }

    @Test
    void shouldAcceptCachedRangedSkillWhenAffordableAndHitboxIntersectsTarget() {
        Character agent = agentAt(100, 100);
        Monster target = monsterAt(250, 100, true);
        Skill skill = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(agent.skillIsCooling(30_000)).thenReturn(false);
        when(agent.getSkillLevel(skill)).thenReturn((byte) 1);
        when(skill.getEffect(1)).thenReturn(effect);
        when(effect.canPaySkillCost(agent)).thenReturn(true);
        when(effect.hasBoundingBox()).thenReturn(false);
        when(effect.getRange()).thenReturn(100);

        try (MockedStatic<SkillFactory> skills = Mockito.mockStatic(SkillFactory.class);
             MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            skills.when(() -> SkillFactory.getSkill(30_000)).thenReturn(skill);
            attacks.when(() -> AgentAttackExecutionProvider.determineSkillRoute(agent, 30_000))
                    .thenReturn(AgentAttackRoute.RANGED);
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(agent))
                    .thenReturn(WeaponType.CLAW);
            attacks.when(() -> AgentAttackExecutionProvider.canUseRangedAttackRoute(
                    eq(AgentAttackRoute.RANGED), eq(WeaponType.CLAW), any(Point.class), any(Point.class)))
                    .thenReturn(true);

            assertTrue(AgentCombatImmediateTargetPolicy.isImmediateProjectileSkillTarget(agent, target, 30_000));
        }
    }

    @Test
    void shouldRejectCachedSkillWhenCoolingUnaffordableOrCloseRoute() {
        Character agent = agentAt(100, 100);
        Monster target = monsterAt(250, 100, true);
        Skill skill = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(agent.getSkillLevel(skill)).thenReturn((byte) 1);
        when(skill.getEffect(1)).thenReturn(effect);

        when(agent.skillIsCooling(30_001)).thenReturn(true);
        assertFalse(AgentCombatImmediateTargetPolicy.isImmediateProjectileSkillTarget(agent, target, 30_001));

        try (MockedStatic<SkillFactory> skills = Mockito.mockStatic(SkillFactory.class);
             MockedStatic<AgentAttackExecutionProvider> attacks =
                     Mockito.mockStatic(AgentAttackExecutionProvider.class, Mockito.CALLS_REAL_METHODS)) {
            skills.when(() -> SkillFactory.getSkill(30_002)).thenReturn(skill);
            when(agent.skillIsCooling(30_002)).thenReturn(false);
            when(effect.canPaySkillCost(agent)).thenReturn(false);
            assertFalse(AgentCombatImmediateTargetPolicy.isImmediateProjectileSkillTarget(agent, target, 30_002));

            skills.when(() -> SkillFactory.getSkill(30_003)).thenReturn(skill);
            when(agent.skillIsCooling(30_003)).thenReturn(false);
            when(effect.canPaySkillCost(agent)).thenReturn(true);
            attacks.when(() -> AgentAttackExecutionProvider.determineSkillRoute(agent, 30_003))
                    .thenReturn(AgentAttackRoute.CLOSE);
            assertFalse(AgentCombatImmediateTargetPolicy.isImmediateProjectileSkillTarget(agent, target, 30_003));
        }
    }

    private static Character agentAt(int x, int y) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(x, y));
        when(agent.getSkills()).thenReturn(Map.of());
        return agent;
    }

    private static Monster monsterAt(int x, int y, boolean alive) {
        Monster monster = mock(Monster.class);
        when(monster.getId()).thenReturn(94_000_000);
        when(monster.getObjectId()).thenReturn(1);
        when(monster.getPosition()).thenReturn(new Point(x, y));
        when(monster.isFacingLeft()).thenReturn(false);
        when(monster.isAlive()).thenReturn(alive);
        return monster;
    }
}
