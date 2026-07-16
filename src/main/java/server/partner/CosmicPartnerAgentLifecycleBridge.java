package server.partner;

import client.Character;
import server.Trade;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentInventoryRuntimeAdapters;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentTransitionBarrierState;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.build.AgentMakerService;
import server.agents.capabilities.supplies.AgentAutopotCleanupService;
import server.agents.plans.AgentScriptTaskQueueService;
import server.agents.capabilities.trade.AgentOfferService;
import server.agents.capabilities.trade.AgentTradeLifecycleRuntimeService;
import server.agents.capabilities.dialogue.AgentPendingActionStateRuntime;

public final class CosmicPartnerAgentLifecycleBridge implements PartnerAgentLifecycleBridge {
    public static final CosmicPartnerAgentLifecycleBridge INSTANCE = new CosmicPartnerAgentLifecycleBridge();

    private CosmicPartnerAgentLifecycleBridge() {
    }

    @Override
    public SpawnedPartner spawnFollowing(Character player, int partnerCharacterId, String partnerName) {
        if (player == null) {
            throw new IllegalArgumentException("Partner owner is required");
        }
        int ownerCharacterId = player.getId();
        if (!PartnerInteractionPolicy.reservePendingActivation(
                partnerCharacterId, ownerCharacterId)) {
            throw new IllegalStateException("That adventuring partner is already being activated");
        }
        AgentRuntimeEntry previous = null;
        boolean restoreGenericMarkerOnFailure = false;
        boolean activated = false;
        try {
            previous = AgentRuntimeRegistry.findByCharacterId(
                    ownerCharacterId, partnerCharacterId);
            if (previous != null) {
                restoreGenericMarkerOnFailure = !previous.isPartnerManaged();
                try (AgentTransitionBarrierState.PauseLease ignored =
                             previous.transitionBarrierState().pauseAndDrain()) {
                    preparePartnerManagedEntry(previous, AgentRuntimeIdentityRuntime.bot(previous));
                }
            }
            AgentLifecycleService.AgentSpawnResult spawn;
            try {
                spawn = AgentInteractionRuntime.spawnPartnerAgentForLeader(player, partnerName);
            } catch (RuntimeException failure) {
                releaseNewEntryAfterFailedSpawn(ownerCharacterId, partnerCharacterId, previous);
                throw failure;
            }
            if (!spawn.success()) {
                releaseNewEntryAfterFailedSpawn(ownerCharacterId, partnerCharacterId, previous);
                throw new IllegalStateException(spawn.errorMessage());
            }
            if (spawn.agent() == null || spawn.agent().getId() != partnerCharacterId) {
                releaseNewEntryAfterFailedSpawn(ownerCharacterId, partnerCharacterId, previous);
                throw new IllegalStateException("Partner Agent spawn returned the wrong character");
            }
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByCharacterId(
                    ownerCharacterId, partnerCharacterId);
            if (entry == null) {
                throw new IllegalStateException("Partner Agent was spawned without a runtime entry");
            }
            spawn.agent().setMapTransitionComplete();
            preparePartnerManagedEntry(entry, spawn.agent());
            activated = true;
            return new SpawnedPartner(spawn.agent(), entry);
        } finally {
            if (!activated && restoreGenericMarkerOnFailure && previous != null) {
                try (AgentTransitionBarrierState.PauseLease ignored =
                             previous.transitionBarrierState().pauseAndDrain()) {
                    previous.clearPartnerManaged();
                }
            }
            PartnerInteractionPolicy.releasePendingActivation(
                    partnerCharacterId, ownerCharacterId);
        }
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

    private void preparePartnerManagedEntry(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null) {
            return;
        }
        entry.markPartnerManaged();
        Trade.cancelTrade(agent, Trade.TradeResult.NO_RESPONSE);
        AgentTradeLifecycleRuntimeService.resetTradeState(
                entry,
                agent,
                AgentInventoryRuntimeAdapters.tradeLifecycleRuntimeCallbacks());
        AgentShopService.cancelShopVisit(entry);
        AgentMakerService.clearAgentRuntimeState(agent.getId());
        AgentScriptTaskQueueService.clearTasks(entry);
        AgentPendingActionStateRuntime.clearPendingAction(entry);
        AgentOfferService.clearPendingOfferForOwnerAsk(entry);
    }

    private void leavePartyAndDisconnect(Character agent) {
        if (agent == null) {
            return;
        }
        if (agent.getParty() != null) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        }
        if (agent.getClient() != null) {
            AgentAutopotCleanupService.preserveOnNextCleanup(agent.getId());
            try {
                agent.getClient().forceDisconnect();
            } finally {
                AgentAutopotCleanupService.cancelPreservation(agent.getId());
            }
        }
    }
}
