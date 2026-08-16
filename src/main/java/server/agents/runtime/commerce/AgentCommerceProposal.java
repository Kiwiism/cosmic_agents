package server.agents.runtime.commerce;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityProposal;

/** Commerce payload paired with the policy-free World Director proposal. */
public record AgentCommerceProposal(
        AgentCommerceVisitRequest visit,
        AgentWorldActivityProposal worldProposal) {
    public AgentCommerceProposal {
        if (visit == null || worldProposal == null
                || worldProposal.kind() != AgentActivityKind.COMMERCE) {
            throw new IllegalArgumentException("Commerce proposal is incomplete");
        }
    }
}
