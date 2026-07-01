package server.agents.capabilities.trade;

import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentGroupedTradeTransferServiceTest {
    @Test
    void startsFirstAvailableEquipsGroupAndSetsMessage() {
        Item reserved = mock(Item.class);
        AgentEquipTradeGroups groups = new AgentEquipTradeGroups(
                List.of(),
                List.of(reserved),
                List.of(mock(Item.class)));
        AtomicReference<String> startedCategory = new AtomicReference<>();
        AtomicReference<List<Item>> startedItems = new AtomicReference<>();
        AtomicReference<String> message = new AtomicReference<>();

        AgentGroupedTradeTransferService.startEquipsGroupTradeTransfer(
                groups,
                (category, items) -> {
                    startedCategory.set(category);
                    startedItems.set(items);
                },
                category -> "msg:" + category,
                (category, text) -> message.set(text),
                ignored -> {});

        assertEquals("equips:reserved_for_other", startedCategory.get());
        assertEquals(List.of(reserved), startedItems.get());
        assertEquals("msg:equips:reserved_for_other", message.get());
    }

    @Test
    void repliesWhenNoEquipGroupsAreAvailable() {
        List<String> replies = new ArrayList<>();

        AgentGroupedTradeTransferService.startEquipsGroupTradeTransfer(
                new AgentEquipTradeGroups(List.of(), List.of(), List.of()),
                (category, items) -> {},
                category -> null,
                (category, text) -> {},
                replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).contains("equips"));
    }

    @Test
    void startsFirstAvailableAmmoGroup() {
        Item ownAmmo = mock(Item.class);
        AtomicReference<String> startedCategory = new AtomicReference<>();
        AtomicReference<List<Item>> startedItems = new AtomicReference<>();

        AgentGroupedTradeTransferService.startAmmoGroupTradeTransfer(
                new AmmoTradeGroups(List.of(), List.of(ownAmmo)),
                (category, items) -> {
                    startedCategory.set(category);
                    startedItems.set(items);
                },
                ignored -> {});

        assertEquals("ammo:own", startedCategory.get());
        assertEquals(List.of(ownAmmo), startedItems.get());
    }
}
