package server.life.autonomy.lordpirate;

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

class LordPirateActorBehaviorTest {
    @Test
    void registersEveryFormForSpawnOwnedServerCombat() {
        for (int id : LordPirateActorBehavior.MOB_IDS) {
            var behavior = ServerMobBehaviorRegistry.behaviorFor(id).orElseThrow();
            assertTrue(behavior.autoStartOnSpawn());
            assertTrue(behavior.usesServerMobPhysics());
        }
    }

    @Test
    void hpGatedSummonPrecedesBuffAndOrdinaryAttack() {
        Monster monster = monster(200_000, 420_000, true);
        var behavior = new LordPirateActorBehavior(9_300_119);
        var selected = behavior.select(monster, List.of(target()),
                ServerMobActionCatalog.forMob(9_300_119), random()).orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.SUMMON, skill.mobSkill().getType());
    }

    @Test
    void ordinaryAttackIsFallbackWhileSkillsCoolDown() {
        Monster monster = monster(420_000, 420_000, false);
        var selected = new LordPirateActorBehavior(9_300_119).select(
                monster, List.of(target()), ServerMobActionCatalog.forMob(9_300_119), random())
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
