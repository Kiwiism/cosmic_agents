package server.partner;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

public interface PartnerAgentLifecycleBridge {
    SpawnedPartner spawnFollowing(Character player, int partnerCharacterId, String partnerName);

    void release(SpawnedPartner partner);

    boolean hasPartnerAgent(int leaderCharacterId, int partnerCharacterId);

    boolean releasePartnerAgent(int leaderCharacterId, int partnerCharacterId);

    record SpawnedPartner(Character character, AgentRuntimeEntry runtimeEntry) {
    }
}
