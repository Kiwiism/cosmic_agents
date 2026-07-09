package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapability;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.integration.QuestGateway;

public final class AgentQuestStartCapability implements AgentCapability {
    private final AgentQuestRequirementValidator validator;
    private final QuestGateway gateway;

    public AgentQuestStartCapability() {
        this(new AgentQuestRequirementValidator(), null);
    }

    public AgentQuestStartCapability(AgentQuestRequirementValidator validator, QuestGateway gateway) {
        this.validator = validator;
        this.gateway = gateway;
    }

    public AgentQuestCapabilityResult plan(AgentQuestCapabilityRequest request) {
        return validator.validateStart(request);
    }

    public AgentQuestCapabilityResult execute(AgentQuestCapabilityRequest request) {
        AgentQuestCapabilityResult plan = plan(request);
        if (gateway == null || plan.status() != AgentCapabilityStatus.NOT_READY) {
            return plan;
        }
        return gateway.startQuest(request, plan);
    }
}
