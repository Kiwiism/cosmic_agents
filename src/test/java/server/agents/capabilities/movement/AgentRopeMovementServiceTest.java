package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.bots.BotEntry;
import server.maps.Rope;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRopeMovementServiceTest {
    @Test
    void attachToRopePreservesLegacyClimbStateAndPosition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 40));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);

        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);

        assertTrue(AgentBotClimbStateRuntime.climbing(entry));
        assertSame(rope, AgentBotClimbStateRuntime.climbRope(entry));
        verify(agent).setPosition(new Point(100, 30));
        verify(agent, atLeastOnce()).setStance(CharacterStance.ROPE_STANCE);
    }

    @Test
    void holdClimbPreservesCurrentRopePosition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 30));
        when(agent.getHp()).thenReturn(1);
        BotEntry entry = new BotEntry(agent, null, null);
        Rope rope = new Rope(100, 0, 80, false);
        AgentRopeMovementService.attachToRope(entry, agent, rope, 30);

        AgentRopeMovementService.holdClimb(entry, agent);

        assertEquals(0, AgentBotClimbStateRuntime.climbVerticalDirection(entry));
        verify(agent, atLeastOnce()).setStance(CharacterStance.ROPE_STANCE);
    }
}
