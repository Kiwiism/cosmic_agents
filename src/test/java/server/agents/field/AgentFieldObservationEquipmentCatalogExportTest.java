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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Regenerates the NPC-shop equipment source pool used by the observation harness. */
class AgentFieldObservationEquipmentCatalogExportTest {
    private static final Pattern SHOP_EQUIP = Pattern.compile("\\(\\s*\\d+\\s*,\\s*(1\\d{6})\\s*,");

    @Test
    @Disabled("manual NPC-shop/WZ catalog regeneration tool")
    void exportsNpcShopEquipmentThroughLevel25() throws Exception {
        if (!DatabaseConnection.isInitialized() && !DatabaseConnection.initializeConnectionPool()) {
            throw new IllegalStateException("database is required to initialize the item provider");
        }
        String sql = Files.readString(Path.of("src/main/resources/db/data/102-shopitems-data.sql"));
        ItemInformationProvider items = ItemInformationProvider.getInstance();
        Map<String, List<Integer>> bySlot = new LinkedHashMap<>();
        Matcher matcher = SHOP_EQUIP.matcher(sql);
        while (matcher.find()) {
            int itemId = Integer.parseInt(matcher.group(1));
            if (items.isCash(itemId) || items.getEquipById(itemId) == null
                    || items.getEquipLevelReq(itemId) > 25) {
                continue;
            }
            String slot = items.getEquipmentSlot(itemId);
            if (slot == null || slot.isBlank() || slot.contains("Cash")) {
                continue;
            }
            bySlot.computeIfAbsent(slot, ignored -> new ArrayList<>()).add(itemId);
        }
        bySlot.values().forEach(ids -> {
            ids.sort(Comparator.naturalOrder());
            List<Integer> unique = ids.stream().distinct().toList();
            ids.clear();
            ids.addAll(unique);
        });
        assertFalse(bySlot.isEmpty());
        Path output = Path.of("tmp/agent-field-observation-equipment.json");
        Files.createDirectories(output.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(output.toFile(),
                Map.of("schemaVersion", 1, "maximumLevel", 25, "npcShopItemIdsBySlot", bySlot));
    }
}
