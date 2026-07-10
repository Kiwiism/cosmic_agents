package client.inventory.manipulator;

import client.inventory.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryManipulatorMetadataTest {
    @Test
    void copiesOwnerAndGiftMetadataToSplitStack() {
        Item source = new Item(2000000, (short) 0, (short) 3);
        source.setOwner("Crafter");
        source.setGiftFrom("Sender");
        Item target = new Item(source.getItemId(), (short) 0, source.getQuantity());

        InventoryManipulator.copyTransferMetadata(source, target);

        assertEquals("Crafter", target.getOwner());
        assertEquals("Sender", target.getGiftFrom());
    }
}
