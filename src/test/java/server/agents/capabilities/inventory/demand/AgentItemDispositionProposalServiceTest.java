package server.agents.capabilities.inventory.demand;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentItemDispositionProposalServiceTest {
    private final AgentItemDispositionProposalService service =
            new AgentItemDispositionProposalService();

    @Test
    void sellsOnlySurplusBeyondAllQuestDemandAndExistingReservations() {
        AgentQuestItemDemandForecast.ItemForecast forecast = forecast(
                100,
                Map.of(
                        AgentQuestDemandCategory.ACTIVE, 20,
                        AgentQuestDemandCategory.WITHIN_5_LEVELS, 30));

        AgentItemDispositionProposal proposal =
                service.propose(forecast, 40, Map.of(), false, "catalog-r1");

        assertEquals(AgentItemDispositionProposal.Disposition.SELL_SAFE_SURPLUS,
                proposal.disposition());
        assertEquals(50, proposal.protectedQuantity());
        assertEquals(50, proposal.proposedQuantity());
        assertEquals("catalog-r1", proposal.catalogRevision());
        assertTrue(proposal.protectedQuantity() + proposal.proposedQuantity()
                <= proposal.ownedQuantity());
    }

    @Test
    void cohortTransferPrecedesStorageAndSale() {
        AgentItemDispositionProposal proposal = service.propose(
                forecast(25, Map.of(AgentQuestDemandCategory.ACTIVE, 5)),
                0, Map.of("PeerAgent", 8), true, "catalog-r1");

        assertEquals(AgentItemDispositionProposal.Disposition.TRANSFER_TO_COHORT,
                proposal.disposition());
        assertEquals(8, proposal.proposedQuantity());
        assertEquals("PeerAgent", proposal.target());
        assertEquals(7, proposal.precedence());
    }

    @Test
    void storagePrecedesSafeSaleWhenAvailable() {
        AgentItemDispositionProposal proposal = service.propose(
                forecast(25, Map.of()), 0, Map.of(), true, "catalog-r1");

        assertEquals(AgentItemDispositionProposal.Disposition.STORE,
                proposal.disposition());
        assertEquals(25, proposal.proposedQuantity());
        assertEquals(8, proposal.precedence());
    }

    private static AgentQuestItemDemandForecast.ItemForecast forecast(
            int owned,
            Map<AgentQuestDemandCategory, Integer> demand) {
        return new AgentQuestItemDemandForecast.ItemForecast(
                4_000_003, "Tree Branch", owned, demand, List.of());
    }
}
