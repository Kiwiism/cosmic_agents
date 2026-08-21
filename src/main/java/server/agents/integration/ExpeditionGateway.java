package server.agents.integration;

import client.Character;
import server.expeditions.Expedition;
import server.expeditions.ExpeditionType;

/** Semantic boundary for channel-owned expedition lookup and removal. */
@AgentGatewayAffinity(
        value = AgentGatewayThreadAffinity.SHARD_SAFE_DIRECT,
        rationale = "Expedition test operations execute on the owning Agent scheduler/channel context.")
public interface ExpeditionGateway {
    Expedition current(Character character, ExpeditionType type);

    void remove(Character character, Expedition expedition);
}
