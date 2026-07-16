package server.agents.capabilities.looting;

import client.Character;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

public final class AgentGrindLootTargetService {
    private AgentGrindLootTargetService() {
    }

    public static void validateCachedGrindLootTarget(AgentRuntimeEntry entry, Character agent) {
        if (entry.isPartnerManaged()) {
            AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
            return;
        }
        if (!AgentGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            return;
        }

        MapItem loot = AgentGrindLootStateRuntime.grindLootTarget(entry);
        if (loot.isPickedUp() || agent.getMap().getMapObject(loot.getObjectId()) != loot) {
            AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
        }
    }

    public static void refreshGrindLootTarget(AgentRuntimeEntry entry,
                                              Character agent,
                                              boolean runAiTick,
                                              int lootRadius) {
        if (entry.isPartnerManaged()) {
            AgentGrindLootStateRuntime.clearGrindLootTarget(entry);
            return;
        }
        if (!runAiTick || AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
            return;
        }

        AgentGrindLootStateRuntime.setGrindLootTarget(entry, AgentLootTargetService.findNearestGrindLootTarget(
                entry,
                agent,
                lootRadius,
                AgentGrindLootStateRuntime::isRetrySuppressed));
    }
}
