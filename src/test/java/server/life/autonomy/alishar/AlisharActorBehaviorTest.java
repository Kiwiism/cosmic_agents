package server.life.autonomy.alishar;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.MobSkillType;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.BossActorBehavior;
import server.life.autonomy.ServerMobActionCatalog;

import java.awt.Point;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlisharActorBehaviorTest {
    @Test
    void usesServerPhysicsWhileServerOwnsCombat() {
        assertTrue(new AlisharActorBehavior().usesServerMobPhysics());
    }

    @Test
    void fullHealthPrioritizesAvailableDebuffsOverOrdinaryAttack() {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getHp()).thenReturn(125_000);
        when(monster.getMaxHp()).thenReturn(125_000);
        when(monster.getMp()).thenReturn(2_500);
        when(monster.canUseSkill(any(), eq(false))).thenReturn(true);
        Character target = targetAt(-100, 0);
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(0);

        BossActorBehavior.SelectedAction selected = new AlisharActorBehavior().select(
                        monster, List.of(target),
                        ServerMobActionCatalog.forMob(AlisharActorBehavior.MOB_ID), random)
                .orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.SEAL, skill.mobSkill().getType());
        verify(random).nextInt(2);
    }

    @Test
    void halfHealthStillPrioritizesAvailableDebuffsOverSummons() {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getHp()).thenReturn(62_500);
        when(monster.getMaxHp()).thenReturn(125_000);
        when(monster.getMp()).thenReturn(2_500);
        when(monster.canUseSkill(any(), eq(false))).thenReturn(true);
        Character target = targetAt(-100, 0);
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(1);

        BossActorBehavior.SelectedAction selected = new AlisharActorBehavior().select(
                        monster, List.of(target),
                        ServerMobActionCatalog.forMob(AlisharActorBehavior.MOB_ID), random)
                .orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.DARKNESS, skill.mobSkill().getType());
        verify(random).nextInt(2);
    }

    @Test
    void summonsRemainAvailableWhileDebuffsAreCoolingDown() {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getHp()).thenReturn(62_500);
        when(monster.getMaxHp()).thenReturn(125_000);
        when(monster.getMp()).thenReturn(2_500);
        when(monster.canUseSkill(any(), eq(false))).thenAnswer(invocation -> {
            server.life.MobSkill skill = invocation.getArgument(0);
            return skill.getType() == MobSkillType.SUMMON;
        });
        Character target = targetAt(-100, 0);
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(1);

        BossActorBehavior.SelectedAction selected = new AlisharActorBehavior().select(
                        monster, List.of(target),
                        ServerMobActionCatalog.forMob(AlisharActorBehavior.MOB_ID), random)
                .orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.SUMMON, skill.mobSkill().getType());
        assertEquals(30, skill.mobSkill().getId().level());
        verify(random).nextInt(2);
    }

    @Test
    void ordinaryAttackRemainsTheFallbackWhileSkillsAreCoolingDown() {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getHp()).thenReturn(125_000);
        when(monster.getMaxHp()).thenReturn(125_000);
        when(monster.getMp()).thenReturn(2_500);
        when(monster.canUseSkill(any(), eq(false))).thenReturn(false);
        Character target = targetAt(-100, 0);
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(0);

        BossActorBehavior.SelectedAction selected = new AlisharActorBehavior().select(
                        monster, List.of(target),
                        ServerMobActionCatalog.forMob(AlisharActorBehavior.MOB_ID), random)
                .orElseThrow();

        assertInstanceOf(BossAction.OrdinaryAttack.class, selected.action());
        verify(random).nextInt(1);
    }

    private static Character targetAt(int x, int y) {
        Character target = mock(Character.class);
        when(target.getPosition()).thenReturn(new Point(x, y));
        return target;
    }
}
