package server.agents.progression;

import org.junit.jupiter.api.Test;
import server.agents.progression.questcatalog.AgentQuestAttemptRequirements;
import server.agents.progression.questcatalog.AgentQuestCatalog;
import server.agents.progression.questcatalog.AgentQuestCatalogRepository;
import server.agents.progression.questcatalog.AgentQuestDefinition;
import server.agents.progression.questcatalog.AgentQuestEligibility;
import server.agents.progression.questcatalog.AgentQuestEligibilityContext;
import server.agents.progression.questcatalog.AgentQuestSelectionDisposition;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentUniversalQuestSelectorTest {
    @Test
    void ranksReadyLocalQuestAheadOfEquivalentRemoteQuest() {
        AgentQuestCatalogRepository catalog = catalog(quest(1, 100, 15), quest(2, 200, 15));
        AgentUniversalQuestSelector selector = new AgentUniversalQuestSelector(catalog);

        List<AgentUniversalQuestSelection> ranked = selector.rank(context(
                Map.of(200, 3), Set.of(), Map.of()));

        assertEquals(List.of(1, 2), ranked.stream()
                .map(selection -> selection.quest().questId()).toList());
        assertEquals(0, ranked.getFirst().routeHops());
        assertTrue(ranked.getFirst().evidence().contains("readiness=eligible"));
    }

    @Test
    void excludesSuppressedActiveAndCompletedQuestsBeforeScoring() {
        AgentQuestCatalogRepository catalog = catalog(
                quest(1, 100, 15), quest(2, 100, 15), quest(3, 100, 15), quest(4, 100, 15));
        AgentUniversalQuestSelector selector = new AgentUniversalQuestSelector(catalog);

        List<AgentUniversalQuestSelection> ranked = selector.rank(context(
                Map.of(), Set.of(4), Map.of(2, 1, 3, 2)));

        assertEquals(List.of(1), ranked.stream()
                .map(selection -> selection.quest().questId()).toList());
        assertEquals(AgentQuestEligibility.Status.ALREADY_IN_PROGRESS,
                catalog.evaluate(2, eligibility(Map.of(2, 1))).status());
        assertEquals(AgentQuestEligibility.Status.ALREADY_COMPLETED,
                catalog.evaluate(3, eligibility(Map.of(3, 2))).status());
    }

    private static AgentUniversalQuestSelectionContext context(
            Map<Integer, Integer> routeHops,
            Set<Integer> suppressed,
            Map<Integer, Integer> questStates) {
        return new AgentUniversalQuestSelectionContext(
                7, 100, eligibility(questStates),
                AgentProgressionProfileRepository.defaultRepository().defaultProfile(),
                routeHops, suppressed);
    }

    private static AgentQuestEligibilityContext eligibility(Map<Integer, Integer> questStates) {
        return new AgentQuestEligibilityContext(15, 100, 10_000, 10, 100, 100, questStates);
    }

    private static AgentQuestCatalogRepository catalog(AgentQuestDefinition... quests) {
        return new AgentQuestCatalogRepository(new AgentQuestCatalog(
                1, "test", "facts-1", "guidance-1", List.of(quests)));
    }

    private static AgentQuestDefinition quest(int id, int startMapId, int recommendedLevel) {
        AgentQuestDefinition.Endpoint endpoint = new AgentQuestDefinition.Endpoint(1000, List.of(startMapId));
        return new AgentQuestDefinition(
                id, "Quest " + id, 1, null, recommendedLevel, Set.of(), List.of(),
                true, AgentQuestSelectionDisposition.ELIGIBLE, endpoint, endpoint,
                List.of(new AgentQuestDefinition.Objective(
                        "kill-" + id, "kill-mob", 100100, "Mob", 10, List.of(100100),
                        List.of(new AgentQuestDefinition.HuntMap(
                                1, startMapId, "Map", List.of(100100), 10, 1, 3)))),
                new AgentQuestAttemptRequirements(0, 0, 0, 0), "", List.of());
    }
}
