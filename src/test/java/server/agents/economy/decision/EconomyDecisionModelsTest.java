package server.agents.economy.decision;

import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.ItemCategory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EconomyDecisionModelsTest {
    @Test
    void questDemandAppearsOnlyWhenPopulationReachesEligibility() {
        var forecast = new QuestDemandForecaster().forecast(
                List.of(new QuestDemandForecaster.AgentCohort(24, "warrior", 10),
                        new QuestDemandForecaster.AgentCohort(25, "thief", 8)),
                List.of(new QuestDemandForecaster.QuestRequirement(2055, 4000030, 100,
                        25, 200, Set.of(), 0.9)));
        assertEquals(800, forecast.getFirst().demandedQuantity());
        assertTrue(forecast.getFirst().evidence().contains("eligibleAgents=8"));
    }

    @Test
    void scrollValueUsesStatsAndDestructionRiskRatherThanItemId() {
        var scroll = new server.agents.economy.catalog.ItemFact(2041019, "Cape DEX", 1, null,
                100, Set.of(ItemCategory.EQUIP_SCROLL), Map.of("success", 60, "DEX", 2));
        double utility = new ItemUtilityModel().expectedScrollUtility(scroll,
                Map.of("DEX", 5d), 100, 1);
        assertTrue(utility > 0);
    }

    @Test
    void keepsNeededItemsAndUsesNpcAsRealFloor() {
        ItemDispositionPolicy policy = new ItemDispositionPolicy();
        assertEquals(ItemDispositionPolicy.Action.KEEP,
                policy.decide(new ItemDispositionPolicy.Input(10, 20, 100, 5, 5, .5, false)).action());
        assertEquals(ItemDispositionPolicy.Action.SELL_TO_NPC,
                policy.decide(new ItemDispositionPolicy.Input(0, 20, 21, 1, 5, .5, true)).action());
    }
}
