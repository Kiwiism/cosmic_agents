package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.agents.capabilities.combat.AgentCombatDamageRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent-owned seam for rope and ladder movement actions while physics internals migrate.
 */
public final class AgentRopeMovementService {
    private AgentRopeMovementService() {
    }

    public static void attachToRope(AgentRuntimeEntry entry, Character agent, Rope rope, int y) {
        attachToRope(entry, agent, rope, y, 0);
    }

    public static void attachToRope(AgentRuntimeEntry entry,
                                    Character agent,
                                    Rope rope,
                                    int y,
                                    int climbDirection) {
        int ropeY = Math.clamp(y, AgentNavigationPhysicsService.firstClimbableY(rope), rope.bottomY());
        AgentClimbStateRuntime.setClimbVerticalDirection(entry, climbDirection);
        setClimbPosition(entry, agent, rope, ropeY);
    }

    public static void holdClimb(AgentRuntimeEntry entry, Character agent) {
        Rope rope = AgentClimbStateRuntime.climbRope(entry);
        if (rope == null) {
            beginFall(entry, agent, 0);
            return;
        }
        if (resolveClimbBoundary(entry, agent, rope, agent.getPosition().y)) {
            return;
        }

        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void advanceClimb(AgentRuntimeEntry entry, Character agent) {
        Rope rope = AgentClimbStateRuntime.climbRope(entry);
        if (rope == null) {
            beginFall(entry, agent, 0);
            return;
        }

        int climbDirection = Integer.compare(AgentClimbStateRuntime.climbVerticalDirection(entry), 0);
        if (climbDirection == 0) {
            holdClimb(entry, agent);
            return;
        }

        int nextY = agent.getPosition().y + climbDirection * AgentMovementKinematicsService.climbStepPerTick();
        if (resolveClimbBoundary(entry, agent, rope, nextY)) {
            return;
        }

        setClimbPosition(entry, agent, rope, nextY);
    }

    /**
     * Advances a navigation-owned climb toward its authored exit height without passing it.
     *
     * <p>Ordinary climb input is directional, but a graph exit can sit partway along a rope.
     * Reapplying the direction selected at rope entry can step across that exact launch pixel and
     * leave the Agent climbing toward the rope head forever. Navigation supplies the exit Y; this
     * movement primitive owns only the continuous integration needed to reach it.</p>
     */
    public static void advanceClimbToward(AgentRuntimeEntry entry, Character agent, int targetY) {
        Rope rope = AgentClimbStateRuntime.climbRope(entry);
        if (rope == null) {
            beginFall(entry, agent, 0);
            return;
        }

        int currentY = agent.getPosition().y;
        int climbDirection = Integer.compare(targetY, currentY);
        AgentClimbStateRuntime.setClimbVerticalDirection(entry, climbDirection);
        if (climbDirection == 0) {
            holdClimb(entry, agent);
            return;
        }

        int climbStep = AgentMovementKinematicsService.climbStepPerTick();
        int distance = Math.abs(targetY - currentY);
        int nextY = distance <= climbStep
                ? targetY
                : currentY + climbDirection * climbStep;
        if (resolveClimbBoundary(entry, agent, rope, nextY)) {
            return;
        }

        setClimbPosition(entry, agent, rope, nextY);
    }

    public static void beginGroundJump(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        if (agent.getMap() != null && agent.getMap().isSwim()) {
            beginSwimGroundJump(entry, agent);
            return;
        }
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.jumpForcePerTick(AgentMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                false);
    }

    public static void beginClimbUpJump(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.jumpForcePerTick(AgentMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                true);
    }

    public static void beginJumpOffRope(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.ropeJumpForcePerTick(AgentMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                false);
    }

    public static void beginRopeTransferJump(AgentRuntimeEntry entry, Character agent, Rope sourceRope, int airVelocityX) {
        AgentClimbStateRuntime.setBlockedRopeGrab(entry, sourceRope);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.ropeJumpForcePerTick(AgentMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                true);
    }

    /**
     * Completes a graph-authored zero-step rope exit onto its adjacent platform.
     *
     * <p>The graph has already verified that the rope head and destination foothold meet. Using
     * that authored landing point avoids probing {@code getPointBelow} from a coordinate exactly
     * level with the foothold, which can skip the adjacent surface and make an Agent repeatedly
     * climb and fall from an otherwise valid rope.</p>
     */
    public static void stepOffRopeOntoGround(AgentRuntimeEntry entry, Character agent, Point landing) {
        if (entry == null || agent == null || landing == null || !AgentClimbStateRuntime.climbing(entry)) {
            return;
        }
        landOnTopGround(entry, agent, new Point(landing));
    }

    private static void setClimbPosition(AgentRuntimeEntry entry, Character agent, Rope rope, int y) {
        Point position = new Point(rope.x(), y);
        agent.setPosition(position);
        AgentClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentMovementStateRuntime.setInAir(entry, false);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentClimbStateRuntime.clearRopeEntry(entry);
        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static void beginSwimGroundJump(AgentRuntimeEntry entry, Character agent) {
        Point position = agent.getPosition();
        AgentClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentSwimStateRuntime.setSwimming(entry, true);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry,
                -profileOrBase(AgentMovementStateRuntime.movementProfile(entry)).jumpSpeedPxs());
        AgentGroundPhysicsService.stopGroundMotion(entry);
        AgentClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        AgentSwimStateRuntime.setSwimJumpRequested(entry, false);
        AgentSwimStateRuntime.setSwimNextJumpAtMs(entry,
                System.currentTimeMillis() + AgentMovementPhysicsConfig.configuredSwimJumpCooldownMs());
        AgentMovementStateRuntime.setMovementVelocity(entry, 0,
                Math.round(AgentMovementPhysicsStateRuntime.verticalVelocity(entry)));
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static boolean resolveClimbBoundary(AgentRuntimeEntry entry, Character agent, Rope rope, int candidateY) {
        if (candidateY <= rope.topY()) {
            Point landing = findTopLandingPoint(agent, rope, candidateY);
            if (landing != null) {
                landOnTopGround(entry, agent, landing);
            } else {
                // A graph edge or warm-up fallback can occasionally attach to a rope whose head
                // has no executable ground exit. Holding at firstClimbableY makes the climbing
                // movement phase own the Agent forever, so navigation never gets another chance
                // to reject or suppress that route. Treat the missing landing as an execution
                // failure: block the same rope for the airborne arc and return to normal physics.
                AgentClimbStateRuntime.setBlockedRopeGrab(entry, rope);
                beginFallPreservingBlockedRope(entry, agent, 0);
            }
            return true;
        }
        if (candidateY > rope.bottomY()) {
            beginFall(entry, agent, 0);
            return true;
        }
        return false;
    }

    private static Point findTopLandingPoint(Character agent, Rope rope, int candidateY) {
        MapleMap map = agent.getMap();
        if (map == null) {
            return null;
        }

        int probeY = Math.min(candidateY, rope.topY()) - 3;
        Point probe = new Point(rope.x(), probeY);
        Point below = map.getPointBelow(probe);
        if (below == null) {
            return null;
        }
        int maxDrop = AgentMovementPhysicsConfig.configuredMaxSnapDrop() + AgentMovementPhysicsConfig.configuredMaxSlopeUp();
        if (below.y - probeY > maxDrop) {
            return null;
        }
        return below;
    }

    static boolean hasTopLanding(MapleMap map, Rope rope) {
        if (map == null || rope == null) {
            return false;
        }
        int probeY = rope.topY() - 3;
        Point below = map.getPointBelow(new Point(rope.x(), probeY));
        if (below == null) {
            return false;
        }
        int maxDrop = AgentMovementPhysicsConfig.configuredMaxSnapDrop()
                + AgentMovementPhysicsConfig.configuredMaxSlopeUp();
        return below.y - probeY <= maxDrop;
    }

    private static void beginFall(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        beginFallPreservingBlockedRope(entry, agent, airVelocityX);
    }

    private static void beginFallPreservingBlockedRope(
            AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        Point position = agent.getPosition();
        agent.setPosition(new Point(position));
        AgentAirborneLaunchService.launchAirborne(entry, position, 0f, airVelocityX, false);
    }

    private static void landOnTopGround(AgentRuntimeEntry entry, Character agent, Point position) {
        agent.setPosition(position);
        AgentMovementStateRuntime.setInAir(entry, false);
        AgentClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentMovementStateRuntime.setCrouching(entry, false);
        AgentClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentClimbStateRuntime.clearRopeEntry(entry);
        AgentMovementStateRuntime.setDownJumpPending(entry, false);
        AgentMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        AgentMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, 0.0);
        AgentClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementPoseService.syncCharacterState(entry);
        AgentCombatDamageRuntime.applyFallDamage(entry, agent, 0f, AgentCombatConfig.cfg);
        AgentMovementPhysicsStateRuntime.resetFallPeakPhysicsY(entry);
    }

    private static AgentMovementProfile profileOrBase(AgentMovementProfile profile) {
        return profile != null ? profile : AgentMovementProfile.base();
    }
}
