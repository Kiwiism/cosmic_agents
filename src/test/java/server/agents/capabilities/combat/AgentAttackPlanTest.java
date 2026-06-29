package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import client.inventory.WeaponType;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import server.life.Monster;

class AgentAttackPlanTest {
    @Test
    void shouldExposeLegacyAttackPlanFieldsAndHelpers() {
        Monster primary = mock(Monster.class);
        Rectangle hitBox = new Rectangle(1, 2, 3, 4);

        AgentAttackPlan plan = new AgentAttackPlan(12, 3, 4, hitBox, List.of(primary),
                AgentAttackRoute.CLOSE, 5, 6, 7, 8, 9, 10, 11, WeaponType.SWORD1H);

        assertEquals(12, plan.skillId);
        assertEquals(3, plan.skillLevel);
        assertEquals(4, plan.numDamage);
        assertEquals(hitBox, plan.hitBox);
        assertEquals(primary, plan.primaryTarget());
        assertEquals(AgentAttackRoute.CLOSE, plan.route);
        assertEquals(5, plan.display);
        assertEquals(6, plan.direction);
        assertEquals(7, plan.rangedDirection);
        assertEquals(8, plan.stance);
        assertEquals(9, plan.speed);
        assertEquals(10, plan.hitDelayMs);
        assertEquals(11, plan.cooldownMs);
        assertEquals(WeaponType.SWORD1H, plan.damageWeaponType);
        assertTrue(plan.hasHitBox());
        assertTrue(plan.isCloseRangeRoute());
    }

    @Test
    void shouldReportMissingHitboxAndNonCloseRoute() {
        AgentAttackPlan plan = new AgentAttackPlan(0, 0, 1, null, List.of(mock(Monster.class)),
                AgentAttackRoute.RANGED, 0, 0, 0, 0, 0, 0, 0, WeaponType.BOW);

        assertFalse(plan.hasHitBox());
        assertFalse(plan.isCloseRangeRoute());
    }
}
