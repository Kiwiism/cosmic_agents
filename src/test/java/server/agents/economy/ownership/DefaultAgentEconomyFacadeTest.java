package server.agents.economy.ownership;

import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.ItemCategory;
import server.agents.economy.catalog.ItemFact;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAgentEconomyFacadeTest {
    private static final UUID RUN = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final CapturingJournal journal = new CapturingJournal();
    private final EconomyCatalog catalog = mock(EconomyCatalog.class);
    private final DefaultAgentEconomyFacade facade = new DefaultAgentEconomyFacade(
            RUN, new ShadowEconomyEvaluator(catalog), journal);

    @Test
    void protectsInventoryUntilFmAppraisalThenAllowsOneExactAuthorizedSale() {
        InventoryItemRef ref = new InventoryItemRef("ETC", (short) 3, 4000000, "fingerprint");
        InventorySnapshot snapshot = snapshot(ref, 12, "revision-a");

        facade.protectAtFreeMarketEntry("agent-1", snapshot, NOW);
        var before = facade.claimNpcSale("agent-1", snapshot, ref, 10,
                "WORLD_NPC", NOW.plusSeconds(1));
        assertFalse(before.allowed());
        assertEquals("NO_ACTIVE_AUTHORIZATION", before.reason());

        facade.appraise("agent-1", snapshot, List.of(new LegacyDispositionProposal(ref, 10,
                LegacyDispositionProposal.Action.SELL_TO_NPC, "legacy trash", "NPC_ANYWHERE")),
                NOW.plusSeconds(2));
        var allowed = facade.claimNpcSale("agent-1", snapshot, ref, 10,
                "FM_REMOTE_NPC", NOW.plusSeconds(3));
        assertTrue(allowed.allowed());
        assertNotNull(allowed.authorizationId());
        assertFalse(facade.claimNpcSale("agent-1", snapshot, ref, 1,
                "FM_REMOTE_NPC", NOW.plusSeconds(4)).allowed());
        assertEquals(3, journal.guardEvents.size());
    }

    @Test
    void rejectsChangedPhysicalItemEvenWhenSlotAndItemIdMatch() {
        InventoryItemRef reviewed = new InventoryItemRef("EQUIP", (short) 2, 1002000, "old-roll");
        InventorySnapshot original = snapshot(reviewed, 1, "revision-a");
        facade.appraise("agent-1", original, List.of(new LegacyDispositionProposal(reviewed, 1,
                LegacyDispositionProposal.Action.SELL_TO_NPC, "legacy trash", "NPC_ANYWHERE")), NOW);

        InventoryItemRef changed = new InventoryItemRef("EQUIP", (short) 2, 1002000, "new-roll");
        var permit = facade.claimNpcSale("agent-1", snapshot(changed, 1, "revision-b"), reviewed,
                1, "WORLD_NPC", NOW.plusSeconds(1));

        assertFalse(permit.allowed());
        assertEquals("STALE_OR_MISSING_ITEM", permit.reason());
    }

    @Test
    void journalsShadowDisagreementWithoutChangingLegacyBehavior() {
        InventoryItemRef ref = new InventoryItemRef("EQUIP", (short) 1, 1002000, "roll");
        when(catalog.item(1002000)).thenReturn(Optional.of(new ItemFact(1002000, "Hat", 10,
                10, 1, Set.of(ItemCategory.EQUIPMENT), Map.of())));

        InventoryReview review = facade.appraise("agent-1", snapshot(ref, 1, "revision"),
                List.of(new LegacyDispositionProposal(ref, 1,
                        LegacyDispositionProposal.Action.SELL_TO_NPC, "legacy trash", "NPC_ANYWHERE")), NOW);

        InventoryDispositionDecision decision = review.decisions().getFirst();
        assertEquals(InventoryDispositionDecision.Disposition.NPC_SALE_AUTHORIZED,
                decision.disposition());
        assertTrue(decision.shadowDisagreement());
        assertEquals("KEEP_FOR_MARKET_REVIEW", decision.shadowAction());
    }

    @Test
    void reviewedValuableItemWithoutSaleProposalRemainsProtected() {
        InventoryItemRef ref = new InventoryItemRef("EQUIP", (short) 1, 1002000, "valuable-roll");
        InventorySnapshot snapshot = snapshot(ref, 1, "revision");

        InventoryReview review = facade.appraise("agent-1", snapshot, List.of(), NOW);

        assertEquals(InventoryDispositionDecision.Disposition.KEEP_REVIEWED,
                review.decisions().getFirst().disposition());
        assertTrue(review.authorizations().isEmpty());
        assertFalse(facade.claimNpcSale("agent-1", snapshot, ref, 1,
                "WORLD_NPC", NOW.plusSeconds(1)).allowed());
    }

    private static InventorySnapshot snapshot(InventoryItemRef ref, int quantity, String revision) {
        return new InventorySnapshot(42, revision,
                List.of(new InventoryItemSnapshot(ref, quantity, Map.of())));
    }

    private static final class CapturingJournal implements EconomyOwnershipJournal {
        private final List<InventoryReview> reviews = new ArrayList<>();
        private final List<String> guardEvents = new ArrayList<>();
        @Override public void appendReview(InventoryReview review) { reviews.add(review); }
        @Override public void markAuthorizationConsumed(UUID id, Instant at) { }
        @Override public void appendGuardEvent(UUID runId, String agentId, int characterId, Instant at,
                                               String action, InventoryItemRef item, int quantity,
                                               boolean allowed, String reason, UUID authorizationId) {
            guardEvents.add(reason);
        }
    }
}
