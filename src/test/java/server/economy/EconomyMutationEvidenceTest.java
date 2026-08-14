package server.economy;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomyMutationEvidenceTest {
    @Test
    void capturesExactMesoAndInventoryDeltasAsMachineReadableEvidence() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(91);
        when(agent.getMeso()).thenReturn(1_000, 850);
        Inventory use = new Inventory(agent, InventoryType.USE, (byte) 24);
        use.addItemFromDB(new Item(2000000, (short) 1, (short) 10));
        for (InventoryType type : InventoryType.values()) {
            if (type != InventoryType.UNDEFINED && type != InventoryType.CANHOLD) {
                when(agent.getInventory(type)).thenReturn(type == InventoryType.USE ? use : null);
            }
        }
        EconomyParticipantSnapshot before = EconomyParticipantSnapshot.capture(agent);
        use.getItem((short) 1).setQuantity((short) 7);
        EconomyParticipantSnapshot after = EconomyParticipantSnapshot.capture(agent);

        EconomyMutationEvidence evidence = EconomyMutationEvidence.between(before, after, null, null);

        assertEquals(-150, evidence.participants().getFirst().mesoDelta());
        assertEquals(-3, evidence.participants().getFirst().itemDeltas().getFirst().quantityDelta());
        assertTrue(evidence.json().contains("\"itemId\":2000000"));
    }
}
