package server.agents.integration.typesafe;

import server.agents.integration.typesafe.state.JevStateBands;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.chat.AgentDirectorDomainContext;
import server.agents.runtime.activity.control.chat.AgentDirectorModelAdvice;
import server.agents.runtime.activity.control.chat.AgentDirectorModelSelection;
import server.agents.runtime.activity.control.chat.AgentDirectorProposalProvider;
import server.agents.runtime.activity.control.chat.AgentDirectorRankedSelection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Director proposals as typed selections instead of parsed LLM prose: one Choice over the
 * executable actions for "what does the operator want", and one Score per training-map candidate
 * for "how well does this map fit the request". Falls through to the wrapped provider whenever
 * TypeSafe is disabled, unavailable, or not confident enough.
 */
public final class TypeSafeDirectorProposalProvider implements AgentDirectorProposalProvider {
    private static final double MIN_SELECT_CONFIDENCE = config.AgentTuning.doubleValue(
            "server.agents.integration.typesafe.TypeSafeDirectorProposalProvider.MIN_SELECT_CONFIDENCE");
    public static final String ACTION_QUESTION = "action";
    public static final String ACTIONABLE_QUESTION = "asks_for_an_action";
    public static final String SELECT_KIND = "director-select";
    public static final String RANK_KIND = "director-rank";
    public static final List<Object> FIT_LEVELS = List.of(
            "poor fit: wrong level range, wrong kind of place, or contradicts the request",
            "partial fit: usable but clearly not what was asked for",
            "good fit: matches the request and the requested level",
            "ideal fit: matches every stated preference of the request");

    private final JevClient client;
    private final AgentDirectorProposalProvider fallback;
    private final boolean enabled;
    private final double minSelectConfidence;

    public TypeSafeDirectorProposalProvider(JevClient client, AgentDirectorProposalProvider fallback, boolean enabled) {
        this(client, fallback, enabled, MIN_SELECT_CONFIDENCE);
    }

    public TypeSafeDirectorProposalProvider(JevClient client, AgentDirectorProposalProvider fallback,
                                            boolean enabled, double minSelectConfidence) {
        this.client = client;
        this.fallback = fallback;
        this.enabled = enabled;
        this.minSelectConfidence = minSelectConfidence;
    }

    /** Wraps {@code fallback} only when the feature flag is on and a key is configured. */
    public static AgentDirectorProposalProvider wrap(AgentDirectorProposalProvider fallback, boolean enabled) {
        JevClient client = JevClient.runtime();
        if (!enabled || !client.configured()) {
            return fallback;
        }
        return new TypeSafeDirectorProposalProvider(client, fallback, true);
    }

    @Override
    public Optional<AgentDirectorModelSelection> select(AgentDirectorExecutiveView view, String operatorPrompt) {
        if (!enabled || view == null || operatorPrompt == null || operatorPrompt.isBlank() || !client.available()) {
            return fallback.select(view, operatorPrompt);
        }
        List<AgentDirectorAction> actions = view.actions().stream()
                .filter(action -> action.availability().executable())
                .toList();
        if (actions.size() < 2) {
            return fallback.select(view, operatorPrompt);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("operator_request", operatorPrompt.trim());
        List<Map<String, Object>> actionRows = new ArrayList<>();
        Map<String, Object> criteria = new LinkedHashMap<>();
        for (AgentDirectorAction action : actions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", action.actionId());
            row.put("label", action.label());
            row.put("activity", action.targetActivityKind() == null ? "" : action.targetActivityKind().name().toLowerCase(Locale.ROOT));
            row.put("destructive", action.destructive());
            actionRows.add(row);
            criteria.put(action.actionId(), action.label()
                    + (action.destructive() ? " (destructive; only when explicitly requested)" : ""));
        }
        criteria.put("none_of_these", "the request does not ask for any of the listed actions");
        state.put("actions", actionRows);
        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("question", "Which listed action does `operator_request` ask the Director to perform?");
        instructions.put("inspect", "`operator_request` against each entry of `actions`");
        Optional<JevResponse> response = client.ask(JevRequest.builder(state).kind(SELECT_KIND)
                .choice(ACTION_QUESTION, instructions, criteria)
                .noul(ACTIONABLE_QUESTION, "Does `operator_request` ask the Director to do something now, rather than ask a question or make small talk?")
                .build());
        if (response.isEmpty()) {
            return fallback.select(view, operatorPrompt);
        }
        JevResponse answers = response.get();
        Optional<JevAnswer> actionAnswer = answers.answer(ACTION_QUESTION);
        String actionId = actionAnswer.flatMap(JevAnswer::mostLikelyOption).orElse("none_of_these");
        double confidence = actionAnswer.map(JevAnswer::confidenceOrZero).orElse(0.0d);
        boolean allowed = actions.stream().anyMatch(action -> action.actionId().equals(actionId));
        if (!allowed || confidence < minSelectConfidence
                || answers.yesProbability(ACTIONABLE_QUESTION) < minSelectConfidence) {
            return fallback.select(view, operatorPrompt);
        }
        String label = actions.stream().filter(action -> action.actionId().equals(actionId))
                .map(AgentDirectorAction::label).findFirst().orElse(actionId);
        String rationale = String.format(Locale.ROOT,
                "Jev matched the request to '%s' with confidence %.2f (asks for an action: %.2f).",
                label, confidence, answers.yesProbability(ACTIONABLE_QUESTION));
        return Optional.of(new AgentDirectorModelSelection(
                actionId, rationale, 0, "typesafe:" + client.settings().model(), answers.latencyMs()));
    }

    @Override
    public Optional<AgentDirectorModelAdvice> recommendTrainingMaps(AgentDirectorExecutiveView view,
                                                                    String operatorPrompt,
                                                                    AgentDirectorDomainContext domainContext) {
        if (!enabled || view == null || operatorPrompt == null || operatorPrompt.isBlank()
                || domainContext == null || domainContext.trainingMaps().isEmpty() || !client.available()) {
            return fallback.recommendTrainingMaps(view, operatorPrompt, domainContext);
        }
        List<AgentDirectorDomainContext.TrainingMapCandidate> candidates = domainContext.trainingMaps().stream()
                .filter(AgentDirectorDomainContext.TrainingMapCandidate::selectable)
                .toList();
        if (candidates.isEmpty()) {
            return fallback.recommendTrainingMaps(view, operatorPrompt, domainContext);
        }
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("operator_request", operatorPrompt.trim());
        state.put("requested_level", domainContext.requestedLevel());
        state.put("agent_level_vs_requested", JevStateBands.levelGap(domainContext.requestedLevel(), domainContext.agentLevel()));
        List<Map<String, Object>> rows = new ArrayList<>();
        JevRequest.Builder builder = JevRequest.builder(state).kind(RANK_KIND);
        for (int index = 0; index < candidates.size(); index++) {
            AgentDirectorDomainContext.TrainingMapCandidate candidate = candidates.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", candidate.actionId());
            row.put("map", candidate.mapName());
            row.put("level_window", candidate.recommendedMinLevel() + " to " + candidate.recommendedMaxLevel());
            row.put("requested_level_is", JevStateBands.levelGap(candidate.recommendedMinLevel(), domainContext.requestedLevel())
                    + " the window's lower bound");
            row.put("terrain", candidate.terrain());
            row.put("agents", "recommended " + candidate.recommendedAgents() + ", at most " + candidate.maximumAgents());
            row.put("tags", candidate.tags());
            row.put("hazards", candidate.hazards());
            row.put("catalog_note", candidate.catalogRationale());
            rows.add(row);
            Map<String, Object> instructions = new LinkedHashMap<>();
            instructions.put("question", "How well does candidate `candidates[" + index + "]` fit `operator_request` for a level `requested_level` Agent?");
            instructions.put("inspect", "`candidates[" + index + "]`, `operator_request`, `requested_level`");
            builder.score(fitQuestion(index), instructions, FIT_LEVELS);
        }
        state.put("candidates", rows);
        Optional<JevResponse> response = client.ask(builder.build());
        if (response.isEmpty()) {
            return fallback.recommendTrainingMaps(view, operatorPrompt, domainContext);
        }
        JevResponse answers = response.get();
        record Scored(AgentDirectorDomainContext.TrainingMapCandidate candidate, JevAnswer answer) {
            double score() {
                return answer.scoreOrZero();
            }
        }
        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Optional<JevAnswer> answer = answers.answer(fitQuestion(index));
            if (answer.isEmpty() || !answer.get().isScore()
                    || answer.get().confidenceOrZero() < minSelectConfidence) {
                return fallback.recommendTrainingMaps(view, operatorPrompt, domainContext);
            }
            scored.add(new Scored(candidates.get(index), answer.get()));
        }
        if (scored.isEmpty()) {
            return fallback.recommendTrainingMaps(view, operatorPrompt, domainContext);
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed()
                .thenComparingInt(entry -> entry.candidate().catalogRank()));
        List<AgentDirectorRankedSelection> selections = new ArrayList<>();
        for (Scored entry : scored) {
            if (selections.size() >= domainContext.requestedCount()) {
                break;
            }
            selections.add(new AgentDirectorRankedSelection(entry.candidate().actionId(), String.format(Locale.ROOT,
                    "Jev rated %s '%s' (score %.2f, confidence %.2f) for a level %d request.",
                    entry.candidate().mapName(), fitLabel(entry.answer()), entry.score(), entry.answer().confidenceOrZero(),
                    domainContext.requestedLevel())));
        }
        return Optional.of(new AgentDirectorModelAdvice(
                selections, "typesafe:" + client.settings().model(), answers.latencyMs()));
    }

    static String fitQuestion(int index) {
        return "fit_" + index;
    }

    /** Short label of the most probable level, read from the answer's own legend when present. */
    static String fitLabel(JevAnswer answer) {
        String option = answer.mostLikelyOption().orElse("");
        String description = answer.legend().getOrDefault(option, option);
        int colon = description.indexOf(':');
        return colon > 0 ? description.substring(0, colon) : description;
    }
}
