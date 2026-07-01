package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.inventory.AgentInventoryDropService;
import server.agents.integration.AgentBotInventoryStateRuntime;
import server.bots.BotEntry;
import server.bots.BotInventoryManager;
import server.bots.BotMovementManager;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentInventoryTransferServiceTest {
    @Test
    void executeTradeChoiceStartsTradeTransfer() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<BotInventoryManager> inventory = mockStatic(BotInventoryManager.class)) {
            AgentInventoryTransferService.executeChoice("scrolls", true, entry, bot);

            inventory.verify(() -> BotInventoryManager.startTradeTransfer("scrolls", entry, bot));
        }
    }

    @Test
    void executeDropChoiceDropsCategoryAndInhibitsLoot() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentInventoryDropService> drops = mockStatic(AgentInventoryDropService.class);
             MockedStatic<BotMovementManager> movement = mockStatic(BotMovementManager.class)) {
            movement.when(() -> BotMovementManager.delayAfterCurrentTick(20_000)).thenReturn(20_050);

            AgentInventoryTransferService.executeChoice("scrolls", false, entry, bot);

            drops.verify(() -> AgentInventoryDropService.dropCategory(
                    eq("scrolls"),
                    eq(entry),
                    eq(bot),
                    any(BiFunction.class)));
            assertEquals(20_050, AgentBotInventoryStateRuntime.lootInhibitMs(entry));
        }
    }

    @Test
    void startTradeTransferDelegatesToLegacyInventoryStateMachine() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<BotInventoryManager> inventory = mockStatic(BotInventoryManager.class)) {
            AgentInventoryTransferService.startTradeTransfer("scrolls", entry, bot);

            inventory.verify(() -> BotInventoryManager.startTradeTransfer("scrolls", entry, bot));
        }
    }

    @Test
    void countsMesoTransfersThroughAgentBoundary() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        org.mockito.Mockito.when(bot.getMeso()).thenReturn(1_500);

        assertEquals(true, AgentInventoryTransferService.hasTransferableItems("mesos:1000", entry, bot));
        assertEquals(false, AgentInventoryTransferService.hasTransferableItems("mesos:2000", entry, bot));
        assertEquals(1_500, AgentInventoryTransferService.countTransferableItems("mesos", entry, bot));
    }
}
