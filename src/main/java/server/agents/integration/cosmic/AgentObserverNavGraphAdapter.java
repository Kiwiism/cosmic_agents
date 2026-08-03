package server.agents.integration.cosmic;

import client.Character;
import client.Client;
import net.packet.InPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.navigation.AgentMapGraphService;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.capabilities.navigation.AgentNavigationTraceRuntime;
import server.agents.capabilities.navigation.AgentNavigationTraceSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.observer.ObserverInterestService;
import server.agents.observer.protocol.ObserverNavGraphProtocol;
import server.maps.MapleMap;
import server.observer.ObserverNavGraphAdapter;
import server.observer.ObserverFeature;
import tools.PacketCreator;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class AgentObserverNavGraphAdapter implements ObserverNavGraphAdapter {
    private static final Logger log = LoggerFactory.getLogger(AgentObserverNavGraphAdapter.class);
    private static final int REQUEST_BYTES = 6;
    private static final long MIN_REQUEST_INTERVAL_NANOS = config.AgentTuning.longValue(
            "server.agents.integration.cosmic.AgentObserverNavGraphAdapter.MIN_REQUEST_INTERVAL_NANOS");
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
                    && action != ObserverNavGraphProtocol.ACTION_ROUTE
                    && action != ObserverNavGraphProtocol.ACTION_AGENT_TRACE)
                || requestId <= 0) {
            return;
        }
        if (action == ObserverNavGraphProtocol.ACTION_AGENT_TRACE) {
            if (packet.available() < 12) {
                return;
            }
            int characterId = packet.readInt();
            long knownRevision = packet.readLong();
            if (rateLimited(client)) {
                return;
            }
            sendAgentTrace(client, requestId, characterId, knownRevision);
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
        int requestedSpeed = packet.available() >= 4
                ? packet.readShort() & 0xFFFF
                : 0;
        int requestedJump = packet.available() >= 2
                ? packet.readShort() & 0xFFFF
                : 0;
        if (rateLimited(client)) {
            log.info("[observer] navgraph request rate-limited observer={} requestId={}",
                    observer.getName(), requestId);
            return;
        }

        MapleMap map = observer.getMap();
        if (map == null) {
            return;
        }

        AgentMovementProfile profile = requestedSpeed > 0 && requestedJump > 0
                ? new AgentMovementProfile(requestedSpeed, requestedJump)
                : AgentMovementProfile.fromCharacter(observer);
        AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, profile);
        if (graph == null) {
            AgentNavigationGraphService.warmGraphAsync(map, profile);
            log.info("[observer] navgraph warming observer={} requestId={} mapId={} speed={} jump={}",
                    observer.getName(), requestId, map.getId(),
                    profile.totalSpeedStat(), profile.totalJumpStat());
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
            log.info("[observer] navgraph ready observer={} requestId={} mapId={} regions={} edges={} bytes={} chunks={}",
                    observer.getName(), requestId, view.mapId(), view.regions().size(),
                    view.edges().size(), payload.length, chunks.size());
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

    private static void sendAgentTrace(Client client,
                                       int requestId,
                                       int characterId,
                                       long knownRevision) {
        Character observer = client.getPlayer();
        AgentMovementProfile fallbackProfile = AgentMovementProfile.fromCharacter(observer);
        if (!ObserverFeature.agentNavigationEnabled()) {
            sendStatus(client, ObserverNavGraphProtocol.STATUS_AGENT_DISABLED,
                    requestId, observer.getMapId(), 0, fallbackProfile);
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        AgentNavigationTraceSnapshot trace = AgentNavigationTraceRuntime.snapshot(
                entry, System.currentTimeMillis());
        if (trace == null
                || entry == null
                || !AgentRuntimeIdentityRuntime.hasBot(entry)
                || AgentRuntimeIdentityRuntime.bot(entry).getWorld() != observer.getWorld()) {
            sendStatus(client, ObserverNavGraphProtocol.STATUS_AGENT_UNAVAILABLE,
                    requestId, observer.getMapId(), 0, fallbackProfile);
            return;
        }

        boolean includePath = knownRevision != trace.routeRevision();
        byte[] payload = ObserverNavGraphProtocol.encodeAgentTrace(trace, includePath);
        List<byte[]> chunks = ObserverNavGraphProtocol.chunks(payload);
        int checksum = ObserverNavGraphProtocol.checksum(payload);
        for (int index = 0; index < chunks.size(); index++) {
            client.sendPacket(PacketCreator.observerNavGraphChunk(
                    ObserverNavGraphProtocol.STATUS_AGENT_TRACE,
                    requestId,
                    trace.mapId(),
                    trace.graphVersion(),
                    trace.speed(),
                    trace.jump(),
                    index,
                    chunks.size(),
                    payload.length,
                    checksum,
                    chunks.get(index)));
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
