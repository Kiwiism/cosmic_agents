package server.agents.capabilities.recovery;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.combat.AgentDeathTickService;
import server.agents.runtime.AgentRuntimeEntry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentRespawnCoordinatorTest {
    @Test
    void delegatesRespawnThroughRecoveryHooks() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = entry.bot();
        AgentDeathTickService.RespawnHooks hooks = mock(AgentDeathTickService.RespawnHooks.class);

        try (MockedStatic<AgentDeathTickService> service = mockStatic(AgentDeathTickService.class)) {
            service.when(() -> AgentDeathTickService.respawnAtNearestTown(
                            eq(entry),
                            eq(agent),
                            eq(25),
                            any(AgentDeathTickService.RespawnHooks.class)))
                    .thenAnswer(invocation -> null);

            AgentRespawnCoordinator.respawnAtNearestTown(entry, agent, 25, hooks);

            service.verify(() -> AgentDeathTickService.respawnAtNearestTown(
                    eq(entry),
                    eq(agent),
                    eq(25),
                    any(AgentDeathTickService.RespawnHooks.class)));
        }
    }
}
