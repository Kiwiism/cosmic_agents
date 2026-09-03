package server.life.autonomy.papapixie;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PapaPixieActorBehaviorTest {
    @Test
    void usesServerPhysicsWhileServerOwnsCombat() {
        assertTrue(new PapaPixieActorBehavior().usesServerMobPhysics());
    }

    @Test
    void doesNotCastDebuffWithoutTargetInsideItsWzEnvelope() {
        Monster monster = papaPixieAtOneHp();
        Character target = targetAt(301, 0);
        BossAction.Skill slow = slowAction();

        assertTrue(new PapaPixieActorBehavior().select(
                monster, List.of(target),
                new ServerMobActionCatalog.MonsterActions(List.of(), List.of(slow)),
                mock(RandomGenerator.class)).isEmpty());
    }

    @Test
    void castsDebuffWhenTargetIsInsideItsWzEnvelope() {
        Monster monster = papaPixieAtOneHp();
        Character target = targetAt(300, 0);
        BossAction.Skill slow = slowAction();
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(1)).thenReturn(0);

        BossActorBehavior.SelectedAction selected = new PapaPixieActorBehavior().select(
                monster, List.of(target),
                new ServerMobActionCatalog.MonsterActions(List.of(), List.of(slow)), random)
                .orElseThrow();

        BossAction.Skill selectedSkill = assertInstanceOf(
                BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.SLOW, selectedSkill.mobSkill().getType());
        assertEquals(target, selected.primaryTarget());
    }

    private static Monster papaPixieAtOneHp() {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getHp()).thenReturn(1);
        when(monster.getMaxHp()).thenReturn(100);
        when(monster.canUseSkill(any(), eq(false))).thenReturn(true);
        return monster;
    }

    private static BossAction.Skill slowAction() {
        return ServerMobActionCatalog.forMob(PapaPixieActorBehavior.MOB_ID).skills().stream()
                .filter(action -> action.mobSkill().getType() == MobSkillType.SLOW)
                .findFirst().orElseThrow();
    }

    private static Character targetAt(int x, int y) {
        Character target = mock(Character.class);
        when(target.getPosition()).thenReturn(new Point(x, y));
        return target;
    }
}
