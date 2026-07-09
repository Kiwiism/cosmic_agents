package server.agents.capabilities.inventory;

import client.inventory.Equip;
import constants.inventory.ItemConstants;
import server.agents.integration.InventoryGateway;

import java.util.Map;

public final class AgentInventorySellTrashPolicy {
    private AgentInventorySellTrashPolicy() {
    }

    public static boolean shouldKeepForSellTrash(InventoryGateway inventory, Equip equip) {
        return shouldKeepForSellTrash(
                equip,
                inventory != null ? inventory.getEquipStats(equip.getItemId()) : null,
                inventory != null ? inventory.getEquipById(equip.getItemId()) : null);
    }

    private static boolean shouldKeepForSellTrash(Equip equip, Map<String, Integer> stats, Equip baseEquip) {
        if (equip.getLevel() > 0) {
            return true;
        }
        if (ItemConstants.isWeapon(equip.getItemId())) {
            if (hasProtectedSellTrashWeaponStat(stats, equip, baseEquip)) {
                return true;
            }
        } else if (equip.getWatk() > 0) {
            return true;
        }
        return hasProtectedSellTrashStat(stats, equip, 6, 10);
    }

    public static boolean hasProtectedSellTrashStat(Map<String, Integer> stats,
                                                    Equip equip,
                                                    int aboveBaseThreshold,
                                                    int pureThreshold) {
        if (stats == null || equip == null) {
            return false;
        }

        boolean str = statProtected(equip.getStr(), stats.getOrDefault("STR", 0), aboveBaseThreshold, pureThreshold);
        boolean dex = statProtected(equip.getDex(), stats.getOrDefault("DEX", 0), aboveBaseThreshold, pureThreshold);
        boolean intt = statProtected(equip.getInt(), stats.getOrDefault("INT", 0), aboveBaseThreshold, pureThreshold);
        boolean luk = statProtected(equip.getLuk(), stats.getOrDefault("LUK", 0), aboveBaseThreshold, pureThreshold);

        int reqJob = stats.getOrDefault("reqJob", 0);
        if (reqJob == 0) {
            return str || dex || intt || luk;
        }

        return ((reqJob & 0x1) != 0 && (str || dex))
                || ((reqJob & 0x2) != 0 && (intt || luk))
                || ((reqJob & 0x4) != 0 && (dex || str))
                || ((reqJob & 0x8) != 0 && (luk || dex))
                || ((reqJob & 0x10) != 0 && (str || dex));
    }

    public static boolean hasProtectedSellTrashWeaponStat(Map<String, Integer> stats, Equip equip, Equip baseEquip) {
        if (equip == null || baseEquip == null) {
            return false;
        }
        boolean mageWeapon = isMageWeapon(stats);
        if (mageWeapon) {
            return equip.getMatk() - baseEquip.getMatk() >= 4;
        }
        return equip.getWatk() - baseEquip.getWatk() >= 4;
    }

    static boolean statProtected(int value, int base, int aboveBaseThreshold, int pureThreshold) {
        return value >= pureThreshold || (value >= aboveBaseThreshold && value > base);
    }

    static boolean isMageWeapon(Map<String, Integer> stats) {
        if (stats == null) {
            return false;
        }
        int reqJob = stats.getOrDefault("reqJob", 0);
        return reqJob != 0 && (reqJob & 0x2) != 0 && (reqJob & ~0x2) == 0;
    }
}
