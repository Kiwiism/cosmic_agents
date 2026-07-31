package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.AbstractPacketHandler;
import net.packet.InPacket;
import server.agents.auth.AgentAuthorityService;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.navigation.AgentMapGraphService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.observer.protocol.ObserverNavGraphProtocol;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ObserverNavGraphHandler extends AbstractPacketHandler {
    private static final int REQUEST_BYTES = 6;
    private static final long MIN_REQUEST_INTERVAL_NANOS = 500_000_000L;
    private static final Map<Client, Long> LAST_REQUEST_NANOS = new WeakHashMap<>();

    @Override
    public void handlePacket(InPacket packet, Client client) {
        Character observer = client.getPlayer();
        if (observer == null
                || (client.getGMLevel() < 2 && !AgentAuthorityService.mayObserve(observer))
                || packet.available() < REQUEST_BYTES) {
            return;
        }

        int version = packet.readByte() & 0xFF;
        int action = packet.readByte() & 0xFF;
        int requestId = packet.readInt();
        if (version != ObserverNavGraphProtocol.VERSION
                || action != ObserverNavGraphProtocol.ACTION_SNAPSHOT
                || requestId <= 0
                || rateLimited(client)) {
            return;
        }

        MapleMap map = observer.getMap();
        if (map == null) {
            return;
        }

        AgentMovementProfile profile = AgentMovementProfile.fromCharacter(observer);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, profile);
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, profile);
            sendStatus(client, ObserverNavGraphProtocol.STATUS_WARMING,
                    requestId, map.getId(), 0, profile);
            return;
        }

        try {
            AgentMapGraphService.MapGraphView view = AgentMapGraphService.graphView(
                    map,
                    graph,
                    AgentNavigationGraphService.cachedMovementProfiles(map.getId()));
            byte[] payload = ObserverNavGraphProtocol.encode(view);
            List<byte[]> chunks = ObserverNavGraphProtocol.chunks(payload);
            int checksum = ObserverNavGraphProtocol.checksum(payload);
            for (int index = 0; index < chunks.size(); index++) {
                client.sendPacket(PacketCreator.observerNavGraphChunk(
                        ObserverNavGraphProtocol.STATUS_READY,
                        requestId,
                        view.mapId(),
                        view.version(),
                        profile.totalSpeedStat(),
                        profile.totalJumpStat(),
                        index,
                        chunks.size(),
                        payload.length,
                        checksum,
                        chunks.get(index)));
            }
        } catch (IllegalArgumentException ignored) {
            sendStatus(client, ObserverNavGraphProtocol.STATUS_TOO_LARGE,
                    requestId, map.getId(), graph.version, profile);
        }
    }

    private static boolean rateLimited(Client client) {
        long now = System.nanoTime();
        synchronized (LAST_REQUEST_NANOS) {
            long previous = LAST_REQUEST_NANOS.getOrDefault(client, 0L);
            if (now - previous < MIN_REQUEST_INTERVAL_NANOS) {
                return true;
            }
            LAST_REQUEST_NANOS.put(client, now);
            return false;
        }
    }

    private static void sendStatus(Client client,
                                   int status,
                                   int requestId,
                                   int mapId,
                                   int graphVersion,
                                   AgentMovementProfile profile) {
        client.sendPacket(PacketCreator.observerNavGraphChunk(
                status,
                requestId,
                mapId,
                graphVersion,
                profile.totalSpeedStat(),
                profile.totalJumpStat(),
                0,
                0,
                0,
                0,
                new byte[0]));
    }
}
