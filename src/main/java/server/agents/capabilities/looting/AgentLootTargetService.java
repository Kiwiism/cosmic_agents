package server.agents.capabilities.looting;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
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
        return findBestGrindLootTarget(entry, agent, passiveLootRadius,
                retrySuppression, Set.of(), -1);
    }

    /**
     * Chooses loot using stable priorities: active quest drops, drops from the
     * Agent's recent kills, same-region drops, then travel distance.
     */
    public static MapItem findBestGrindLootTarget(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  int passiveLootRadius,
                                                  GrindLootRetrySuppression retrySuppression,
                                                  Set<Integer> recentKillObjectIds,
                                                  int preferredRegionId) {
        return findBestGrindLootTarget(entry, agent, passiveLootRadius,
                retrySuppression, recentKillObjectIds, preferredRegionId,
                AgentCombatConfig.cfg.GRIND_SEEK_RANGE);
    }

    public static MapItem findBestGrindLootTarget(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  int passiveLootRadius,
                                                  GrindLootRetrySuppression retrySuppression,
                                                  Set<Integer> recentKillObjectIds,
                                                  int preferredRegionId,
                                                  int maximumSeekRadius) {
        if (agent == null || hasAnyInventoryFull(agent)) return null;
        MapleMap map = agent.getMap();
        if (map == null) return null;

        long now = System.currentTimeMillis();
        Point agentPos = agent.getPosition();
        int seekRange = Math.min(AgentCombatConfig.cfg.GRIND_SEEK_RANGE,
                Math.max(passiveLootRadius, maximumSeekRadius));
        double seekRangeSq = (double) seekRange * seekRange;
        AgentNavigationGraph graph = preferredRegionId < 0
                ? null
                : AgentNavigationGraphService.peekBestGraph(
                map, AgentMovementStateRuntime.movementProfile(entry));
        Set<Integer> recentKills = recentKillObjectIds == null
                ? Set.of()
                : recentKillObjectIds;
        MapItem best = null;
        LootScore bestScore = null;

        for (MapItem drop : map.getDroppedItems()) {
            if (!AgentLootEligibility.canBotTargetLoot(entry, agent, map, drop, now)) continue;
            if (retrySuppression != null && retrySuppression.isSuppressed(entry, drop, now)) continue;
            Point dropPos = drop.getPosition();
            if (Math.abs(dropPos.x - agentPos.x) <= passiveLootRadius
                    && Math.abs(dropPos.y - agentPos.y) <= passiveLootRadius) {
                continue;
            }
            double distSq = dropPos.distanceSq(agentPos);
            if (distSq > seekRangeSq) continue;
            boolean questDrop = drop.getQuest() > 0
                    || (drop.getMeso() <= 0
                    && AgentInventoryReservationRuntime.isReserved(entry, drop.getItemId(), now));
            boolean recentKillDrop = drop.getDropper() != null
                    && recentKills.contains(drop.getDropper().getObjectId());
            boolean sameRegion = graph != null
                    && graph.findRegionId(map, dropPos) == preferredRegionId;
            LootScore score = new LootScore(
                    questDrop ? 0 : 1,
                    recentKillDrop ? 0 : 1,
                    sameRegion ? 0 : 1,
                    distSq,
                    drop.getObjectId());
            if (bestScore == null || score.compareTo(bestScore) < 0) {
                bestScore = score;
                best = drop;
            }
        }
        return best;
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

    private record LootScore(int questRank,
                             int recentKillRank,
                             int regionRank,
                             double distanceSq,
                             int objectId) implements Comparable<LootScore> {
        @Override
        public int compareTo(LootScore other) {
            int result = Integer.compare(questRank, other.questRank);
            if (result != 0) return result;
            result = Integer.compare(recentKillRank, other.recentKillRank);
            if (result != 0) return result;
            result = Integer.compare(regionRank, other.regionRank);
            if (result != 0) return result;
            result = Double.compare(distanceSq, other.distanceSq);
            if (result != 0) return result;
            return Integer.compare(objectId, other.objectId);
        }
    }

    /**
     * Returns the position of the nearest lootable drop within the patrol region
     * and its immediate neighbours.
     */
    public static Point findNearestPatrolLootTarget(AgentRuntimeEntry entry, int patrolRegionId) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null) return null;
        if (hasAnyInventoryFull(agent)) return null;
        MapleMap map = agent.getMap();
        if (map == null) return null;

        AgentNavigationGraph graph = AgentNavigationGraphService.peekBestGraph(
                map,
                AgentMovementStateRuntime.movementProfile(entry));
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
