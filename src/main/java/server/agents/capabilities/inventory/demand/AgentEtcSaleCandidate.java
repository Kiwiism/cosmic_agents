package server.agents.capabilities.inventory.demand;

import client.inventory.Item;

public record AgentEtcSaleCandidate(
        Item item,
        short quantity,
        AgentItemDispositionProposal proposal) {

    public AgentEtcSaleCandidate {
        if (item == null || quantity <= 0 || proposal == null
                || item.getItemId() != proposal.itemId()) {
            throw new IllegalArgumentException("valid ETC sale candidate is required");
        }
    }
}
