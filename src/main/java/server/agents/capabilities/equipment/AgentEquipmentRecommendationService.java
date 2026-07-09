package server.agents.capabilities.equipment;

import client.Character;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import config.YamlConfig;
import constants.inventory.EquipSlot;
import server.agents.capabilities.equipment.AgentEquipmentRecommendationPolicy.RecommendationScope;
import server.agents.capabilities.equipment.AgentEquipmentRecommendationPolicy.RecommendationHooks;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Agent-owned recommendation layer over Agent equipment optimizer orchestration.
 */
public final class AgentEquipmentRecommendationService {
    private AgentEquipmentRecommendationService() {
    }

    public static List<AgentEquipRecommendation> findRecommendedEquips(Character receiver, Character holder) {
        return findRecommendedEquips(receiver, holder, RecommendationScope.IMMEDIATE);
    }

    public static List<AgentEquipRecommendation> findFutureRecommendedEquips(Character receiver, Character holder) {
        return findRecommendedEquips(receiver, holder, RecommendationScope.FUTURE);
    }

    public static List<AgentEquipRecommendation> findRecommendedEquipsFromItems(Character receiver,
                                                                                Collection<Equip> holderItems) {
        return buildRecommendations(receiver, holderItems, RecommendationScope.IMMEDIATE);
    }

    public static List<AgentEquipRecommendation> findFutureRecommendedEquipsFromItems(Character receiver,
                                                                                      Collection<Equip> holderItems) {
        return buildRecommendations(receiver, holderItems, RecommendationScope.FUTURE);
    }

    private static List<AgentEquipRecommendation> findRecommendedEquips(Character receiver,
                                                                        Character holder,
                                                                        RecommendationScope scope) {
        InventoryGateway inventory = inventory();
        RecommendationHooks hooks = AgentEquipmentRecommendationPolicy.RecommendationHooks.from(inventory);
        Inventory holderEquipInv = holder.getInventory(InventoryType.EQUIP);

        Set<Equip> holderItems = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Item item : holderEquipInv.list()) {
            if (!(item instanceof Equip equip) || inventory.isCashItem(item.getItemId())) continue;
            if (item.isUntradeable() && !YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE) continue;
            String textSlot = inventory.getEquipmentSlot(equip.getItemId());
            if (textSlot == null) continue;
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            if (eslot == null || eslot == EquipSlot.PET_EQUIP) continue;
            if (eslot.getPrimarySlot() == 0) continue;
            short primarySlot = (short) eslot.getPrimarySlot();
            if (primarySlot == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(receiver, inventory.getWeaponType(equip.getItemId()))) {
                continue;
            }
            if (!AgentEquipmentRecommendationPolicy.isRecommendationCandidate(receiver, hooks, equip, primarySlot, scope)) {
                continue;
            }
            holderItems.add(equip);
        }

        return buildRecommendations(receiver, holderItems, scope);
    }

    private static List<AgentEquipRecommendation> buildRecommendations(Character receiver,
                                                                       Collection<Equip> holderItems,
                                                                       RecommendationScope scope) {
        Inventory receiverEquippedInv = receiver.getInventory(InventoryType.EQUIPPED);
        List<AgentEquipRecommendation> recommendations = new ArrayList<>();
        AgentEquipmentOptimizerResult opt = AgentEquipmentOptimizationService.runOptimizerWithExtras(receiver, holderItems, scope);
        if (opt.weapon() != null && holderItems.contains(opt.weapon())) {
            Equip cur = (Equip) receiverEquippedInv.getItem((short) -11);
            recommendations.add(new AgentEquipRecommendation((short) -11, cur, opt.weapon()));
        }
        for (Map.Entry<Short, Equip> pick : opt.picks().entrySet()) {
            if (holderItems.contains(pick.getValue())) {
                Equip cur = (Equip) receiverEquippedInv.getItem(pick.getKey());
                recommendations.add(new AgentEquipRecommendation(pick.getKey(), cur, pick.getValue()));
            }
        }
        return recommendations;
    }

    public static List<Item> collectRecommendedItems(Character receiver, Character holder) {
        return new ArrayList<>(findRecommendedEquips(receiver, holder).stream()
                .map(AgentEquipRecommendation::candidate)
                .toList());
    }

    public static AgentEquipRecommendation findRecommendationForItem(Character receiver,
                                                                     Character holder,
                                                                     Item holderItem) {
        return findRecommendationForItem(receiver, holder, holderItem, RecommendationScope.IMMEDIATE);
    }

    public static AgentEquipRecommendation findFutureRecommendationForItem(Character receiver,
                                                                           Character holder,
                                                                           Item holderItem) {
        return findRecommendationForItem(receiver, holder, holderItem, RecommendationScope.FUTURE);
    }

    private static AgentEquipRecommendation findRecommendationForItem(Character receiver,
                                                                      Character holder,
                                                                      Item holderItem,
                                                                      RecommendationScope scope) {
        if (!(holderItem instanceof Equip candidate)) return null;

        InventoryGateway inventory = inventory();
        RecommendationHooks hooks = AgentEquipmentRecommendationPolicy.RecommendationHooks.from(inventory);
        if (inventory.isCashItem(candidate.getItemId())) return null;
        if (holderItem.isUntradeable() && !YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE) return null;

        String textSlot = inventory.getEquipmentSlot(candidate.getItemId());
        if (textSlot == null) return null;
        EquipSlot slot = EquipSlot.getFromTextSlot(textSlot);
        if (slot == null || slot == EquipSlot.PET_EQUIP) return null;
        short primarySlot = (short) slot.getPrimarySlot();
        if (primarySlot == 0) return null;
        if (primarySlot == (short) -11
                && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(receiver, inventory.getWeaponType(candidate.getItemId()))) {
            return null;
        }
        if (!AgentEquipmentRecommendationPolicy.isRecommendationCandidate(receiver, hooks, candidate, primarySlot, scope)) {
            return null;
        }
        if (scope == RecommendationScope.IMMEDIATE
                && !AgentEquipmentReservePolicy.isEquipUsefulToAgent(receiver, hooks, candidate)) {
            return null;
        }

        Inventory receiverEquippedInv = receiver.getInventory(InventoryType.EQUIPPED);
        AgentEquipmentOptimizerResult opt = AgentEquipmentOptimizationService.runOptimizerWithExtras(receiver, List.of(candidate), scope);
        if (opt.weapon() == candidate) {
            Equip cur = (Equip) receiverEquippedInv.getItem((short) -11);
            return new AgentEquipRecommendation((short) -11, cur, candidate);
        }
        for (Map.Entry<Short, Equip> pick : opt.picks().entrySet()) {
            if (pick.getValue() == candidate) {
                Equip cur = (Equip) receiverEquippedInv.getItem(pick.getKey());
                return new AgentEquipRecommendation(pick.getKey(), cur, candidate);
            }
        }
        return null;
    }

    public static String recommendationSummary(Character receiver, Character holder, int maxItems) {
        List<AgentEquipRecommendation> recommendations = findRecommendedEquips(receiver, holder);
        return formatRecommendationSummary(
                recommendations,
                maxItems,
                inventory()::getItemName);
    }

    private static InventoryGateway inventory() {
        return AgentInventoryGatewayRuntime.inventory();
    }

    static String formatRecommendationSummary(List<AgentEquipRecommendation> recommendations,
                                              int maxItems,
                                              IntFunction<String> itemName) {
        if (recommendations.isEmpty()) {
            return null;
        }

        StringBuilder summary = new StringBuilder("better gear for you: ");
        int count = Math.min(maxItems, recommendations.size());
        for (int i = 0; i < count; i++) {
            AgentEquipRecommendation recommendation = recommendations.get(i);
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(AgentEquipmentSlotResolver.slotLabel(recommendation.targetSlot()))
                    .append(" -> ")
                    .append(itemName.apply(recommendation.candidate().getItemId()));
        }
        if (recommendations.size() > count) {
            summary.append(" +").append(recommendations.size() - count).append(" more");
        }
        return summary.toString();
    }
}
