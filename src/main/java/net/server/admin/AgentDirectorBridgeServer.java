package net.server.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import server.agents.integration.cosmic.CosmicAgentWorldDirectorApplicationFactory;
import server.agents.integration.cosmic.CosmicAgentCleanSlateResetFactory;
import server.agents.administration.AgentCleanSlateResetService;
import server.agents.integration.ollama.DirectorLlmSettings;
import server.agents.integration.ollama.OllamaDirectorProposalProvider;
import server.agents.presentation.director.AgentDirectorApiView;
import server.agents.presentation.director.AgentCleanSlateApiView;
import server.agents.runtime.activity.control.AgentWorldDirectorApplication;
import server.agents.runtime.activity.control.chat.AgentDirectorChatService;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposalService;
import server.agents.runtime.activity.control.proposal.AgentFileDirectorProposalStore;
import server.agents.runtime.activity.control.proposal.AgentDirectorProposalSource;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.social.persistence.SocialMemoryDatabaseRuntime;
import server.agents.social.persistence.SocialPostgresDataSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Authenticated loopback control plane for the local Director panel. */
public final class AgentDirectorBridgeServer {
    public static final String ENABLED_ENV = "COSMIC_DIRECTOR_PANEL_ENABLED";
    private static final int DEFAULT_PORT = 8790;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpServer server;
    private final String token;
    private final AgentWorldDirectorApplication application;
    private final AgentDirectorProposalService proposals;
    private final AgentDirectorChatService chat;
    private final AgentCleanSlateResetService resets;
    private final DirectorLlmSettings llmSettings;
    private final OllamaDirectorProposalProvider llm;

    public AgentDirectorBridgeServer() throws IOException {
        int port = intEnv("COSMIC_DIRECTOR_PORT", DEFAULT_PORT);
        this.token = DatabaseConsoleBridgeSecurity.requireStrongToken(
                System.getenv("COSMIC_DIRECTOR_TOKEN"), "COSMIC_DIRECTOR_TOKEN");
        this.application = CosmicAgentWorldDirectorApplicationFactory.create();
        this.proposals = new AgentDirectorProposalService(
                AgentFileDirectorProposalStore.runtimeDefault());
        this.llmSettings = DirectorLlmSettings.runtime();
        this.llm = new OllamaDirectorProposalProvider(llmSettings);
        this.chat = new AgentDirectorChatService(llm, proposals);
        this.resets = CosmicAgentCleanSlateResetFactory.create();
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.setExecutor(new ThreadPoolExecutor(2, 4, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(64), r -> {
            Thread thread = new Thread(r, "agent-director-bridge");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy()));
        this.server.createContext("/internal/director", this::handle);
    }

    public static boolean enabled() {
        String value = System.getenv(ENABLED_ENV);
        return value != null && !(value.isBlank() || "false".equalsIgnoreCase(value)
                || "0".equals(value) || "no".equalsIgnoreCase(value));
    }

    public void start() {
        server.start();
        llm.prewarmAsync();
    }
    public void stop() { server.stop(0); }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!authorized(exchange)) {
                send(exchange, 401, error("UNAUTHORIZED", "A valid Director token is required"));
                return;
            }
            route(exchange);
        } catch (AgentDirectorProposalService.StaleProposalException stale) {
            send(exchange, 409, Map.of("error", error("CONTEXT_STALE", stale.getMessage()),
                    "proposal", stale.proposal()));
        } catch (IllegalArgumentException failure) {
            send(exchange, 400, error("BAD_REQUEST", failure.getMessage()));
        } catch (IllegalStateException failure) {
            send(exchange, 409, error(classify(failure.getMessage()), failure.getMessage()));
        } catch (Exception failure) {
            send(exchange, 500, error("INTERNAL_ERROR", failure.getClass().getSimpleName()));
        }
    }

    private void route(HttpExchange exchange) throws Exception {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        long nowMs = System.currentTimeMillis();
        if ("GET".equals(method) && "/internal/director/health".equals(path)) {
            var llmHealth = llm.health();
            send(exchange, 200, Map.of(
                    "status", "UP", "checkedAt", Instant.ofEpochMilli(nowMs).toString(),
                    "ollama", Map.of("enabled", llmSettings.enabled(),
                            "ready", llmHealth.ready(),
                            "reachable", llmHealth.reachable(),
                            "modelAvailable", llmHealth.modelAvailable(),
                            "model", llmHealth.model(), "status", llmHealth.status()),
                    "socialDatabase", Map.of("enabled", SocialPostgresDataSource.enabled(),
                            "available", !SocialPostgresDataSource.enabled()
                                    || SocialMemoryDatabaseRuntime.store().isPresent())));
            return;
        }
        if ("GET".equals(method) && "/internal/director/agents".equals(path)) {
            send(exchange, 200, Map.of("schemaVersion", 1, "generatedAtMs", nowMs,
                    "agents", application.agents().stream()
                            .map(AgentDirectorApiView::roster).toList()));
            return;
        }
        if (!path.matches("/internal/director/agents/\\d+(/.*)?")) {
            send(exchange, 404, error("NOT_FOUND", "Unknown Director endpoint"));
            return;
        }
        String suffix = path.substring("/internal/director/agents/".length());
        int slash = suffix.indexOf('/');
        int agentId = Integer.parseInt(slash < 0 ? suffix : suffix.substring(0, slash));
        String operation = slash < 0 ? "" : suffix.substring(slash);

        if ("GET".equals(method) && operation.isEmpty()) {
            var view = application.view(agentId, 30, nowMs);
            send(exchange, 200, AgentDirectorApiView.from(
                    view, proposals.list(agentId, nowMs), nowMs));
            return;
        }
        if ("POST".equals(method) && "/spawn".equals(operation)) {
            JsonNode body = readBody(exchange);
            send(exchange, 200, application.spawnIdle(agentId,
                    requiredInt(body, "world"), requiredInt(body, "channel"), nowMs));
            return;
        }
        if ("POST".equals(method) && "/reset/preview".equals(operation)) {
            JsonNode body = readBody(exchange);
            send(exchange, 200, AgentCleanSlateApiView.preview(resets.preview(
                    agentId, "local-director-operator", requiredText(body, "reason"), nowMs)));
            return;
        }
        if ("POST".equals(method) && "/reset/execute".equals(operation)) {
            JsonNode body = readBody(exchange);
            send(exchange, 200, AgentCleanSlateApiView.result(resets.execute(
                    agentId, requiredText(body, "resetId"),
                    requiredText(body, "confirmationToken"),
                    requiredText(body, "confirmationPhrase"), nowMs)));
            return;
        }
        if ("PUT".equals(method) && "/mode".equals(operation)) {
            JsonNode body = readBody(exchange);
            AgentWorldDirectorMode mode = AgentWorldDirectorMode.valueOf(
                    requiredText(body, "mode").toUpperCase());
            if (mode == AgentWorldDirectorMode.SHADOW || mode == AgentWorldDirectorMode.CONTROLLED) {
                throw new IllegalArgumentException("legacy Director modes are not exposed by v1");
            }
            send(exchange, 200, application.setMode(
                    agentId, mode, optionalText(body, "reason"), nowMs));
            return;
        }
        if ("POST".equals(method) && "/actions".equals(operation)) {
            JsonNode body = readBody(exchange);
            send(exchange, 200, application.execute(agentId,
                    requiredText(body, "actionId"), requiredText(body, "contextRevision"),
                    optionalText(body, "idempotencyKey").isEmpty()
                            ? UUID.randomUUID().toString()
                            : optionalText(body, "idempotencyKey"),
                    optionalText(body, "reason"), body.path("confirmDestructive").asBoolean(false),
                    nowMs));
            return;
        }
        if ("POST".equals(method) && "/proposals/policy".equals(operation)) {
            send(exchange, 200, proposals.proposeRecommended(
                    application.view(agentId, 12, nowMs),
                    AgentDirectorProposalSource.POLICY, nowMs));
            return;
        }
        if ("POST".equals(method) && "/proposals".equals(operation)) {
            JsonNode body = readBody(exchange);
            var view = application.view(agentId, 12, nowMs);
            send(exchange, 200, proposals.propose(
                    view, requiredText(body, "actionId"),
                    AgentDirectorProposalSource.OPERATOR,
                    optionalText(body, "rationale"),
                    body.path("expectedEnergyDelta").asInt(0), nowMs));
            return;
        }
        if ("POST".equals(method) && "/chat".equals(operation)) {
            JsonNode body = readBody(exchange);
            send(exchange, 200, chat.respond(application.view(agentId, 12, nowMs),
                    requiredText(body, "message"), nowMs));
            return;
        }
        if ("POST".equals(method) && operation.matches("/proposals/[^/]+/approve")) {
            JsonNode body = readBody(exchange);
            String proposalId = segment(operation, "/proposals/", "/approve");
            send(exchange, 200, proposals.approve(application, agentId, proposalId,
                    body.path("confirmDestructive").asBoolean(false), nowMs));
            return;
        }
        if ("POST".equals(method) && operation.matches("/proposals/[^/]+/reject")) {
            JsonNode body = readBody(exchange);
            String proposalId = segment(operation, "/proposals/", "/reject");
            send(exchange, 200, proposals.reject(agentId, proposalId,
                    optionalText(body, "reason"), nowMs));
            return;
        }
        if ("POST".equals(method) && operation.matches("/directives/[^/]+/cancel")) {
            JsonNode body = readBody(exchange);
            send(exchange, 200, application.cancel(agentId,
                    segment(operation, "/directives/", "/cancel"),
                    optionalText(body, "reason"), nowMs));
            return;
        }
        if ("POST".equals(method) && operation.matches("/outcomes/[^/]+/acknowledge")) {
            JsonNode body = readBody(exchange);
            send(exchange, 200, application.acknowledgeOutcome(agentId,
                    segment(operation, "/outcomes/", "/acknowledge"),
                    optionalText(body, "reason"), nowMs));
            return;
        }
        send(exchange, 404, error("NOT_FOUND", "Unknown Director endpoint"));
    }

    private boolean authorized(HttpExchange exchange) {
        List<String> values = exchange.getRequestHeaders()
                .getOrDefault("Authorization", new ArrayList<>());
        return values.stream().anyMatch(value ->
                DatabaseConsoleBridgeSecurity.matchesBearer(value, token));
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new IllegalArgumentException("Content-Type must be application/json");
        }
        return json.readTree(DatabaseConsoleBridgeSecurity.readBounded(exchange.getRequestBody()));
    }

    private static int requiredInt(JsonNode body, String field) {
        if (!body.has(field) || !body.get(field).canConvertToInt()) {
            throw new IllegalArgumentException("Missing numeric field: " + field);
        }
        return body.get(field).asInt();
    }

    private static String requiredText(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value.isEmpty()) throw new IllegalArgumentException("Missing text field: " + field);
        return value;
    }

    private static String optionalText(JsonNode body, String field) {
        return body == null ? "" : body.path(field).asText("").trim();
    }

    private static String segment(String path, String prefix, String suffix) {
        return path.substring(prefix.length(), path.length() - suffix.length());
    }

    private static Map<String, Object> error(String code, String message) {
        return Map.of("code", code, "message", message == null ? "" : message);
    }

    private static String classify(String message) {
        String value = message == null ? "" : message.toLowerCase();
        if (value.contains("offline")) return "AGENT_OFFLINE";
        if (value.contains("confirmation")) return "CONFIRMATION_REQUIRED";
        if (value.contains("context changed")) return "CONTEXT_STALE";
        if (value.contains("not available") || value.contains("not executable")) {
            return "ACTION_UNAVAILABLE";
        }
        return "CONFLICT";
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] payload = json.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream response = exchange.getResponseBody()) { response.write(payload); }
    }

    private static int intEnv(String name, int fallback) {
        try { return Integer.parseInt(System.getenv().getOrDefault(name, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
