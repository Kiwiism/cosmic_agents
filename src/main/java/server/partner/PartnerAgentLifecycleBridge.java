package server.partner;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public interface PartnerAgentLifecycleBridge {
    SpawnedPartner spawnFollowing(Character player, String partnerName);

    void release(SpawnedPartner partner);

    record SpawnedPartner(Character character, AgentRuntimeEntry runtimeEntry) {
    }
}
