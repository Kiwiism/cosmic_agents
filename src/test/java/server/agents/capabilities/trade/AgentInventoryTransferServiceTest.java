package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.bots.BotEntry;
import server.bots.BotInventoryManager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentInventoryTransferServiceTest {
    @Test
    void startTradeTransferDelegatesToLegacyInventoryStateMachine() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<BotInventoryManager> inventory = mockStatic(BotInventoryManager.class)) {
            AgentInventoryTransferService.startTradeTransfer("scrolls", entry, bot);

            inventory.verify(() -> BotInventoryManager.startTradeTransfer("scrolls", entry, bot));
        }
    }
}
