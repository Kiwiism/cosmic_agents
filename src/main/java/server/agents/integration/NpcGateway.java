package server.agents.integration;

import server.agents.capabilities.npc.AgentNpcInteractionRequest;
import server.agents.capabilities.npc.AgentNpcInteractionResult;

@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "NPC capability execution validates and mutates only the owning Agent session.")
public interface NpcGateway {
    AgentNpcInteractionResult executeNpcInteraction(AgentNpcInteractionRequest request,
            AgentNpcInteractionResult plan);
}

