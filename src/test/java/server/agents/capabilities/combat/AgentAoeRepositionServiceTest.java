package server.agents.capabilities.combat;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotAoeRepositionStateRuntime;
import server.agents.integration.AgentBotCombatAoeRepositionRuntime;
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
        AgentBotAoeRepositionStateRuntime.setAnchor(
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
        assertTrue(AgentBotAoeRepositionStateRuntime.hasAnchor(entry));
    }

    @Test
    void deadTargetClearsExistingAnchor() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBotAoeRepositionStateRuntime.setAnchor(
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
        assertFalse(AgentBotAoeRepositionStateRuntime.hasAnchor(entry));
    }

    @Test
    void newAnchorFromPlannerIsStoredWithDeadline() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character agent = mock(Character.class);
        Monster target = mock(Monster.class);
        AgentAttackPlan plan = mock(AgentAttackPlan.class);
        Point anchor = new Point(250, 100);

        try (MockedStatic<AgentBotCombatAoeRepositionRuntime> planner =
                     mockStatic(AgentBotCombatAoeRepositionRuntime.class)) {
            planner.when(() -> AgentBotCombatAoeRepositionRuntime.aoeRepositionTarget(
                            entry, agent, target, plan, AgentCombatConfig.cfg))
                    .thenReturn(anchor);

            Point result = AgentAoeRepositionService.resolveAoeReposition(
                    entry,
                    agent,
                    target,
                    plan,
                    new Point(100, 100));

            assertEquals(anchor, result);
            assertEquals(anchor, AgentBotAoeRepositionStateRuntime.anchor(entry));
            assertTrue(AgentBotAoeRepositionStateRuntime.deadlineMs(entry) > System.currentTimeMillis());
        }
    }
}
