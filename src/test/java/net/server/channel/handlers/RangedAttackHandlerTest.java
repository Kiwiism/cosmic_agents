package net.server.channel.handlers;

import client.BotClient;
import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.id.ItemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RangedAttackHandlerTest {
    @Test
    void shouldConsumeProjectileFromBotCharacterWhenClientPlayerIsUnset() {
        Character bot = mock(Character.class);
        Inventory useInventory = new Inventory(bot, InventoryType.USE, (byte) 8);
        Item stars = new Item(ItemId.SUBI_THROWING_STARS, (short) 1, (short) 10);
        useInventory.addItem(stars);
        when(bot.getInventory(InventoryType.USE)).thenReturn(useInventory);

        BotClient client = new BotClient(0, 1);

        RangedAttackHandler.consumeProjectile(bot, client, (short) 1, (short) 1);

        assertEquals(9, useInventory.getItem((short) 1).getQuantity());
    }
}
