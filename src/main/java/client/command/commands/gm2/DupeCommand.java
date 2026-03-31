package client.command.commands.gm2;

import client.Character;
import client.Client;
import client.command.Command;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import server.ItemInformationProvider;

public class DupeCommand extends Command {
    {
        setDescription("Duplicate the first item in your equip inventory.");
    }

    @Override
    public void execute(Client c, String[] params) {
        Character player = c.getPlayer();
        Inventory equip = player.getInventory(InventoryType.EQUIP);

        Item item = null;
        for (short slot = 1; slot <= equip.getSlotLimit(); slot++) {
            item = equip.getItem(slot);
            if (item != null) break;
        }

        if (item == null) {
            player.yellowMessage("No items in your equip inventory.");
            return;
        }

        if (equip.isFull()) {
            player.yellowMessage("Equip inventory is full.");
            return;
        }

        Item copy = item.copy();
        if (!InventoryManipulator.addFromDrop(c, copy, false)) {
            player.yellowMessage("Failed to add item — inventory may be full.");
            return;
        }

        String name = ItemInformationProvider.getInstance().getName(item.getItemId());
        player.yellowMessage("Duped: " + (name != null ? name : item.getItemId()));
    }
}
