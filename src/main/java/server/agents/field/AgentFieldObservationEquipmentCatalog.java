package server.agents.field;

import java.util.List;
import java.util.Map;

record AgentFieldObservationEquipmentCatalog(
        int schemaVersion,
        int maximumLevel,
        Map<String, List<Integer>> npcShopItemIdsBySlot) {

    AgentFieldObservationEquipmentCatalog {
        if (schemaVersion != 1 || maximumLevel != 25 || npcShopItemIdsBySlot == null
                || npcShopItemIdsBySlot.isEmpty()) {
            throw new IllegalArgumentException("valid observation equipment catalog fields are required");
        }
        npcShopItemIdsBySlot = npcShopItemIdsBySlot.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
