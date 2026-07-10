package server.agents.commands;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentLeaderTransferCoordinatorTest {
    @AfterEach
    void clearRegistry() {
        AgentRuntimeRegistry.entriesByLeaderId().clear();
    }

    @Test
    void preservesNoGroupReplyWhenLeaderHasNoAgents() {
        String result = AgentLeaderTransferCoordinator.transferAgent(
                7,
                mock(Character.class),
                "AgentA",
                "PlayerB",
                entry -> {
                },
                (leaderId, leader, agent) -> null);

        assertEquals("You have no bots.", result);
    }
}
