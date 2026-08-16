package server.agents.economy.integration.cosmic;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.economy.market.FreeMarketPhysicalGateway;
import server.agents.economy.market.PrivateTradeArrangement;
import server.agents.economy.persistence.StallOfferStore;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.social.TradeExecutionGateway;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class CosmicPrivateTradeArrangementServiceTest {
    @Test
    void settlesOnlyAfterPhysicalApproachAndExactTradeValidation() {
        UUID run = UUID.randomUUID();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        Character buyer = mock(Character.class);
        Character seller = mock(Character.class);
        when(buyer.getMeso()).thenReturn(300_000);
        when(seller.getId()).thenReturn(202);
        EconomyAgentProfile buyerProfile = profile("buyer");
        EconomyAgentProfile sellerProfile = profile("seller");
        PrivateTradeArrangement agreement = new PrivateTradeArrangement(UUID.randomUUID(), run,
                UUID.randomUUID(), "buyer", "seller", "stall-1", "stall-1:3", 910000001,
                1302013, "exact-kfan", 1, 250_000, at.minusSeconds(1), at.plusSeconds(600),
                PrivateTradeArrangement.Status.PENDING_MEETUP);
        StallOfferStore store = mock(StallOfferStore.class);
        when(store.pendingArrangementForBuyer(run, "buyer", at)).thenReturn(Optional.of(agreement));
        EconomyParticipantRegistry participants = mock(EconomyParticipantRegistry.class);
        when(participants.byLogicalId("seller")).thenReturn(Optional.of(
                new CosmicPublicTradeNegotiator.Participant(seller, sellerProfile)));
        FreeMarketPhysicalGateway physical = mock(FreeMarketPhysicalGateway.class);
        var target = new FreeMarketPhysicalGateway.StallTarget(77, 202, 910000001, 10, 10);
        when(physical.requestRoom(buyer, 910000001)).thenReturn(FreeMarketPhysicalGateway.ActionStatus.ARRIVED);
        when(physical.visibleStalls(buyer)).thenReturn(List.of(target));
        when(physical.requestApproach(buyer, target)).thenReturn(FreeMarketPhysicalGateway.ActionStatus.ARRIVED);
        CosmicMarketSellerGateway sellerGateway = mock(CosmicMarketSellerGateway.class);
        when(sellerGateway.close(seller, "ACCEPTED_OFFER_EXACT_TRADE")).thenReturn(true);
        CosmicNegotiatedTradeExecutor trades = mock(CosmicNegotiatedTradeExecutor.class);
        when(trades.executeExactItem(agreement.arrangementId().toString(), "buyer", 250_000,
                "seller", 1302013, "exact-kfan", 1)).thenReturn(
                new TradeExecutionGateway.Result(true, "tx-exact", "settled"));

        var result = new CosmicPrivateTradeArrangementService(run, store, participants, physical,
                sellerGateway, trades).progress(buyer, buyerProfile, at);

        assertTrue(result.completed());
        assertFalse(result.externalActionPending());
        verify(store).resolveArrangement(agreement.arrangementId(),
                PrivateTradeArrangement.Status.EXECUTED, at, "tx-exact", "settled");
    }

    private static EconomyAgentProfile profile(String id) {
        return new EconomyAgentProfile(id, "BEGINNER", .5, .5, .5, .5, .5, .5,
                24, .5, .5);
    }
}
