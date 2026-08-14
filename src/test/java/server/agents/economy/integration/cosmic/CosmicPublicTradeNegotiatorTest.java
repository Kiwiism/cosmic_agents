package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.market.MarketObservation;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.agents.economy.persistence.NegotiationEvidenceStore;
import server.agents.economy.scenario.EconomyAgentProfile;
import server.agents.economy.social.TradeExecutionGateway;
import server.maps.MapleMap;
import server.maps.PlayerShop;

import java.awt.Point;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CosmicPublicTradeNegotiatorTest {
    private final UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private Character buyer;
    private Character seller;
    private EconomyEvidenceJournal journal;
    private NegotiationEvidenceStore sessions;
    private TradeExecutionGateway trades;
    private CosmicPublicTradeNegotiator.StallCloser closer;
    private CosmicPublicTradeNegotiator.PublicChatGateway chat;

    @BeforeEach
    void setUp() {
        buyer = mock(Character.class); seller = mock(Character.class);
        MapleMap room = mock(MapleMap.class); PlayerShop shop = mock(PlayerShop.class);
        when(buyer.getMap()).thenReturn(room); when(seller.getMap()).thenReturn(room);
        when(buyer.getMapId()).thenReturn(910000001); when(seller.getMapId()).thenReturn(910000001);
        when(buyer.getPosition()).thenReturn(new Point(100, 0));
        when(seller.getPosition()).thenReturn(new Point(120, 0));
        when(seller.getPlayerShop()).thenReturn(shop); when(shop.isOpen()).thenReturn(true);
        journal = mock(EconomyEvidenceJournal.class); sessions = mock(NegotiationEvidenceStore.class);
        trades = mock(TradeExecutionGateway.class); closer = mock(CosmicPublicTradeNegotiator.StallCloser.class);
        chat = mock(CosmicPublicTradeNegotiator.PublicChatGateway.class);
    }

    @Test
    void publiclyAcceptsClosesRealStallAndUsesRealTradeGateway() {
        when(closer.close(seller, "NEGOTIATED_DIRECT_TRADE")).thenReturn(true);
        when(trades.execute(anyString(), eq("buyer"), any(), eq("seller"), any()))
                .thenReturn(new TradeExecutionGateway.Result(true, "tx-1", "committed"));
        CosmicPublicTradeNegotiator negotiator = negotiator(profile("seller", 1));

        AutonomousFreeMarketBehavior.NegotiationBehavior.Result result = negotiator.attempt(
                buyer, profile("buyer", 0), List.of(need(950)), List.of(observation()), now);

        assertTrue(result.attempted()); assertTrue(result.success()); assertEquals("EXECUTED", result.outcome());
        verify(closer).close(seller, "NEGOTIATED_DIRECT_TRADE");
        verify(trades).execute(anyString(), eq("buyer"), any(), eq("seller"), any());
        verify(chat, times(4)).broadcast(any(), anyString());
        verify(journal, times(4)).appendSocial(any());
        verify(sessions).record(eq(runId), eq(4000000), eq(now), eq(now), any(), eq("tx-1"));
    }

    @Test
    void rejectsBelowSellerReserveWithoutClosingStallOrMutatingHoldings() {
        CosmicPublicTradeNegotiator negotiator = negotiator(profile("seller", 0));

        var result = negotiator.attempt(buyer, profile("buyer", 0), List.of(need(950)),
                List.of(observation()), now);

        assertTrue(result.attempted()); assertFalse(result.success()); assertEquals("REJECTED", result.outcome());
        verifyNoInteractions(closer, trades);
        verify(chat, times(3)).broadcast(any(), anyString());
        verify(sessions).record(eq(runId), eq(4000000), eq(now), eq(now), any(), isNull());
    }

    @Test
    void publiclyCounteroffersAndSettlesItemForItemPlusMesoBarter() {
        Inventory buyerEtc = mock(Inventory.class);
        when(buyer.getInventory(InventoryType.ETC)).thenReturn(buyerEtc);
        when(buyerEtc.countById(4000001)).thenReturn(1);
        when(closer.close(seller, "NEGOTIATED_DIRECT_TRADE")).thenReturn(true);
        when(trades.execute(anyString(), eq("buyer"), any(), eq("seller"), any()))
                .thenReturn(new TradeExecutionGateway.Result(true, "tx-barter", "committed"));
        EconomyAgentProfile sellerProfile = profile("seller", 0);
        CosmicPublicTradeNegotiator.Participant participant =
                new CosmicPublicTradeNegotiator.Participant(seller, sellerProfile);
        AgentNeed sellerNeed = new AgentNeed(4000001, 0, 1, .8,
                EconomicReason.QUEST_REQUIREMENT, now, 200, java.util.Set.of(),
                java.util.Set.of(), "seller accepted quest");
        CosmicPublicTradeNegotiator negotiator = new CosmicPublicTradeNegotiator(runId,
                characterId -> characterId == 2 ? Optional.of(participant) : Optional.empty(),
                closer, trades, journal, sessions, (item, quantity) -> 100, chat, true,
                (character, profile, at) -> List.of(sellerNeed), Duration.ofMinutes(2), 120);

        var result = negotiator.attempt(buyer, profile("buyer", 0), List.of(need(950)),
                List.of(observation()), now);

        assertTrue(result.success()); assertEquals(4000001, result.evidence().get("barterItemId"));
        org.mockito.ArgumentCaptor<server.agents.economy.social.TradeOffer> buyerOffer =
                org.mockito.ArgumentCaptor.forClass(server.agents.economy.social.TradeOffer.class);
        verify(trades).execute(anyString(), eq("buyer"), buyerOffer.capture(), eq("seller"), any());
        assertEquals(Map.of(4000001, 1), buyerOffer.getValue().items());
        assertEquals(850, buyerOffer.getValue().mesos());
        verify(chat, times(5)).broadcast(any(), anyString());
    }

    private CosmicPublicTradeNegotiator negotiator(EconomyAgentProfile sellerProfile) {
        CosmicPublicTradeNegotiator.Participant participant =
                new CosmicPublicTradeNegotiator.Participant(seller, sellerProfile);
        return new CosmicPublicTradeNegotiator(runId,
                characterId -> characterId == 2 ? Optional.of(participant) : Optional.empty(),
                closer, trades, journal, sessions, (item, quantity) -> 100, chat,
                Duration.ofMinutes(2), 120);
    }

    private AgentNeed need(long wtp) {
        return new AgentNeed(4000000, 0, 1, .8, EconomicReason.QUEST_REQUIREMENT, now,
                wtp, java.util.Set.of(), java.util.Set.of(), "accepted quest");
    }

    private MarketObservation observation() {
        return new MarketObservation("00000000-0000-0000-0000-000000000011", "buyer", now,
                910000001, "2", "escrow:0", 4000000, 1, 1000, 1, 1, 1000,
                "fp", Map.of(), MarketObservation.State.LISTED);
    }

    private static EconomyAgentProfile profile(String id, double negotiationAggressiveness) {
        return new EconomyAgentProfile(id, "warrior", .5, .5, .5, .5, .5, .5,
                24, negotiationAggressiveness, .5);
    }
}
