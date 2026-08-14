package server.agents.economy.decision;

import org.junit.jupiter.api.Test;
import server.agents.economy.market.EconomicReason;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AgentDemandPortfolioServiceTest {
    @Test
    void createsQuestAndScrollDemandOnlyFromActualEligibleOwnedState() {
        var state = new AgentDemandPortfolioService.AgentEconomicState(25, List.of(),
                List.of(new AgentDemandPortfolioService.QuestObjective(2055, 4000030, 100,
                        20, 25, true, false, true, .9, 50_000)), List.of(),
                List.of(new AgentDemandPortfolioService.ScrollProject(2041019, 1102000,
                        true, 5, 0, 5, 12, .6, 100_000, Set.of(2041020))), List.of());

        List<AgentNeed> needs = new AgentDemandPortfolioService().build(state, Instant.EPOCH);

        assertEquals(2, needs.size());
        assertEquals(75, needs.stream().filter(n -> n.reason() == EconomicReason.QUEST_REQUIREMENT)
                .findFirst().orElseThrow().deficit());
        assertTrue(needs.stream().anyMatch(n -> n.reason() == EconomicReason.SCROLL_UPGRADE
                && n.complements().contains(1102000)));
    }

    @Test
    void rejectsFutureQuestHoardingAndScrollsWithoutOwnedEquipment() {
        var state = new AgentDemandPortfolioService.AgentEconomicState(25, List.of(),
                List.of(new AgentDemandPortfolioService.QuestObjective(2055, 4000030, 100,
                        0, 0, false, false, true, .9, 50_000)), List.of(),
                List.of(new AgentDemandPortfolioService.ScrollProject(2041019, 1102000,
                        false, 5, 0, 5, 12, .6, 100_000, Set.of())), List.of());
        assertTrue(new AgentDemandPortfolioService().build(state, Instant.EPOCH).isEmpty());
    }
}
