package server.agents.capabilities.trade;

import server.agents.capabilities.inventory.AgentAmmoTradeClassificationService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy.AmmoTradeGroups;

import java.util.function.Supplier;

public final class AgentTradeGroupNavigationService {
    private AgentTradeGroupNavigationService() {
    }

    public static String nextEquipsGroup(String category, Supplier<AgentEquipTradeGroups> groups) {
        return AgentEquipTradeGroupService.nextEquipsGroup(category, groups.get());
    }

    public static String nextAmmoGroup(String category, Supplier<AmmoTradeGroups> groups) {
        return AgentAmmoTradeClassificationService.nextAmmoGroup(category, groups.get());
    }
}
