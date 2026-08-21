package server.agents.progression.questwork.shadow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.progression.questcatalog.AgentQuestAttemptRequirements;
import server.agents.progression.questcatalog.AgentQuestCatalog;
import server.agents.progression.questcatalog.AgentQuestCatalogRepository;
import server.agents.progression.questcatalog.AgentQuestDefinition;
import server.agents.progression.questcatalog.AgentQuestSelectionDisposition;
import server.agents.progression.questwork.AgentFileQuestWorkUnitStore;
import server.agents.progression.questwork.AgentQuestLiveState;
import server.agents.progression.questwork.AgentQuestWorkAction;
import server.agents.progression.questwork.AgentQuestWorkReconciler;
import server.agents.progression.questwork.AgentQuestWorkUnitService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQuestWorkShadowAdapterTest {
    @TempDir
    Path directory;

    @Test
    void reportsExactAgreementWithoutExecutingExistingPlan() {
        AgentQuestWorkUnitService service = service();
        service.begin("work-1", "agent-1", 101, 1, 100L);
        AgentQuestWorkShadowAdapter adapter = new AgentQuestWorkShadowAdapter(service);
        AgentQuestPlanShadowObservation observation = new AgentQuestPlanShadowObservation(
                "legacy-plan", "hunt", 1,
                AgentQuestWorkAction.TRAVEL_TO_HUNT_MAP, 200, 200L);

        AgentQuestWorkShadowReport report = adapter.compare(
                "work-1", live(101, 1), observation, 200L);

        assertTrue(report.matches());
        assertEquals(observation, report.existingPlan());
        assertEquals(AgentQuestWorkAction.TRAVEL_TO_HUNT_MAP,
                report.durableRecommendation().nextAction());
    }

    @Test
    void distinguishesActionDestinationAndQuestDivergence() {
        AgentQuestWorkUnitService service = service();
        service.begin("work-2", "agent-2", 102, 1, 100L);
        AgentQuestWorkShadowAdapter adapter = new AgentQuestWorkShadowAdapter(service);

        AgentQuestWorkShadowReport action = adapter.compare("work-2", live(102, 1),
                observation(1, AgentQuestWorkAction.COMPLETE_OBJECTIVES, 0), 200L);
        AgentQuestWorkShadowReport destination = adapter.compare("work-2", live(102, 1),
                observation(1, AgentQuestWorkAction.TRAVEL_TO_HUNT_MAP, 201), 300L);
        AgentQuestWorkShadowReport quest = adapter.compare("work-2", live(102, 1),
                observation(2, AgentQuestWorkAction.TRAVEL_TO_HUNT_MAP, 200), 400L);

        assertEquals(AgentQuestShadowComparison.ACTION_MISMATCH, action.comparison());
        assertEquals(AgentQuestShadowComparison.DESTINATION_MISMATCH, destination.comparison());
        assertEquals(AgentQuestShadowComparison.QUEST_MISMATCH, quest.comparison());
        assertFalse(action.matches());
    }

    private static AgentQuestPlanShadowObservation observation(
            int questId, AgentQuestWorkAction action, int destination) {
        return new AgentQuestPlanShadowObservation(
                "legacy-plan", "step", questId, action, destination, 200L);
    }

    private static AgentQuestLiveState live(int characterId, int questState) {
        return new AgentQuestLiveState(
                characterId, 15, 100, questState, Map.of(), Map.of("kill-1", 0));
    }

    private AgentQuestWorkUnitService service() {
        return new AgentQuestWorkUnitService(
                new AgentQuestCatalogRepository(new AgentQuestCatalog(
                        1, "test", "facts-1", "guidance-1", List.of(quest()))),
                new AgentFileQuestWorkUnitStore(directory), new AgentQuestWorkReconciler());
    }

    private static AgentQuestDefinition quest() {
        AgentQuestDefinition.Endpoint endpoint =
                new AgentQuestDefinition.Endpoint(1000, List.of(100));
        return new AgentQuestDefinition(
                1, "Quest", 1, null, 15, Set.of(), List.of(), true,
                AgentQuestSelectionDisposition.ELIGIBLE, endpoint, endpoint,
                List.of(new AgentQuestDefinition.Objective(
                        "kill-1", "kill-mob", 100100, "Mob", 10, List.of(100100),
                        List.of(new AgentQuestDefinition.HuntMap(
                                1, 200, "Hunt", List.of(100100), 10, 1, 3)))),
                new AgentQuestAttemptRequirements(0, 0, 0, 0), "", List.of());
    }
}
