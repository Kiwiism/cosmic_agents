package server.agents.progression;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.contracts.AgentResourceCategory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCareerShopCatalogTest {
    @Test
    void basicPotionsUseNearestRoutableVictoriaShop() {
        AgentCareerBuildBundle pirate = AgentCareerBuildBundleRepository.defaultRepository()
                .find("pirate-gun-standard-v1").orElseThrow();

        AgentCareerShopCatalog.ShopStop stop = AgentCareerShopCatalog.forSupply(
                pirate, AgentResourceCategory.MP_POTION, 101010000);

        assertEquals(101000002, stop.mapId());
        assertEquals(1031100, stop.npcId());
    }

    @Test
    void careerSpecificSuppliesKeepCareerShop() {
        AgentCareerBuildBundle pirate = AgentCareerBuildBundleRepository.defaultRepository()
                .find("pirate-gun-standard-v1").orElseThrow();

        AgentCareerShopCatalog.ShopStop stop = AgentCareerShopCatalog.forSupply(
                pirate, AgentResourceCategory.BULLET, 101010000);

        assertEquals(120000200, stop.mapId());
        assertEquals(1091002, stop.npcId());
    }
}
