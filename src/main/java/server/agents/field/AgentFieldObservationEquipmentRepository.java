package server.agents.field;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

final class AgentFieldObservationEquipmentRepository {
    private static final String RESOURCE = "/agents/field/victoria-level0-25-equipment.json";
    private static final AgentFieldObservationEquipmentCatalog CATALOG = load();

    private AgentFieldObservationEquipmentRepository() {
    }

    static List<Integer> itemIds() {
        return java.util.stream.Stream.concat(npcShopItemIds().stream(), victoriaDropItemIds().stream())
                .distinct().toList();
    }

    static List<Integer> npcShopItemIds() {
        return flattened(CATALOG.npcShopItemIdsBySlot());
    }

    static List<Integer> victoriaDropItemIds() {
        return flattened(CATALOG.victoriaDropItemIdsBySlot());
    }

    static java.util.Set<String> sourceSlots() {
        return java.util.stream.Stream.concat(
                        CATALOG.npcShopItemIdsBySlot().keySet().stream(),
                        CATALOG.victoriaDropItemIdsBySlot().keySet().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static List<Integer> flattened(java.util.Map<String, List<Integer>> bySlot) {
        return bySlot.values().stream().flatMap(List::stream).distinct().toList();
    }

    private static AgentFieldObservationEquipmentCatalog load() {
        try (InputStream input = AgentFieldObservationEquipmentRepository.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing observation equipment catalog " + RESOURCE);
            }
            return new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .readValue(input, AgentFieldObservationEquipmentCatalog.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not load observation equipment catalog", failure);
        }
    }
}
