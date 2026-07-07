package server.agents.capabilities.movement;

import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentGroundMovementService {
    private AgentGroundMovementService() {
    }

    public static int resolveGroundStepX(AgentRuntimeEntry entry, Point botPos, Point targetPos, int stopDist, int followDist) {
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || botPos == null || targetPos == null) {
            return 0;
        }
        if (AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry)) {
            int localStopDist = Math.min(stopDist, 12);
            return updateStepX(entry, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos.x, targetPos.x, localStopDist, localStopDist);
        }
        return updateStepX(entry, AgentBotRuntimeIdentityRuntime.botMap(entry), botPos.x, targetPos.x, stopDist, followDist);
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
        int stepX = calcStepX(map, AgentBotMovementStateRuntime.movementProfile(entry), botX, targetX,
                AgentBotMovementStateRuntime.wasMovingX(entry), stopDist, followDist);
        if (stepX == 0) {
            AgentBotMovementStateRuntime.setWasMovingX(entry, false);
            return 0;
        }
        AgentBotMovementStateRuntime.setWasMovingX(entry, true);
        return stepX;
    }
}
