package server.agents.capabilities.movement.fidget;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeFidgetBoundsTest {
    @Test
    void townLifeJumpSteersBackTowardItsHomeAnchor() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(100, 0));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentFidgetService.startFidget(entry, AgentFidgetMode.JUMP, 0L, 10_000,
                AgentFidgetTrigger.TOWN_LIFE);

        assertEquals(-1, AgentFidgetService.boundedTownLifeDirection(
                entry, new Point(140, 0), 10));
        assertEquals(1, AgentFidgetService.boundedTownLifeDirection(
                entry, new Point(60, 0), 10));
        assertEquals(0, AgentFidgetService.boundedTownLifeDirection(
                entry, new Point(110, 0), 10));
    }
}
