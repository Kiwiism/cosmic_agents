package server.agents.capabilities.inventory;

import client.inventory.InventoryType;
import client.inventory.Item;
import constants.game.GameConstants;

import java.util.List;

public final class AgentInventoryTradePolicy {
    public static final int TRADE_WINDOW_ITEM_LIMIT = 9;
    private static final String RESERVED_EQUIPS_CATEGORY_PREFIX = "equips:reserved:";

    private AgentInventoryTradePolicy() {
    }

    public static String reservedEquipsCategory(int requestedPage) {
        return RESERVED_EQUIPS_CATEGORY_PREFIX + requestedPage;
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
}
