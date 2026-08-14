package server.agents.economy.catalog;

import java.util.List;
import java.util.Optional;

/** Read-only port; production implementations delegate to Cosmic's established loaders. */
public interface EconomyCatalog {
    String version();
    Optional<ItemFact> item(int itemId);
    List<MonsterDropFact> monsterDrops(int monsterId);
    Optional<NpcShopFact> npcShop(int npcId);
}
