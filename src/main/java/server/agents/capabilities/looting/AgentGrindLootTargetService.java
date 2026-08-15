package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.movement.AgentPatrolStateRuntime;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.Set;

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
            resolveRecentKillIfDrained(entry, agent.getMap(), dropperObjectId(loot));
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
        WeaponType weaponType = equippedWeaponType(agent);
        boolean ranged = AgentPostKillLootPolicy.isRanged(weaponType);
        AgentLootDecisionTraceState.Mode traceMode = ranged
                ? AgentLootDecisionTraceState.Mode.POST_KILL_RANGED
                : AgentLootDecisionTraceState.Mode.POST_KILL_MELEE;
        if (!AgentPostKillLootPolicy.shouldCollect(
                weaponType, postKill, hasCombatTarget, nowMs)) {
            recordLootDecision(entry, traceMode,
                    AgentLootDecisionTraceState.Outcome.POLICY_DEFERRED,
                    nowMs, postKill.killCount(), hasCombatTarget, null, 0L);
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
                maximumSeekRadius,
                AgentPostKillLootPolicy.targetLootAgeMs(weaponType, true));
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, selected);
        recordLootDecision(entry, traceMode,
                selected == null
                        ? AgentLootDecisionTraceState.Outcome.NO_ELIGIBLE_DROP
                        : AgentLootDecisionTraceState.Outcome.TARGET_SELECTED,
                nowMs, postKill.killCount(), hasCombatTarget, selected,
                AgentPostKillLootPolicy.targetLootAgeMs(weaponType, true));
    }

    /**
     * Gives a just-finished combat target a bounded handoff to loot before target search may
     * acquire another, potentially remote, mob. This is intentionally limited to recorded
     * kills: ordinary map drops never block combat acquisition.
     */
    public static boolean preparePostKillLootBeforeTargetSearch(AgentRuntimeEntry entry,
                                                                Character agent,
                                                                boolean runAiTick,
                                                                int lootRadius,
                                                                long nowMs) {
        if (entry == null || agent == null || AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
            return false;
        }
        AgentPostKillLootState.Snapshot postKill = entry.capabilityStates()
                .require(AgentPostKillLootState.STATE_KEY)
                .snapshot(nowMs);
        if (!postKill.hasKills()) {
            return false;
        }
        WeaponType weaponType = equippedWeaponType(agent);
        if (AgentPostKillLootPolicy.isRanged(weaponType)
                && !AgentPostKillLootPolicy.shouldCollect(
                weaponType, postKill, true, nowMs)) {
            return false;
        }
        refreshGrindLootTarget(entry, agent, runAiTick, lootRadius, false);
        if (AgentGrindLootStateRuntime.hasGrindLootTarget(entry)) {
            return true;
        }
        long settleAgeMs = AgentPostKillLootPolicy.targetLootAgeMs(weaponType, true);
        return nowMs - postKill.oldestKillAtMs() < settleAgeMs;
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
        WeaponType weaponType = equippedWeaponType(agent);
        AgentPostKillLootState.Snapshot postKill = postKillState.snapshot(nowMs);
        MapItem selected = AgentLootTargetService.findBestGrindLootTarget(
                entry,
                agent,
                passiveLootRadius,
                AgentGrindLootStateRuntime::isRetrySuppressed,
                postKill.killedObjectIds(),
                currentRegionId(entry, agent),
                maximumSeekRadius,
                AgentPostKillLootPolicy.targetLootAgeMs(weaponType, true));
        AgentGrindLootStateRuntime.setGrindLootTarget(entry, selected);
        recordLootDecision(entry, AgentLootDecisionTraceState.Mode.PRE_EXIT,
                selected == null
                        ? AgentLootDecisionTraceState.Outcome.NO_ELIGIBLE_DROP
                        : AgentLootDecisionTraceState.Outcome.TARGET_SELECTED,
                nowMs, postKill.killCount(), false, selected,
                AgentPostKillLootPolicy.targetLootAgeMs(weaponType, true));
    }

    public static Point immediateMeleeLootPosition(AgentRuntimeEntry entry,
                                                   Character agent,
                                                   Point agentPosition,
                                                   int passiveLootRadius,
                                                   long nowMs) {
        if (entry == null || agent == null || agentPosition == null || agent.getMap() == null) {
            return null;
        }
        WeaponType weaponType = equippedWeaponType(agent);
        if (AgentPostKillLootPolicy.isRanged(weaponType)) {
            return null;
        }
        AgentPostKillLootState.Snapshot postKill = entry.capabilityStates()
                .require(AgentPostKillLootState.STATE_KEY)
                .snapshot(nowMs);
        Set<Integer> recentKills = postKill.killedObjectIds();
        if (recentKills.isEmpty()) {
            return null;
        }

        MapleMap map = agent.getMap();
        int immediateRadius = AgentLootCollectionPolicyConfig.meleeImmediateRadius();
        MapItem nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (MapItem drop : map.getDroppedItems()) {
            int dropperObjectId = dropperObjectId(drop);
            if (!recentKills.contains(dropperObjectId)
                    || !AgentLootEligibility.isPresent(map, drop)
                    || !AgentLootEligibility.canBotLoot(entry, agent, drop)) {
                continue;
            }
            Point dropPosition = drop.getPosition();
            if (Math.abs(dropPosition.x - agentPosition.x) > immediateRadius
                    || Math.abs(dropPosition.y - agentPosition.y) > immediateRadius) {
                continue;
            }
            double distanceSq = dropPosition.distanceSq(agentPosition);
            if (distanceSq < nearestDistanceSq) {
                nearest = drop;
                nearestDistanceSq = distanceSq;
            }
        }
        if (nearest == null) {
            recordLootDecision(entry, AgentLootDecisionTraceState.Mode.POST_KILL_MELEE,
                    AgentLootDecisionTraceState.Outcome.NO_ELIGIBLE_DROP,
                    nowMs, postKill.killCount(), true, null,
                    AgentPostKillLootPolicy.targetLootAgeMs(weaponType, true));
            return null;
        }

        long targetAgeMs = AgentPostKillLootPolicy.targetLootAgeMs(weaponType, true);
        if (nowMs - nearest.getDropTime() < targetAgeMs) {
            recordLootDecision(entry, AgentLootDecisionTraceState.Mode.POST_KILL_MELEE,
                    AgentLootDecisionTraceState.Outcome.WAITING_FOR_DROP,
                    nowMs, postKill.killCount(), true, nearest, targetAgeMs);
            return new Point(agentPosition);
        }
        recordLootDecision(entry, AgentLootDecisionTraceState.Mode.POST_KILL_MELEE,
                AgentLootDecisionTraceState.Outcome.TARGET_SELECTED,
                nowMs, postKill.killCount(), true, nearest, targetAgeMs);
        Point lootPosition = nearest.getPosition();
        if (Math.abs(lootPosition.x - agentPosition.x) <= passiveLootRadius
                && Math.abs(lootPosition.y - agentPosition.y) <= passiveLootRadius) {
            return new Point(agentPosition);
        }
        return lootPosition;
    }

    public static boolean canTargetCachedGrindLoot(AgentRuntimeEntry entry,
                                                   Character agent,
                                                   MapItem loot,
                                                   long nowMs) {
        if (entry == null || agent == null || agent.getMap() == null || loot == null) {
            return false;
        }
        Set<Integer> recentKills = entry.capabilityStates()
                .require(AgentPostKillLootState.STATE_KEY)
                .snapshot(nowMs)
                .killedObjectIds();
        WeaponType weaponType = equippedWeaponType(agent);
        boolean recentKillDrop = recentKills.contains(dropperObjectId(loot));
        return AgentLootEligibility.canBotTargetLoot(
                entry,
                agent,
                agent.getMap(),
                loot,
                nowMs,
                AgentPostKillLootPolicy.targetLootAgeMs(weaponType, recentKillDrop));
    }

    private static int currentRegionId(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getMap() == null) {
            return -1;
        }
        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                agent.getMap(), AgentMovementStateRuntime.movementProfile(entry));
        return graph == null ? -1 : graph.findRegionId(agent.getMap(), agent.getPosition());
    }

    private static WeaponType equippedWeaponType(Character agent) {
        Inventory equipped = agent == null
                ? null
                : agent.getInventory(InventoryType.EQUIPPED);
        return equipped == null
                ? null
                : AgentAttackExecutionProvider.getEquippedWeaponType(agent);
    }

    private static int dropperObjectId(MapItem drop) {
        return drop != null && drop.getDropper() != null
                ? drop.getDropper().getObjectId()
                : -1;
    }

    private static void resolveRecentKillIfDrained(AgentRuntimeEntry entry,
                                                   MapleMap map,
                                                   int dropperObjectId) {
        if (entry == null || map == null || dropperObjectId <= 0) {
            return;
        }
        boolean hasRemainingDrop = map.getDroppedItems().stream()
                .anyMatch(drop -> !drop.isPickedUp()
                        && dropperObjectId(drop) == dropperObjectId);
        if (!hasRemainingDrop) {
            entry.capabilityStates()
                    .require(AgentPostKillLootState.STATE_KEY)
                    .resolveKill(dropperObjectId);
        }
    }

    private static void recordLootDecision(AgentRuntimeEntry entry,
                                           AgentLootDecisionTraceState.Mode mode,
                                           AgentLootDecisionTraceState.Outcome outcome,
                                           long nowMs,
                                           int recentKillCount,
                                           boolean hasCombatTarget,
                                           MapItem target,
                                           long requiredDropAgeMs) {
        if (entry == null) {
            return;
        }
        entry.capabilityStates().require(AgentLootDecisionTraceState.STATE_KEY).record(
                mode,
                outcome,
                nowMs,
                recentKillCount,
                hasCombatTarget,
                target == null ? 0 : target.getObjectId(),
                requiredDropAgeMs,
                target == null ? 0L : Math.max(0L, nowMs - target.getDropTime()));
    }
}
