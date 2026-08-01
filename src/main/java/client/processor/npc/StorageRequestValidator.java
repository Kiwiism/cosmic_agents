package client.processor.npc;

import client.inventory.InventoryType;

final class StorageRequestValidator {
    private StorageRequestValidator() {
    }

    static boolean isKnownAction(byte mode) {
        return mode >= 4 && mode <= 8;
    }

    static boolean isValidTakeout(byte inventoryType, byte slot, byte storageSlots) {
        return InventoryType.getByType(inventoryType) != InventoryType.UNDEFINED
                && slot >= 0 && slot < storageSlots;
    }

    static boolean isValidInventorySlot(short slot, byte slotLimit) {
        return slot >= 1 && slot <= slotLimit;
    }
}
