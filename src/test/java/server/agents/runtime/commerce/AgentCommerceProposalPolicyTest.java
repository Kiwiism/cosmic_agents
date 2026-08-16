package server.agents.runtime.commerce;

import org.junit.jupiter.api.Test;
import server.agents.economy.session.CommerceParticipant;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCommerceProposalPolicyTest {
    @Test
    void proposesCommerceForMarketableInventoryWithoutStartingIt() {
        AgentCommerceProposal proposal = new AgentCommerceProposalPolicy().evaluate(request(),
                new AgentCommerceNeedSnapshot(.72d, 4, false, false,
                        false, 1_000L, true));

        assertTrue(proposal.worldProposal().eligible());
        assertTrue(proposal.worldProposal().evidence().contains("marketable=4"));
    }

    @Test
    void failsClosedWhenCommerceHasNoSessionCapacity() {
        AgentCommerceProposal proposal = new AgentCommerceProposalPolicy().evaluate(request(),
                new AgentCommerceNeedSnapshot(.95d, 8, true, true,
                        true, 1_000L, false));

        assertFalse(proposal.worldProposal().eligible());
    }

    private static AgentCommerceVisitRequest request() {
        return new AgentCommerceVisitRequest("proposal-1", "inventory",
                new CommerceParticipant("agent-1", "thief", .5, .5, .5, .5,
                        .5, .5, 24, .5, .5),
                AgentCommerceVisitRequest.Purpose.SELL_INVENTORY,
                30_000L, 5_000L, Map.of());
    }
}
