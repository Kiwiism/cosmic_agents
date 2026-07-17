package server.agents.runtime;

import server.agents.capabilities.movement.AgentFormationService;
import server.maps.reservation.CharacterSpaceOwner;
import server.maps.reservation.CharacterSpaceReservationRuntime;
import server.agents.capabilities.reactor.AgentReactorTargetReservationRuntime;
import server.agents.capabilities.recovery.AgentLeaderSafetyService;
import server.agents.capabilities.supplies.AgentAutopotCleanupService;
import server.agents.capabilities.build.AgentMakerService;
import server.agents.capabilities.dialogue.llm.AgentLlmReplyService;
import server.agents.capabilities.equipment.AgentAutoEquipThrottle;
import server.agents.capabilities.navigation.AgentNavigationWarmupService;
import server.agents.capabilities.supplies.AgentAmmoService;
import server.agents.capabilities.supplies.AgentPotionService;
import server.agents.capabilities.trade.AgentManualTradeService;
import server.agents.capabilities.trade.AgentTransferRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.diagnostics.AgentRunObservationRuntime;
import server.agents.runtime.async.AgentAsyncTaskGateway;

import client.Character;

import java.util.List;

public final class AgentRuntimeCleanupService {
    private AgentRuntimeCleanupService() {
    }

    public static void removeAgentsForLeader(int leaderCharId) {
        List<AgentRuntimeEntry> removedEntries = AgentRuntimeRegistry.unregisterLeader(leaderCharId);
        List<Integer> agentIds = removedEntries.stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(java.util.Objects::nonNull)
                .map(Character::getId)
                .toList();
        removedEntries.forEach(AgentLifecycleService::cancelScheduledTickIfPresent);
        AgentFormationService.formationsByLeaderId().remove(leaderCharId);
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().remove(leaderCharId);
        agentIds.forEach(AgentRuntimeCleanupService::clearAgentStateIfInactive);
        clearLeaderStateIfInactive(leaderCharId);
    }

    public static boolean removeAgentByCharacterId(int agentCharId) {
        int leaderId = AgentRuntimeRegistry.leaderIdForAgentCharacter(agentCharId);
        AgentRuntimeEntry removedEntry = AgentRuntimeRegistry.unregisterAgentCharacter(agentCharId);
        boolean removed = removedEntry != null;
        if (removed) {
            AgentLifecycleService.cancelScheduledTickIfPresent(removedEntry);
        }
        clearAgentStateIfInactive(agentCharId);
        if (leaderId >= 0) {
            clearLeaderStateIfInactive(leaderId);
        }
        return removed;
    }

    public static boolean removeAgent(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Character leader = AgentRuntimeIdentityRuntime.owner(entry);
        int leaderId = leader == null
                ? AgentRuntimeRegistry.leaderIdForAgentCharacter(AgentRuntimeIdentityRuntime.botId(entry))
                : leader.getId();
        boolean removed = leaderId >= 0 && AgentRuntimeRegistry.unregisterEntry(leaderId, entry);
        if (removed) {
            AgentLifecycleService.cancelScheduledTickIfPresent(entry);
        }
        if (agent != null) {
            clearAgentStateIfInactive(agent.getId());
        }
        if (leader != null) {
            clearLeaderStateIfInactive(leader.getId());
        }
        return removed;
    }

    public static boolean cleanupAgentRuntimeState(Character agent) {
        if (agent == null) {
            return false;
        }

        AgentRuntimeEntry activeEntry = findEntryForCharacterInstance(agent);
        boolean removed = activeEntry != null && removeAgent(activeEntry);
        clearAgentStateIfInactive(agent.getId());
        AgentAutopotCleanupService.clearAgentAutopotState(agent);
        return removed;
    }

    private static AgentRuntimeEntry findEntryForCharacterInstance(Character agent) {
        return AgentRuntimeRegistry.findByCharacterInstance(agent);
    }

    private static void clearAgentStateIfInactive(int agentId) {
        if (AgentRuntimeRegistry.hasActiveAgentCharacterId(agentId)) {
            return;
        }
        AgentAutoEquipThrottle.clearAgentRuntimeState(agentId);
        AgentAsyncTaskGateway.runtime().clearSession(agentId);
        AgentTransferRuntime.clearAgentRuntimeState(agentId);
        AgentLlmReplyService.clearAgentRuntimeState(agentId);
        AgentMakerService.clearAgentRuntimeState(agentId);
        AgentManualTradeService.clearAgentRuntimeState(agentId);
        CharacterSpaceReservationRuntime.release(CharacterSpaceOwner.character(agentId));
        AgentReactorTargetReservationRuntime.release(agentId);
        AgentRunObservationRuntime.unregister(agentId);
    }

    private static void clearLeaderStateIfInactive(int leaderId) {
        if (!AgentRuntimeRegistry.entriesForLeader(leaderId).isEmpty()) {
            return;
        }
        AgentFormationService.formationsByLeaderId().remove(leaderId);
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().remove(leaderId);
        AgentPotionService.clearLeaderRuntimeState(leaderId);
        AgentAmmoService.clearLeaderRuntimeState(leaderId);
        AgentNavigationWarmupService.clearLeaderRuntimeState(leaderId);
    }
}
