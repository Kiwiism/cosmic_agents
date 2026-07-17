package server.agents.runtime;

import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentRelaxerSpotReservationRuntime;
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
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.runtime.async.AgentAsyncTaskGateway;

import client.Character;

import java.util.List;

public final class AgentRuntimeCleanupService {
    private AgentRuntimeCleanupService() {
    }

    public static void removeAgentsForLeader(int leaderCharId) {
        removeAgentsForCohort(leaderCharId);
    }

    public static void removeAgentsForCohort(long cohortId) {
        List<AgentRuntimeEntry> removedEntries = AgentRuntimeRegistry.unregisterCohort(cohortId);
        List<Integer> agentIds = removedEntries.stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(java.util.Objects::nonNull)
                .map(Character::getId)
                .toList();
        removedEntries.forEach(entry -> {
            AgentLifecycleStateRuntime.transition(entry, AgentLifecyclePhase.OFFLINE, "cohort runtime cleanup");
            AgentLifecycleService.cancelScheduledTickIfPresent(entry);
        });
        int compatibilityId = Math.toIntExact(cohortId);
        AgentFormationService.formationsByLeaderId().remove(compatibilityId);
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().remove(compatibilityId);
        agentIds.forEach(AgentRuntimeCleanupService::clearAgentStateIfInactive);
        clearCohortStateIfInactive(cohortId);
    }

    public static boolean removeAgentByCharacterId(int agentCharId) {
        int leaderId = AgentRuntimeRegistry.leaderIdForAgentCharacter(agentCharId);
        AgentRuntimeEntry removedEntry = AgentRuntimeRegistry.unregisterAgentCharacter(agentCharId);
        boolean removed = removedEntry != null;
        if (removed) {
            AgentLifecycleStateRuntime.transition(removedEntry, AgentLifecyclePhase.OFFLINE, "runtime cleanup");
            AgentLifecycleService.cancelScheduledTickIfPresent(removedEntry);
        }
        clearAgentStateIfInactive(agentCharId);
        if (leaderId >= 0) {
            clearCohortStateIfInactive(leaderId);
        }
        return removed;
    }

    public static boolean removeAgent(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        long cohortId = AgentRelationshipRuntime.cohortId(entry);
        boolean removed = AgentRuntimeRegistry.unregisterEntry(entry);
        if (removed) {
            AgentLifecycleStateRuntime.transition(entry, AgentLifecyclePhase.OFFLINE, "runtime cleanup");
            AgentLifecycleService.cancelScheduledTickIfPresent(entry);
        }
        if (agent != null) {
            clearAgentStateIfInactive(agent.getId());
        }
        clearCohortStateIfInactive(cohortId);
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
        AgentRelaxerSpotReservationRuntime.release(agentId);
    }

    private static void clearCohortStateIfInactive(long cohortId) {
        if (!AgentRuntimeRegistry.entriesForCohort(cohortId).isEmpty()) {
            return;
        }
        int compatibilityId = Math.toIntExact(cohortId);
        AgentFormationService.formationsByLeaderId().remove(compatibilityId);
        AgentLeaderSafetyService.townClusterAnchorsByLeaderId().remove(compatibilityId);
        AgentPotionService.clearLeaderRuntimeState(compatibilityId);
        AgentAmmoService.clearLeaderRuntimeState(compatibilityId);
        AgentNavigationWarmupService.clearLeaderRuntimeState(compatibilityId);
    }
}
