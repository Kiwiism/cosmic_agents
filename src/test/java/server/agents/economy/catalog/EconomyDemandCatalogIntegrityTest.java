package server.agents.economy.catalog;

import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.EconomyConfigLoader;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EconomyDemandCatalogIntegrityTest {
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
