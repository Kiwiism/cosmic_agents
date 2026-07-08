package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSwimMovementServiceTest {
    @Test
    void idleTargetHoldsUpToAvoidEndlessSink() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentSwimMovementService.computeSwimIntents(entry, null);

        assertEquals(-1, AgentSwimStateRuntime.swimVerticalHold(entry));
        assertEquals(0, AgentSwimStateRuntime.swimMoveDirection(entry));
        assertFalse(AgentSwimStateRuntime.swimJumpRequested(entry));
    }

    @Test
    void attackCooldownClearsInputAndDoesNotSetIntent() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentSwimStateRuntime.setSwimMoveDirection(entry, 1);
        AgentSwimStateRuntime.setSwimVerticalHold(entry, -1);
        AgentSwimStateRuntime.setSwimJumpRequested(entry, true);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 500);

        AgentSwimMovementService.computeSwimIntents(entry, new Point(100, 100));

        assertEquals(0, AgentSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(0, AgentSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentSwimStateRuntime.swimJumpRequested(entry));
    }

    @Test
    void arrivalBandHoldsUpWithoutJumpOrHorizontalPush() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentSwimMovementService.computeSwimIntents(entry, new Point(104, 110));

        assertEquals(0, AgentSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(-1, AgentSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentSwimStateRuntime.swimJumpRequested(entry));
    }

    @Test
    void targetFarAboveRequestsJumpAndUpHold() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 200));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentSwimMovementService.computeSwimIntents(entry, new Point(100, 50));

        assertTrue(AgentSwimStateRuntime.swimJumpRequested(entry));
        assertEquals(-1, AgentSwimStateRuntime.swimVerticalHold(entry));
    }
}
