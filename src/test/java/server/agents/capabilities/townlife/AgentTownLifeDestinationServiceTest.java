package server.agents.capabilities.townlife;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.reservation.CharacterSpaceReservationRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTownLifeDestinationServiceTest {
    @AfterEach
    void clearReservations() {
        CharacterSpaceReservationRuntime.clear();
    }

    @Test
    void agentsReserveDifferentRestDestinations() {
        Character first = agent(41);
        Character second = agent(42);
        AgentRuntimeEntry firstEntry = new AgentRuntimeEntry(first, null, null);
        AgentRuntimeEntry secondEntry = new AgentRuntimeEntry(second, null, null);
        AgentTownLifeState firstState = firstEntry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        AgentTownLifeState secondState = secondEntry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        firstState.start(0L, 1);
        secondState.start(0L, 2);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);

        AgentTownLifeDestinationService.Destination firstDestination =
                AgentTownLifeDestinationService.select(firstEntry, first, firstState,
                        AgentTownLifeState.Activity.REST, 1L, gateway);
        AgentTownLifeDestinationService.Destination secondDestination =
                AgentTownLifeDestinationService.select(secondEntry, second, secondState,
                        AgentTownLifeState.Activity.REST, 1L, gateway);

        assertNotNull(firstDestination);
        assertNotNull(secondDestination);
        assertNotEquals(firstDestination.point(), secondDestination.point());
    }

    private Character agent(int id) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(id);
        when(agent.getMapId()).thenReturn(LithHarborTownLifeCatalog.LITH_HARBOR_MAP_ID);
        when(agent.getWorld()).thenReturn(0);
        when(agent.getPosition()).thenReturn(new Point(0, 0));
        return agent;
    }
}
