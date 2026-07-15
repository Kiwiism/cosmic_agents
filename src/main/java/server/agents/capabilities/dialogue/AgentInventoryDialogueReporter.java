package server.agents.capabilities.dialogue;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.ItemRestrictionPolicy;
import server.StatEffect;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.integration.InventoryGateway;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentInventoryDialogueReporter {
    private static final String RESERVED_EQUIPS_CATEGORY_PREFIX = "equips:reserved:";

    private AgentInventoryDialogueReporter() {
    }

    public static int countEquipScrolls(Character agent) {
        int count = 0;
        for (Item item : agent.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            if (ItemConstants.isEquipScroll(id)) {
                count += item.getQuantity();
            }
        }
        return count;
    }

    public static String scrollReport(Character agent) {
        return AgentDialogueReportFormatter.scrollCount(countEquipScrolls(agent));
    }

    public static String slotsReport(Character agent) {
        StringBuilder sb = new StringBuilder();
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            Inventory inv = agent.getInventory(type);
            int used = inv.getSlotLimit() - inv.getNumFreeSlot();
            int total = inv.getSlotLimit();
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(type.name().toLowerCase()).append(": ").append(used).append('/').append(total);
        }
        return sb.toString();
    }

    public static String inventorySummary(Character agent, InventoryGateway inventoryGateway) {
        StringBuilder sb = new StringBuilder();
        for (InventoryType type : List.of(
                InventoryType.EQUIP, InventoryType.USE, InventoryType.ETC, InventoryType.SETUP)) {
            Inventory inv = agent.getInventory(type);
            int used = inv.getSlotLimit() - inv.getNumFreeSlot();
            int total = inv.getSlotLimit();
            if (!sb.isEmpty()) {
                sb.append(" | ");
            }
            sb.append(type.name().toLowerCase()).append(' ').append(used).append('/').append(total);
            if (type == InventoryType.USE) {
                appendUseInventorySummary(sb, agent, inv, inventoryGateway);
            }
        }
        return sb.toString();
    }

    private static void appendUseInventorySummary(StringBuilder sb, Character agent, Inventory inventory, InventoryGateway inventoryGateway) {
        int scrolls = 0;
        int pots = 0;
        int buffs = 0;
        for (Item item : inventory.list()) {
            if (!isSafeToMention(agent, item, inventoryGateway)) {
                continue;
            }
            int id = item.getItemId();
            if (ItemConstants.isEquipScroll(id)) {
                scrolls += item.getQuantity();
            } else if (isRecoveryPotion(id, inventoryGateway)) {
                pots += item.getQuantity();
            } else if (isBuffConsumable(id, inventoryGateway)) {
                buffs += item.getQuantity();
            }
        }
        if (scrolls <= 0 && pots <= 0 && buffs <= 0) {
            return;
        }

        sb.append(" (");
        boolean any = false;
        if (scrolls > 0) {
            sb.append(scrolls).append(scrolls != 1 ? " scrolls" : " scroll");
            any = true;
        }
        if (pots > 0) {
            if (any) {
                sb.append(", ");
            }
            sb.append(pots).append(pots != 1 ? " pots" : " pot");
            any = true;
        }
        if (buffs > 0) {
            if (any) {
                sb.append(", ");
            }
            sb.append(buffs).append(buffs != 1 ? " buffs" : " buff");
        }
        sb.append(')');
    }

    private static boolean isRecoveryPotion(int itemId, InventoryGateway inventoryGateway) {
        return AgentUseItemClassificationPolicy.isRecoveryPotion(itemEffect(itemId, inventoryGateway));
    }

    private static boolean isBuffConsumable(int itemId, InventoryGateway inventoryGateway) {
        return AgentUseItemClassificationPolicy.isBuffConsumable(itemEffect(itemId, inventoryGateway));
    }

    private static StatEffect itemEffect(int itemId, InventoryGateway inventoryGateway) {
        try {
            return inventoryGateway.getItemEffect(itemId);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean isSafeToMention(Character agent, Item item, InventoryGateway inventoryGateway) {
        if (item.isUntradeable() && !ItemRestrictionPolicy.allowsUntradeable(agent, item.getItemId())) {
            return false;
        }
        return !inventoryGateway.isQuestItem(item.getItemId());
    }

    public static String noItemsReply(String category) {
        return noItemsReply(category, AgentDialogueCatalog.noItemsReplies());
    }

    public static String noItemsReply(String category, List<String> templates) {
        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        return String.format(template, noItemsCategoryLabel(category));
    }

    public static String noItemsCategoryLabel(String category) {
        if (category == null) {
            return "those items";
        }
        return switch (category) {
            case "mesos" -> "mesos";
            case "recommended" -> "better gear for you";
            case "scrolls" -> "scrolls";
            case "pots" -> "pots";
            case "buff" -> "buff pots";
            case "use" -> "use items";
            case "ammo" -> "ammo";
            case "equips" -> "equips";
            case "trash" -> "trash equips";
            case "etc" -> "etc items";
            default -> {
                if (category.startsWith(RESERVED_EQUIPS_CATEGORY_PREFIX)) {
                    yield "reserved equips";
                }
                if (category.startsWith("mesos:")) {
                    yield "mesos";
                }
                yield category.startsWith("name:") ? category.substring(5) : "those items";
            }
        };
    }
}
