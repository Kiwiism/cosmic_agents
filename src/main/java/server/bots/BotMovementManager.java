package server.bots;

import client.Character;
import io.netty.buffer.Unpooled;
import net.packet.ByteBufInPacket;
import net.packet.InPacket;
import net.packet.Packet;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;
import tools.PacketCreator;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

class BotMovementManager {
    enum ActionType {
        IDLE,
        WALK,
        CROUCH,
        JUMP,
        CLIMB_UP,
        CLIMB_DOWN
    }

    record MoveAction(ActionType type, int stepX) {
        private static final MoveAction IDLE = new MoveAction(ActionType.IDLE, 0);
        private static final MoveAction CROUCH = new MoveAction(ActionType.CROUCH, 0);
        private static final MoveAction CLIMB_UP = new MoveAction(ActionType.CLIMB_UP, 0);
        private static final MoveAction CLIMB_DOWN = new MoveAction(ActionType.CLIMB_DOWN, 0);

        static MoveAction idle() {
            return IDLE;
        }

        static MoveAction walk(int stepX) {
            return new MoveAction(ActionType.WALK, stepX);
        }

        static MoveAction crouch() {
            return CROUCH;
        }

        static MoveAction jump(int stepX) {
            return new MoveAction(ActionType.JUMP, stepX);
        }

        static MoveAction climbUp() {
            return CLIMB_UP;
        }

        static MoveAction climbDown() {
            return CLIMB_DOWN;
        }
    }

    static final class JumpLanding {
        private final Point point;
        private final Foothold foothold;

        JumpLanding(Point point, Foothold foothold) {
            this.point = point;
            this.foothold = foothold;
        }

        Point point() {
            return point;
        }

        Foothold foothold() {
            return foothold;
        }
    }

    static class Config extends BotPhysicsEngine.Config {
        public int STOP_DIST = 30;
        public int FOLLOW_DIST = 80;

        public int JUMP_Y_THRESH = 30;
        public int JUMP_COOLDOWN_MS = 1000;
        public int TELEPORT_DIST = 2000;
    }

    static Config cfg = bindConfig(new Config());

    private static Config bindConfig(Config config) {
        BotPhysicsEngine.cfg = config;
        return config;
    }

    static int tickDown(int remainingMs) {
        if (remainingMs <= 0) {
            return 0;
        }
        return Math.max(0, remainingMs - BotPhysicsEngine.cfg.TICK_MS);
    }

    static int delayAfterCurrentTick(int durationMs) {
        if (durationMs <= 0) {
            return 0;
        }
        return Math.max(0, durationMs - BotPhysicsEngine.cfg.TICK_MS);
    }

    static int walkStep(MapleMap map) {
        return BotPhysicsEngine.walkStep(map);
    }

    static int velocityFromDeltaX(double deltaX) {
        return BotPhysicsEngine.velocityFromDeltaX(deltaX);
    }

    static void stopGroundMotion(BotEntry entry) {
        BotPhysicsEngine.stopGroundMotion(entry);
    }

    static JumpLanding simulateJumpLanding(MapleMap map, Point from, int stepX) {
        return wrapLanding(BotPhysicsEngine.simulateJumpLanding(map, from, stepX));
    }

    static JumpLanding simulateRopeJumpLanding(MapleMap map, Point from, int stepX) {
        return wrapLanding(BotPhysicsEngine.simulateRopeJumpLanding(map, from, stepX));
    }

    static boolean canReachRopeFromGround(MapleMap map, Point from, Rope rope) {
        return BotPhysicsEngine.canReachRopeFromGround(map, from, rope);
    }

    static void resetEntryState(BotEntry entry) {
        BotPhysicsEngine.resetMotion(entry, entry.bot.getPosition());
        entry.grindTarget = null;
        entry.attackCooldownMs = 0;
        entry.navTargetPos = null;
        entry.navEdge = null;
        entry.navTargetRegionId = -1;
        entry.navPreciseTarget = false;
        entry.movementBroadcastValid = false;
    }

    static void tickClimbing(BotEntry entry, Point targetPos, boolean runAiTick) {
        long startedAt = System.nanoTime();
        try {
            Character bot = entry.bot;
            Rope rope = entry.climbRope;
            if (rope == null) {
                BotPhysicsEngine.beginFall(entry, bot, 0);
                broadcastMovement(entry);
                return;
            }

            BotPhysicsEngine.tickMotionTimers(entry);
            Point botPos = bot.getPosition();
            int dy = targetPos.y - botPos.y;
            int dxOwner = targetPos.x - rope.x();

            entry.jumpCooldownMs = tickDown(entry.jumpCooldownMs);

            if (botPos.y <= rope.topY()) {
                Point ground = bot.getMap().getPointBelow(new Point(botPos.x, botPos.y - 3));
                if (ground != null && ground.y <= botPos.y + BotPhysicsEngine.climbStepPerTick() + 2) {
                    BotPhysicsEngine.landOnGround(entry, bot, new Point(botPos.x, ground.y));
                } else {
                    BotPhysicsEngine.beginFall(entry, bot, 0);
                }
                broadcastMovement(entry);
                return;
            }

            if (botPos.y >= rope.bottomY() + 3) {
                BotPhysicsEngine.beginFall(entry, bot, 0);
                broadcastMovement(entry);
                return;
            }

            if (runAiTick) {
                if (shouldJumpOffCommittedClimb(entry, botPos)) {
                    jumpOffRope(entry, bot, entry.navEdge.launchStepX);
                    return;
                }
                if (Math.abs(dxOwner) > cfg.FOLLOW_DIST && entry.jumpCooldownMs == 0
                        && rope.bottomY() < targetPos.y) {
                    jumpOffRope(entry, bot, dxOwner);
                    return;
                }
            }

            boolean climbIdle = shouldHoldClimbIdle(entry, dy, dxOwner);
            if (climbIdle) {
                BotPhysicsEngine.holdClimb(entry);
                broadcastMovement(entry);
                return;
            }

            MoveAction action = planClimbAction(entry, botPos, dy);
            applyClimbAction(entry, bot, action);
        } finally {
            BotPerformanceMonitor.record("move-climb", System.nanoTime() - startedAt);
        }
    }

    static void jumpOffRope(BotEntry entry, Character bot, int dx) {
        int walkStep = BotPhysicsEngine.walkStep(bot.getMap());
        int airVelX = dx > 0 ? walkStep : dx < 0 ? -walkStep : 0;
        BotPhysicsEngine.beginJumpOffRope(entry, bot, airVelX);
        entry.ropeGrabCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS + 200);
        entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
        broadcastMovement(entry);
    }

    private static MoveAction planClimbAction(BotEntry entry, Point botPos, int dy) {
        if (isCommittedClimbDown(entry, botPos)) {
            return MoveAction.climbDown();
        }
        if (dy < 0 || entry.climbRope.topY() < botPos.y + 200) {
            return MoveAction.climbUp();
        }
        return MoveAction.climbDown();
    }

    private static void applyClimbAction(BotEntry entry, Character bot, MoveAction action) {
        int step = BotPhysicsEngine.climbStepPerTick();
        int currentY = bot.getPosition().y;
        int nextY = switch (action.type()) {
            case CLIMB_UP -> currentY - step;
            case CLIMB_DOWN -> currentY + step;
            default -> currentY;
        };

        if (action.type() == ActionType.IDLE) {
            BotPhysicsEngine.holdClimb(entry);
        } else {
            BotPhysicsEngine.moveOnRope(entry, bot, nextY);
        }
        broadcastMovement(entry);
    }

    private static boolean shouldJumpOffCommittedClimb(BotEntry entry, Point botPos) {
        if (entry.navEdge == null || entry.navEdge.type != BotNavigationGraph.EdgeType.CLIMB) {
            return false;
        }
        if (entry.navEdge.launchStepX == 0 || entry.jumpCooldownMs != 0) {
            return false;
        }
        if (botPos.y > entry.climbRope.topY() + cfg.JUMP_Y_THRESH) {
            return false;
        }

        JumpLanding landing = simulateRopeJumpLanding(entry.bot.getMap(), botPos, entry.navEdge.launchStepX);
        if (landing == null) {
            return false;
        }

        BotNavigationGraph graph = BotNavigationGraphProvider.getGraph(entry.bot.getMap());
        int landingRegionId = graph.regionIdByFootholdId.getOrDefault(landing.foothold().getId(), -1);
        return landingRegionId == entry.navEdge.toRegionId;
    }

    static boolean shouldHoldClimbIdle(BotEntry entry, int dy, int dxOwner) {
        if (entry.navEdge != null && entry.navEdge.type == BotNavigationGraph.EdgeType.CLIMB) {
            return false;
        }
        return !entry.grinding
                && Math.abs(dy) < cfg.FOLLOW_DIST
                && Math.abs(dxOwner) < cfg.FOLLOW_DIST * 2;
    }

    private static boolean isCommittedClimbDown(BotEntry entry, Point botPos) {
        return entry.navEdge != null
                && entry.navEdge.type == BotNavigationGraph.EdgeType.CLIMB
                && entry.navEdge.endPoint.y > botPos.y + 8;
    }

    static void tickAirborne(BotEntry entry) {
        long startedAt = System.nanoTime();
        try {
            BotPhysicsEngine.tickMotionTimers(entry);

            Character bot = entry.bot;
            Point botPos = bot.getPosition();
            Point previousPos = BotPhysicsEngine.roundedAirPosition(entry);

            if (successfullyGrabbedRope(entry, bot, botPos)) {
                return;
            }

            Point nextPos = BotPhysicsEngine.advanceAirbornePosition(entry, bot);
            if (entry.velY > 0 && BotPhysicsEngine.canLand(entry)) {
                Point floorPoint = bot.getMap().getPointBelow(new Point(nextPos.x, previousPos.y + 1));
                if (floorPoint != null && floorPoint.y <= nextPos.y) {
                    BotPhysicsEngine.landOnGround(entry, bot, new Point(nextPos.x, floorPoint.y));
                    entry.jumpCooldownMs = 0;
                    broadcastMovement(entry);
                    return;
                }
            }

            broadcastMovement(entry);
        } finally {
            BotPerformanceMonitor.record("move-air", System.nanoTime() - startedAt);
        }
    }

    private static boolean successfullyGrabbedRope(BotEntry entry, Character bot, Point botPos) {
        if (!entry.climbUpIntent || entry.ropeGrabCooldownMs != 0) {
            return false;
        }

        for (Rope rope : bot.getMap().getRopes()) {
            if (Math.abs(rope.x() - botPos.x) > BotPhysicsEngine.cfg.ROPE_GRAB_X) {
                continue;
            }
            if (botPos.y < rope.topY() || botPos.y > rope.bottomY()) {
                continue;
            }

            BotPhysicsEngine.attachToRope(entry, bot, rope, botPos.y);
            broadcastMovement(entry);
            return true;
        }

        return false;
    }

    static void tickGrounded(BotEntry entry, Point targetPos) {
        long startedAt = System.nanoTime();
        try {
            Character bot = entry.bot;
            Point botPos = bot.getPosition();

            BotPhysicsEngine.tickMotionTimers(entry);
            BotPhysicsEngine.syncGroundPosition(entry, botPos.x);

            Foothold currentFh = BotPhysicsEngine.findGroundFoothold(bot.getMap(), botPos);
            if (currentFh == null) {
                BotPhysicsEngine.beginFall(entry, bot, 0);
                broadcastMovement(entry);
                return;
            }

            entry.jumpCooldownMs = tickDown(entry.jumpCooldownMs);
            if (entry.downJumpPending) {
                performDownJump(entry);
                return;
            }

            MoveAction action = planGroundAction(entry, botPos, targetPos);
            applyGroundAction(entry, currentFh, action);
        } finally {
            BotPerformanceMonitor.record("move-ground", System.nanoTime() - startedAt);
        }
    }

    private static MoveAction planGroundAction(BotEntry entry, Point botPos, Point targetPos) {
        int stopDist = entry.navPreciseTarget ? 4 : cfg.STOP_DIST;
        int followDist = entry.navPreciseTarget ? 4 : cfg.FOLLOW_DIST;
        int stepX = updateStepX(entry, entry.bot.getMap(), botPos.x, targetPos.x, stopDist, followDist);
        if (stepX == 0) {
            return MoveAction.idle();
        }
        if (!isPathWalkable(entry.bot, botPos, stepX)) {
            return MoveAction.idle();
        }
        return MoveAction.walk(stepX);
    }

    private static void applyGroundAction(BotEntry entry, Foothold currentFh, MoveAction action) {
        Character bot = entry.bot;
        entry.lastDesiredDirection = switch (action.type()) {
            case WALK, JUMP -> Integer.compare(action.stepX(), 0);
            default -> 0;
        };

        if (action.type() == ActionType.CROUCH) {
            BotPhysicsEngine.queueDownJump(entry, bot);
            broadcastMovement(entry);
            return;
        }

        BotPhysicsEngine.GroundMotion motion =
                BotPhysicsEngine.applyGroundMotion(entry, bot, currentFh, entry.lastDesiredDirection);
        if (motion.lostGround()) {
            broadcastMovement(entry);
            return;
        }

        if (motion.stepX() == 0) {
            applyIdleOrInPlaceMotion(entry);
            return;
        }

        broadcastMovement(entry);
    }

    private static void applyIdleOrInPlaceMotion(BotEntry entry) {
        if (entry.movementVelX == 0) {
            BotPhysicsEngine.idleOnGround(entry, entry.bot);
        }
        broadcastMovement(entry);
    }

    private static void performDownJump(BotEntry entry) {
        BotPhysicsEngine.beginDownJump(entry, entry.bot);
        entry.jumpCooldownMs = delayAfterCurrentTick(cfg.JUMP_COOLDOWN_MS);
        broadcastMovement(entry);
    }

    static int calcStepX(MapleMap map, int botX, int targetX, boolean wasMovingX) {
        return calcStepX(map, botX, targetX, wasMovingX, cfg.STOP_DIST, cfg.FOLLOW_DIST);
    }

    static int calcStepX(MapleMap map, int botX, int targetX, boolean wasMovingX, int stopDist, int followDist) {
        int dx = targetX - botX;
        int absDx = Math.abs(dx);
        if (absDx <= stopDist) {
            return 0;
        }
        if (!wasMovingX && absDx <= followDist) {
            return 0;
        }
        return Math.min(absDx, BotPhysicsEngine.walkStep(map)) * (dx >= 0 ? 1 : -1);
    }

    static int updateStepX(BotEntry entry, MapleMap map, int botX, int targetX) {
        return updateStepX(entry, map, botX, targetX, cfg.STOP_DIST, cfg.FOLLOW_DIST);
    }

    static int updateStepX(BotEntry entry, MapleMap map, int botX, int targetX, int stopDist, int followDist) {
        int stepX = calcStepX(map, botX, targetX, entry.wasMovingX, stopDist, followDist);
        if (stepX == 0) {
            entry.wasMovingX = false;
            return 0;
        }
        entry.wasMovingX = true;
        return stepX;
    }

    static boolean isPathWalkable(Character bot, Point botPos, int stepX) {
        Point next = bot.getMap().getPointBelow(new Point(botPos.x + stepX, botPos.y - BotPhysicsEngine.cfg.MAX_SLOPE_UP));
        if (next == null) {
            return false;
        }

        int dy = next.y - botPos.y;
        return dy <= BotPhysicsEngine.cfg.MAX_SNAP_DROP
                && dy >= -BotPhysicsEngine.cfg.MAX_SLOPE_UP;
    }

    static void initiateJump(BotEntry entry, Character bot, int dx) {
        int airVelX = 0;
        if (dx != 0) {
            int walkStep = BotPhysicsEngine.walkStep(bot.getMap());
            airVelX = dx >= 0 ? walkStep : -walkStep;
        }
        BotPhysicsEngine.beginGroundJump(entry, bot, airVelX);
        broadcastMovement(entry);
    }

    static void initiateRopeJump(BotEntry entry, Character bot, int dx) {
        int walkStep = BotPhysicsEngine.walkStep(bot.getMap());
        int airVelX = dx > 0 ? walkStep : dx < 0 ? -walkStep : 0;
        BotPhysicsEngine.beginClimbUpJump(entry, bot, airVelX);
        broadcastMovement(entry);
    }

    static void broadcastMovement(BotEntry entry) {
        Character bot = entry.bot;
        int x = bot.getPosition().x;
        int y = bot.getPosition().y;
        BotPhysicsEngine.MovementSnapshot snapshot = BotPhysicsEngine.movementSnapshot(entry);

        if (entry.movementBroadcastValid
                && entry.lastBroadcastX == x
                && entry.lastBroadcastY == y
                && entry.lastBroadcastVelX == snapshot.velX()
                && entry.lastBroadcastVelY == snapshot.velY()
                && entry.lastBroadcastStance == snapshot.stance()) {
            return;
        }

        entry.movementBroadcastValid = true;
        entry.lastBroadcastX = x;
        entry.lastBroadcastY = y;
        entry.lastBroadcastVelX = snapshot.velX();
        entry.lastBroadcastVelY = snapshot.velY();
        entry.lastBroadcastStance = snapshot.stance();
        sendMovementPacket(bot, snapshot);
    }

    private static void sendMovementPacket(Character bot, BotPhysicsEngine.MovementSnapshot snapshot) {
        byte[] data = new byte[15];
        data[0] = 1;
        int x = bot.getPosition().x;
        int y = bot.getPosition().y;
        data[2] = (byte) (x & 0xFF);
        data[3] = (byte) (x >> 8);
        data[4] = (byte) (y & 0xFF);
        data[5] = (byte) (y >> 8);
        data[6] = (byte) (snapshot.velX() & 0xFF);
        data[7] = (byte) (snapshot.velX() >> 8);
        data[8] = (byte) (snapshot.velY() & 0xFF);
        data[9] = (byte) (snapshot.velY() >> 8);
        data[12] = (byte) snapshot.stance();
        data[13] = (byte) (BotPhysicsEngine.cfg.TICK_MS & 0xFF);
        data[14] = (byte) (BotPhysicsEngine.cfg.TICK_MS >> 8);
        InPacket packet = new ByteBufInPacket(Unpooled.wrappedBuffer(data));
        Packet movePacket = PacketCreator.movePlayer(bot.getId(), packet, data.length);
        bot.getMap().broadcastMessage(bot, movePacket, false);
    }

    static Map<Integer, Foothold> buildFhIndex(MapleMap map) {
        Map<Integer, Foothold> index = new HashMap<>();
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            index.put(foothold.getId(), foothold);
        }
        return index;
    }

    private static JumpLanding wrapLanding(BotPhysicsEngine.JumpLanding landing) {
        if (landing == null) {
            return null;
        }
        return new JumpLanding(landing.point(), landing.foothold());
    }
}
