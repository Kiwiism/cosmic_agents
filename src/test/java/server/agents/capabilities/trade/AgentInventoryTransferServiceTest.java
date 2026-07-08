package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.movement.AgentMovementTimers;
import server.agents.capabilities.inventory.AgentInventoryDropService;
import server.agents.capabilities.inventory.AgentInventoryStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentInventoryTransferServiceTest {
    @Test
    void executeTradeChoiceStartsTradeTransfer() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentTradeTransferRouter> router = mockStatic(AgentTradeTransferRouter.class)) {
            AgentInventoryTransferService.executeChoice("scrolls", true, entry, bot);

            router.verify(() -> AgentTradeTransferRouter.routeCategoryTransfer(
                    eq("scrolls"),
                    eq(false),
                    eq(false),
                    eq(false),
                    anyLong(),
                    any(AgentTradeTransferRouter.TransferCallbacks.class)));
        }
    }

    @Test
    void executeDropChoiceDropsCategoryAndInhibitsLoot() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentInventoryDropService> drops = mockStatic(AgentInventoryDropService.class)) {
            AgentInventoryTransferService.executeChoice("scrolls", false, entry, bot);

            drops.verify(() -> AgentInventoryDropService.dropCategory(
                    eq("scrolls"),
                    eq(entry),
                    eq(bot),
                    any(BiFunction.class)));
            assertEquals(AgentMovementTimers.delayAfterCurrentTick(20_000),
                    AgentInventoryStateRuntime.lootInhibitMs(entry));
        }
    }

    @Test
    void startTradeTransferRoutesThroughAgentTradeRouter() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        when(bot.getTrade()).thenReturn(null);

        try (MockedStatic<AgentTradeTransferRouter> router = mockStatic(AgentTradeTransferRouter.class)) {
            AgentInventoryTransferService.startTradeTransfer("scrolls", entry, bot);

            router.verify(() -> AgentTradeTransferRouter.routeCategoryTransfer(
                    eq("scrolls"),
                    eq(false),
                    eq(false),
                    eq(false),
                    anyLong(),
                    any(AgentTradeTransferRouter.TransferCallbacks.class)));
        }
    }

    @Test
    void countsMesoTransfersThroughAgentBoundary() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        org.mockito.Mockito.when(bot.getMeso()).thenReturn(1_500);

        assertEquals(true, AgentInventoryTransferService.hasTransferableItems("mesos:1000", entry, bot));
        assertEquals(false, AgentInventoryTransferService.hasTransferableItems("mesos:2000", entry, bot));
        assertEquals(1_500, AgentInventoryTransferService.countTransferableItems("mesos", entry, bot));
    }

    @Test
    void equipsGroupMessageUsesAgentDialogueCatalogPools() {
        try (MockedStatic<AgentTradeDialogueService> dialogue = mockStatic(AgentTradeDialogueService.class)) {
            dialogue.when(() -> AgentTradeDialogueService.equipsGroupMessage("equips:reserved_for_other"))
                    .thenReturn("other");
            dialogue.when(() -> AgentTradeDialogueService.equipsGroupMessage("equips:reserved_for_self"))
                    .thenReturn("self");
            dialogue.when(() -> AgentTradeDialogueService.equipsGroupMessage("equips:normal"))
                    .thenReturn(null);

            assertEquals("other", AgentInventoryTransferService.equipsGroupMessage("equips:reserved_for_other"));
            assertEquals("self", AgentInventoryTransferService.equipsGroupMessage("equips:reserved_for_self"));
            assertNull(AgentInventoryTransferService.equipsGroupMessage("equips:normal"));
        }
    }
}
