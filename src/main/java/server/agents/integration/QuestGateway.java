package server.agents.integration;

import server.agents.capabilities.quest.AgentQuestCapabilityRequest;
import server.agents.capabilities.quest.AgentQuestCapabilityResult;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Quest capability mutations are scoped to the owning Agent and authoritative quest APIs.")
public interface QuestGateway {
    AgentQuestCapabilityResult startQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);

    AgentQuestCapabilityResult completeQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);

    AgentQuestCapabilityResult forfeitQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);

    AgentQuestCapabilityResult resetQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);
}

