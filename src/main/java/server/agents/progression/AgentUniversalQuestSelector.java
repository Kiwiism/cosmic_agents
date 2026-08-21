package server.agents.progression;

import server.agents.progression.questcatalog.AgentQuestCatalogRepository;
import server.agents.progression.questcatalog.AgentQuestDefinition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Deterministically ranks ready quests without accepting or starting any quest. */
public final class AgentUniversalQuestSelector {
    private final AgentQuestCatalogRepository catalog;

    public AgentUniversalQuestSelector(AgentQuestCatalogRepository catalog) {
        if (catalog == null) throw new IllegalArgumentException("quest catalog is required");
        this.catalog = catalog;
    }

    public List<AgentUniversalQuestSelection> rank(AgentUniversalQuestSelectionContext context) {
        if (context == null) throw new IllegalArgumentException("quest selection context is required");
        List<AgentUniversalQuestSelection> ranked = new ArrayList<>();
        for (AgentQuestDefinition quest : catalog.catalog().entries()) {
            if (context.suppressedQuestIds().contains(quest.questId())
                    || !catalog.evaluate(quest.questId(), context.eligibility()).eligible()) {
                continue;
            }
            java.util.OptionalInt routeHops = routeHops(quest, context);
            if (routeHops.isEmpty()) continue;
            long score = AgentProgressionDecisionPolicy.questScore(
                    context.profile(), context.characterId(), context.eligibility().level(),
                    context.currentMapId(), routeHops.getAsInt(), quest);
            ranked.add(new AgentUniversalQuestSelection(
                    quest, score, routeHops.getAsInt(),
                    evidence(quest, context, routeHops.getAsInt())));
        }
        ranked.sort(Comparator.comparingLong(AgentUniversalQuestSelection::score).reversed()
                .thenComparingInt(selection -> selection.quest().questId()));
        return List.copyOf(ranked);
    }

    public java.util.Optional<AgentUniversalQuestSelection> select(
            AgentUniversalQuestSelectionContext context) {
        return rank(context).stream().findFirst();
    }

    private static java.util.OptionalInt routeHops(
            AgentQuestDefinition quest,
            AgentUniversalQuestSelectionContext context) {
        if (quest.start().mapIds().contains(context.currentMapId())) {
            return java.util.OptionalInt.of(0);
        }
        return quest.start().mapIds().stream()
                .map(context.routeHopsByMapId()::get)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .map(java.util.OptionalInt::of)
                .orElseGet(java.util.OptionalInt::empty);
    }

    private static List<String> evidence(
            AgentQuestDefinition quest,
            AgentUniversalQuestSelectionContext context,
            int routeHops) {
        return List.of(
                "readiness=eligible",
                "level=" + context.eligibility().level()
                        + "; recommended=" + quest.recommendedLevel(),
                "start=" + (routeHops == 0 ? "local" : "route-hops:" + routeHops),
                "profile=" + context.profile().profileId(),
                "objectives=" + quest.objectives().size());
    }
}
