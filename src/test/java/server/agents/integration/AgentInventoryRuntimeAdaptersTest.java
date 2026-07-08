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

class AgentInventoryRuntimeAdaptersTest {
    @Test
    void exposesInventoryRuntimeCallbackAdapters() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        Character agent = mock(Character.class);

        assertNotNull(AgentInventoryRuntimeAdapters.passiveLootRuntimeCallbacks());
        assertNotNull(AgentInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry));
        assertNotNull(AgentInventoryRuntimeAdapters.tradeTickRuntimeCallbacks());
        assertNotNull(AgentInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks());
        assertNotNull(AgentInventoryRuntimeAdapters.transferAvailabilityRuntimeCallbacks());
        assertNotNull(AgentInventoryRuntimeAdapters.tradeRuntimeCallbacks(entry, agent));
    }

    @Test
    void manualTradeGreetingUsesAgentTradeDialogueService() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);

        try (MockedStatic<AgentTradeDialogueService> tradeDialogue = mockStatic(AgentTradeDialogueService.class)) {
            tradeDialogue.when(AgentTradeDialogueService::manualTradeGreeting).thenReturn("hello");

            String greeting = AgentInventoryRuntimeAdapters.manualTradeRuntimeCallbacks(entry).manualTradeGreeting();

            assertEquals("hello", greeting);
            tradeDialogue.verify(AgentTradeDialogueService::manualTradeGreeting);
        }
    }
}
