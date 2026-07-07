package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.HashSet;
import java.util.Set;

public final class AgentLootTargetService {
    @FunctionalInterface
    public interface GrindLootRetrySuppression {
        boolean isSuppressed(AgentRuntimeEntry entry, MapItem drop, long now);
    }

    private AgentLootTargetService() {
    }

    /**
     * Returns the nearest lootable drop within grind seek range, excluding drops
     * already inside passive-pickup radius.
     */
    public static MapItem findNearestGrindLootTarget(AgentRuntimeEntry entry,
                                                     Character agent,
                                                     int passiveLootRadius,
                                                     GrindLootRetrySuppression retrySuppression) {
        if (agent == null || hasAnyInventoryFull(agent)) return null;
        MapleMap map = agent.getMap();
        if (map == null) return null;

        long now = System.currentTimeMillis();
        Point agentPos = agent.getPosition();
        double seekRangeSq = (double) AgentCombatConfig.cfg.GRIND_SEEK_RANGE * AgentCombatConfig.cfg.GRIND_SEEK_RANGE;
        MapItem nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (MapItem drop : map.getDroppedItems()) {
            if (!AgentLootEligibility.canBotTargetLoot(entry, agent, map, drop, now)) continue;
            if (retrySuppression != null && retrySuppression.isSuppressed(entry, drop, now)) continue;
            Point dropPos = drop.getPosition();
            if (Math.abs(dropPos.x - agentPos.x) <= passiveLootRadius
                    && Math.abs(dropPos.y - agentPos.y) <= passiveLootRadius) {
                continue;
            }
            double distSq = dropPos.distanceSq(agentPos);
            if (distSq > seekRangeSq || distSq >= nearestDistSq) continue;
            nearestDistSq = distSq;
            nearest = drop;
        }
        return nearest;
    }

    public static boolean hasAnyInventoryFull(Character agent) {
        if (agent == null) return false;
        for (InventoryType type : new InventoryType[]{
                InventoryType.EQUIP, InventoryType.USE, InventoryType.SETUP, InventoryType.ETC}) {
            Inventory inventory = agent.getInventory(type);
            if (inventory != null && inventory.isFull()) return true;
        }
        return false;
    }

    /**
     * Returns the position of the nearest lootable drop within the patrol region
     * and its immediate neighbours.
     */
    public static Point findNearestPatrolLootTarget(AgentRuntimeEntry entry, int patrolRegionId) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        if (agent == null) return null;
        if (hasAnyInventoryFull(agent)) return null;
        MapleMap map = agent.getMap();
        if (map == null) return null;

        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map,
                AgentBotMovementStateRuntime.movementProfile(entry));
        if (graph == null) return null;

        Set<Integer> allowed = new HashSet<>();
        allowed.add(patrolRegionId);
        allowed.addAll(graph.getMutualAdjacentRegionIds(patrolRegionId));

        long now = System.currentTimeMillis();
        Point agentPos = agent.getPosition();
        Point nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (MapItem drop : map.getDroppedItems()) {
            if (!AgentLootEligibility.canBotTargetLoot(entry, agent, map, drop, now)) continue;
            Point dropPos = drop.getPosition();
            if (!allowed.contains(graph.findRegionId(map, dropPos))) continue;
            double distSq = dropPos.distanceSq(agentPos);
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = dropPos;
            }
        }
        return nearest;
    }
}
