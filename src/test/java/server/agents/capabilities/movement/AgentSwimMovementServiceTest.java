package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotCombatCooldownStateRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSwimMovementServiceTest {
    @Test
    void idleTargetHoldsUpToAvoidEndlessSink() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentSwimMovementService.computeSwimIntents(entry, null);

        assertEquals(-1, AgentBotSwimStateRuntime.swimVerticalHold(entry));
        assertEquals(0, AgentBotSwimStateRuntime.swimMoveDirection(entry));
        assertFalse(AgentBotSwimStateRuntime.swimJumpRequested(entry));
    }

    @Test
    void attackCooldownClearsInputAndDoesNotSetIntent() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotSwimStateRuntime.setSwimMoveDirection(entry, 1);
        AgentBotSwimStateRuntime.setSwimVerticalHold(entry, -1);
        AgentBotSwimStateRuntime.setSwimJumpRequested(entry, true);
        AgentBotCombatCooldownStateRuntime.maxAttackCooldown(entry, 500);

        AgentSwimMovementService.computeSwimIntents(entry, new Point(100, 100));

        assertEquals(0, AgentBotSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(0, AgentBotSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentBotSwimStateRuntime.swimJumpRequested(entry));
    }

    @Test
    void arrivalBandHoldsUpWithoutJumpOrHorizontalPush() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 100));
        BotEntry entry = new BotEntry(agent, null, null);

        AgentSwimMovementService.computeSwimIntents(entry, new Point(104, 110));

        assertEquals(0, AgentBotSwimStateRuntime.swimMoveDirection(entry));
        assertEquals(-1, AgentBotSwimStateRuntime.swimVerticalHold(entry));
        assertFalse(AgentBotSwimStateRuntime.swimJumpRequested(entry));
    }

    @Test
    void targetFarAboveRequestsJumpAndUpHold() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 200));
        BotEntry entry = new BotEntry(agent, null, null);

        AgentSwimMovementService.computeSwimIntents(entry, new Point(100, 50));

        assertTrue(AgentBotSwimStateRuntime.swimJumpRequested(entry));
        assertEquals(-1, AgentBotSwimStateRuntime.swimVerticalHold(entry));
    }
}
