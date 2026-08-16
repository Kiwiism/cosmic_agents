package server.agents.runtime.hunting;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHuntingVisitRuntimeTest {
    @Test
    void recordsTypedQuestOwnershipBeforeDelegatingCombat() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(100030000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentHuntingVisitRequest request = new AgentHuntingVisitRequest(
                "nautilus:pack", AgentActivityKind.QUESTING,
                AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE, 100030000,
                Set.of(1210101), Set.of(1210100));

        AgentHuntingVisitRuntime.engage(entry, agent, gateway, request, 500L);

        verify(gateway).grind(entry, Set.of(1210101), Set.of(1210100));
        assertEquals(request, entry.capabilityStates()
                .require(AgentHuntingVisitState.STATE_KEY).snapshot().request());
    }
}
