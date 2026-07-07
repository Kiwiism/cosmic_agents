package server.agents.capabilities.looting;

import client.Character;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

public final class AgentGrindLootTargetService {
    private AgentGrindLootTargetService() {
    }

    public static void validateCachedGrindLootTarget(AgentRuntimeEntry entry, Character agent) {
        if (!AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            return;
        }

        MapItem loot = AgentBotGrindLootStateRuntime.grindLootTarget(entry);
        if (loot.isPickedUp() || agent.getMap().getMapObject(loot.getObjectId()) != loot) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
        }
    }

    public static void refreshGrindLootTarget(AgentRuntimeEntry entry,
                                              Character agent,
                                              boolean runAiTick,
                                              int lootRadius) {
        if (!runAiTick || AgentBotPatrolStateRuntime.hasPatrolRegion(entry)) {
            return;
        }

        AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, AgentLootTargetService.findNearestGrindLootTarget(
                entry,
                agent,
                lootRadius,
                AgentBotGrindLootStateRuntime::isRetrySuppressed));
    }
}
