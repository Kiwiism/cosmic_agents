package server.life.autonomy.balrog;

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

class EasyBalrogBehaviorTest {
    @Test
    void bodyCounterAndUndeadEnterOnlyAtTheirWzHpGates() {
        Monster body = bodyAtHp(25_000);
        Character target = targetAt(0, 0);
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(anyInt())).thenAnswer(
                invocation -> invocation.<Integer>getArgument(0) - 1);

        BossActorBehavior.SelectedAction selected = new EasyBalrogBodyBehavior().select(
                body, List.of(target),
                ServerMobActionCatalog.forMob(EasyBalrogBodyBehavior.MOB_ID), random)
                .orElseThrow();

        BossAction.Skill skill = assertInstanceOf(BossAction.Skill.class, selected.action());
        assertEquals(MobSkillType.UNDEAD, skill.mobSkill().getType());
        verify(random).nextInt(2);
    }

    @Test
    void bodyFallsBackToOrdinaryAttacksWhileSpellsAreUnavailable() {
        Monster body = bodyAtHp(25_000);
        when(body.canUseSkill(any(), eq(false))).thenReturn(false);
        RandomGenerator random = mock(RandomGenerator.class);
        when(random.nextInt(anyInt())).thenReturn(0);

        BossActorBehavior.SelectedAction selected = new EasyBalrogBodyBehavior().select(
                body, List.of(targetAt(0, 0)),
                ServerMobActionCatalog.forMob(EasyBalrogBodyBehavior.MOB_ID), random)
                .orElseThrow();

        assertInstanceOf(BossAction.OrdinaryAttack.class, selected.action());
        verify(random).nextInt(4);
    }

    @Test
    void fakeBodyCannotSelectAnAction() {
        Monster body = bodyAtHp(100_000);
        when(body.isFake()).thenReturn(true);

        assertTrue(new EasyBalrogBodyBehavior().select(
                body, List.of(targetAt(0, 0)),
                ServerMobActionCatalog.forMob(EasyBalrogBodyBehavior.MOB_ID),
                mock(RandomGenerator.class)).isEmpty());
    }

    private static Monster bodyAtHp(int hp) {
        Monster body = mock(Monster.class);
        when(body.getPosition()).thenReturn(new Point(0, 0));
        when(body.getHp()).thenReturn(hp);
        when(body.getMaxHp()).thenReturn(100_000);
        when(body.getMp()).thenReturn(10_000);
        when(body.canUseSkill(any(), eq(false))).thenReturn(true);
        return body;
    }

    private static Character targetAt(int x, int y) {
        Character target = mock(Character.class);
        when(target.getPosition()).thenReturn(new Point(x, y));
        return target;
    }
}
