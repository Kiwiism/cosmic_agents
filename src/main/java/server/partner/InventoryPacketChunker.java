package server.partner;

import client.inventory.ModifyInventory;

import java.util.ArrayList;
import java.util.List;

public final class InventoryPacketChunker {
    public static final int DEFAULT_MAX_OPERATIONS = 200;

    private InventoryPacketChunker() {
    }

    public static List<List<ModifyInventory>> chunk(List<ModifyInventory> operations) {
        return chunk(operations, DEFAULT_MAX_OPERATIONS);
    }

    public static List<List<ModifyInventory>> chunk(List<ModifyInventory> operations, int maxOperations) {
        if (maxOperations < 1 || maxOperations > 255) {
            throw new IllegalArgumentException("Inventory packet operation count must be between 1 and 255");
        }
        if (operations == null || operations.isEmpty()) {
            return List.of();
        }
        List<List<ModifyInventory>> chunks = new ArrayList<>((operations.size() + maxOperations - 1) / maxOperations);
        for (int offset = 0; offset < operations.size(); offset += maxOperations) {
            chunks.add(List.copyOf(operations.subList(offset, Math.min(operations.size(), offset + maxOperations))));
        }
        return List.copyOf(chunks);
    }
}
