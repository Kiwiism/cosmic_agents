package server.agents.capabilities.townlife;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentTownLifeDirectedEncounterRuntimeTest {
    @AfterEach
    void clearRegistry() {
        AgentRuntimeRegistry.clear();
    }

    @Test
    void operatorCanSelectExactParticipantsForAPlaceholderSocialExchange() {
        AgentRuntimeEntry first = entry(801, "First");
        AgentRuntimeEntry second = entry(802, "Second");
        AgentRuntimeEntry third = entry(803, "Third");
        List<AgentRuntimeEntry> participants = List.of(first, second, third);
        participants.forEach(AgentRuntimeRegistry::registerEntry);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        try (var gatewayRuntime = mockStatic(AgentPrimitiveCapabilityGatewayRuntime.class)) {
            gatewayRuntime.when(AgentPrimitiveCapabilityGatewayRuntime::gateway)
                    .thenReturn(gateway);

            assertTrue(AgentTownLifeDirectedEncounterRuntime.start(
                    participants, AgentTownLifeEncounterState.Type.SOCIAL_CHAT,
                    "", 1_000L));
        }

        for (AgentRuntimeEntry entry : participants) {
            AgentTownLifeEncounterState.Snapshot encounter = entry.capabilityStates()
                    .require(AgentTownLifeEncounterState.STATE_KEY).snapshot();
            assertTrue(encounter.active());
            assertEquals(List.of(801, 802, 803), encounter.participantAgentIds());
        }
        AgentTownLifeEncounterCoordinator.finish(first,
                server.agents.integration.AgentRuntimeIdentityRuntime.bot(first), false, 2_000L);
    }

    private static AgentRuntimeEntry entry(int id, String name) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getName()).thenReturn(name);
        when(agent.getMapId()).thenReturn(104000000);
        when(agent.getWorld()).thenReturn(0);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        when(agent.getChair()).thenReturn(-1);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        entry.capabilityStates().require(AgentTownLifeState.STATE_KEY)
                .start(0L, id, 104000000);
        return entry;
    }
}
