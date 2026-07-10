package server.agents.runtime;

import server.agents.capabilities.follow.AgentFollowTargetPositionService;

import client.Character;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentFollowTargetPositionServiceTest {
    @Test
    void resolvesToRequestedXAtLeaderYWhenMapIsMissing() {
        Character leader = mock(Character.class);

        Point resolved = AgentFollowTargetPositionService.resolve(
                new Point(150, 90),
                leader,
                new Point(100, 200),
                120,
                null,
                12);

        assertEquals(new Point(150, 200), resolved);
    }
}
