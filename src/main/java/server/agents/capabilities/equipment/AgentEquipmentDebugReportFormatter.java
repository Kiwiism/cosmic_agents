package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import server.agents.integration.InventoryGateway;

import java.util.Map;

public final class AgentEquipmentDebugReportFormatter {
    private AgentEquipmentDebugReportFormatter() {
    }

    public interface ItemInfo {
        String getName(int itemId);
        String getEquipmentSlot(int itemId);
        Map<String, Integer> getEquipStats(int itemId);
        int getEquipLevelReq(int itemId);

        static ItemInfo from(InventoryGateway inventory) {
            return new ItemInfo() {
                @Override public String getName(int itemId) {
                    return inventory.getItemName(itemId);
                }
                @Override public String getEquipmentSlot(int itemId) {
                    return inventory.getEquipmentSlot(itemId);
                }
                @Override public Map<String, Integer> getEquipStats(int itemId) {
                    return inventory.getEquipStats(itemId);
                }
                @Override public int getEquipLevelReq(int itemId) {
                    return inventory.getEquipLevelRequirement(itemId);
                }
            };
        }
    }

    public static String itemHeader(boolean includeSelfReserve) {
        return String.format("%-3s %-30s %-7s %4s %4s %4s %4s %4s %4s %4s %4s %4s %4s %5s %5s%s   reqs%n",
                "pos", "name", "slot", "STR", "DEX", "INT", "LUK", "WAK", "MAK", "WDF", "MDF", "ACC", "AVD", "HP", "MP",
                includeSelfReserve ? "  SELF" : "");
    }

    public static void appendItemRow(StringBuilder sb,
                                     InventoryGateway inventory,
                                     Equip equip,
                                     short position,
                                     Boolean selfReserve) {
        appendItemRow(sb, ItemInfo.from(inventory), equip, position, selfReserve);
    }

    static void appendItemRow(StringBuilder sb, ItemInfo itemInfo, Equip equip, short position, Boolean selfReserve) {
        String name = itemInfo.getName(equip.getItemId());
        if (name == null) name = "id=" + equip.getItemId();
        if (name.length() > 30) name = name.substring(0, 30);
        String textSlot = itemInfo.getEquipmentSlot(equip.getItemId());
        sb.append(String.format("%-3d %-30s %-7s %4d %4d %4d %4d %4d %4d %4d %4d %4d %4d %5d %5d%s   ",
                position, name, textSlot == null ? "?" : textSlot,
                equip.getStr(), equip.getDex(), equip.getInt(), equip.getLuk(),
                equip.getWatk(), equip.getMatk(), equip.getWdef(), equip.getMdef(),
                equip.getAcc(), equip.getAvoid(), equip.getHp(), equip.getMp(),
                selfReserve == null ? "" : String.format("  %-4s", selfReserve ? "Y" : "N")));

        Map<String, Integer> stats = itemInfo.getEquipStats(equip.getItemId());
        if (stats != null) {
            int requiredLevel = itemInfo.getEquipLevelReq(equip.getItemId());
            int requiredJob = stats.getOrDefault("reqJob", 0);
            int requiredStr = stats.getOrDefault("reqSTR", 0);
            int requiredDex = stats.getOrDefault("reqDEX", 0);
            int requiredInt = stats.getOrDefault("reqINT", 0);
            int requiredLuk = stats.getOrDefault("reqLUK", 0);
            int requiredFame = stats.getOrDefault("reqPOP", 0);
            sb.append("lv").append(requiredLevel).append(" job").append(requiredJob);
            if (requiredStr > 0) sb.append(" str").append(requiredStr);
            if (requiredDex > 0) sb.append(" dex").append(requiredDex);
            if (requiredInt > 0) sb.append(" int").append(requiredInt);
            if (requiredLuk > 0) sb.append(" luk").append(requiredLuk);
            if (requiredFame > 0) sb.append(" pop").append(requiredFame);
        }
        sb.append('\n');
    }

    public static int safeMapId(Character agent) {
        if (agent == null) return -1;
        try {
            return agent.getMap() != null ? agent.getMap().getId() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }
}
