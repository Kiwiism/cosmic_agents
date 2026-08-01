package client.processor.npc;

import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageRequestValidatorTest {
    @Test
    void acceptsOnlyDefinedStorageActions() {
        assertFalse(StorageRequestValidator.isKnownAction((byte) 3));
        assertTrue(StorageRequestValidator.isKnownAction((byte) 4));
        assertTrue(StorageRequestValidator.isKnownAction((byte) 8));
        assertFalse(StorageRequestValidator.isKnownAction((byte) 9));
    }

    @Test
    void validatesZeroBasedTakeoutSlotsAndInventoryType() {
        assertTrue(StorageRequestValidator.isValidTakeout(
                InventoryType.ETC.getType(), (byte) 0, (byte) 4));
        assertFalse(StorageRequestValidator.isValidTakeout(
                InventoryType.UNDEFINED.getType(), (byte) 0, (byte) 4));
        assertFalse(StorageRequestValidator.isValidTakeout(
                InventoryType.ETC.getType(), (byte) 4, (byte) 4));
    }

    @Test
    void validatesOneBasedCharacterInventorySlots() {
        assertFalse(StorageRequestValidator.isValidInventorySlot((short) 0, (byte) 24));
        assertTrue(StorageRequestValidator.isValidInventorySlot((short) 1, (byte) 24));
        assertTrue(StorageRequestValidator.isValidInventorySlot((short) 24, (byte) 24));
        assertFalse(StorageRequestValidator.isValidInventorySlot((short) 25, (byte) 24));
    }
}
