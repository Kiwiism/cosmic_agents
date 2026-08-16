package server.agents.economy.catalog;

import java.util.List;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Read-only port; production implementations delegate to Cosmic's established loaders. */
public interface EconomyCatalog {
    String version();
    Optional<ItemFact> item(int itemId);
    List<MonsterDropFact> monsterDrops(int monsterId);
    default Optional<MonsterFact> monster(int monsterId) { return Optional.empty(); }
    default List<GlobalDropFact> globalDrops(int mapId) { return List.of(); }
    default Optional<EquipmentRollFact> rollEquipment(int itemId, DoubleSupplier random) {
        return Optional.empty();
    }
    Optional<NpcShopFact> npcShop(int npcId);
}
