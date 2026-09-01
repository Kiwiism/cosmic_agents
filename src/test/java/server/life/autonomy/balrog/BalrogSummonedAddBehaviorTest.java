package server.life.autonomy.balrog;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.ServerMobActionCatalog;

import java.awt.Point;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalrogSummonedAddBehaviorTest {
    @Test
    void juniorBalrogWaitsForItsAggroTargetToEnterAttackRange() {
        assertWaitsThenAttacks(BalrogSummonedAddBehavior.JR_BALROG_ID);
    }

    @Test
    void flyingCrimsonBalrogWaitsForItsAggroTargetToEnterAttackRange() {
        assertWaitsThenAttacks(BalrogSummonedAddBehavior.CRIMSON_BALROG_ID);
    }

    private static void assertWaitsThenAttacks(int mobId) {
        Monster monster = mock(Monster.class);
        Character target = mock(Character.class);
        BalrogSummonedAddBehavior behavior = BalrogSummonedAddBehavior
                .behaviorFor(mobId).orElseThrow();
        var actions = ServerMobActionCatalog.forMob(mobId);
        when(monster.getPosition()).thenReturn(new Point(0, 0));
        when(monster.getMp()).thenReturn(500);
        when(target.getPosition()).thenReturn(new Point(1_000, 0));

        assertTrue(behavior.select(
                monster, List.of(target), actions, mock(RandomGenerator.class)).isEmpty());

        when(target.getPosition()).thenReturn(new Point(100, 0));
        assertInstanceOf(BossAction.OrdinaryAttack.class, behavior.select(
                monster, List.of(target), actions, mock(RandomGenerator.class))
                .orElseThrow().action());
        assertTrue(behavior.usesPrimaryAggroTargetOnly());
    }
}
