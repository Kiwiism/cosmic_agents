package server.partner;

import client.Character;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

public final class CosmicPartnerAgentLifecycleBridge implements PartnerAgentLifecycleBridge {
    public static final CosmicPartnerAgentLifecycleBridge INSTANCE = new CosmicPartnerAgentLifecycleBridge();

    private CosmicPartnerAgentLifecycleBridge() {
    }

    @Override
    public SpawnedPartner spawnFollowing(Character player, String partnerName) {
        AgentLifecycleService.AgentSpawnResult spawn = AgentInteractionRuntime.spawnAgentForLeader(player, partnerName);
        if (!spawn.success()) {
            throw new IllegalStateException(spawn.errorMessage());
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(player.getId(), spawn.agent().getId());
        if (entry == null) {
            throw new IllegalStateException("Partner Agent was spawned without a runtime entry");
        }
        entry.markPartnerManaged();
        return new SpawnedPartner(spawn.agent(), entry);
    }

    @Override
    public void release(SpawnedPartner partner) {
        if (partner == null || partner.character() == null) {
            return;
        }
        if (partner.runtimeEntry() != null) {
            AgentRuntimeCleanupService.removeAgent(partner.runtimeEntry());
        }
        if (partner.character().getClient() != null) {
            partner.character().getClient().forceDisconnect();
        }
    }
}
