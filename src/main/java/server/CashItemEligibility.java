package server;

import client.inventory.Equip;
import constants.id.ItemId;
import provider.Data;
import provider.DataTool;

import java.util.HashMap;
import java.util.Map;

final class CashItemEligibility {
    private static final int HORNTAIL_NECKLACE = 1122000;

    private CashItemEligibility() {
    }

    static Map<Integer, Integer> loadVegaSpellScrolls(Data vegaData) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Data entry : vegaData.getChildren()) {
            int scrollId = DataTool.getInt("item", entry, 0);
            String probability = DataTool.getString("prob", entry, "");
            int spellId = switch (probability) {
                case "[R8]0.1" -> ItemId.VEGAS_SPELL_10;
                case "[R8]0.6" -> ItemId.VEGAS_SPELL_60;
                default -> 0;
            };
            if (scrollId > 0 && spellId > 0) {
                result.put(scrollId, spellId);
            }
        }
        return Map.copyOf(result);
    }

    static boolean canUseViciousHammer(Equip equip, Map<String, Integer> stats) {
        return equip != null
                && equip.getVicious() < 2
                && equip.getItemId() != HORNTAIL_NECKLACE
                && stats != null
                && stats.getOrDefault("tuc", 0) > 0
                && stats.getOrDefault("cash", 0) == 0;
    }
}
