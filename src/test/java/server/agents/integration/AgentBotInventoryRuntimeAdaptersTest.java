package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.trade.AgentTradeDialogueService;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotInventoryRuntimeAdaptersTest {
    @Test
    void exposesInventoryRuntimeCallbackAdapters() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        Character agent = mock(Character.class);

        assertNotNull(AgentBotInventoryRuntimeAdapters.passiveLootRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry));
        assertNotNull(AgentBotInventoryRuntimeAdapters.tradeTickRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks());
        assertNotNull(AgentBotInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent));
    }

    @Test
    void manualTradeGreetingUsesAgentTradeDialogueService() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        try (MockedStatic<AgentTradeDialogueService> tradeDialogue = mockStatic(AgentTradeDialogueService.class)) {
            tradeDialogue.when(AgentTradeDialogueService::manualTradeGreeting).thenReturn("hello");

            String greeting = AgentBotInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry).manualTradeGreeting();

            assertEquals("hello", greeting);
            tradeDialogue.verify(AgentTradeDialogueService::manualTradeGreeting);
        }
    }
}
