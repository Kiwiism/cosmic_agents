package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class AgentBotInventoryRuntimeAdaptersTest {
    @Test
    void exposesInventoryRuntimeCallbackAdapters() {
        BotEntry entry = new BotEntry(mock(Character.class), null, null);
        Character agent = mock(Character.class);

        assertNotNull(AgentBotInventoryRuntimeAdapters.passiveLootRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry));
        assertNotNull(AgentBotInventoryRuntimeAdapters.tradeTickRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent));
    }
}
