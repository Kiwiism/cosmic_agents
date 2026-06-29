package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.awt.Rectangle;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentAttackExecutionProvider.BasicAttackData;
import server.life.Monster;

class AgentBasicAttackPlannerTest {
    @Test
    void shouldSelectInitialTargetWhenBasicHitboxIntersects() {
        Monster target = mock(Monster.class);
        BasicAttackData data = data(new Rectangle(0, 0, 10, 10));

        AgentBasicAttackPlanner.BasicAttackSelection selection = AgentBasicAttackPlanner.selectBasicAttack(
                target,
                ignored -> data,
                (candidate, hitBox) -> candidate,
                (hitBox, monster) -> true,
                ignored -> null);

        assertEquals(target, selection.target());
        assertEquals(data, selection.attackData());
    }

    @Test
    void shouldRebuildAttackDataWhenEffectivePrimaryChanges() {
        Monster original = mock(Monster.class);
        Monster effective = mock(Monster.class);
        Map<Monster, BasicAttackData> dataByTarget = new HashMap<>();
        dataByTarget.put(original, data(new Rectangle(0, 0, 10, 10)));
        BasicAttackData effectiveData = data(new Rectangle(20, 0, 10, 10));
        dataByTarget.put(effective, effectiveData);

        AgentBasicAttackPlanner.BasicAttackSelection selection = AgentBasicAttackPlanner.selectBasicAttack(
                original,
                dataByTarget::get,
                (candidate, hitBox) -> candidate == original ? effective : candidate,
                (hitBox, monster) -> true,
                ignored -> null);

        assertEquals(effective, selection.target());
        assertEquals(effectiveData, selection.attackData());
    }

    @Test
    void shouldPivotToOppositeFacingTargetWhenOriginalHitboxMisses() {
        Monster original = mock(Monster.class);
        Monster pivoted = mock(Monster.class);
        BasicAttackData pivotData = data(new Rectangle(20, 0, 10, 10));
        Map<Monster, BasicAttackData> dataByTarget = new HashMap<>();
        dataByTarget.put(original, data(new Rectangle(0, 0, 10, 10)));
        dataByTarget.put(pivoted, pivotData);

        AgentBasicAttackPlanner.BasicAttackSelection selection = AgentBasicAttackPlanner.selectBasicAttack(
                original,
                dataByTarget::get,
                (candidate, hitBox) -> candidate,
                (hitBox, monster) -> monster == pivoted,
                ignored -> pivoted);

        assertEquals(pivoted, selection.target());
        assertEquals(pivotData, selection.attackData());
    }

    @Test
    void shouldReturnNullWhenOriginalHitboxMissesAndNoPivotExists() {
        Monster original = mock(Monster.class);

        assertNull(AgentBasicAttackPlanner.selectBasicAttack(
                original,
                ignored -> data(new Rectangle(0, 0, 10, 10)),
                (candidate, hitBox) -> candidate,
                (hitBox, monster) -> false,
                ignored -> null));
    }

    private static BasicAttackData data(Rectangle hitBox) {
        return new BasicAttackData(hitBox, 0, 0, 0, "stab", 0, 0, 0, 0, AgentAttackRoute.CLOSE);
    }
}
