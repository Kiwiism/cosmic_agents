package server.agents.capabilities.inventory;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.game.GameConstants;
import server.agents.capabilities.equipment.AgentWeaponCompatibilityPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

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

    public record UseTradeGroups(List<Item> uncategorized, List<Item> categorized) {}

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

    public static String reservedEquipsPageMessage(String category, int reservedCount) {
        if (reservedCount <= 0) {
            return null;
        }
        int page = clampTradePage(requestedReservedEquipsPage(category), reservedCount);
        int lastPage = clampTradePage(Integer.MAX_VALUE, reservedCount);
        return "reserved equips page " + page + "/" + lastPage;
    }

    public static List<Item> reservedEquipsPageItems(String category, List<Item> reservedItems) {
        if (reservedItems.isEmpty()) {
            return List.of();
        }
        int page = clampTradePage(requestedReservedEquipsPage(category), reservedItems.size());
        int from = (page - 1) * TRADE_WINDOW_ITEM_LIMIT;
        int to = Math.min(from + TRADE_WINDOW_ITEM_LIMIT, reservedItems.size());
        return new ArrayList<>(reservedItems.subList(from, to));
    }

    public static EquipsGroup equipsGroupFromCategory(String category) {
        if (category == null || !category.startsWith("equips:")) return null;
        try {
            return EquipsGroup.valueOf(category.substring("equips:".length()).toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static EquipsGroup firstAvailableEquipsGroup(Predicate<EquipsGroup> hasItems) {
        for (EquipsGroup group : EquipsGroup.values()) {
            if (hasItems.test(group)) {
                return group;
            }
        }
        return null;
    }

    public static String nextAvailableEquipsGroupCategory(String category, Predicate<EquipsGroup> hasItems) {
        EquipsGroup current = equipsGroupFromCategory(category);
        if (current == null) return null;
        for (EquipsGroup group = current.next(); group != null; group = group.next()) {
            if (hasItems.test(group)) {
                return group.categoryString();
            }
        }
        return null;
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

    public static List<Item> prioritizeEtcTradeItems(List<Item> items, Character recipient) {
        return prioritizeRecipientDuplicateItemIds(items, InventoryType.ETC, recipient, true);
    }

    public static List<Item> prioritizeTradeUseItems(List<Item> uncategorized,
                                                     List<Item> categorizedOther,
                                                     List<Item> potionAmmo,
                                                     Character recipient) {
        List<Item> ordered = new ArrayList<>(
                uncategorized.size() + categorizedOther.size() + potionAmmo.size());
        ordered.addAll(prioritizeRecipientDuplicateItemIds(uncategorized, InventoryType.USE, recipient));
        ordered.addAll(prioritizeRecipientDuplicateItemIds(categorizedOther, InventoryType.USE, recipient));
        ordered.addAll(prioritizeRecipientDuplicateItemIds(potionAmmo, InventoryType.USE, recipient));
        return ordered;
    }

    public static UseTradeGroups classifyUseTradeGroups(Character agent,
                                                        Character recipient,
                                                        IntPredicate isRecoveryPotion,
                                                        IntPredicate isTradeAmmoItem,
                                                        IntPredicate isEquipScroll,
                                                        IntPredicate isBuffConsumable,
                                                        IntPredicate isQuestItem,
                                                        IntPredicate allowsUntradeableItem) {
        List<Item> uncategorized = new ArrayList<>();
        List<Item> categorizedOther = new ArrayList<>();
        List<Item> potionAmmo = new ArrayList<>();
        uncategorized.addAll(AgentInventoryItemPolicy.collectSafeItems(agent, InventoryType.USE, item -> {
            int id = item.getItemId();
            if (isRecoveryPotion.test(id) || isTradeAmmoItem.test(id)) {
                potionAmmo.add(item);
                return false;
            }
            if (isEquipScroll.test(id) || isBuffConsumable.test(id)) {
                categorizedOther.add(item);
                return false;
            }
            return true;
        }, isQuestItem, allowsUntradeableItem));
        List<Item> ordered = prioritizeTradeUseItems(uncategorized, categorizedOther, potionAmmo, recipient);
        int uncategorizedCount = uncategorized.size();
        return new UseTradeGroups(
                new ArrayList<>(ordered.subList(0, uncategorizedCount)),
                new ArrayList<>(ordered.subList(uncategorizedCount, ordered.size())));
    }

    public static UseTradeGroups classifyUseTradeGroups(Character agent,
                                                        Character recipient,
                                                        IntPredicate isRecoveryPotion,
                                                        IntPredicate isTradeAmmoItem,
                                                        IntPredicate isEquipScroll,
                                                        IntPredicate isBuffConsumable,
                                                        IntPredicate isQuestItem,
                                                        boolean untradeableItemsTradeable) {
        return classifyUseTradeGroups(agent, recipient, isRecoveryPotion, isTradeAmmoItem, isEquipScroll,
                isBuffConsumable, isQuestItem, itemId -> untradeableItemsTradeable);
    }

    public static List<Item> prioritizeScrollTradeItems(List<Item> items, Character recipient) {
        return prioritizeRecipientDuplicateItemIds(items, InventoryType.USE, recipient);
    }

    public static List<Item> prioritizeRecipientDuplicateItemIds(List<Item> items,
                                                                 InventoryType type,
                                                                 Character recipient) {
        return prioritizeRecipientDuplicateItemIds(items, type, recipient, false);
    }

    private static List<Item> prioritizeRecipientDuplicateItemIds(List<Item> items,
                                                                  InventoryType type,
                                                                  Character recipient,
                                                                  boolean requireMatchingItemType) {
        if (items.size() <= 1) {
            return items;
        }

        List<Item> sorted = sortItemsByItemId(items);
        if (recipient == null) {
            return sorted;
        }

        Inventory recipientInventory = recipient.getInventory(type);
        if (recipientInventory == null) {
            return sorted;
        }

        Set<Integer> recipientItemIds = new HashSet<>();
        for (Item recipientItem : recipientInventory) {
            recipientItemIds.add(recipientItem.getItemId());
        }

        List<Item> prioritized = new ArrayList<>(items.size());
        List<Item> remainder = new ArrayList<>(items.size());
        for (Item item : sorted) {
            boolean typeMatches = !requireMatchingItemType || item.getInventoryType() == type;
            if (typeMatches && recipientItemIds.contains(item.getItemId())) {
                prioritized.add(item);
            } else {
                remainder.add(item);
            }
        }
        prioritized.addAll(remainder);
        return prioritized;
    }
}
