package server.agents.capabilities.looting;

import client.Character;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.bots.BotEntry;
import server.maps.MapItem;

public final class AgentGrindLootTargetService {
    private AgentGrindLootTargetService() {
    }

    public static void validateCachedGrindLootTarget(BotEntry entry, Character agent) {
        if (!AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            return;
        }

        MapItem loot = AgentBotGrindLootStateRuntime.grindLootTarget(entry);
        if (loot.isPickedUp() || agent.getMap().getMapObject(loot.getObjectId()) != loot) {
            AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);
        }
    }

    public static void refreshGrindLootTarget(BotEntry entry,
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
