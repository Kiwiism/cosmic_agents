package server.agents.observer.adapter;

import client.Character;
import client.Client;
import net.packet.InPacket;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.navigation.AgentMapGraphService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.observer.ObserverInterestService;
import server.agents.observer.protocol.ObserverNavGraphProtocol;
import server.maps.MapleMap;
import server.observer.ObserverNavGraphAdapter;
import tools.PacketCreator;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class AgentObserverNavGraphAdapter implements ObserverNavGraphAdapter {
    private static final int REQUEST_BYTES = 6;
    private static final long MIN_REQUEST_INTERVAL_NANOS = 500_000_000L;
    private static final Map<Client, Long> LAST_REQUEST_NANOS = new WeakHashMap<>();

    @Override
    public void handle(InPacket packet, Client client) {
        Character observer = client.getPlayer();
        if (observer == null || packet.available() < REQUEST_BYTES) {
            return;
        }

        int version = packet.readByte() & 0xFF;
        int action = packet.readByte() & 0xFF;
        int requestId = packet.readInt();
        if (version != ObserverNavGraphProtocol.VERSION
                || (action != ObserverNavGraphProtocol.ACTION_SNAPSHOT
                    && action != ObserverNavGraphProtocol.ACTION_ROUTE)
                || requestId <= 0) {
            return;
        }
        if (action == ObserverNavGraphProtocol.ACTION_ROUTE && packet.available() < 8) {
            return;
        }
        int fromRegion = action == ObserverNavGraphProtocol.ACTION_ROUTE
                ? packet.readInt()
                : 0;
        int toRegion = action == ObserverNavGraphProtocol.ACTION_ROUTE
                ? packet.readInt()
                : 0;
        if (rateLimited(client)) {
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
            if (action == ObserverNavGraphProtocol.ACTION_ROUTE) {
                sendRoute(client, requestId, map, graph, profile, fromRegion, toRegion);
                return;
            }
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
            int status = action == ObserverNavGraphProtocol.ACTION_ROUTE
                    ? ObserverNavGraphProtocol.STATUS_INVALID_ROUTE
                    : ObserverNavGraphProtocol.STATUS_TOO_LARGE;
            sendStatus(client, status, requestId, map.getId(), graph.version, profile);
        }
    }

    private static void sendRoute(Client client,
                                  int requestId,
                                  MapleMap map,
                                  AgentNavigationGraph graph,
                                  AgentMovementProfile profile,
                                  int fromRegion,
                                  int toRegion) {
        AgentMapGraphService.RouteView route = AgentMapGraphService.testRoute(
                map, graph, fromRegion, toRegion, false);
        ObserverInterestService.publish(
                client.getPlayer(),
                ObserverInterestService.Type.ROUTE,
                20,
                routeDetail(route));
        byte[] payload = ObserverNavGraphProtocol.encodeRoute(route);
        List<byte[]> chunks = ObserverNavGraphProtocol.chunks(payload);
        int checksum = ObserverNavGraphProtocol.checksum(payload);
        for (int index = 0; index < chunks.size(); index++) {
            client.sendPacket(PacketCreator.observerNavGraphChunk(
                    ObserverNavGraphProtocol.STATUS_ROUTE,
                    requestId,
                    map.getId(),
                    graph.version,
                    profile.totalSpeedStat(),
                    profile.totalJumpStat(),
                    index,
                    chunks.size(),
                    payload.length,
                    checksum,
                    chunks.get(index)));
        }
    }

    private static String routeDetail(AgentMapGraphService.RouteView route) {
        String result = route.reached()
                ? "reached"
                : route.bestEffort() ? "best effort" : "unreached";
        return "Route " + route.fromRegion() + " -> " + route.toRegion()
                + ": " + result
                + ", " + route.path().size() + " step(s), "
                + route.expandedNodes() + " expanded";
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
