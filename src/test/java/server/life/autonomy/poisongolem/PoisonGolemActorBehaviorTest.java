package server.life.autonomy.poisongolem;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.MobSkillType;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.ServerMobActionCatalog;
import server.life.autonomy.ServerMobBehaviorRegistry;

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
import static org.mockito.Mockito.when;

class PoisonGolemActorBehaviorTest {
    @Test
    void registersEveryFormAndSummonForServerCombat() {
        for (int id : PoisonGolemActorBehavior.MOB_IDS) {
            var behavior = ServerMobBehaviorRegistry.behaviorFor(id).orElseThrow();
            assertTrue(behavior.autoStartOnSpawn());
            assertTrue(behavior.forceServerAuthority());
            assertTrue(behavior.usesServerMobPhysics());
        }
        PoisonGolemSummonedAddBehavior.MOB_IDS.forEach(id ->
                assertTrue(ServerMobBehaviorRegistry.supports(id)));
    }

    @Test
    void firstFormUsesItsWzControlSkillsBeforeOrdinaryAttack() {
        Monster monster = monster(63_000, 63_000, true);
        var selected = new PoisonGolemActorBehavior(9_300_180).select(
                monster, List.of(target()), ServerMobActionCatalog.forMob(9_300_180), random())
                .orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertTrue(skill.mobSkill().getType() == MobSkillType.AREA_POISON
                || skill.mobSkill().getType() == MobSkillType.WEAKNESS);
    }

    @Test
    void secondFormUsesHpGatedSummonBeforeControlSkills() {
        Monster monster = monster(41_500, 83_000, true);
        var selected = new PoisonGolemActorBehavior(9_300_181).select(
                monster, List.of(target()), ServerMobActionCatalog.forMob(9_300_181), random())
                .orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.SUMMON, skill.mobSkill().getType());
        assertEquals(94, skill.mobSkill().getId().level());
    }

    @Test
    void finalFormUsesAvailableWzHealAtLowHp() {
        Monster monster = monster(67_000, 113_500, true);
        var selected = new PoisonGolemActorBehavior(9_300_182).select(
                monster, List.of(target()), ServerMobActionCatalog.forMob(9_300_182), random())
                .orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.HEAL_M, skill.mobSkill().getType());
    }

    @Test
    void ordinaryWzAttackIsFallbackWhenSkillsAreCoolingDown() {
        Monster monster = monster(113_500, 113_500, false);
        var selected = new PoisonGolemActorBehavior(9_300_182).select(
                monster, List.of(target()), ServerMobActionCatalog.forMob(9_300_182), random())
                .orElseThrow();

        assertInstanceOf(BossAction.OrdinaryAttack.class, selected.action());
    }

    private static Monster monster(int hp, int maxHp, boolean skills) {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getHp()).thenReturn(hp);
        when(monster.getMaxHp()).thenReturn(maxHp);
        when(monster.getMp()).thenReturn(500);
        when(monster.canUseSkill(any(), eq(false))).thenReturn(skills);
        return monster;
    }

    private static Character target() {
        Character target = mock(Character.class);
        when(target.getPosition()).thenReturn(new Point(-100, 0));
        return target;
    }

    private static RandomGenerator random() {
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(0);
        return random;
    }
}
