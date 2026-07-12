package server.partner;

import client.Character;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
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
    public SpawnedPartner spawnFollowing(Character player, int partnerCharacterId, String partnerName) {
        AgentRuntimeEntry previous = AgentRuntimeRegistry.findByCharacterId(player.getId(), partnerCharacterId);
        AgentLifecycleService.AgentSpawnResult spawn;
        try {
            spawn = AgentInteractionRuntime.spawnAgentForLeader(player, partnerName);
        } catch (RuntimeException failure) {
            releaseNewEntryAfterFailedSpawn(player.getId(), partnerCharacterId, previous);
            throw failure;
        }
        if (!spawn.success()) {
            releaseNewEntryAfterFailedSpawn(player.getId(), partnerCharacterId, previous);
            throw new IllegalStateException(spawn.errorMessage());
        }
        if (spawn.agent() == null || spawn.agent().getId() != partnerCharacterId) {
            releaseNewEntryAfterFailedSpawn(player.getId(), partnerCharacterId, previous);
            throw new IllegalStateException("Partner Agent spawn returned the wrong character");
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(player.getId(), partnerCharacterId);
        if (entry == null) {
            throw new IllegalStateException("Partner Agent was spawned without a runtime entry");
        }
        spawn.agent().setMapTransitionComplete();
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
        leavePartyAndDisconnect(partner.character());
    }

    @Override
    public boolean hasPartnerAgent(int leaderCharacterId, int partnerCharacterId) {
        return AgentRuntimeRegistry.findByCharacterId(leaderCharacterId, partnerCharacterId) != null;
    }

    @Override
    public boolean releasePartnerAgent(int leaderCharacterId, int partnerCharacterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(leaderCharacterId, partnerCharacterId);
        if (entry == null) {
            return false;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentRuntimeCleanupService.removeAgent(entry);
        leavePartyAndDisconnect(agent);
        return true;
    }

    private void releaseNewEntryAfterFailedSpawn(int leaderCharacterId,
                                                  int partnerCharacterId,
                                                  AgentRuntimeEntry previous) {
        AgentRuntimeEntry current = AgentRuntimeRegistry.findByCharacterId(
                leaderCharacterId, partnerCharacterId);
        if (current != null && current != previous) {
            releasePartnerAgent(leaderCharacterId, partnerCharacterId);
        }
    }

    private void leavePartyAndDisconnect(Character agent) {
        if (agent == null) {
            return;
        }
        if (agent.getParty() != null) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        }
        if (agent.getClient() != null) {
            agent.getClient().forceDisconnect();
        }
    }
}
