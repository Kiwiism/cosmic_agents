package server.agents.capabilities.movement;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

/** Initializes normal airborne physics when a map portal spawns an Agent above ground. */
public final class AgentSpawnFallService {
    public static final int MIN_DROP_PX = 12;

    private AgentSpawnFallService() {
    }

    public static boolean shouldFall(Point spawnPosition, Point groundPosition) {
        return spawnPosition != null && groundPosition != null
                && groundPosition.y - spawnPosition.y > MIN_DROP_PX;
    }

    public static void beginFall(AgentRuntimeEntry entry, Character agent) {
        Point position = agent.getPosition();
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementStateRuntime.clearMoveDirection(entry);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPoseService.syncCharacterState(entry);
    }
}
