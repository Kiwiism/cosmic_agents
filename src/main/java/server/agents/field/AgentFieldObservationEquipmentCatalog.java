package server.agents.field;

import java.util.List;
import java.util.Map;
import java.util.Set;

record AgentFieldObservationEquipmentCatalog(
        int schemaVersion,
        int maximumLevel,
        Map<String, List<Integer>> npcShopItemIdsBySlot,
        Map<String, List<Integer>> victoriaDropItemIdsBySlot) {
    static final Set<String> ALLOWED_SOURCE_SLOTS = Set.of(
            "Wp", "WpSi", "Cp", "Ma", "Pn", "MaPn", "So", "Gv", "Ae", "Si");

    AgentFieldObservationEquipmentCatalog {
        if (schemaVersion != 2 || maximumLevel != 25 || npcShopItemIdsBySlot == null
                || npcShopItemIdsBySlot.isEmpty() || victoriaDropItemIdsBySlot == null
                || victoriaDropItemIdsBySlot.isEmpty()) {
            throw new IllegalArgumentException("valid observation equipment catalog fields are required");
        }
        npcShopItemIdsBySlot = validatedSource(npcShopItemIdsBySlot);
        victoriaDropItemIdsBySlot = validatedSource(victoriaDropItemIdsBySlot);
    }

    private static Map<String, List<Integer>> validatedSource(Map<String, List<Integer>> source) {
        if (!ALLOWED_SOURCE_SLOTS.containsAll(source.keySet())
                || source.values().stream().flatMap(List::stream)
                .anyMatch(itemId -> itemId == null || itemId < 1_000_000 || itemId >= 2_000_000)) {
            throw new IllegalArgumentException("observation equipment source contains a disallowed slot or item");
        }
        return source.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
