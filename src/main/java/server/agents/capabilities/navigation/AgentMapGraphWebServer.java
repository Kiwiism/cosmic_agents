package server.agents.capabilities.navigation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.server.Server;
import net.server.channel.Channel;
import net.server.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.combat.AgentCombatPolicyDiagnostics;
import server.agents.capabilities.combat.AgentCombatTargetTraceRuntime;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.field.AgentFieldRuntime;
import server.agents.field.AgentFieldLadderRuntime;
import server.agents.field.AgentFieldObservationRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapleMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AgentMapGraphWebServer {
    public static final String ENABLED_ENV = "COSMIC_AGENT_MAPGRAPH_ENABLED";
    public static final String PORT_ENV = "COSMIC_AGENT_MAPGRAPH_PORT";
    public static final int DEFAULT_PORT = 8790;

    private static final Logger log = LoggerFactory.getLogger(AgentMapGraphWebServer.class);
    private static final String PAGE_RESOURCE = "/web/agent-mapgraph.html";

    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final ExecutorService executor;

    public AgentMapGraphWebServer() throws IOException {
        this(intEnv(PORT_ENV, DEFAULT_PORT));
    }

    AgentMapGraphWebServer(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "agent-mapgraph-web");
            thread.setDaemon(true);
            return thread;
        });
        this.server.setExecutor(executor);
        this.server.createContext("/mapgraph", this::servePage);
        this.server.createContext("/api/mapgraph", this::serveMapGraph);
        this.server.createContext("/api/pathfind", this::servePathfind);
        this.server.createContext("/api/agentfield", this::serveAgentField);
        this.server.createContext("/api/agenttrace", this::serveAgentTrace);
        this.server.createContext("/api/health", this::serveHealth);
    }

    public void start() {
        server.start();
        log.info("Agent map-graph viewer listening on http://127.0.0.1:{}/mapgraph?id=103000000", boundPort());
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    int boundPort() {
        return server.getAddress().getPort();
    }

    public static boolean enabled() {
        String value = System.getenv(ENABLED_ENV);
        if (value == null || value.isBlank()) {
            return false;
        }
        return !("false".equalsIgnoreCase(value)
                || "0".equals(value)
                || "no".equalsIgnoreCase(value));
    }

    private void servePage(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        try (InputStream input = AgentMapGraphWebServer.class.getResourceAsStream(PAGE_RESOURCE)) {
            if (input == null) {
                sendJson(exchange, 500, Map.of("error", "Map-graph page resource is missing"));
                return;
            }
            send(exchange, 200, "text/html; charset=utf-8", input.readAllBytes());
        }
    }

    private void serveHealth(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        sendJson(exchange, 200, Map.of(
                "status", "UP",
                "service", "agent-mapgraph"));
    }

    private void serveMapGraph(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        try {
            Map<String, String> query = query(exchange);
            int mapId = requiredInt(query, "id");
            AgentMovementProfile profile = profile(query);
            MapleMap map = loadMap(mapId);
            if (map == null) {
                sendJson(exchange, 404, Map.of("error", "Map " + mapId + " was not found"));
                return;
            }

            AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, profile);
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(map, profile);
                sendJson(exchange, 202, Map.of(
                        "status", "warming",
                        "mapId", mapId,
                        "speed", profile.totalSpeedStat(),
                        "jump", profile.totalJumpStat()));
                return;
            }

            sendJson(exchange, 200, AgentMapGraphService.graphView(
                    map,
                    graph,
                    AgentNavigationGraphService.cachedMovementProfiles(mapId)));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Agent map-graph request failed", exception);
            sendJson(exchange, 500, Map.of("error", "Map-graph request failed"));
        }
    }

    private void servePathfind(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        try {
            Map<String, String> query = query(exchange);
            int mapId = requiredInt(query, "id");
            int fromRegion = requiredInt(query, "from");
            int toRegion = requiredInt(query, "to");
            boolean exhaustive = "exhaustive".equalsIgnoreCase(query.getOrDefault("mode", "normal"));
            AgentMovementProfile profile = profile(query);
            MapleMap map = loadMap(mapId);
            if (map == null) {
                sendJson(exchange, 404, Map.of("error", "Map " + mapId + " was not found"));
                return;
            }

            AgentNavigationGraph graph = AgentNavigationGraphService.peekGraph(map, profile);
            if (graph == null) {
                AgentNavigationGraphService.warmGraphAsync(map, profile);
                sendJson(exchange, 202, Map.of("status", "warming", "mapId", mapId));
                return;
            }

            sendJson(exchange, 200, AgentMapGraphService.testRoute(
                    map,
                    graph,
                    fromRegion,
                    toRegion,
                    exhaustive));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Agent map-graph pathfind request failed", exception);
            sendJson(exchange, 500, Map.of("error", "Pathfind request failed"));
        }
    }

    private void serveAgentField(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        try {
            int mapId = requiredInt(query(exchange), "id");
            Object ladder = AgentFieldLadderRuntime.reportForMapId(mapId);
            if (ladder == null) {
                ladder = Map.of();
            }
            Object observation = AgentFieldObservationRuntime.statusForMapId(mapId);
            if (observation == null) {
                observation = Map.of();
            }
            sendJson(exchange, 200, Map.of(
                    "mapId", mapId,
                    "sessions", AgentFieldRuntime.snapshotsForMapId(
                            mapId, System.currentTimeMillis()),
                    "ladder", ladder,
                    "observation", observation));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Agent field diagnostics request failed", exception);
            sendJson(exchange, 500, Map.of("error", "Agent field diagnostics request failed"));
        }
    }

    private void serveAgentTrace(HttpExchange exchange) throws IOException {
        if (!requireGet(exchange)) {
            return;
        }
        try {
            int characterId = requiredInt(query(exchange), "id");
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
            if (entry == null) {
                sendJson(exchange, 404, Map.of(
                        "error", "Active Agent " + characterId + " was not found"));
                return;
            }
            long nowMs = System.currentTimeMillis();
            sendJson(exchange, 200, Map.of(
                    "characterId", characterId,
                    "sampledAtMs", nowMs,
                    "navigation", AgentNavigationTraceRuntime.snapshot(entry, nowMs),
                    "edgeReliability", AgentNavigationEdgeReliabilityRuntime.snapshot(entry, nowMs),
                    "combatTarget", AgentCombatTargetTraceRuntime.snapshot(entry, nowMs),
                    "combatPolicy", AgentCombatPolicyDiagnostics.snapshot(entry, nowMs)));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Agent trace diagnostics request failed", exception);
            sendJson(exchange, 500, Map.of("error", "Agent trace diagnostics request failed"));
        }
    }

    private MapleMap loadMap(int mapId) {
        for (World world : Server.getInstance().getWorlds()) {
            for (Channel channel : world.getChannels()) {
                try {
                    MapleMap map = channel.getMapFactory().getMap(mapId);
                    if (map != null) {
                        return map;
                    }
                } catch (RuntimeException ignored) {
                    // Try the same map in another channel before reporting it missing.
                }
            }
        }
        return null;
    }

    private boolean requireGet(HttpExchange exchange) throws IOException {
        if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", "GET");
        sendJson(exchange, 405, Map.of("error", "GET required"));
        return false;
    }

    private AgentMovementProfile profile(Map<String, String> query) {
        AgentMovementProfile baseProfile = AgentMovementProfile.base();
        int speed = optionalInt(query, "speed", baseProfile.totalSpeedStat());
        int jump = optionalInt(query, "jump", baseProfile.totalJumpStat());
        return new AgentMovementProfile(speed, jump);
    }

    private Map<String, String> query(HttpExchange exchange) {
        Map<String, String> values = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isBlank()) {
            return values;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length == 2
                    ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                    : "";
            values.put(key, value);
        }
        return values;
    }

    private int requiredInt(Map<String, String> query, String name) {
        String value = query.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing numeric query parameter: " + name);
        }
        return parseInt(name, value);
    }

    private int optionalInt(Map<String, String> query, String name, int fallback) {
        String value = query.get(name);
        return value == null || value.isBlank() ? fallback : parseInt(name, value);
    }

    private int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric query parameter: " + name);
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        send(exchange, status, "application/json; charset=utf-8", json.writeValueAsBytes(body));
    }

    private void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if ("http://localhost:8000".equals(origin)
                || "http://127.0.0.1:8000".equals(origin)) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
        }
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
