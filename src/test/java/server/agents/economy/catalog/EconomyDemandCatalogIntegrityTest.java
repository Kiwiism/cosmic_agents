package server.agents.economy.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import server.agents.economy.scenario.EconomyConfigLoader;
import constants.inventory.ItemConstants;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomyDemandCatalogIntegrityTest {
    @Test
    void configuredStallPermitIsTheRealRegularPlayerShopPermitInWz() throws Exception {
        String wzPath = System.getProperty("wz-path");
        Assumptions.assumeTrue(wzPath != null, "set -Dwz-path for authoritative WZ integration");
        int permitId = new EconomyConfigLoader().load().config().bootstrap.shopPermitItemId;
        assertEquals(5_140_000, permitId);
        assertTrue(ItemConstants.isPlayerShop(permitId));
        assertTrue(!ItemConstants.isHiredMerchant(permitId));

        String cashNames = Files.readString(Path.of(wzPath, "String.wz", "Cash.img.xml"));
        assertTrue(cashNames.contains("<imgdir name=\"5140000\"><string name=\"name\" value=\"Regular Store Permit\""));
        assertTrue(cashNames.contains("Can sell up to 16 items at once."));
        String cashItems = Files.readString(Path.of(wzPath, "Item.wz", "Cash", "0514.img.xml"));
        assertTrue(cashItems.contains("<imgdir name=\"05140000\">"));
    }

    @Test
    void everyConfiguredResourceAndDispositionNpcIsBackedByRealSqlAndMapEvidence() throws Exception {
        var config = new EconomyConfigLoader().load().config();
        String shops = resource("/db/data/101-shops-data.sql");
        String stock = resource("/db/data/102-shopitems-data.sql");
        Map<Integer, Integer> shopByNpc = new HashMap<>();
        var shopRows = Pattern.compile("\\((\\d+),\\s*(\\d+)\\)").matcher(shops);
        while (shopRows.find()) shopByNpc.put(Integer.parseInt(shopRows.group(2)),
                Integer.parseInt(shopRows.group(1)));

        Map<Integer, Set<Integer>> itemsByShop = new HashMap<>();
        var itemRows = Pattern.compile("\\((\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+)\\)")
                .matcher(stock);
        while (itemRows.find()) itemsByShop.computeIfAbsent(Integer.parseInt(itemRows.group(1)),
                ignored -> new HashSet<>()).add(Integer.parseInt(itemRows.group(2)));

        NpcLocationIndex locations = NpcLocationIndex.loadDefault();
        for (var target : config.demand.resourceTargets) {
            assertTrue(shopByNpc.containsKey(target.npcId), "missing real shop NPC " + target.npcId);
            assertTrue(itemsByShop.getOrDefault(shopByNpc.get(target.npcId), Set.of())
                    .contains(target.itemId), "NPC " + target.npcId + " does not sell " + target.itemId);
            assertTrue(locations.primaryMap(target.npcId).isPresent(),
                    "missing original NPC map evidence " + target.npcId);
        }
        assertTrue(shopByNpc.containsKey(config.npcCommerce.dispositionNpcId));
        assertTrue(locations.primaryMap(config.npcCommerce.dispositionNpcId).isPresent());
    }

    private static String resource(String path) throws Exception {
        try (InputStream input = EconomyDemandCatalogIntegrityTest.class.getResourceAsStream(path)) {
            if (input == null) throw new AssertionError("missing " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
