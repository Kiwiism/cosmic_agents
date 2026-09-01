package server.life.autonomy.chronos;

import client.Character;
import org.junit.jupiter.api.Test;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.life.autonomy.ServerMobActionCatalog;

import java.awt.Point;
import java.util.List;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChronosFamilyActorBehaviorTest {
    @Test
    void platoonChronosCastsLinkedMagicAttackOnlyInsideItsWzRange() {
        ChronosFamilyActorBehavior behavior = ChronosFamilyActorBehavior
                .behaviorFor(9_300_016).orElseThrow();
        Monster monster = monsterAt(0, 0);
        Character target = targetAt(150, 0);

        var selected = behavior.select(monster, List.of(target),
                ServerMobActionCatalog.forMob(9_300_016), mock(RandomGenerator.class));

        assertTrue(selected.isPresent());
        BossAction.OrdinaryAttack attack =
                (BossAction.OrdinaryAttack) selected.orElseThrow().action();
        assertTrue(attack.magic());
        assertEquals(target, selected.orElseThrow().primaryTarget());

        when(target.getPosition()).thenReturn(new Point(250, 0));
        assertTrue(behavior.select(monster, List.of(target),
                ServerMobActionCatalog.forMob(9_300_016), mock(RandomGenerator.class)).isEmpty());
    }

    @Test
    void basicChronosRemainsContactOnlyBecauseWzHasNoActiveAttack() {
        ChronosFamilyActorBehavior behavior = ChronosFamilyActorBehavior
                .behaviorFor(9_300_015).orElseThrow();

        assertTrue(behavior.select(monsterAt(0, 0), List.of(targetAt(50, 0)),
                ServerMobActionCatalog.forMob(9_300_015),
                mock(RandomGenerator.class)).isEmpty());
    }

    private static Monster monsterAt(int x, int y) {
        Monster monster = mock(Monster.class);
        when(monster.getPosition()).thenReturn(new Point(x, y));
        when(monster.getMp()).thenReturn(100);
        return monster;
    }

    private static Character targetAt(int x, int y) {
        Character target = mock(Character.class);
        when(target.getPosition()).thenReturn(new Point(x, y));
        return target;
    }
}
