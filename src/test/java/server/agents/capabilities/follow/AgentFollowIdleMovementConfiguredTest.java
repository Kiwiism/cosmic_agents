package server.agents.capabilities.follow;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentModeStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowIdleMovementConfiguredTest {
    @Test
    void parksFollowMovementUsingRuntimeMovementConfig() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(80, 100));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentModeStateRuntime.setFollowing(entry, true);

        assertTrue(AgentFollowIdleMovementService.tryFollowIdleMovementFastPath(
                entry,
                agent,
                new Point(100, 100),
                1_000L));
    }
}
