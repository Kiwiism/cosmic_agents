package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentBotMovementPhysicsStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.*;

public final class AgentFallbackMovementService {
    private AgentFallbackMovementService() {
    }

    public static Point resolveSteeringTarget(BotEntry entry, Point botPos, Point targetPos) {
        Rope rope = selectNearbyRope(entry, botPos, targetPos);
        Point ledgeTarget = resolveFallbackLedgeTarget(entry, botPos, targetPos, rope);
        if (ledgeTarget != null) {
            return ledgeTarget;
        }
        if (rope == null) {
            return targetPos;
        }
        return new Point(rope.x(), botPos.y);
    }

    public static boolean tryImmediateAction(BotEntry entry, Point botPos, Point targetPos) {
        Character bot = bot(entry);
        MapleMap map = map(entry);
        Rope rope = selectNearbyRope(entry, botPos, targetPos);
        if (rope != null) {
            if (canDirectlyAttachToRope(botPos, rope)) {
                int attachY = Math.max(rope.topY(), Math.min(botPos.y, rope.bottomY()));
                BotPhysicsEngine.attachToRope(entry, bot, rope, attachY);
                AgentMovementBroadcastService.broadcastMovement(entry);
                return true;
            }

            int ropeDx = rope.x() - botPos.x;
            int ropeJumpRange = Math.max(AgentMovementPhysicsConfig.configuredRopeGrabX() * 2,
                    AgentMovementKinematicsService.walkStep(map, movementProfile(entry)) * 2);
            if (Math.abs(ropeDx) <= ropeJumpRange
                    && BotPhysicsEngine.canReachRopeFromGround(map, botPos, rope, movementProfile(entry))) {
                AgentJumpActionService.initiateRopeJump(entry, bot, ropeDx);
                return true;
            }
        }

        // In swim maps, leap upward off the platform to chase an airborne
        // target above (e.g. owner swimming overhead). Once airborne, swim
        // physics owns motion and steers horizontally toward the target.
        // Without this, bot stays grounded forever since walking on platforms
        // never closes vertical distance to a swimming target.
        if (shouldJumpUpIntoSwim(entry, botPos, targetPos)) {
            AgentJumpActionService.initiateJump(entry, bot, 0);
            return true;
        }

        if (shouldUseDownJump(entry, botPos, targetPos, rope)) {
            AgentQueuedMovementActionService.queueDownJump(entry, bot);
            AgentMovementBroadcastService.broadcastMovement(entry);
            return true;
        }

        Point steeringTarget = rope == null ? targetPos : new Point(rope.x(), targetPos.y);
            int stepX = AgentGroundMovementService.resolveGroundStepX(entry, botPos, steeringTarget,
                AgentMovementPhysicsConfig.configuredStopDist(), AgentMovementPhysicsConfig.configuredFollowDist());
        if (stepX == 0 || BotPhysicsEngine.canWalkGroundStep(map, botPos, stepX)) {
            return false;
        }

        if (shouldUseJump(entry, botPos, steeringTarget, stepX)) {
            AgentJumpActionService.initiateJump(entry, bot, steeringTarget.x - botPos.x);
            return true;
        }

        return false;
    }

    private static boolean shouldJumpUpIntoSwim(BotEntry entry, Point botPos, Point targetPos) {
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || botPos == null || targetPos == null) {
            return false;
        }
        if (AgentBotMovementStateRuntime.inAir(entry)
                || AgentBotMovementStateRuntime.climbing(entry)
                || AgentBotMovementPhysicsStateRuntime.jumpCooldownMs(entry) > 0
                || AgentBotMovementStateRuntime.downJumpPending(entry)) {
            return false;
        }
        MapleMap map = map(entry);
        if (map == null || !map.isSwim()) {
            return false;
        }
        // Target must be sufficiently above bot. dy < 0 = target higher in MS coords.
        int dy = targetPos.y - botPos.y;
        return dy < -Math.max(AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2, 60);
    }

    public static boolean shouldWalkOffLedge(BotEntry entry, Point botPos, Point targetPos, int stepX) {
        if (entry == null || !AgentBotNavigationDebugStateRuntime.graphWarmupFallback(entry) || botPos == null || targetPos == null || stepX == 0) {
            return false;
        }
        if (targetPos.y <= botPos.y + AgentMovementPhysicsConfig.configuredMaxSnapDrop()) {
            return false;
        }
        Point ahead = new Point(botPos.x + stepX, botPos.y);
        return BotPhysicsEngine.isGroundFarBelow(map(entry), ahead);
    }

    private static Rope selectNearbyRope(BotEntry entry, Point botPos, Point targetPos) {
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || botPos == null || targetPos == null) {
            return null;
        }

        int dy = targetPos.y - botPos.y;
        if (Math.abs(dy) < Math.max(AgentMovementPhysicsConfig.configuredJumpYThreshold() * 2, 60)) {
            return null;
        }

        MapleMap map = map(entry);
        int walkStep = AgentMovementKinematicsService.walkStep(map, movementProfile(entry));
        int searchX = Math.max(walkStep * 4, 90);
        Rope best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Rope rope : map.getRopes()) {
            int dx = Math.abs(rope.x() - botPos.x);
            if (dx > searchX) {
                continue;
            }

            if (dy < 0) {
                if (rope.topY() >= botPos.y - AgentMovementPhysicsConfig.configuredMaxSnapDrop()) {
                    continue;
                }
                if (rope.bottomY() < botPos.y - AgentMovementPhysicsConfig.configuredMaxSnapDrop()) {
                    continue;
                }
                if (rope.topY() > targetPos.y + AgentMovementPhysicsConfig.configuredFollowYCap()) {
                    continue;
                }
            } else {
                if (rope.bottomY() <= botPos.y + AgentMovementPhysicsConfig.configuredMaxSnapDrop()) {
                    continue;
                }
                if (rope.topY() > botPos.y + AgentMovementPhysicsConfig.configuredMaxSlopeUp()) {
                    continue;
                }
                if (rope.bottomY() < targetPos.y - AgentMovementPhysicsConfig.configuredFollowYCap()) {
                    continue;
                }
            }

            int verticalPenalty = dy < 0
                    ? Math.max(0, rope.topY() - targetPos.y)
                    : Math.max(0, targetPos.y - rope.bottomY());
            int score = dx * 4 + verticalPenalty;
            if (score < bestScore) {
                best = rope;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean canDirectlyAttachToRope(Point botPos, Rope rope) {
        // Allow attach when bot is within rope's Y range, OR slightly above
        // rope.topY (within MAX_SNAP_DROP) so a player standing on a platform
        // whose surface meets the rope's head can transition into climbing
        // without first going airborne — same "press DOWN to grab from top"
        // motion as the real client. attachY snaps to rope.topY in the caller.
        return botPos != null
                && rope != null
                && Math.abs(botPos.x - rope.x()) <= AgentMovementPhysicsConfig.configuredRopeGrabX()
                && botPos.y >= rope.topY() - AgentMovementPhysicsConfig.configuredMaxSnapDrop()
                && botPos.y <= rope.bottomY() + AgentMovementPhysicsConfig.configuredMaxSnapDrop();
    }

    private static Point resolveFallbackLedgeTarget(BotEntry entry, Point botPos, Point targetPos, Rope rope) {
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || botPos == null || targetPos == null || rope != null) {
            return null;
        }
        MapleMap map = map(entry);
        if (!shouldConsiderFallbackDrop(entry, map, botPos, targetPos)) {
            return null;
        }

        Foothold foothold = AgentGroundingService.findGroundFoothold(map, botPos);
        if (foothold == null) {
            return null;
        }

        Point left = walkOffTarget(map, foothold, movementProfile(entry), -1);
        Point right = walkOffTarget(map, foothold, movementProfile(entry), 1);
        Point best = chooseBetterLedgeTarget(botPos, targetPos, left, right);
        if (best == null) {
            return null;
        }
        return new Point(best.x, targetPos.y);
    }

    private static boolean shouldUseDownJump(BotEntry entry, Point botPos, Point targetPos, Rope rope) {
        if (entry == null || botPos == null || targetPos == null || rope != null) {
            return false;
        }
        MapleMap map = map(entry);
        if (!shouldConsiderFallbackDrop(entry, map, botPos, targetPos)
                || !BotPhysicsEngine.canStartDownJump(map, botPos)) {
            return false;
        }
        return Math.abs(targetPos.x - botPos.x) <= Math.max(AgentMovementPhysicsConfig.configuredFollowDist(),
                AgentMovementKinematicsService.walkStep(map, movementProfile(entry)) * 4);
    }

    private static boolean shouldConsiderFallbackDrop(BotEntry entry, MapleMap map, Point botPos, Point targetPos) {
        if (entry == null || map == null || botPos == null || targetPos == null) {
            return false;
        }
        int dy = targetPos.y - botPos.y;
        if (dy < Math.max(AgentMovementPhysicsConfig.configuredMaxSnapDrop() * 3, 90)) {
            return false;
        }

        Foothold currentFoothold = AgentGroundingService.findGroundFoothold(map, botPos);
        Foothold targetFoothold = AgentGroundingService.findGroundFoothold(map, targetPos);
        return currentFoothold == null
                || targetFoothold == null
                || currentFoothold.getId() != targetFoothold.getId();
    }

    private static Point walkOffTarget(MapleMap map, Foothold foothold, AgentMovementProfile profile, int direction) {
        if (map == null || foothold == null || direction == 0) {
            return null;
        }
        Point endpoint = direction < 0
                ? new Point(foothold.getX1(), foothold.getY1())
                : new Point(foothold.getX2(), foothold.getY2());
        int step = direction * Math.max(1, AgentMovementKinematicsService.walkStep(map, profile));
        Point ahead = new Point(endpoint.x + step, endpoint.y);
        return BotPhysicsEngine.isGroundFarBelow(map, ahead) ? ahead : null;
    }

    private static Point chooseBetterLedgeTarget(Point botPos, Point targetPos, Point left, Point right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }

        int desiredDirection = Integer.compare(targetPos.x, botPos.x);
        if (desiredDirection < 0 && left.x <= botPos.x) {
            return left;
        }
        if (desiredDirection > 0 && right.x >= botPos.x) {
            return right;
        }

        int leftScore = Math.abs(targetPos.x - left.x) + Math.abs(botPos.x - left.x);
        int rightScore = Math.abs(targetPos.x - right.x) + Math.abs(botPos.x - right.x);
        return leftScore <= rightScore ? left : right;
    }

    private static boolean shouldUseJump(BotEntry entry, Point botPos, Point steeringTarget, int stepX) {
        if (entry == null || botPos == null || steeringTarget == null || stepX == 0) {
            return false;
        }
        if (shouldWalkOffLedge(entry, botPos, steeringTarget, stepX)) {
            return false;
        }

        MapleMap map = map(entry);
        int direction = Integer.signum(stepX);
        int jumpStep = direction * AgentMovementKinematicsService.walkStep(map, movementProfile(entry));
        BotPhysicsEngine.JumpLanding landing =
                BotPhysicsEngine.simulateJumpLanding(map, botPos, jumpStep, movementProfile(entry));
        return isUsefulJumpProbeLanding(botPos, steeringTarget, direction, landing);
    }

    private static Character bot(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.bot(entry);
    }

    private static MapleMap map(BotEntry entry) {
        return AgentBotRuntimeIdentityRuntime.botMap(entry);
    }

    private static AgentMovementProfile movementProfile(BotEntry entry) {
        return AgentBotMovementStateRuntime.movementProfile(entry);
    }

    private static boolean isUsefulJumpProbeLanding(Point botPos,
                                                    Point steeringTarget,
                                                    int direction,
                                                    BotPhysicsEngine.JumpLanding landing) {
        if (landing == null || landing.point() == null || direction == 0) {
            return false;
        }
        Point landingPoint = landing.point();
        int landingDx = landingPoint.x - botPos.x;
        if (Integer.signum(landingDx) != direction) {
            return false;
        }

        int distanceBefore = Math.abs(steeringTarget.x - botPos.x);
        int distanceAfter = Math.abs(steeringTarget.x - landingPoint.x);
        if (distanceAfter >= distanceBefore) {
            return false;
        }

        boolean targetIsAboveOrLevel = steeringTarget.y <= botPos.y + AgentMovementPhysicsConfig.configuredMaxSnapDrop();
        boolean landingIsAboveOrLevel = landingPoint.y <= botPos.y + AgentMovementPhysicsConfig.configuredMaxSnapDrop();
        return targetIsAboveOrLevel && landingIsAboveOrLevel;
    }
}
