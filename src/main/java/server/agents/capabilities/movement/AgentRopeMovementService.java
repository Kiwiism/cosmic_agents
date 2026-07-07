package server.agents.capabilities.movement;

import client.Character;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.navigation.AgentNavigationPhysicsService;
import server.agents.integration.AgentBotClimbStateRuntime;
import server.agents.integration.AgentBotCombatDamageRuntime;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotSwimStateRuntime;
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
        int ropeY = Math.clamp(y, AgentNavigationPhysicsService.firstClimbableY(rope), rope.bottomY());
        AgentBotClimbStateRuntime.setClimbVerticalDirection(entry, 0);
        setClimbPosition(entry, agent, rope, ropeY);
    }

    public static void holdClimb(AgentRuntimeEntry entry, Character agent) {
        Rope rope = AgentBotClimbStateRuntime.climbRope(entry);
        if (rope == null) {
            beginFall(entry, agent, 0);
            return;
        }
        if (resolveClimbBoundary(entry, agent, rope, agent.getPosition().y)) {
            return;
        }

        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    public static void advanceClimb(AgentRuntimeEntry entry, Character agent) {
        Rope rope = AgentBotClimbStateRuntime.climbRope(entry);
        if (rope == null) {
            beginFall(entry, agent, 0);
            return;
        }

        int climbDirection = Integer.compare(AgentBotClimbStateRuntime.climbVerticalDirection(entry), 0);
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

    public static void beginGroundJump(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        if (agent.getMap() != null && agent.getMap().isSwim()) {
            beginSwimGroundJump(entry, agent);
            return;
        }
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.jumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                false);
    }

    public static void beginClimbUpJump(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.jumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                true);
    }

    public static void beginJumpOffRope(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.ropeJumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                false);
    }

    public static void beginRopeTransferJump(AgentRuntimeEntry entry, Character agent, Rope sourceRope, int airVelocityX) {
        AgentBotClimbStateRuntime.setBlockedRopeGrab(entry, sourceRope);
        AgentAirborneLaunchService.launchAirborne(
                entry,
                agent.getPosition(),
                -AgentMovementKinematicsService.ropeJumpForcePerTick(AgentBotMovementStateRuntime.movementProfile(entry)),
                airVelocityX,
                true);
    }

    private static void setClimbPosition(AgentRuntimeEntry entry, Character agent, Rope rope, int y) {
        Point position = new Point(rope.x(), y);
        agent.setPosition(position);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, rope);
        AgentBotMovementStateRuntime.setInAir(entry, false);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static void beginSwimGroundJump(AgentRuntimeEntry entry, Character agent) {
        Point position = agent.getPosition();
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setInAir(entry, true);
        AgentBotSwimStateRuntime.setSwimming(entry, true);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry,
                -profileOrBase(AgentBotMovementStateRuntime.movementProfile(entry)).jumpSpeedPxs());
        AgentGroundPhysicsService.stopGroundMotion(entry);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotSwimStateRuntime.setSwimJumpRequested(entry, false);
        AgentBotSwimStateRuntime.setSwimNextJumpAtMs(entry,
                System.currentTimeMillis() + AgentMovementPhysicsConfig.configuredSwimJumpCooldownMs());
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0,
                Math.round(AgentBotMovementPhysicsStateRuntime.verticalVelocity(entry)));
        AgentMovementPoseService.syncCharacterState(entry);
    }

    private static boolean resolveClimbBoundary(AgentRuntimeEntry entry, Character agent, Rope rope, int candidateY) {
        if (candidateY <= rope.topY()) {
            Point landing = findTopLandingPoint(agent, rope, candidateY);
            if (landing != null) {
                landOnTopGround(entry, agent, landing);
            } else {
                setClimbPosition(entry, agent, rope, AgentNavigationPhysicsService.firstClimbableY(rope));
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

    private static void beginFall(AgentRuntimeEntry entry, Character agent, int airVelocityX) {
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        Point position = agent.getPosition();
        agent.setPosition(new Point(position));
        AgentAirborneLaunchService.launchAirborne(entry, position, 0f, airVelocityX, false);
    }

    private static void landOnTopGround(AgentRuntimeEntry entry, Character agent, Point position) {
        agent.setPosition(position);
        AgentBotMovementStateRuntime.setInAir(entry, false);
        AgentBotClimbStateRuntime.setClimbingOnRope(entry, null);
        AgentBotMovementStateRuntime.setCrouching(entry, false);
        AgentBotClimbStateRuntime.setClimbUpIntent(entry, false);
        AgentBotMovementPhysicsStateRuntime.setVerticalVelocity(entry, 0f);
        AgentBotMovementPhysicsStateRuntime.setAirVelocityX(entry, 0);
        AgentBotMovementPhysicsStateRuntime.setAirSteerVelocityX(entry, 0.0);
        AgentBotMovementPhysicsStateRuntime.setFixedAirArc(entry, false);
        AgentBotMovementPhysicsStateRuntime.setPhysicsPosition(entry, position);
        AgentBotClimbStateRuntime.clearRopeEntry(entry);
        AgentBotMovementStateRuntime.setDownJumpPending(entry, false);
        AgentBotMovementStateRuntime.setDownJumpGracePeriodMs(entry, 0L);
        AgentBotMovementPhysicsStateRuntime.setGroundPhysicsCarryMs(entry, 0.0);
        AgentBotClimbStateRuntime.clearBlockedRopeGrab(entry);
        AgentBotMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 0.0);
        AgentBotMovementStateRuntime.setMovementVelocity(entry, 0, 0);
        AgentMovementPoseService.syncCharacterState(entry);
        AgentBotCombatDamageRuntime.applyFallDamage(entry, agent, 0f, AgentCombatConfig.cfg);
        AgentBotMovementPhysicsStateRuntime.resetFallPeakPhysicsY(entry);
    }

    private static AgentMovementProfile profileOrBase(AgentMovementProfile profile) {
        return profile != null ? profile : AgentMovementProfile.base();
    }
}
