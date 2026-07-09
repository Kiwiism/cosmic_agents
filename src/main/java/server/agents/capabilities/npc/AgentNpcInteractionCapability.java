package server.agents.capabilities.npc;

import server.agents.capabilities.AgentCapability;
import server.agents.integration.NpcGateway;

public final class AgentNpcInteractionCapability implements AgentCapability {
    private final AgentNpcInteractionValidator validator;
    private final NpcGateway gateway;

    public AgentNpcInteractionCapability() {
        this(new AgentNpcInteractionValidator(), null);
    }

    public AgentNpcInteractionCapability(AgentNpcInteractionValidator validator, NpcGateway gateway) {
        this.validator = validator;
        this.gateway = gateway;
    }

    public AgentNpcInteractionResult plan(AgentNpcInteractionRequest request) {
        return validator.validate(request);
    }

    public AgentNpcInteractionResult execute(AgentNpcInteractionRequest request) {
        AgentNpcInteractionResult plan = plan(request);
        if (gateway == null || plan.status() != server.agents.capabilities.AgentCapabilityStatus.NOT_READY) {
            return plan;
        }
        return gateway.executeNpcInteraction(request, plan);
    }
}
