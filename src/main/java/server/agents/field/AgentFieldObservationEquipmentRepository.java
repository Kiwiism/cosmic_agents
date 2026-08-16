package server.agents.field;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

final class AgentFieldObservationEquipmentRepository {
    private static final String RESOURCE = "/agents/field/victoria-level0-25-npc-shop-equipment.json";
    private static final AgentFieldObservationEquipmentCatalog CATALOG = load();

    private AgentFieldObservationEquipmentRepository() {
    }

    static List<Integer> itemIds() {
        return CATALOG.npcShopItemIdsBySlot().values().stream().flatMap(List::stream).distinct().toList();
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
