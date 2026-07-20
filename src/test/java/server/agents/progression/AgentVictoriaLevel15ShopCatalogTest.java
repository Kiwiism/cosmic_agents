package server.agents.progression;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentVictoriaLevel15ShopCatalogTest {
    @Test
    void everyCatalogedShopAndRelevantStockItemExistsInBootstrapSql() throws IOException {
        String shops = resource("/db/data/101-shops-data.sql");
        String stock = resource("/db/data/102-shopitems-data.sql");

        for (AgentVictoriaLevel15Catalog.Career career
                : AgentVictoriaLevel15CatalogRepository.defaultRepository().catalog().careers()) {
            assertTrue(shops.contains("(" + career.shopNpcId() + ", " + career.shopNpcId() + ")"),
                    () -> "missing shop row for NPC " + career.shopNpcId());
            for (int itemId : career.verifiedShopItemIds()) {
                assertTrue(stock.contains("(" + career.shopNpcId() + ", " + itemId + ","),
                        () -> "shop " + career.shopNpcId() + " does not sell cataloged item " + itemId);
            }
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream input = AgentVictoriaLevel15ShopCatalogTest.class.getResourceAsStream(path)) {
            assertNotNull(input, "missing test resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
