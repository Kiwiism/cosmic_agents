package server.agents.capabilities.follow;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentFollowMapSyncCoordinatorTest {
    @Test
    void delegatesThroughConfiguredFollowMapHooks() {
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        Character agent = mock(Character.class);
        Character anchor = mock(Character.class);
        AgentFollowMapSyncService.FollowMapSyncHooks hooks =
                mock(AgentFollowMapSyncService.FollowMapSyncHooks.class);

        try (MockedStatic<AgentFollowMapSyncService> service = mockStatic(AgentFollowMapSyncService.class)) {
            service.when(() -> AgentFollowMapSyncService.syncFollowMap(
                            eq(entry), eq(agent), eq(anchor), eq(hooks)))
                    .thenReturn(true);

            assertTrue(AgentFollowMapSyncCoordinator.syncFollowMap(entry, agent, anchor, hooks));

            service.verify(() -> AgentFollowMapSyncService.syncFollowMap(
                    eq(entry), eq(agent), eq(anchor), eq(hooks)));
        }
    }
}
