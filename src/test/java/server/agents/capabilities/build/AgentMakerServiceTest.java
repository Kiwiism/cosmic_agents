package server.agents.capabilities.build;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.capabilities.build.AgentMakerRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.MakerGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMakerServiceTest {
    @Test
    void makeCrystalsBusyReplyUsesAgentReplyAdapter() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Set<Integer> active = activeMakerSet();
        active.add(100);

        try (MockedStatic<AgentMakerRuntime> replies = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleMakeCrystals(entry, mock(InventoryGateway.class));

            replies.verify(() -> AgentMakerRuntime.replyNow(entry, "still working on the last batch, hang on"));
        } finally {
            active.remove(100);
        }
    }

    @Test
    void disassembleTrashBusyReplyUsesAgentReplyAdapter() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(200);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Set<Integer> active = activeMakerSet();
        active.add(200);

        try (MockedStatic<AgentMakerRuntime> replies = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleDisassembleTrash(entry, mock(InventoryGateway.class));

            replies.verify(() -> AgentMakerRuntime.replyNow(entry, "still working on the last batch, hang on"));
        } finally {
            active.remove(200);
        }
    }

    @Test
    void makeCrystalsNoMakerSkillReplyUsesMakerGateway() {
        Character bot = mock(Character.class);
        when(bot.getId()).thenReturn(300);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        MakerGateway maker = mock(MakerGateway.class);
        when(maker.getMakerSkillLevel(bot)).thenReturn(0);

        try (MockedStatic<AgentMakerRuntime> replies = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleMakeCrystals(entry, mock(InventoryGateway.class), maker);

            replies.verify(() -> AgentMakerRuntime.replyNow(entry, "I can't - I don't have the Maker skill"));
        }
    }

    @Test
    void partnerManagedEntryRejectsMakerInventoryOperationsAtEntry() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        InventoryGateway inventory = mock(InventoryGateway.class);
        MakerGateway maker = mock(MakerGateway.class);
        entry.markPartnerManaged();

        try (MockedStatic<AgentMakerRuntime> replies = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleMakeCrystals(entry, inventory, maker);
            AgentMakerService.handleDisassembleTrash(entry, inventory, maker);

            replies.verify(() -> AgentMakerRuntime.replyNow(
                    eq(entry),
                    eq("I leave crafting and inventory decisions to you while we're adventuring partners.")),
                    times(2));
        }

        verify(maker, never()).getMakerSkillLevel(any());
        verify(maker, never()).makeLeftoverCrystal(any(), anyInt());
        verify(maker, never()).disassembleEquip(any(), anyShort());
        verify(inventory, never()).getMakerCrystalFromLeftover(anyInt());
    }

    @Test
    void queuedMakerStepStopsIfEntryBecomesPartnerManaged() throws Exception {
        Character bot = mock(Character.class);
        Inventory etc = mock(Inventory.class);
        Item leftover = mock(Item.class);
        InventoryGateway inventory = mock(InventoryGateway.class);
        MakerGateway maker = mock(MakerGateway.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        ArgumentCaptor<Runnable> firstStep = ArgumentCaptor.forClass(Runnable.class);

        when(bot.getId()).thenReturn(400);
        when(bot.getInventory(InventoryType.ETC)).thenReturn(etc);
        when(etc.list()).thenReturn(List.of(leftover));
        when(etc.countById(4000000)).thenReturn(100);
        when(leftover.getItemId()).thenReturn(4000000);
        when(inventory.getMakerCrystalFromLeftover(4000000)).thenReturn(4260000);
        when(maker.getMakerSkillLevel(bot)).thenReturn(1);

        try (MockedStatic<AgentMakerRuntime> runtime = mockStatic(AgentMakerRuntime.class)) {
            AgentMakerService.handleMakeCrystals(entry, inventory, maker);
            runtime.verify(() -> AgentMakerRuntime.afterRandomDelay(
                    eq(entry), eq(900), eq(1100), firstStep.capture()));

            entry.markPartnerManaged();
            firstStep.getValue().run();
        } finally {
            activeMakerSet().remove(400);
        }

        verify(maker, never()).makeLeftoverCrystal(any(), anyInt());
        assertFalse(activeMakerSet().contains(400));
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> activeMakerSet() throws ReflectiveOperationException {
        Field active = AgentMakerService.class.getDeclaredField("ACTIVE");
        active.setAccessible(true);
        return (Set<Integer>) active.get(null);
    }
}
