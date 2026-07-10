package server.agents.capabilities.follow;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentOwnerMotionStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentFollowMotionObservationServiceTest {
    @Test
    void updatesObservedLeaderMotionFromPreviousLeaderPosition() {
        AgentRuntimeEntry entry = entry();
        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(10, 20));

        AgentFollowMotionObservationService.updateObservedLeaderMotion(entry, new Point(13, 18));

        assertEquals(3, AgentOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(-2, AgentOwnerMotionStateRuntime.observedOwnerStepY(entry));
    }

    @Test
    void ignoresMissingLeaderMotionInputs() {
        AgentRuntimeEntry entry = entry();

        AgentFollowMotionObservationService.updateObservedLeaderMotion(null, new Point(13, 18));
        AgentFollowMotionObservationService.updateObservedLeaderMotion(entry, null);

        assertEquals(0, AgentOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(0, AgentOwnerMotionStateRuntime.observedOwnerStepY(entry));
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }
}
