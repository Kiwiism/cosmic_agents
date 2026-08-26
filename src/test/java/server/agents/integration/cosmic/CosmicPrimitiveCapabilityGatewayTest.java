package server.agents.integration.cosmic;

import client.Character;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.quest.Quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CosmicPrimitiveCapabilityGatewayTest {
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
