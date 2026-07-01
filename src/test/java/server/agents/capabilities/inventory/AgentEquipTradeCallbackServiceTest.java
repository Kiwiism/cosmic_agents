package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService.ClassificationCallbacks;
import server.agents.capabilities.inventory.AgentEquipTradeClassificationService.SlowClassificationReport;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentEquipTradeCallbackServiceTest {
    @Test
    void buildsEquipTradeCallbacksFromLegacyOperations() {
        Character agent = mock(Character.class);
        Character owner = mock(Character.class);
        Item item = mock(Item.class);
        List<Item> bagItems = List.of(item);
        Set<Item> selfKeep = Set.of(item);
        SlowClassificationReport report = new SlowClassificationReport(
                1, "agent", "owner", 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1);
        AtomicReference<SlowClassificationReport> warned = new AtomicReference<>();

        ClassificationCallbacks callbacks = AgentEquipTradeCallbackService.equipTradeCallbacks(
                () -> true,
                () -> 50L,
                currentAgent -> {
                    assertSame(agent, currentAgent);
                    return bagItems;
                },
                currentAgent -> {
                    assertSame(agent, currentAgent);
                    return selfKeep;
                },
                currentItem -> currentItem == item,
                () -> owner,
                warned::set);

        assertTrue(callbacks.profileEquips());
        assertEquals(50L, callbacks.slowWarnNs());
        assertSame(bagItems, callbacks.collectEquipBagItems(agent));
        assertSame(selfKeep, callbacks.collectPotentialSelfUpgradeItems(agent));
        assertTrue(callbacks.isReservedForOtherRecipients(item));
        assertSame(owner, callbacks.owner());
        callbacks.warnSlowClassification(report);
        assertSame(report, warned.get());
    }
}
