package server.agents.capabilities.recovery;

import client.Character;
import server.agents.capabilities.movement.AgentFarmAnchorStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.shop.AgentShopStateRuntime;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Agent-owned recovery teleport rules used by the movement tick shell.
 */
public final class AgentRecoveryTeleportService {
    @FunctionalInterface
    public interface TeleportAction {
        void teleport(AgentRuntimeEntry entry, Character agent, Point position);
    }

    public record RecoveryHooks(BiFunction<MapleMap, Point, Point> groundPointFinder,
                                TeleportAction teleporter,
                                Consumer<AgentRuntimeEntry> afterTeleportReset,
                                Consumer<AgentRuntimeEntry> movementBroadcaster) {
    }

    private AgentRecoveryTeleportService() {
    }

    public static boolean recoverTeleportDistance(AgentRuntimeEntry entry,
                                                  Character agent,
                                                  Point targetPosition,
                                                  int teleportDistance,
                                                  int outOfBoundsTeleportDistance,
                                                  RecoveryHooks hooks) {
        Point agentPosition = agent.getPosition();
        int manhattan = Math.abs(agentPosition.x - targetPosition.x) + Math.abs(agentPosition.y - targetPosition.y);
        if (manhattan > teleportDistance) {
            return executeRecoveryTeleport(entry, agent, targetPosition, hooks);
        }

        Rectangle area = agent.getMap() == null ? null : agent.getMap().getMapArea();
        if (hasKnownMapBounds(area)
                && !area.contains(agentPosition)
                && manhattan > outOfBoundsTeleportDistance) {
            return executeRecoveryTeleport(entry, agent, targetPosition, hooks);
        }
        return false;
    }

    public static boolean recoverGrindPartyTeleportDistance(AgentRuntimeEntry entry,
                                                            Character agent,
                                                            Character partyAnchor,
                                                            int teleportDistance,
                                                            int outOfBoundsTeleportDistance,
                                                            int multiplier,
                                                            RecoveryHooks hooks) {
        if (entry == null || agent == null || partyAnchor == null || !AgentModeStateRuntime.grinding(entry)
                || AgentShopStateRuntime.shopVisitPending(entry)) {
            return false;
        }
        if (AgentMoveTargetStateRuntime.hasMoveTarget(entry) || AgentFarmAnchorStateRuntime.hasFarmAnchor(entry)) {
            return false;
        }
        if (agent.getMap() == null || partyAnchor.getMap() != agent.getMap()) {
            return false;
        }

        Point agentPosition = agent.getPosition();
        Point anchorPosition = partyAnchor.getPosition();
        if (agentPosition == null || anchorPosition == null || !isInKnownMapBounds(agent.getMap(), anchorPosition)) {
            return false;
        }

        int manhattan = Math.abs(agentPosition.x - anchorPosition.x) + Math.abs(agentPosition.y - anchorPosition.y);
        int distanceMultiplier = Math.max(1, multiplier);
        if (manhattan > teleportDistance * distanceMultiplier) {
            return executeRecoveryTeleport(entry, agent, anchorPosition, hooks);
        }

        Rectangle area = agent.getMap().getMapArea();
        if (hasKnownMapBounds(area)
                && !area.contains(agentPosition)
                && manhattan > outOfBoundsTeleportDistance * distanceMultiplier) {
            return executeRecoveryTeleport(entry, agent, anchorPosition, hooks);
        }
        return false;
    }

    private static boolean executeRecoveryTeleport(AgentRuntimeEntry entry,
                                                   Character agent,
                                                   Point targetPosition,
                                                   RecoveryHooks hooks) {
        Point spawn = hooks.groundPointFinder().apply(agent.getMap(), new Point(targetPosition.x, targetPosition.y - 1));
        if (spawn == null) {
            spawn = targetPosition;
        }
        hooks.teleporter().teleport(entry, agent, spawn);
        hooks.afterTeleportReset().accept(entry);
        hooks.movementBroadcaster().accept(entry);
        return true;
    }

    private static boolean isInKnownMapBounds(MapleMap map, Point point) {
        Rectangle area = map == null ? null : map.getMapArea();
        return !hasKnownMapBounds(area) || area.contains(point);
    }

    private static boolean hasKnownMapBounds(Rectangle area) {
        return area != null && area.width > 0 && area.height > 0;
    }
}
