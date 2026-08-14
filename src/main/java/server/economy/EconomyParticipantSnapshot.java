package server.economy;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.ItemFactory;
import tools.Pair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

final class EconomyParticipantSnapshot {
    private final Character participant;
    private final int mesos;
    private final Map<InventoryType, Inventory> inventories;
    private final Character.EconomyProgressionSnapshot progression;
    private final Character.EconomyQuestSnapshot quests;

    private EconomyParticipantSnapshot(Character participant, int mesos,
                                       Map<InventoryType, Inventory> inventories) {
        this.participant = participant;
        this.mesos = mesos;
        this.inventories = inventories;
        this.progression = participant.captureEconomyProgression();
        this.quests = participant.captureEconomyQuests();
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
        participant.restoreEconomyProgression(progression);
        participant.restoreEconomyQuests(quests);
    }

    void persist(Connection connection, EconomyParticipantSnapshot before) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE characters SET meso = ? WHERE id = ?")) {
            statement.setInt(1, mesos);
            statement.setInt(2, participant.getId());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Economy participant no longer exists: " + participant.getId());
            }
        }

        ArrayList<Pair<Item, InventoryType>> items = new ArrayList<>();
        for (Map.Entry<InventoryType, Inventory> entry : inventories.entrySet()) {
            for (Item item : entry.getValue().list()) {
                items.add(new Pair<>(item.copy(), entry.getKey()));
            }
        }
        ItemFactory.INVENTORY.saveItems(items, participant.getId(), connection);
        participant.persistEconomyProgression(connection, progression,
                before == null || !progression.skills().equals(before.progression.skills()));
        if (before == null || !quests.equals(before.quests))
            participant.persistEconomyQuests(connection, quests);
    }

    void disconnectNetworkSession() {
        if (participant.getClient() != null) {
            participant.getClient().disconnectSession();
        }
    }

    int characterId() { return participant.getId(); }
    int mesos() { return mesos; }
    Map<InventoryType, Inventory> inventories() { return inventories; }
    Character.EconomyProgressionSnapshot progression() { return progression; }
}
