package server.agents.capabilities.inventory;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.game.GameConstants;
import server.agents.capabilities.equipment.AgentWeaponCompatibilityPolicy;

import java.util.Comparator;
import java.util.List;

public final class AgentInventoryTradePolicy {
    public static final int TRADE_WINDOW_ITEM_LIMIT = 9;
    private static final String RESERVED_EQUIPS_CATEGORY_PREFIX = "equips:reserved:";

    private AgentInventoryTradePolicy() {
    }

    public enum EquipsGroup {
        NORMAL, RESERVED_FOR_OTHER, RESERVED_FOR_SELF;

        public String categoryString() {
            return "equips:" + name().toLowerCase();
        }

        public EquipsGroup next() {
            EquipsGroup[] values = values();
            int next = ordinal() + 1;
            return next < values.length ? values[next] : null;
        }
    }

    public enum AmmoGroup {
        NON_OWN, OWN;

        public String categoryString() {
            return "ammo:" + name().toLowerCase();
        }

        public AmmoGroup next() {
            AmmoGroup[] values = values();
            int next = ordinal() + 1;
            return next < values.length ? values[next] : null;
        }
    }

    public static String reservedEquipsCategory(int requestedPage) {
        return RESERVED_EQUIPS_CATEGORY_PREFIX + requestedPage;
    }

    public static boolean isReservedEquipsCategory(String category) {
        return category != null && category.startsWith(RESERVED_EQUIPS_CATEGORY_PREFIX);
    }

    public static int requestedReservedEquipsPage(String category) {
        if (!isReservedEquipsCategory(category)) {
            return 1;
        }
        try {
            return Integer.parseInt(category.substring(RESERVED_EQUIPS_CATEGORY_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    public static EquipsGroup equipsGroupFromCategory(String category) {
        if (category == null || !category.startsWith("equips:")) return null;
        try {
            return EquipsGroup.valueOf(category.substring("equips:".length()).toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static AmmoGroup ammoGroupFromCategory(String category) {
        if (category == null || !category.startsWith("ammo:")) return null;
        try {
            return AmmoGroup.valueOf(category.substring("ammo:".length()).toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static int clampTradePage(int requestedPage, int totalItems) {
        int maxPage = Math.max(1, (totalItems + TRADE_WINDOW_ITEM_LIMIT - 1) / TRADE_WINDOW_ITEM_LIMIT);
        return Math.max(1, Math.min(requestedPage, maxPage));
    }

    public static boolean isMesoCategory(String category) {
        return category != null && (category.equals("mesos") || category.startsWith("mesos:"));
    }

    public static int requestedTradeMesos(String category) {
        if (!isMesoCategory(category)) {
            return 0;
        }
        if ("mesos".equals(category)) {
            return -1;
        }

        try {
            return Integer.parseInt(category.substring("mesos:".length()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static String notEnoughMesosReply(int requestedMesos, int currentMesos) {
        return "i only have " + GameConstants.numberWithCommas(currentMesos)
                + " mesos rn, not " + GameConstants.numberWithCommas(requestedMesos);
    }

    public static int itemQuantitySum(List<Item> items) {
        int total = 0;
        for (Item item : items) {
            total += item.getInventoryType() == InventoryType.EQUIP ? 1 : Math.max(0, item.getQuantity());
        }
        return total;
    }

    public static List<Item> sortItemsByItemId(List<Item> items) {
        return items.stream()
                .sorted(Comparator.comparingInt(Item::getItemId).thenComparingInt(Item::getPosition))
                .toList();
    }

    public static List<Item> sortEquipsByItemId(List<Item> items) {
        if (items.size() <= 1) return items;
        List<Item> sorted = sortItemsByItemId(items);
        items.clear();
        items.addAll(sorted);
        return items;
    }

    public static List<Item> sortReservedEquipsByTradeScore(List<Item> items, Character agent) {
        if (items.size() <= 1) return items;
        Job job = agent.getJob();
        items.sort(Comparator
                .comparingInt((Item item) -> item instanceof Equip equip ? equipTradeScore(equip, job) : Integer.MIN_VALUE)
                .thenComparingInt(Item::getItemId)
                .thenComparingInt(Item::getPosition));
        return items;
    }

    /** Score used to order own-class equips worst-to-best: 4*watk + matk + main + sec. */
    public static int equipTradeScore(Equip equip, Job job) {
        int main;
        int secondary;
        if (AgentWeaponCompatibilityPolicy.isMageJob(job)) {
            main = equip.getInt();
            secondary = equip.getLuk();
        } else if (job != null && (job.isA(Job.BOWMAN)
                || job == Job.GUNSLINGER || job == Job.OUTLAW || job == Job.CORSAIR)) {
            main = equip.getDex();
            secondary = equip.getStr();
        } else if (job != null && job.isA(Job.THIEF)) {
            main = equip.getLuk();
            secondary = equip.getDex();
        } else {
            main = equip.getStr();
            secondary = equip.getDex();
        }
        return 4 * equip.getWatk() + equip.getMatk() + main + secondary;
    }
}
