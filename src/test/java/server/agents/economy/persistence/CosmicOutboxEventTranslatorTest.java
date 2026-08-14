package server.agents.economy.persistence;

import org.junit.jupiter.api.Test;
import server.agents.economy.domain.*;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CosmicOutboxEventTranslatorTest {
    private static final UUID RUN = UUID.randomUUID();

    @Test
    void npcPurchaseCreatesProvenanceAndBalancedSourceSinkPostings() {
        CosmicOutboxEventTranslator translator = translator((run, account, item, fingerprint, quantity) -> List.of());
        CosmicOutboxRecord receipt = receipt("SHOP_BUY", null,
                "shop=1 npc=1012004 action=buy item=2000000 quantity=5 mesos=250",
                payload(participant(10, 1_000, 750, List.of(item(2000000, "USE", "stack", 0, 5)))));

        var plan = translator.translate(receipt);

        assertEquals(EconomicEventKind.NPC_PURCHASE, plan.event().kind());
        assertEquals(1, plan.createdLots().size());
        assertEquals(5, plan.createdLots().getFirst().quantity());
        assertEquals(0, balance(plan.event(), AssetKey.MESO));
        assertEquals(0, balance(plan.event(), AssetKey.item(2000000)));
        assertTrue(plan.event().postings().stream().anyMatch(p -> p.account().type().equals("SOURCE")
                && p.quantity() == -5));
    }

    @Test
    void stallListingMovesOwnedLotIntoEscrowAndSaleNeverDebitsSellerInventory() {
        CosmicOutboxEventTranslator.LotResolver lotResolver = (run, account, item, fingerprint, quantity) ->
                List.of(new CosmicOutboxEventTranslator.LotSlice("farm-lot", quantity));
        CosmicOutboxEventTranslator translator = translator(lotResolver);
        var listing = receipt("PLAYER_SHOP_LIST", null,
                "escrow=esc-1 map=910000001 listings=1", payload(participant(10, 1_000, 1_000,
                        List.of(item(4000000, "ETC", "stack", 10, 0)))));
        var listed = translator.translate(listing).event();
        assertTrue(listed.postings().stream().anyMatch(p -> p.account().equals(LedgerAccount.escrow("esc-1"))
                && p.quantity() == 10 && p.lotId().equals("farm-lot")));

        var sale = new CosmicOutboxRecord(UUID.randomUUID(), UUID.randomUUID().toString(),
                "PLAYER_SHOP_SALE", 20, 10,
                "shop=3 item=4000000 quantity=10 bundles=1 gross=1000 buyerTax=20 sellerTax=30 fee=50 escrow=esc-1",
                payload(new CosmicOutboxEventTranslator.ParticipantDelta(20, 2_000, 980, -1_020,
                                20, 20, 0, 0, List.of(item(4000000, "ETC", "stack", 0, 10))),
                        participant(10, 1_000, 1_970, List.of())), RUN, Instant.EPOCH, "d", null,
                "c".repeat(64), "catalog", "upgrade", true, true, Instant.EPOCH);
        EconomicEvent sold = translator.translate(sale).event();

        assertEquals(EconomicEventKind.STALL_SALE, sold.kind());
        assertTrue(sold.postings().stream().anyMatch(p -> p.account().equals(LedgerAccount.escrow("esc-1"))
                && p.asset().equals(AssetKey.item(4000000)) && p.quantity() == -10));
        assertFalse(sold.postings().stream().anyMatch(p -> p.account().equals(LedgerAccount.agent("agent-10"))
                && p.asset().equals(AssetKey.item(4000000))));
        assertEquals(-1_020, sold.postings().stream().filter(p -> p.account().equals(LedgerAccount.agent("agent-20"))
                && p.asset().equals(AssetKey.MESO)).mapToLong(LedgerPosting::quantity).sum());
    }

    @Test
    void refusesUnattributedReceiptsInsteadOfInventingSimulationFacts() {
        CosmicOutboxRecord receipt = new CosmicOutboxRecord(UUID.randomUUID(), "k", "SHOP_BUY",
                10, null, "npc=1", "{\"participants\":[]}", null, null,
                null, null, null, null, null, false, false, Instant.EPOCH);
        assertThrows(CosmicOutboxEventTranslator.EvidenceMismatchException.class,
                () -> translator((run, account, item, fingerprint, quantity) -> List.of()).translate(receipt));
    }

    @Test
    void directTradeUsesIncomingSettlementEvidenceAndNativeFees() {
        CosmicOutboxEventTranslator translator = translator((run, account, item, fingerprint, quantity) ->
                List.of(new CosmicOutboxEventTranslator.LotSlice(account.ownerId() + ":lot", quantity)));
        var first = new CosmicOutboxEventTranslator.ParticipantDelta(10, 1_000, 100_200, 99_200,
                20, 20, 0, 0, List.of(item(4000001, "ETC", "b", 0, 2)));
        var second = new CosmicOutboxEventTranslator.ParticipantDelta(20, 1_000, 5_821_000,
                5_820_000, 20, 20, 0, 0, List.of(item(4000000, "ETC", "a", 0, 3)));
        CosmicOutboxRecord receipt = new CosmicOutboxRecord(UUID.randomUUID(), "trade-key", "PLAYER_TRADE",
                10, 20, "firstMesos=100000 secondMesos=6000000 firstItems=1 secondItems=1",
                payload(first, second), RUN, Instant.EPOCH, "d", null, "c".repeat(64), "catalog",
                "negotiated", true, true, Instant.EPOCH);

        EconomicEvent event = translator.translate(receipt).event();

        assertEquals(EconomicEventKind.DIRECT_TRADE, event.kind());
        assertEquals(0, balance(event, AssetKey.MESO));
        assertTrue(event.postings().stream().anyMatch(p -> p.account().equals(LedgerAccount.agent("agent-20"))
                && p.asset().equals(AssetKey.item(4000001)) && p.quantity() == -2));
        assertTrue(event.postings().stream().anyMatch(p -> p.account().equals(LedgerAccount.agent("agent-10"))
                && p.asset().equals(AssetKey.item(4000000)) && p.quantity() == -3));
    }

    private static CosmicOutboxEventTranslator translator(CosmicOutboxEventTranslator.LotResolver lots) {
        return new CosmicOutboxEventTranslator((run, character, agent) -> {
            String id = agent ? "agent-" + character : "character-" + character;
            return new CosmicOutboxEventTranslator.Participant(id,
                    agent ? LedgerAccount.agent(id) : new LedgerAccount("HUMAN", id));
        }, lots, (run, activity) -> { throw new AssertionError("not a farm test"); });
    }

    private static CosmicOutboxRecord receipt(String kind, Integer secondary, String summary, String payload) {
        return new CosmicOutboxRecord(UUID.randomUUID(), UUID.randomUUID().toString(), kind, 10,
                secondary, summary, payload, RUN, Instant.EPOCH, "decision", null,
                "c".repeat(64), "catalog", "reason", true, secondary != null, Instant.EPOCH);
    }

    private static CosmicOutboxEventTranslator.ParticipantDelta participant(int id, int before, int after,
                                                                              List<CosmicOutboxEventTranslator.ItemDelta> items) {
        return new CosmicOutboxEventTranslator.ParticipantDelta(id, before, after, after - before,
                20, 20, 0, 0, items);
    }

    private static CosmicOutboxEventTranslator.ItemDelta item(int id, String type, String fingerprint,
                                                               int before, int after) {
        return new CosmicOutboxEventTranslator.ItemDelta(id, type, fingerprint, before, after,
                after - before, Map.of("owner", ""));
    }

    private static String payload(CosmicOutboxEventTranslator.ParticipantDelta... participants) {
        try { return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                new CosmicOutboxEventTranslator.MutationPayload(List.of(participants)) ); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new AssertionError(e); }
    }

    private static long balance(EconomicEvent event, AssetKey asset) {
        return event.postings().stream().filter(p -> p.asset().equals(asset))
                .mapToLong(LedgerPosting::quantity).sum();
    }
}
