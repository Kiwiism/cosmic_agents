package server.economy;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;

import java.util.EnumMap;
import java.util.Map;

final class EconomyParticipantSnapshot {
    private final Character participant;
    private final int mesos;
    private final Map<InventoryType, Inventory> inventories;

    private EconomyParticipantSnapshot(Character participant, int mesos,
                                       Map<InventoryType, Inventory> inventories) {
        this.participant = participant;
        this.mesos = mesos;
        this.inventories = inventories;
    }

    static EconomyParticipantSnapshot capture(Character participant) {
        Map<InventoryType, Inventory> copies = new EnumMap<>(InventoryType.class);
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.UNDEFINED || type == InventoryType.CANHOLD) {
                continue;
            }
            Inventory source = participant.getInventory(type);
            if (source == null) {
                continue;
            }
            Inventory copy = new Inventory(participant, type, source.getSlotLimit());
            for (Item item : source.list()) {
                copy.addItemFromDB(item.copy());
            }
            copies.put(type, copy);
        }
        return new EconomyParticipantSnapshot(participant, participant.getMeso(), copies);
    }

    void restore() {
        for (Map.Entry<InventoryType, Inventory> entry : inventories.entrySet()) {
            participant.setInventory(entry.getKey(), entry.getValue());
        }
        int mesoDelta = mesos - participant.getMeso();
        if (mesoDelta != 0) {
            participant.gainMeso(mesoDelta, false, false, false);
        }
    }

    void disconnectNetworkSession() {
        if (participant.getClient() != null) {
            participant.getClient().disconnectSession();
        }
    }
}
