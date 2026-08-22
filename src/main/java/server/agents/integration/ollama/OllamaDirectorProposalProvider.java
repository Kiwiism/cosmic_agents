package server.agents.integration.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.chat.AgentDirectorModelSelection;
import server.agents.runtime.activity.control.chat.AgentDirectorModelAdvice;
import server.agents.runtime.activity.control.chat.AgentDirectorDomainContext;
import server.agents.runtime.activity.control.chat.AgentDirectorRankedSelection;
import server.agents.runtime.activity.control.chat.AgentDirectorProposalProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Proposal-only Ollama adapter. Model output is constrained to current action IDs. */
public final class OllamaDirectorProposalProvider implements AgentDirectorProposalProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DirectorLlmSettings settings;
    private final HttpClient http;
    private final AtomicBoolean prewarmStarted = new AtomicBoolean();

    public OllamaDirectorProposalProvider(DirectorLlmSettings settings) {
        this(settings, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }

    OllamaDirectorProposalProvider(DirectorLlmSettings settings, HttpClient http) {
        if (settings == null || http == null) throw new IllegalArgumentException("settings are required");
        this.settings = settings;
        this.http = http;
    }

    @Override
    public Optional<AgentDirectorModelSelection> select(
            AgentDirectorExecutiveView view, String operatorPrompt) {
        if (!settings.enabled() || view == null || operatorPrompt == null
                || operatorPrompt.isBlank()) return Optional.empty();
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(settings.endpoint() + "/api/generate"))
                    .timeout(Duration.ofMillis(settings.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JSON.writeValueAsString(body(view, operatorPrompt))))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            String generated = JSON.readTree(response.body()).path("response").asText("");
            JsonNode selection = JSON.readTree(generated);
            String actionId = selection.path("actionId").asText("").trim();
            boolean allowed = view.actions().stream().anyMatch(action ->
                    action.actionId().equals(actionId) && action.availability().executable());
            if (!allowed) return Optional.empty();
            String rationale = selection.path("rationale").asText("").trim();
            if (rationale.isEmpty()) return Optional.empty();
            return Optional.of(new AgentDirectorModelSelection(
                    actionId, rationale, selection.path("expectedEnergyDelta").asInt(0),
                    "ollama:" + settings.model(),
                    Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<AgentDirectorModelAdvice> recommendTrainingMaps(
            AgentDirectorExecutiveView view,
            String operatorPrompt,
            AgentDirectorDomainContext domainContext) {
        if (!settings.enabled() || view == null || operatorPrompt == null
                || operatorPrompt.isBlank() || domainContext == null
                || domainContext.trainingMaps().isEmpty()) return Optional.empty();
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(settings.endpoint() + "/api/generate"))
                    .timeout(Duration.ofMillis(settings.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            JSON.writeValueAsString(adviceBody(view, operatorPrompt, domainContext))))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            JsonNode output = JSON.readTree(
                    JSON.readTree(response.body()).path("response").asText(""));
            JsonNode recommendations = output.path("recommendations");
            if (!recommendations.isArray()) return Optional.empty();
            Set<String> allowed = domainContext.trainingMaps().stream()
                    .map(AgentDirectorDomainContext.TrainingMapCandidate::actionId)
                    .collect(java.util.stream.Collectors.toSet());
            Set<String> seen = new HashSet<>();
            List<AgentDirectorRankedSelection> selections = new ArrayList<>();
            for (JsonNode recommendation : recommendations) {
                String actionId = normalizedActionId(
                        recommendation.path("actionId").asText("").trim(), allowed);
                String rationale = bounded(
                        recommendation.path("rationale").asText("").trim(), 500);
                if (!allowed.contains(actionId) || rationale.isEmpty() || !seen.add(actionId)) {
                    continue;
                }
                selections.add(new AgentDirectorRankedSelection(actionId, rationale));
                if (selections.size() >= domainContext.requestedCount()) break;
            }
            if (selections.isEmpty()) return Optional.empty();
            return Optional.of(new AgentDirectorModelAdvice(
                    selections, "ollama:" + settings.model(),
                    Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception failure) {
            return Optional.empty();
        }
    }

    public DirectorLlmHealth health() {
        if (!settings.enabled()) {
            return new DirectorLlmHealth(false, false, false, settings.model(), "DISABLED");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(settings.endpoint() + "/api/tags"))
                    .timeout(Duration.ofMillis(Math.min(settings.timeoutMs(), 1_500)))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new DirectorLlmHealth(true, false, false, settings.model(), "UNREACHABLE");
            }
            JsonNode models = JSON.readTree(response.body()).path("models");
            boolean installed = models.isArray()
                    && java.util.stream.StreamSupport.stream(models.spliterator(), false)
                    .map(model -> model.path("name").asText(model.path("model").asText("")))
                    .anyMatch(name -> name.equals(settings.model()));
            return new DirectorLlmHealth(true, true, installed, settings.model(),
                    installed ? "READY" : "MODEL_NOT_FOUND");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new DirectorLlmHealth(true, false, false, settings.model(), "INTERRUPTED");
        } catch (Exception failure) {
            return new DirectorLlmHealth(true, false, false, settings.model(), "UNREACHABLE");
        }
    }

    /** Loads the configured model off the bridge threads so the first operator prompt is warm. */
    public void prewarmAsync() {
        if (!settings.enabled() || !prewarmStarted.compareAndSet(false, true)) return;
        Thread thread = new Thread(this::prewarm, "agent-director-ollama-prewarm");
        thread.setDaemon(true);
        thread.start();
    }

    private Map<String, Object> body(AgentDirectorExecutiveView view, String operatorPrompt) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("num_predict", settings.maxPredictTokens());
        options.put("num_ctx", settings.numContext());
        options.put("temperature", 0.15);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("stream", false);
        body.put("think", false);
        body.put("keep_alive", "10m");
        body.put("format", selectionFormat(view));
        body.put("system", "You are the high-level Director for Cosmic MapleStory v83. "
                + "Treat supplied Agent OS actions and catalog facts as authoritative. Select exactly one "
                + "executable actionId from the supplied list. Never invent actions and never "
                + "claim execution. Return JSON only: {actionId,rationale,expectedEnergyDelta}. "
                + "expectedEnergyDelta is an integer from -100 to 100.");
        body.put("prompt", prompt(view, operatorPrompt));
        body.put("options", options);
        return body;
    }

    private Map<String, Object> adviceBody(
            AgentDirectorExecutiveView view,
            String operatorPrompt,
            AgentDirectorDomainContext domainContext) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("num_predict", settings.maxPredictTokens());
        options.put("num_ctx", settings.numContext());
        options.put("temperature", 0.2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", settings.model());
        body.put("stream", false);
        body.put("think", false);
        body.put("keep_alive", "10m");
        body.put("format", adviceFormat(domainContext));
        body.put("system", "You are the high-level Director for Cosmic MapleStory v83. "
                + "Rank only the supplied training-map actionIds. The catalog rank and weight "
                + "are evidence, not commands: independently evaluate map fit using the Agent's "
                + "level, job, energy, personality, resources, terrain, hazards, conditions, and "
                + "mob composition. Never invent game facts, maps, routes, actions, or execution. "
                + "Return JSON only: {recommendations:[{actionId,rationale}]}. Return at most "
                + domainContext.requestedCount() + " unique recommendations.");
        body.put("prompt", advicePrompt(view, operatorPrompt, domainContext));
        body.put("options", options);
        return body;
    }

    private static String prompt(AgentDirectorExecutiveView view, String operatorPrompt) {
        StringBuilder text = new StringBuilder(2048)
                .append("Operator request: ").append(operatorPrompt.trim()).append('\n')
                .append("Agent: ").append(view.context().agentName()).append('\n')
                .append("Current: ").append(view.activity().now()).append('\n')
                .append("Energy: ").append(view.energy().energyPercent()).append("% (")
                .append(view.energy().band()).append(")\n")
                .append("Personality traits: ").append(view.profile().traits()).append('\n')
                .append("HP: ").append(view.context().hp()).append('/').append(view.context().maxHp())
                .append(" MP: ").append(view.context().mp()).append('/').append(view.context().maxMp())
                .append(" Mesos: ").append(view.context().meso()).append('\n')
                .append("Executable actions:\n");
        for (AgentDirectorAction action : view.actions()) {
            if (action.availability().executable()) {
                text.append("- ").append(action.actionId()).append(" | ")
                        .append(action.label()).append(" | ").append(action.reason()).append('\n');
            }
        }
        return text.toString();
    }

    private static String advicePrompt(
            AgentDirectorExecutiveView view,
            String operatorPrompt,
            AgentDirectorDomainContext context) {
        StringBuilder text = new StringBuilder(8192)
                .append("Operator request: ").append(operatorPrompt.trim()).append('\n')
                .append("Domain: ").append(context.domain()).append(" / ")
                .append(context.gameDataVersion()).append('\n')
                .append("Selected Agent: ").append(view.context().agentName())
                .append(" level ").append(view.context().level())
                .append(" jobId ").append(view.context().jobId())
                .append(" currentMap ").append(view.context().mapId()).append('\n')
                .append("Requested evaluation level: ").append(context.requestedLevel()).append('\n')
                .append("Energy: ").append(view.energy().energyPercent()).append("% (")
                .append(view.energy().band()).append(")\n")
                .append("Personality traits: ").append(view.profile().traits()).append('\n')
                .append("HP: ").append(view.context().hp()).append('/').append(view.context().maxHp())
                .append(" MP: ").append(view.context().mp()).append('/').append(view.context().maxMp())
                .append(" Mesos: ").append(view.context().meso()).append('\n')
                .append("Catalog training-map candidates:\n");
        for (AgentDirectorDomainContext.TrainingMapCandidate map : context.trainingMaps()) {
            text.append("- ").append(map.actionId()).append(" | ").append(map.mapName())
                    .append(" (map ").append(map.mapId()).append(") | catalogRank ")
                    .append(map.catalogRank()).append(" weight ").append(map.catalogWeight())
                    .append(" | levelBand ").append(map.recommendedMinLevel()).append('-')
                    .append(map.recommendedMaxLevel()).append(" | capacity ")
                    .append(map.recommendedAgents()).append('/').append(map.maximumAgents())
                    .append(" | terrain ").append(map.terrain())
                    .append(" | tags ").append(map.tags())
                    .append(" | hazards ").append(map.hazards())
                    .append(" | conditions ").append(map.conditions())
                    .append(" | catalogRationale ").append(map.catalogRationale())
                    .append(" | spawns ");
            for (AgentDirectorDomainContext.SpawnFact spawn : map.spawns()) {
                text.append(spawn.mobName()).append("(L").append(spawn.mobLevel())
                        .append(" x").append(spawn.expectedCount()).append(' ')
                        .append(spawn.role()).append("); ");
            }
            text.append('\n');
        }
        return text.toString();
    }

    private static String bounded(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength).trim();
    }

    private void prewarm() {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", settings.model());
            body.put("stream", false);
            body.put("prompt", "");
            body.put("keep_alive", "10m");
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(settings.endpoint() + "/api/generate"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                    .build();
            http.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // A later operator request still gets deterministic fallback if Ollama is unavailable.
        }
    }

    private static Map<String, Object> selectionFormat(AgentDirectorExecutiveView view) {
        List<String> actionIds = view.actions().stream()
                .filter(action -> action.availability().executable())
                .map(AgentDirectorAction::actionId).toList();
        return objectSchema(Map.of(
                "actionId", Map.of("type", "string", "enum", actionIds),
                "rationale", Map.of("type", "string"),
                "expectedEnergyDelta", Map.of("type", "integer", "minimum", -100,
                        "maximum", 100)),
                List.of("actionId", "rationale", "expectedEnergyDelta"));
    }

    private static Map<String, Object> adviceFormat(AgentDirectorDomainContext context) {
        List<String> actionIds = context.trainingMaps().stream()
                .map(AgentDirectorDomainContext.TrainingMapCandidate::actionId).toList();
        Map<String, Object> item = objectSchema(Map.of(
                "actionId", Map.of("type", "string", "enum", actionIds),
                "rationale", Map.of("type", "string")),
                List.of("actionId", "rationale"));
        return objectSchema(Map.of(
                "recommendations", Map.of("type", "array", "items", item,
                        "minItems", 1, "maxItems", context.requestedCount())),
                List.of("recommendations"));
    }

    private static Map<String, Object> objectSchema(
            Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static String normalizedActionId(String raw, Set<String> allowed) {
        if (allowed.contains(raw)) return raw;
        if (raw.chars().allMatch(java.lang.Character::isDigit)) {
            String hunting = "hunting-map:" + raw;
            if (allowed.contains(hunting)) return hunting;
        }
        return raw;
    }
}
