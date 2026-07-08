package server.agents.capabilities.movement;

import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentGroundMovementService {
    private AgentGroundMovementService() {
    }

    public static int resolveGroundStepX(AgentRuntimeEntry entry, Point botPos, Point targetPos, int stopDist, int followDist) {
        if (entry == null || !AgentRuntimeIdentityRuntime.hasBot(entry) || botPos == null || targetPos == null) {
            return 0;
        }
        if (AgentNavigationDebugStateRuntime.graphWarmupFallback(entry)) {
            int localStopDist = Math.min(stopDist, 12);
            return updateStepX(entry, AgentRuntimeIdentityRuntime.botMap(entry), botPos.x, targetPos.x, localStopDist, localStopDist);
        }
        return updateStepX(entry, AgentRuntimeIdentityRuntime.botMap(entry), botPos.x, targetPos.x, stopDist, followDist);
    }

    public static int calcStepX(MapleMap map, int botX, int targetX, boolean wasMovingX) {
        return calcStepX(map, AgentMovementProfile.base(), botX, targetX, wasMovingX,
                AgentMovementPhysicsConfig.configuredStopDist(), AgentMovementPhysicsConfig.configuredFollowDist());
    }

    public static int calcStepX(MapleMap map, int botX, int targetX, boolean wasMovingX, int stopDist, int followDist) {
        return calcStepX(map, AgentMovementProfile.base(), botX, targetX, wasMovingX, stopDist, followDist);
    }

    public static int calcStepX(MapleMap map,
                                AgentMovementProfile profile,
                                int botX,
                                int targetX,
                                boolean wasMovingX,
                                int stopDist,
                                int followDist) {
        return AgentGroundMovementPolicy.calcStepX(
                botX,
                targetX,
                wasMovingX,
                stopDist,
                followDist,
                AgentMovementKinematicsService.walkStep(map, profile));
    }

    public static int updateStepX(AgentRuntimeEntry entry, MapleMap map, int botX, int targetX) {
        return updateStepX(entry, map, botX, targetX,
                AgentMovementPhysicsConfig.configuredStopDist(), AgentMovementPhysicsConfig.configuredFollowDist());
    }

    public static int updateStepX(AgentRuntimeEntry entry, MapleMap map, int botX, int targetX, int stopDist, int followDist) {
        int stepX = calcStepX(map, AgentMovementStateRuntime.movementProfile(entry), botX, targetX,
                AgentMovementStateRuntime.wasMovingX(entry), stopDist, followDist);
        if (stepX == 0) {
            AgentMovementStateRuntime.setWasMovingX(entry, false);
            return 0;
        }
        AgentMovementStateRuntime.setWasMovingX(entry, true);
        return stepX;
    }
}
