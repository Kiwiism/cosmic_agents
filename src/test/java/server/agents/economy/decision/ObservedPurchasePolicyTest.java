package server.agents.economy.decision;

import org.junit.jupiter.api.Test;
import server.agents.economy.integration.cosmic.CosmicMarketObservationService;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.session.CommerceParticipant;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ObservedPurchasePolicyTest {
    @Test
    void buysOnlyObservedAffordableNeedWithinLiquidityBudget() {
        var offer = new CosmicMarketObservationService.ObservedOffer(1, 0,
                new MarketObservation("o", "a", Instant.EPOCH, 910000001, "s", "l",
                        4000000, 20, 100, 10, 2, 1_000, MarketObservation.State.LISTED));
        AgentNeed need = new AgentNeed(4000000, 0, 15, .8, EconomicReason.QUEST_REQUIREMENT,
                Instant.EPOCH, 2_500, Set.of(), Set.of(), "accepted quest");
        CommerceParticipant profile = new CommerceParticipant("a", "warrior", .5, .5,
                .2, .5, .5, .5, 24, .5, .2);

        var decision = new ObservedPurchasePolicy().choose(List.of(offer), List.of(need), profile, 10_000);

        assertTrue(decision.isPresent());
        assertEquals(2, decision.orElseThrow().bundles());
        assertEquals(2_000, decision.orElseThrow().totalPrice());
    }

    @Test
    void cannotUseUnobservedGlobalPriceOrSpendLiquidityReserve() {
        AgentNeed need = new AgentNeed(4000000, 0, 1, 1, EconomicReason.QUEST_REQUIREMENT,
                Instant.EPOCH, 10_000, Set.of(), Set.of(), "accepted quest");
        CommerceParticipant profile = new CommerceParticipant("a", "warrior", .5, .5,
                .9, .5, .5, .5, 24, .5, .2);
        assertTrue(new ObservedPurchasePolicy().choose(List.of(), List.of(need), profile, 10_000).isEmpty());
    }
}
