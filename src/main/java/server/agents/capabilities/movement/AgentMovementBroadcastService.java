package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.Point;

/**
 * Agent movement broadcast state. Cosmic packet construction and map broadcast
 * are delegated to the packet gateway boundary.
 */
public final class AgentMovementBroadcastService {
    /** Sentinel used for rope climbs to select the client-side rope render layer. */
    static final int ROPE_CLIMB_FOOTHOLD_ID = -2;
    /** Sentinel emitted between leaving a climbable and landing on real ground. */
    static final int AIRBORNE_FOOTHOLD_ID = 0;

    private AgentMovementBroadcastService() {
    }

    public static void broadcastMovement(AgentRuntimeEntry entry) {
        if (!AgentPerformanceMonitor.enabled()) {
            doBroadcastMovement(entry);
            return;
        }

        long startedAt = System.nanoTime();
        try {
            doBroadcastMovement(entry);
        } finally {
            AgentPerformanceMonitor.record("broadcast-move", System.nanoTime() - startedAt);
        }
    }

    private static void doBroadcastMovement(AgentRuntimeEntry entry) {
        AgentMovementBroadcastStateRuntime.markReconciled(entry);
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot.getMap() == null || !bot.getMap().isObservedByPlayer()) {
            AgentMovementBroadcastStateRuntime.invalidate(entry);
            return;
        }
        int x = bot.getPosition().x;
        int y = bot.getPosition().y;
        AgentMovementPacketSnapshot snapshot = AgentMovementSnapshotService.currentSnapshot(entry);
        int fhId = resolveBroadcastFhId(entry, bot);

        if (AgentMovementBroadcastStateRuntime.matches(
                entry, x, y, snapshot.velX(), snapshot.velY(), snapshot.stance(), fhId)) {
            return;
        }

        AgentMovementBroadcastStateRuntime.record(
                entry, x, y, snapshot.velX(), snapshot.velY(), snapshot.stance(), fhId);
        sendMovementPacket(bot, snapshot, fhId);
    }

    // Real clients report the foothold ID they're standing on in every move packet; the
    // client uses it to pick the render z-layer. Cache the last real ground foothold for
    // ordinary movement, but use the captured rope sentinel while attached to a rope.
    static int resolveBroadcastFhId(AgentRuntimeEntry entry, Character bot) {
        // Native capture confirms every sustained rope fragment carries fh=-2. Sending the
        // ground foothold here makes remote clients retain the ground render layer, which is
        // why synthetic characters appeared behind rope artwork.
        Rope climbable = AgentClimbStateRuntime.climbRope(entry);
        if (AgentClimbStateRuntime.climbing(entry) && climbable != null && !climbable.isLadder()) {
            return ROPE_CLIMB_FOOTHOLD_ID;
        }
        // Native rope-jump capture switches from fh=-2 to fh=0 until the landing
        // fragment supplies the destination foothold. Resolving a platform below an
        // airborne Agent preserves the wrong render layer during detachment.
        if (AgentMovementStateRuntime.inAir(entry)) {
            return AIRBORNE_FOOTHOLD_ID;
        }
        Foothold fh = AgentGroundingService.findGroundFoothold(bot.getMap(), bot.getPosition());
        if (fh != null) {
            AgentMovementPhysicsStateRuntime.setLastGroundFhId(entry, fh.getId());
        }
        return AgentMovementPhysicsStateRuntime.lastGroundFhId(entry);
    }

    private static void sendMovementPacket(Character bot, AgentMovementPacketSnapshot snapshot, int fhId) {
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
        data[10] = (byte) (fhId & 0xFF);
        data[11] = (byte) (fhId >> 8);
        data[12] = (byte) snapshot.stance();
        int movementTickMs = AgentMovementPhysicsConfig.configuredMovementTickMs();
        data[13] = (byte) (movementTickMs & 0xFF);
        data[14] = (byte) (movementTickMs >> 8);
        AgentPacketGatewayRuntime.packets().broadcastMovePlayer(bot, data);
    }

    /**
     * Broadcasts the client Flash Jump action without letting the relative fragment inherit
     * an undefined position. The v83 client requires command 6 to follow an absolute fragment
     * in the same movement path.
     */
    public static void broadcastFlashJump(AgentRuntimeEntry entry, int relativeX, int relativeY) {
        AgentMovementBroadcastStateRuntime.markReconciled(entry);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentMovementPacketSnapshot snapshot = AgentMovementSnapshotService.currentSnapshot(entry);
        int footholdId = resolveBroadcastFhId(entry, agent);
        Point position = agent.getPosition();
        byte[] data = buildFlashJumpMovementData(
                position,
                snapshot,
                footholdId,
                relativeX,
                relativeY,
                AgentMovementPhysicsConfig.configuredMovementTickMs());

        AgentPacketGatewayRuntime.packets().broadcastMovePlayer(agent, data);
        AgentMovementBroadcastStateRuntime.record(
                entry,
                position.x,
                position.y,
                snapshot.velX(),
                snapshot.velY(),
                snapshot.stance(),
                footholdId);
    }

    /**
     * Broadcasts a client-true Teleport blink: origin, destination, then an absolute
     * landing fragment. Teleport fragments use the client decode layout
     * {@code x, y, foothold, stance, elapsed} with zero elapsed time.
     */
    public static void broadcastTeleport(AgentRuntimeEntry entry, Point origin, Point destination) {
        AgentMovementBroadcastStateRuntime.markReconciled(entry);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentMovementPacketSnapshot snapshot = AgentMovementSnapshotService.currentSnapshot(entry);
        int destinationFootholdId = resolveBroadcastFhId(entry, agent);
        Foothold originFoothold = AgentGroundingService.findGroundFoothold(agent.getMap(), origin);
        int originFootholdId = originFoothold != null ? originFoothold.getId() : destinationFootholdId;
        byte[] data = buildTeleportMovementData(
                origin,
                destination,
                snapshot,
                originFootholdId,
                destinationFootholdId,
                AgentMovementPhysicsConfig.configuredMovementTickMs());

        AgentPacketGatewayRuntime.packets().broadcastMovePlayer(agent, data);
        AgentMovementBroadcastStateRuntime.record(
                entry,
                destination.x,
                destination.y,
                snapshot.velX(),
                snapshot.velY(),
                snapshot.stance(),
                destinationFootholdId);
    }

    static byte[] buildFlashJumpMovementData(Point position,
                                             AgentMovementPacketSnapshot snapshot,
                                             int footholdId,
                                             int relativeX,
                                             int relativeY,
                                             int movementTickMs) {
        byte[] data = new byte[23];
        int offset = 0;
        data[offset++] = 2;
        offset = putAbsoluteFragment(data, offset, position.x, position.y,
                snapshot.velX(), snapshot.velY(), footholdId, snapshot.stance(), movementTickMs);
        data[offset++] = 6;
        offset = putShort(data, offset, relativeX);
        offset = putShort(data, offset, relativeY);
        data[offset++] = (byte) snapshot.stance();
        putShort(data, offset, 0);
        return data;
    }

    static byte[] buildTeleportMovementData(Point origin,
                                            Point destination,
                                            AgentMovementPacketSnapshot snapshot,
                                            int originFootholdId,
                                            int destinationFootholdId,
                                            int movementTickMs) {
        byte[] data = new byte[35];
        int offset = 0;
        data[offset++] = 3;
        offset = putTeleportFragment(data, offset, 4, origin.x, origin.y,
                originFootholdId, snapshot.stance());
        offset = putTeleportFragment(data, offset, 3, destination.x, destination.y,
                0, snapshot.stance());
        putAbsoluteFragment(data, offset, destination.x, destination.y,
                snapshot.velX(), snapshot.velY(), destinationFootholdId,
                snapshot.stance(), movementTickMs);
        return data;
    }

    private static int putTeleportFragment(byte[] data,
                                           int offset,
                                           int command,
                                           int x,
                                           int y,
                                           int footholdId,
                                           int stance) {
        data[offset++] = (byte) command;
        offset = putShort(data, offset, x);
        offset = putShort(data, offset, y);
        offset = putShort(data, offset, footholdId);
        data[offset++] = (byte) stance;
        return putShort(data, offset, 0);
    }

    private static int putAbsoluteFragment(byte[] data,
                                           int offset,
                                           int x,
                                           int y,
                                           int velocityX,
                                           int velocityY,
                                           int footholdId,
                                           int stance,
                                           int movementTickMs) {
        data[offset++] = 0;
        offset = putShort(data, offset, x);
        offset = putShort(data, offset, y);
        offset = putShort(data, offset, velocityX);
        offset = putShort(data, offset, velocityY);
        offset = putShort(data, offset, footholdId);
        data[offset++] = (byte) stance;
        return putShort(data, offset, movementTickMs);
    }

    private static int putShort(byte[] data, int offset, int value) {
        data[offset++] = (byte) (value & 0xFF);
        data[offset++] = (byte) (value >> 8);
        return offset;
    }
}
