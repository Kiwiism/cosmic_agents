package server.agents.capabilities.movement.fidget;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBoundedFidgetAirborneTest {
    @Test
    void activeJumpFidgetKeepsOwningItsAirborneArc() {
        AgentRuntimeEntry entry = entry();
        AgentFidgetStateRuntime.start(entry, AgentFidgetMode.JUMP, AgentFidgetTrigger.TOWN_LIFE,
                10_000L, 0L, 0, false, 0, new Point(10, 20), 0L, 0L);
        AgentMovementStateRuntime.setInAir(entry, true);

        assertFalse(AgentFidgetService.invalidBoundedAirborneState(entry));
    }

    @Test
    void nonJumpPresentationDoesNotCaptureUnrelatedAirborneMotion() {
        AgentRuntimeEntry entry = entry();
        AgentFidgetStateRuntime.start(entry, AgentFidgetMode.WAIT, AgentFidgetTrigger.TOWN_LIFE,
                10_000L, 0L, 0, false, 0, new Point(10, 20), 0L, 0L);
        AgentMovementStateRuntime.setInAir(entry, true);

        assertTrue(AgentFidgetService.invalidBoundedAirborneState(entry));
    }

    private static AgentRuntimeEntry entry() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        return new AgentRuntimeEntry(agent, agent, null);
    }
}
