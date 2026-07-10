package server.agents.capabilities.movement;

import client.Character;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.monitoring.AgentPerformanceMonitor;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;

/**
 * Agent movement broadcast state. Cosmic packet construction and map broadcast
 * are delegated to the packet gateway boundary.
 */
public final class AgentMovementBroadcastService {
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
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
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
    // client uses it to pick the render z-layer. Without it, bots draw on the top layer
    // (in front of tiles/walls). While airborne, clients keep sending the last-known
    // ground fh, so cache it on the bot entry.
    private static int resolveBroadcastFhId(AgentRuntimeEntry entry, Character bot) {
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
}
