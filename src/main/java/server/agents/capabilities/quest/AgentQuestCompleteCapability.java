package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapability;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.integration.QuestGateway;

public final class AgentQuestCompleteCapability implements AgentCapability {
    private final AgentQuestRequirementValidator validator;
    private final QuestGateway gateway;

    public AgentQuestCompleteCapability() {
        this(new AgentQuestRequirementValidator(), null);
    }

    public AgentQuestCompleteCapability(AgentQuestRequirementValidator validator, QuestGateway gateway) {
        this.validator = validator;
        this.gateway = gateway;
    }

    public AgentQuestCapabilityResult plan(AgentQuestCapabilityRequest request) {
        return validator.validateComplete(request);
    }

    public AgentQuestCapabilityResult execute(AgentQuestCapabilityRequest request) {
        AgentQuestCapabilityResult plan = plan(request);
        if (gateway == null || plan.status() != AgentCapabilityStatus.NOT_READY) {
            return plan;
        }
        return gateway.completeQuest(request, plan);
    }
}
