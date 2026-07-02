package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotModeStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowIdleMovementRuntimeTest {
    @Test
    void parksFollowMovementUsingRuntimeMovementConfig() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(80, 100));
        BotEntry entry = new BotEntry(agent, mock(Character.class), null);
        AgentBotModeStateRuntime.setFollowing(entry, true);

        assertTrue(AgentFollowIdleMovementRuntime.tryFollowIdleMovementFastPath(
                entry,
                agent,
                new Point(100, 100),
                1_000L));
    }
}
