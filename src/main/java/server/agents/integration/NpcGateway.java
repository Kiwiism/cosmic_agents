package server.agents.integration;

import server.agents.capabilities.npc.AgentNpcInteractionRequest;
import server.agents.capabilities.npc.AgentNpcInteractionResult;

public interface NpcGateway {
    AgentNpcInteractionResult executeNpcInteraction(AgentNpcInteractionRequest request,
            AgentNpcInteractionResult plan);
}

