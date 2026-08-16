package server.agents.field;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import server.ItemInformationProvider;
import tools.DatabaseConnection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Regenerates the NPC-shop and Victoria-drop equipment pool used by the observation harness. */
class AgentFieldObservationEquipmentCatalogExportTest {
    private static final Pattern SHOP_EQUIP = Pattern.compile("\\(\\s*\\d+\\s*,\\s*(1\\d{6})\\s*,");
    private static final Pattern DROP_EQUIP = Pattern.compile(
            "\\(\\s*(\\d+)\\s*,\\s*(1\\d{6})\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");

    @Test
    @Disabled("manual NPC-shop/Victoria-drop/WZ catalog regeneration tool")
    void exportsNpcShopEquipmentThroughLevel25() throws Exception {
        if (!DatabaseConnection.isInitialized() && !DatabaseConnection.initializeConnectionPool()) {
            throw new IllegalStateException("database is required to initialize the item provider");
        }
        String sql = Files.readString(Path.of("src/main/resources/db/data/102-shopitems-data.sql"));
        ItemInformationProvider items = ItemInformationProvider.getInstance();
        Map<String, List<Integer>> shopsBySlot = new LinkedHashMap<>();
        Matcher matcher = SHOP_EQUIP.matcher(sql);
        while (matcher.find()) {
            addEligible(items, shopsBySlot, Integer.parseInt(matcher.group(1)));
        }
        Set<Integer> victoriaMobIds = AgentFieldObservationCatalogRepository.defaultRepository().maps().stream()
                .flatMap(map -> map.allowedMobIds().stream()).collect(java.util.stream.Collectors.toSet());
        Map<String, List<Integer>> dropsBySlot = new LinkedHashMap<>();
        String dropsSql = Files.readString(Path.of("src/main/resources/db/data/152-drop-data.sql"));
        matcher = DROP_EQUIP.matcher(dropsSql);
        while (matcher.find()) {
            int dropperId = Integer.parseInt(matcher.group(1));
            int questId = Integer.parseInt(matcher.group(3));
            int chance = Integer.parseInt(matcher.group(4));
            if (victoriaMobIds.contains(dropperId) && questId == 0 && chance > 0) {
                addEligible(items, dropsBySlot, Integer.parseInt(matcher.group(2)));
            }
        }
        normalize(shopsBySlot);
        normalize(dropsBySlot);
        assertFalse(shopsBySlot.isEmpty());
        assertFalse(dropsBySlot.isEmpty());
        Path output = Path.of("tmp/agent-field-observation-equipment.json");
        Files.createDirectories(output.getParent());
        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("schemaVersion", 2);
        catalog.put("maximumLevel", 25);
        catalog.put("npcShopItemIdsBySlot", shopsBySlot);
        catalog.put("victoriaDropItemIdsBySlot", dropsBySlot);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.toFile(), catalog);
    }

    private static void addEligible(ItemInformationProvider items,
                                    Map<String, List<Integer>> bySlot,
                                    int itemId) {
        if (items.isCash(itemId) || items.getEquipById(itemId) == null
                || items.getEquipLevelReq(itemId) > 25) {
            return;
        }
        String slot = items.getEquipmentSlot(itemId);
        if (!AgentFieldObservationEquipmentCatalog.ALLOWED_SOURCE_SLOTS.contains(slot)) {
            return;
        }
        bySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(itemId);
    }

    private static void normalize(Map<String, List<Integer>> bySlot) {
        bySlot.values().forEach(ids -> {
            ids.sort(Comparator.naturalOrder());
            List<Integer> unique = ids.stream().distinct().toList();
            ids.clear();
            ids.addAll(unique);
        });
    }
}
