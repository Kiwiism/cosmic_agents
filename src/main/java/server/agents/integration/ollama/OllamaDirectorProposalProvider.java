package server.agents.integration.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.chat.AgentDirectorModelSelection;
import server.agents.runtime.activity.control.chat.AgentDirectorProposalProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Proposal-only Ollama adapter. Model output is constrained to current action IDs. */
public final class OllamaDirectorProposalProvider implements AgentDirectorProposalProvider {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final DirectorLlmSettings settings;
    private final HttpClient http;

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
        body.put("format", "json");
        body.put("system", "You are a high-level game Agent Director. Select exactly one "
                + "executable actionId from the supplied list. Never invent actions and never "
                + "claim execution. Return JSON only: {actionId,rationale,expectedEnergyDelta}. "
                + "expectedEnergyDelta is an integer from -100 to 100.");
        body.put("prompt", prompt(view, operatorPrompt));
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
}
