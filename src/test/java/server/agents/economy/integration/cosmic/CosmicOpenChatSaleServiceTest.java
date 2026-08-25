package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.shop.AgentFreeMarketStallService;
import server.agents.economy.communication.EconomicIntent;
import server.agents.economy.decision.AgentNeed;
import server.agents.economy.market.EconomicReason;
import server.agents.economy.persistence.EconomyEvidenceJournal;
import server.agents.economy.scenario.EconomyEngineConfig;
import server.agents.economy.scenario.NamedRandomStreams;
import server.agents.economy.session.CommerceParticipant;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CosmicOpenChatSaleServiceTest {
    @Test
    void reservesOneRealBundlePublishesIntentAndAdvertisesOnSchedule() {
        UUID runId = UUID.randomUUID();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        Character seller = mock(Character.class);
        Inventory inventory = mock(Inventory.class);
        Item item = new Item(1302000, (short) 2, (short) 2);
        when(seller.getId()).thenReturn(77);
        when(seller.getMapId()).thenReturn(910000000);
        when(seller.getInventory(InventoryType.EQUIP)).thenReturn(inventory);
        when(inventory.getItem((short) 2)).thenReturn(item);

        CosmicAgentEconomyFacade economy = mock(CosmicAgentEconomyFacade.class);
        UUID intentId = UUID.randomUUID();
        when(economy.publishIntent(eq("seller"), eq(""), eq(EconomicIntent.Kind.SELL_INTEREST),
                eq(1302000), anyString(), eq(1), eq(400_000L), eq(910000000), anyString(),
                anyMap(), eq(at), eq(Duration.ofMinutes(10))))
                .thenReturn(new EconomicIntent(intentId, runId, "seller", "",
                        EconomicIntent.Kind.SELL_INTEREST, 1302000, "fingerprint", 1,
                        400_000, 910000000, "sale", java.util.Map.of(), at,
                        at.plus(Duration.ofMinutes(10)), EconomicIntent.Status.OPEN));
        List<String> chat = new ArrayList<>();
        CosmicOpenChatSaleService.ReservationGateway reservations =
                mock(CosmicOpenChatSaleService.ReservationGateway.class);
        EconomyParticipantRegistry participants = new EconomyParticipantRegistry(id -> null);
        participants.admitted(profile("seller"), seller);
        CosmicNegotiatedTradeExecutor executor = mock(CosmicNegotiatedTradeExecutor.class);
        CosmicOpenChatSaleService service = new CosmicOpenChatSaleService(runId, "cfg", "catalog",
                config(), new NamedRandomStreams(1), participants, economy,
                mock(EconomyEvidenceJournal.class), executor,
                reservations, (itemId, quantity) -> 100_000,
                (speaker, text) -> chat.add(text),
                (buyer, target) -> CosmicCounterpartyApproachService.Status.ASSIGNED);
        MarketSellerPlan plan = new MarketSellerPlan(List.of(), List.of(
                new AgentFreeMarketStallService.Listing(InventoryType.EQUIP,
                        (short) 2, (short) 1, (short) 2, 400_000)),
                910000001, "shop");

        var prepared = service.prepare(seller, profile("seller"), plan, at);

        assertTrue(prepared.selected());
        assertEquals(1302000, prepared.itemId());
        assertEquals(400_000, prepared.askMesos());
        assertEquals(360_000, prepared.reserveMesos());
        assertEquals(1, prepared.plan().stallListings().getFirst().bundles());
        verify(reservations).reserve(eq(seller), any(UUID.class), eq(1302000), eq(1),
                eq(Duration.ofMinutes(10)));
        assertTrue(chat.isEmpty());

        var active = service.progressSeller(seller, profile("seller"), at.plusSeconds(5));

        assertTrue(active.attempted());
        assertFalse(active.done());
        assertEquals(1, chat.size());
        assertEquals(1, service.views().getFirst().advertisements());

        Character buyer = mock(Character.class);
        when(buyer.getMapId()).thenReturn(910000000);
        when(buyer.getMeso()).thenReturn(1_000_000);
        AgentNeed need = new AgentNeed(1302000, 0, 1, 1, EconomicReason.EQUIPMENT_UPGRADE,
                at.plusSeconds(30), 400_000, java.util.Set.of(), java.util.Set.of(), "test");
        var approach = service.attemptPurchase(buyer, profile("buyer"), List.of(need), at.plusSeconds(6));

        assertEquals("APPROACH_ASSIGNED", approach.outcome());
        assertEquals(Boolean.TRUE, approach.evidence().get("externalActionPending"));
        verifyNoInteractions(executor);

        CosmicOpenChatSaleService.ReservationGateway restoredReservations =
                mock(CosmicOpenChatSaleService.ReservationGateway.class);
        CosmicOpenChatSaleService restored = new CosmicOpenChatSaleService(runId, "cfg", "catalog",
                config(), new NamedRandomStreams(1), new EconomyParticipantRegistry(id -> null), economy,
                mock(EconomyEvidenceJournal.class), mock(CosmicNegotiatedTradeExecutor.class),
                restoredReservations, (itemId, quantity) -> 100_000, (speaker, text) -> { });
        restored.restoreState(service.snapshotState());

        assertEquals(1, restored.views().size());
        restored.progressSeller(seller, profile("seller"), at.plusSeconds(6));
        verify(restoredReservations).reserve(eq(seller), any(UUID.class), eq(1302000), eq(1),
                eq(Duration.ofSeconds(594)));
        restored.shutdown();
        verify(restoredReservations).release(77);
        assertTrue(restored.views().isEmpty());
    }

    @Test
    void refusesOfferWhoseNpcOpportunityFloorExceedsAsk() {
        Character seller = mock(Character.class);
        Inventory inventory = mock(Inventory.class);
        Item item = new Item(1302000, (short) 2, (short) 1);
        when(seller.getId()).thenReturn(77);
        when(seller.getMapId()).thenReturn(910000000);
        when(seller.getInventory(InventoryType.EQUIP)).thenReturn(inventory);
        when(inventory.getItem((short) 2)).thenReturn(item);
        CosmicAgentEconomyFacade economy = mock(CosmicAgentEconomyFacade.class);
        CosmicOpenChatSaleService service = new CosmicOpenChatSaleService(UUID.randomUUID(), "cfg", "cat",
                config(), new NamedRandomStreams(1), new EconomyParticipantRegistry(id -> null), economy,
                mock(EconomyEvidenceJournal.class), mock(CosmicNegotiatedTradeExecutor.class),
                mock(CosmicOpenChatSaleService.ReservationGateway.class),
                (itemId, quantity) -> 500_000, (speaker, text) -> { });
        MarketSellerPlan plan = new MarketSellerPlan(List.of(), List.of(
                new AgentFreeMarketStallService.Listing(InventoryType.EQUIP,
                        (short) 2, (short) 1, (short) 1, 400_000)), 910000001, "shop");

        var prepared = service.prepare(seller, profile("seller"), plan,
                Instant.parse("2026-01-01T00:00:00Z"));

        assertFalse(prepared.selected());
        verifyNoInteractions(economy);
    }

    @Test
    void structuredOfferAndAcceptanceSettleOnlyAfterExactCosmicTradeCommits() {
        UUID runId = UUID.randomUUID();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        Character seller = mock(Character.class); Character buyer = mock(Character.class);
        Inventory inventory = mock(Inventory.class);
        when(seller.getId()).thenReturn(77); when(buyer.getId()).thenReturn(88);
        when(seller.getMapId()).thenReturn(910000000); when(buyer.getMapId()).thenReturn(910000000);
        when(buyer.getMeso()).thenReturn(1_000_000);
        when(seller.getInventory(InventoryType.EQUIP)).thenReturn(inventory);
        when(inventory.getItem((short) 2)).thenReturn(new Item(1302000, (short) 2, (short) 1));
        EconomyParticipantRegistry participants = new EconomyParticipantRegistry(id -> null);
        participants.admitted(profile("seller"), seller); participants.admitted(profile("buyer"), buyer);
        CosmicAgentEconomyFacade economy = mock(CosmicAgentEconomyFacade.class);
        AtomicInteger intents = new AtomicInteger();
        when(economy.publishIntent(anyString(), anyString(), any(), anyInt(), anyString(), anyInt(),
                anyLong(), anyInt(), anyString(), anyMap(), any(), any())).thenAnswer(call -> {
            String actor = call.getArgument(0); String counterparty = call.getArgument(1);
            EconomicIntent.Kind kind = call.getArgument(2); int itemId = call.getArgument(3);
            String fingerprint = call.getArgument(4); int quantity = call.getArgument(5);
            long mesos = call.getArgument(6); Integer map = call.getArgument(7);
            String text = call.getArgument(8); java.util.Map<String, Object> attributes = call.getArgument(9);
            Instant logicalAt = call.getArgument(10); Duration lifetime = call.getArgument(11);
            return new EconomicIntent(new UUID(0, intents.incrementAndGet()), runId, actor, counterparty,
                    kind, itemId, fingerprint, quantity, mesos, map, text, attributes, logicalAt,
                    logicalAt.plus(lifetime), EconomicIntent.Status.OPEN);
        });
        CosmicNegotiatedTradeExecutor executor = mock(CosmicNegotiatedTradeExecutor.class);
        when(executor.executeExactItem(anyString(), eq("buyer"), eq(400_000L), eq("seller"),
                eq(1302000), anyString(), eq(1))).thenReturn(
                new server.agents.economy.social.TradeExecutionGateway.Result(true, "trade-1", "settled"));
        CosmicOpenChatSaleService.ReservationGateway reservations =
                mock(CosmicOpenChatSaleService.ReservationGateway.class);
        CosmicOpenChatSaleService service = new CosmicOpenChatSaleService(runId, "cfg", "catalog",
                config(), new NamedRandomStreams(1), participants, economy,
                mock(EconomyEvidenceJournal.class), executor, reservations,
                (itemId, quantity) -> 100_000, (speaker, text) -> { },
                (shopper, target) -> CosmicCounterpartyApproachService.Status.ARRIVED);
        MarketSellerPlan plan = new MarketSellerPlan(List.of(), List.of(
                new AgentFreeMarketStallService.Listing(InventoryType.EQUIP,
                        (short) 2, (short) 1, (short) 1, 400_000)), 910000001, "shop");
        service.prepare(seller, profile("seller"), plan, at);
        service.progressSeller(seller, profile("seller"), at.plusSeconds(5));
        AgentNeed need = new AgentNeed(1302000, 0, 1, 1, EconomicReason.EQUIPMENT_UPGRADE,
                at.plusSeconds(30), 400_000, java.util.Set.of(), java.util.Set.of(), "test");

        var result = service.attemptPurchase(buyer, profile("buyer"), List.of(need), at.plusSeconds(6));

        assertTrue(result.sold()); assertTrue(result.done()); assertEquals("AGENT_TRADE_SETTLED", result.outcome());
        verify(economy, never()).resolveIntent(anyString(), any(), eq(EconomicIntent.Status.ACCEPTED),
                any(), anyString());
        verify(economy, times(3)).resolveIntent(anyString(), any(), eq(EconomicIntent.Status.SETTLED),
                any(), anyString());
        verify(reservations).release(77);
        assertTrue(service.views().isEmpty());
    }

    private static EconomyEngineConfig.OpenChatSelling config() {
        EconomyEngineConfig.OpenChatSelling value = new EconomyEngineConfig.OpenChatSelling();
        value.enabled = true;
        value.eligibleAgentRatio = 1;
        value.maximumActiveOffersPerAgent = 1;
        value.maximumActiveOffersPerRoom = 8;
        value.minimumNpcPremiumBasisPoints = 1000;
        value.maximumNegotiatedDiscountBasisPoints = 1000;
        value.initialAdvertisementDelay = "PT5S";
        value.minimumRepeatDelay = "PT1M";
        value.maximumRepeatDelay = "PT3M";
        value.maximumAdvertisements = 3;
        value.offerLifetime = "PT10M";
        value.negotiationTimeout = "PT2M";
        value.humanTradeEnabled = true;
        value.agentTradeEnabled = true;
        value.allowedMaps = List.of(910000000);
        value.flavorTemplate = "S> {item_stats} {item_name} {ask} meso, trade me";
        return value;
    }

    private static CommerceParticipant profile(String id) {
        return new CommerceParticipant(id, "warrior", .5, .5, .5, .5,
                .5, .5, 24, .5, .5);
    }
}
