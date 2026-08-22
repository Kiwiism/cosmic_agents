package server.agents.runtime.activity.control.chat;

import server.agents.progression.AgentVictoriaTrainingCatalog;
import server.agents.progression.AgentVictoriaTrainingCatalogRepository;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects only the small catalog slice relevant to the operator's question. */
public final class AgentDirectorDomainContextBuilder {
    private static final Pattern LEVEL = Pattern.compile("\\b(?:lv\\.?|level)\\s*(\\d{1,3})\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOP_COUNT = Pattern.compile("\\btop\\s*(\\d{1,2})\\b",
            Pattern.CASE_INSENSITIVE);

    private final AgentVictoriaTrainingCatalogRepository training;

    public AgentDirectorDomainContextBuilder() {
        this(AgentVictoriaTrainingCatalogRepository.defaultRepository());
    }

    AgentDirectorDomainContextBuilder(AgentVictoriaTrainingCatalogRepository training) {
        if (training == null) throw new IllegalArgumentException("training catalog is required");
        this.training = training;
    }

    public boolean isTrainingMapQuestion(String prompt) {
        String value = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        boolean trainingActivity = value.contains("grind") || value.contains("hunt")
                || value.contains("train");
        boolean locationQuestion = value.contains(" map") || value.startsWith("map")
                || value.contains("where");
        boolean rankingQuestion = value.contains("top") || value.contains("best")
                || value.contains("recommend") || value.contains("consider")
                || value.contains("which");
        return trainingActivity && (locationQuestion || rankingQuestion);
    }

    public AgentDirectorDomainContext build(
            AgentDirectorExecutiveView view, String operatorPrompt) {
        if (view == null || view.context() == null) {
            throw new IllegalArgumentException("Director view is required");
        }
        String prompt = operatorPrompt == null ? "" : operatorPrompt;
        int agentLevel = view.context().level();
        int requestedLevel = parsed(prompt, LEVEL, agentLevel);
        int requestedCount = Math.max(1, Math.min(5, parsed(prompt, TOP_COUNT, 3)));
        AgentVictoriaTrainingCatalog catalog = training.catalog();
        List<AgentDirectorDomainContext.TrainingMapCandidate> candidates = new ArrayList<>();
        for (AgentVictoriaTrainingCatalog.TrainingChoice choice
                : training.choicesForLevel(requestedLevel)) {
            AgentVictoriaTrainingCatalog.TrainingMap map =
                    training.findMap(choice.mapId()).orElse(null);
            if (map == null) continue;
            String actionId = "hunting-map:" + map.mapId();
            AgentDirectorAction action = view.actions().stream()
                    .filter(candidate -> candidate.actionId().equals(actionId))
                    .findFirst().orElse(null);
            List<AgentDirectorDomainContext.SpawnFact> spawns = map.spawns().stream()
                    .map(spawn -> new AgentDirectorDomainContext.SpawnFact(
                            spawn.mobId(), spawn.mobName(), spawn.mobLevel(),
                            spawn.expectedCount(), spawn.role()))
                    .toList();
            candidates.add(new AgentDirectorDomainContext.TrainingMapCandidate(
                    actionId, action == null ? "Hunt — " + map.mapName() : action.label(),
                    map.mapId(), map.mapName(), choice.rank(), choice.weight(),
                    map.recommendedMinLevel(), map.recommendedMaxLevel(),
                    map.recommendedAgents(), map.maximumAgents(), map.terrain(),
                    choice.rationale(), choice.conditions(), map.tags(), map.hazards(),
                    spawns, action != null && action.availability().executable()));
        }
        return new AgentDirectorDomainContext(
                "Cosmic MapleStory v83", catalog.gameDataVersion(), requestedLevel,
                agentLevel, requestedCount, candidates);
    }

    private static int parsed(String prompt, Pattern pattern, int fallback) {
        Matcher matcher = pattern.matcher(prompt == null ? "" : prompt);
        if (!matcher.find()) return fallback;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
