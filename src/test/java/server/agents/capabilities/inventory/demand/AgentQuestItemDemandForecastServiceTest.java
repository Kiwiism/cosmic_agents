package server.agents.capabilities.inventory.demand;

import client.QuestStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQuestItemDemandForecastServiceTest {
    private static final int ITEM_ID = 4_000_003;

    @Test
    void filtersCompletedJobAndPrerequisiteDemandBeforeCategorizingHorizons() {
        AgentQuestItemDemandForecastService service = service(List.of(
                demand(100, 5, 10, List.of(), List.of(), true, "eligible"),
                demand(101, 7, 10, List.of(), List.of(), true, "eligible"),
                demand(102, 11, 12, List.of(400),
                        List.of(new AgentQuestItemDemandIndex.Prerequisite(90, 2)),
                        true, "eligible"),
                demand(103, 13, 20, List.of(), List.of(), true, "eligible"),
                demand(104, 17, 40, List.of(), List.of(), true, "eligible"),
                demand(105, 19, 10, List.of(), List.of(), true, "review-blocked")));
        AgentQuestDemandProfile profile = new AgentQuestDemandProfile(
                10, 0, Set.of(400),
                Map.of(
                        100, QuestStatus.Status.STARTED.getId(),
                        101, QuestStatus.Status.COMPLETED.getId(),
                        90, QuestStatus.Status.COMPLETED.getId()),
                Set.of(102),
                Map.of(ITEM_ID, 8));

        AgentQuestItemDemandForecast.ItemForecast item =
                service.forecast(profile).items().getFirst();

        assertEquals(5, item.demand(AgentQuestDemandCategory.ACTIVE));
        assertEquals(11, item.demand(AgentQuestDemandCategory.COMMITTED));
        assertEquals(13, item.demand(AgentQuestDemandCategory.WITHIN_15_LEVELS));
        assertEquals(0, item.demand(AgentQuestDemandCategory.WITHIN_25_LEVELS));
        assertEquals(3, item.evidence().size());
    }

    @Test
    void doesNotTreatCommittedNotStartedPrerequisiteAsSatisfied() {
        AgentQuestItemDemandForecastService service = service(List.of(
                demand(200, 5, 10, List.of(),
                        List.of(new AgentQuestItemDemandIndex.Prerequisite(90, 0)),
                        true, "eligible")));
        AgentQuestDemandProfile profile = new AgentQuestDemandProfile(
                10, 0, Set.of(),
                Map.of(90, QuestStatus.Status.STARTED.getId()),
                Set.of(90),
                Map.of());

        assertTrue(service.forecast(profile).items().isEmpty());
    }

    @Test
    void categorizesOlderEligibleQuestsWithinTheSameBoundedLevelHorizons() {
        AgentQuestItemDemandForecastService service = service(List.of(
                demand(300, 5, 5, List.of(), List.of(), true, "eligible")));
        AgentQuestDemandProfile profile = new AgentQuestDemandProfile(
                20, 0, Set.of(), Map.of(), Set.of(), Map.of());

        AgentQuestItemDemandForecast.ItemForecast item =
                service.forecast(profile).items().getFirst();

        assertEquals(0, item.demand(AgentQuestDemandCategory.WITHIN_5_LEVELS));
        assertEquals(5, item.demand(AgentQuestDemandCategory.WITHIN_15_LEVELS));
    }

    private static AgentQuestItemDemandForecastService service(
            List<AgentQuestItemDemandIndex.QuestDemand> demands) {
        AgentQuestItemDemandIndex index = new AgentQuestItemDemandIndex(
                1, "test", "revision", List.of(5, 15, 25),
                List.of(new AgentQuestItemDemandIndex.Entry(
                        ITEM_ID, "Tree Branch",
                        demands.stream().mapToInt(
                                AgentQuestItemDemandIndex.QuestDemand::requiredCount).sum(),
                        demands)));
        return new AgentQuestItemDemandForecastService(
                new AgentQuestItemDemandIndexRepository(index));
    }

    private static AgentQuestItemDemandIndex.QuestDemand demand(
            int questId,
            int count,
            int minLevel,
            List<Integer> jobs,
            List<AgentQuestItemDemandIndex.Prerequisite> prerequisites,
            boolean autonomous,
            String disposition) {
        return new AgentQuestItemDemandIndex.QuestDemand(
                questId, "Quest " + questId, count, minLevel, null,
                jobs, prerequisites, autonomous, disposition);
    }
}
