package server.agents.integration;

import server.agents.capabilities.quest.AgentQuestCapabilityRequest;
import server.agents.capabilities.quest.AgentQuestCapabilityResult;

public interface QuestGateway {
    AgentQuestCapabilityResult startQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);

    AgentQuestCapabilityResult completeQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);

    AgentQuestCapabilityResult forfeitQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);

    AgentQuestCapabilityResult resetQuest(AgentQuestCapabilityRequest request, AgentQuestCapabilityResult plan);
}

