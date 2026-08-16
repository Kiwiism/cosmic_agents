package server.agents.inventory;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.equipment.AgentEquipmentRuntime;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.inventory.AgentInventoryTickRuntime;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.supplies.AgentSupplyRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.maintenance.AgentMaintenanceSupervisor;

/**
 * Stable Inventory system boundary over the proven item, supply, equipment, and maintenance
 * implementations. Delegation is deliberate during parity migration.
 */
public final class AgentInventorySystem {
    private AgentInventorySystem() {
    }

    public static void tickPassiveLoot(AgentRuntimeEntry entry, Character agent) {
        AgentInventoryTickRuntime.tickPassiveLoot(entry, agent);
    }

    public static void tickPotionCheck(AgentRuntimeEntry entry, Character agent) {
        AgentPotionService.tickPotionCheck(entry, agent, AgentInventoryGatewayRuntime.inventory());
    }

    public static void tickPassiveRecovery(AgentRuntimeEntry entry, Character agent) {
        AgentPotionService.tickPassiveRecovery(entry, agent);
    }

    public static void tickTrade(AgentRuntimeEntry entry, Character agent) {
        AgentInventoryTickRuntime.tickTrade(entry, agent);
    }

    public static void tickManualTrade(AgentRuntimeEntry entry, Character agent) {
        AgentInventoryTickRuntime.tickManualTrade(entry, agent);
    }

    public static boolean tickMaintenance(
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        return AgentMaintenanceSupervisor.tickRuntime(entry, agent, nowMs);
    }

    public static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(
            AgentRuntimeEntry entry) {
        return AgentSupplyRuntime.supplyRequestCallbacks(entry);
    }

    public static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(
            AgentRuntimeEntry entry) {
        return AgentEquipmentRuntime.equipmentCallbacks(entry);
    }

    public static void autoEquip(
            Character agent, Character owner, Item pendingOffer, boolean force) {
        AgentEquipmentService.autoEquip(agent, owner, pendingOffer, force);
    }

    public static void autoEquip(Character agent, Character owner, Item pendingOffer) {
        AgentEquipmentService.autoEquip(agent, owner, pendingOffer);
    }
}
