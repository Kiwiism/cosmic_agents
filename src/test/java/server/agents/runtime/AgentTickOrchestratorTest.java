package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotTickCadenceStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentTickOrchestratorTest {
    @Test
    void prepareTickRecordsNonAiTickUntilCadenceIsDue() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);

        boolean runAiTick = AgentTickOrchestrator.prepareTick(entry, 100, 250, 1_000L);

        assertFalse(runAiTick);
        assertFalse(AgentBotTickStateRuntime.lastTickWasAi(entry));
        assertEquals(1_000L, AgentBotTickStateRuntime.lastTickAtMs(entry));
        assertEquals(100, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }

    @Test
    void prepareTickRecordsAiTickAndCarriesCadenceRemainder() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        entry.setAiTickAccumulatorMs(200);

        boolean runAiTick = AgentTickOrchestrator.prepareTick(entry, 100, 250, 2_000L);

        assertTrue(runAiTick);
        assertTrue(AgentBotTickStateRuntime.lastTickWasAi(entry));
        assertEquals(2_000L, AgentBotTickStateRuntime.lastTickAtMs(entry));
        assertEquals(50, AgentBotTickCadenceStateRuntime.aiTickAccumulatorMs(entry));
    }
}
