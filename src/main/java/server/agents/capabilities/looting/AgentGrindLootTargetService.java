package server.agents.capabilities.looting;

import client.Character;
import client.inventory.WeaponType;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;

public final class AgentGrindLootTargetService {
    private AgentGrindLootTargetService() {
    }

    public static void validateCachedGrindLootTarget(AgentRuntimeEntry entry, Character agent) {
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
        refreshGrindLootTarget(entry, agent, runAiTick, lootRadius, false);
    }

    public static void refreshGrindLootTarget(AgentRuntimeEntry entry,
                                              Character agent,
                                              boolean runAiTick,
                                              int lootRadius,
                                              boolean hasCombatTarget) {
        if (!runAiTick || AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
            return;
        }

        long nowMs = System.currentTimeMillis();
        AgentPostKillLootState postKillState =
                entry.capabilityStates().require(AgentPostKillLootState.STATE_KEY);
        AgentPostKillLootState.Snapshot postKill = postKillState.snapshot(nowMs);
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(agent);
        boolean ranged = AgentPostKillLootPolicy.isRanged(weaponType);
        if (!AgentPostKillLootPolicy.shouldCollect(
                weaponType, postKill, hasCombatTarget, nowMs)) {
            return;
        }
        int maximumSeekRadius = !ranged && hasCombatTarget && postKill.hasKills()
                ? AgentLootCollectionPolicyConfig.meleeImmediateRadius()
                : AgentCombatConfig.cfg.GRIND_SEEK_RANGE;

        MapItem selected = AgentLootTargetService.findBestGrindLootTarget(
                entry,
                agent,
                lootRadius,
                AgentGrindLootStateRuntime::isRetrySuppressed,
                postKill.killedObjectIds(),
                currentRegionId(entry, agent),
                maximumSeekRadius);
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, selected);
        if (selected != null) {
            postKillState.batchScheduled();
        }
    }

    public static void refreshPreExitLootTarget(AgentRuntimeEntry entry,
                                                Character agent,
                                                boolean runAiTick,
                                                int passiveLootRadius,
                                                int maximumSeekRadius) {
        if (!runAiTick || AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        AgentPostKillLootState postKillState =
                entry.capabilityStates().require(AgentPostKillLootState.STATE_KEY);
        MapItem selected = AgentLootTargetService.findBestGrindLootTarget(
                entry,
                agent,
                passiveLootRadius,
                AgentGrindLootStateRuntime::isRetrySuppressed,
                postKillState.snapshot(nowMs).killedObjectIds(),
                currentRegionId(entry, agent),
                maximumSeekRadius);
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, selected);
        if (selected != null) {
            postKillState.batchScheduled();
        }
    }

    private static int currentRegionId(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getMap() == null) {
            return -1;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        return graph == null ? -1 : graph.findRegionId(agent.getMap(), agent.getPosition());
    }
}
