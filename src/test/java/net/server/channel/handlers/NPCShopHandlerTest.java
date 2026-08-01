package net.server.channel.handlers;

import client.Character;
import client.Client;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import net.packet.InPacket;
import org.junit.jupiter.api.Test;
import server.Shop;
import server.security.SecurityEventRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NPCShopHandlerTest {
    @Test
    void rejectsSaleWhenPacketItemDoesNotMatchInventorySlot() {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        Shop shop = mock(Shop.class);
        Inventory inventory = mock(Inventory.class);
        Item actual = mock(Item.class);
        InPacket packet = mock(InPacket.class);
        when(client.getPlayer()).thenReturn(player);
        when(player.getShop()).thenReturn(shop);
        when(player.getInventory(InventoryType.USE)).thenReturn(inventory);
        when(inventory.getItem((short) 1)).thenReturn(actual);
        when(actual.getItemId()).thenReturn(2000001);
        when(packet.readByte()).thenReturn((byte) 1);
        when(packet.readShort()).thenReturn((short) 1, (short) 3);
        when(packet.readInt()).thenReturn(2000000);

        new NPCShopHandler().handlePacket(packet, client);

        verify(shop, never()).sell(client, InventoryType.USE, (short) 1, (short) 3);
        assertEquals("sale-item-mismatch",
                SecurityEventRuntime.snapshot().getLast().evidence().get("reason"));
    }

    @Test
    void rejectsMutationWithoutActiveShopSession() {
        Client client = mock(Client.class);
        Character player = mock(Character.class);
        InPacket packet = mock(InPacket.class);
        when(client.getPlayer()).thenReturn(player);
        when(packet.readByte()).thenReturn((byte) 0);

        new NPCShopHandler().handlePacket(packet, client);

        assertEquals("no-active-shop", SecurityEventRuntime.snapshot().getLast().evidence().get("reason"));
    }
}
