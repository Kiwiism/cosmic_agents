package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.economy.persistence.EconomyBootstrapSnapshot;
import server.economy.EconomyItemEvidence;

import java.util.ArrayList;
import java.util.List;

public final class CosmicEconomyBootstrapSnapshot {
    private CosmicEconomyBootstrapSnapshot() { }

    public static EconomyBootstrapSnapshot capture(Character character) {
        List<EconomyBootstrapSnapshot.Holding> holdings = new ArrayList<>();
        for (InventoryType type : InventoryType.values()) {
            if (type == InventoryType.UNDEFINED || type == InventoryType.CANHOLD
                    || character.getInventory(type) == null) continue;
            for (Item item : character.getInventory(type).list()) {
                EconomyItemEvidence.Description description = EconomyItemEvidence.describe(item);
                holdings.add(new EconomyBootstrapSnapshot.Holding(item.getItemId(), item.getQuantity(),
                        type.name(), description.fingerprint(), description.attributes()));
            }
        }
        Character.EconomyProgressionSnapshot progression = character.captureEconomyProgression();
        return new EconomyBootstrapSnapshot(character.getId(), character.getMeso(),
                progression.level(), progression.experience(), holdings);
    }
}
