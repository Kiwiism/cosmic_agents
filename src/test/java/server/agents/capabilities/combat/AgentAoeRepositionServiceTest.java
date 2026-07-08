package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentAoeRepositionStateRuntime;
import server.agents.integration.AgentCombatAoeRepositionRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentAoeRepositionServiceTest {
    @Test
    void existingLiveAnchorIsReusedUntilArrivalOrExpiry() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point anchor = new Point(300, 100);
        AgentAoeRepositionStateRuntime.setAnchor(
                entry,
                anchor,
                System.currentTimeMillis() + AgentCombatConfig.cfg.AOE_REPOSITION_MAX_MS);
        Monster target = mock(Monster.class);
        when(target.isAlive()).thenReturn(true);

        Point result = AgentAoeRepositionService.resolveAoeReposition(
                entry,
                mock(Character.class),
                target,
                mock(AgentAttackPlan.class),
                new Point(100, 100));

        assertEquals(anchor, result);
        assertTrue(AgentAoeRepositionStateRuntime.hasAnchor(entry));
    }

    @Test
    void deadTargetClearsExistingAnchor() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentAoeRepositionStateRuntime.setAnchor(
                entry,
                new Point(300, 100),
                System.currentTimeMillis() + AgentCombatConfig.cfg.AOE_REPOSITION_MAX_MS);
        Monster target = mock(Monster.class);
        when(target.isAlive()).thenReturn(false);

        Point result = AgentAoeRepositionService.resolveAoeReposition(
                entry,
                mock(Character.class),
                target,
                mock(AgentAttackPlan.class),
                new Point(100, 100));

        assertNull(result);
        assertFalse(AgentAoeRepositionStateRuntime.hasAnchor(entry));
    }

    @Test
    void newAnchorFromPlannerIsStoredWithDeadline() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Monster target = mock(Monster.class);
        AgentAttackPlan plan = mock(AgentAttackPlan.class);
        Point anchor = new Point(250, 100);

        try (MockedStatic<AgentCombatAoeRepositionRuntime> planner =
                     mockStatic(AgentCombatAoeRepositionRuntime.class)) {
            planner.when(() -> AgentCombatAoeRepositionRuntime.aoeRepositionTarget(
                            entry, agent, target, plan, AgentCombatConfig.cfg))
                    .thenReturn(anchor);

            Point result = AgentAoeRepositionService.resolveAoeReposition(
                    entry,
                    agent,
                    target,
                    plan,
                    new Point(100, 100));

            assertEquals(anchor, result);
            assertEquals(anchor, AgentAoeRepositionStateRuntime.anchor(entry));
            assertTrue(AgentAoeRepositionStateRuntime.deadlineMs(entry) > System.currentTimeMillis());
        }
    }
}
