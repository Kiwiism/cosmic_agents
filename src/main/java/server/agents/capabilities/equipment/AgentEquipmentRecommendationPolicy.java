package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.WeaponType;
import server.agents.integration.InventoryGateway;

public final class AgentEquipmentRecommendationPolicy {
    private AgentEquipmentRecommendationPolicy() {
    }

    public enum RecommendationScope {
        IMMEDIATE,
        FUTURE
    }

    public interface RecommendationHooks extends AgentEquipmentReservePolicy.EquipUsefulnessHooks {
        boolean canWear(Character agent, Equip equip, short primarySlot);

        static RecommendationHooks from(InventoryGateway inventory) {
            return new RecommendationHooks() {
                @Override public boolean canWear(Character agent, Equip equip, short primarySlot) {
                    return inventory.canWearEquipment(agent, equip, primarySlot);
                }
                @Override public boolean isCash(int itemId) { return inventory.isCashItem(itemId); }
                @Override public String getEquipmentSlot(int itemId) { return inventory.getEquipmentSlot(itemId); }
                @Override public WeaponType getWeaponType(int itemId) { return inventory.getWeaponType(itemId); }
                @Override public boolean meetsReqs(Equip equip, Job job, int level, int str, int dex,
                                                   int int_, int luk, int fame) {
                    return inventory.meetsEquipRequirements(equip, job, level, str, dex, int_, luk, fame);
                }
            };
        }
    }

    public static boolean isRecommendationCandidate(Character agent,
                                                    RecommendationHooks hooks,
                                                    Equip equip,
                                                    short primarySlot,
                                                    RecommendationScope scope) {
        if (scope == RecommendationScope.IMMEDIATE) {
            return hooks.canWear(agent, equip, primarySlot)
                    || AgentEquipmentReservePolicy.statOnlyBlocked(agent, hooks, equip);
        }
        return AgentEquipmentReservePolicy.statOnlyBlocked(agent, hooks, equip);
    }
}
