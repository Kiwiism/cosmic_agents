package server.partner;

import client.inventory.Item;
import client.inventory.ModifyInventory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryPacketChunkerTest {
    @Test
    void chunksFullInventoryRefreshWithinPacketByteCount() {
        List<ModifyInventory> operations = new ArrayList<>();
        for (short slot = 1; slot <= 450; slot++) {
            operations.add(new ModifyInventory(0, new Item(2000000, slot, (short) 1)));
        }

        List<List<ModifyInventory>> chunks = InventoryPacketChunker.chunk(operations);

        assertEquals(List.of(200, 200, 50), chunks.stream().map(List::size).toList());
    }

    @Test
    void rejectsOperationLimitThatCannotFitProtocolByte() {
        assertThrows(IllegalArgumentException.class,
                () -> InventoryPacketChunker.chunk(List.of(), 256));
    }
}
