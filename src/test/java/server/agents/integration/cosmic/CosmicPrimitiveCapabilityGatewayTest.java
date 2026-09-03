package server.agents.integration.cosmic;

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentFarmAnchorStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.quest.Quest;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicPrimitiveCapabilityGatewayTest {
    @Test
    void ordinaryPreferredGrindReleasesAnAuthoredCombatAnchor() {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(105100400);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentFarmAnchorStateRuntime.setFarmAnchor(
                entry, new Point(520, 258), agent.getMapId());
        AgentModeStateRuntime.startGrinding(entry);

        CosmicPrimitiveCapabilityGateway.INSTANCE.grind(
                entry, Set.of(6400009), Set.of(8830009));

        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
    }

    @Test
    void questProgressReadsOrdinaryProgressKey() {
        Character agent = mock(Character.class);
        QuestStatus status = mock(QuestStatus.class);
        when(agent.getQuestNoAdd(any(Quest.class))).thenReturn(status);
        when(status.getProgress(3300001)).thenReturn("7");

        assertEquals(7, CosmicPrimitiveCapabilityGateway.INSTANCE
                .questProgress(agent, 2330, 3300001));
    }

    @Test
    void questProgressResolvesInfoNumberToSlotZero() {
        Character agent = mock(Character.class);
        QuestStatus status = mock(QuestStatus.class);
        when(agent.getQuestNoAdd(any(Quest.class))).thenReturn(status);
        when(status.getInfoNumber()).thenReturn((short) 2314);
        when(status.getProgress(0)).thenReturn("1");

        assertEquals(1, CosmicPrimitiveCapabilityGateway.INSTANCE
                .questProgress(agent, 2314, 2314));
    }
}
