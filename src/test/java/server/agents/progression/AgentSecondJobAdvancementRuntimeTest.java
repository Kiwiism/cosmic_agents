package server.agents.progression;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentCharacterStateSnapshot;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSecondJobAdvancementRuntimeTest {
    @Test
    void examinerTransitionStopsCombatOnceWithoutDiscardingApproachRouteEveryTick() {
        Character agent = mock(Character.class);
        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSecondJobAdvancementState state = entry.capabilityStates()
                .require(AgentSecondJobAdvancementState.STATE_KEY);
        AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require("hunter");
        state.begin(branch.id(), 1L);

        when(gateway.characterState(agent)).thenReturn(
                new AgentCharacterStateSnapshot(300, 30, 1_000, 1_000, 500, 500, true));
        when(gateway.mapId(agent)).thenReturn(branch.trialMapId());
        when(gateway.itemCount(agent, branch.collectionItemId())).thenReturn(branch.requiredCount());
        when(gateway.freeSlots(agent, branch.collectionItemId())).thenReturn(1);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.npcPosition(agent, branch.examinerNpcId())).thenReturn(new Point(1_000, 0));
        when(agent.getPosition()).thenReturn(new Point(0, 0));

        AgentSecondJobAdvancementRuntime.tick(entry, agent, 10L, gateway);
        AgentSecondJobAdvancementRuntime.tick(entry, agent, 20L, gateway);

        verify(gateway, times(1)).stop(entry);
        verify(gateway, times(2)).navigate(entry, new Point(1_000, 0), true);
    }
}
