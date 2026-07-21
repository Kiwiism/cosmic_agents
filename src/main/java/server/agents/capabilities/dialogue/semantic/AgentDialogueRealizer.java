package server.agents.capabilities.dialogue.semantic;

import server.agents.personality.AgentPersonalityProfile;

public interface AgentDialogueRealizer {
    String realize(AgentSemanticDialogueAct act, AgentPersonalityProfile profile);
}
