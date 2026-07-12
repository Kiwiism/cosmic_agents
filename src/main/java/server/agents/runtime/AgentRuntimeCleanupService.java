package server.agents.runtime;

import server.agents.capabilities.movement.AgentFormationService;
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
import server.partner.PartnerRecoveryService;

import client.Character;

import java.util.List;

public final class AgentRuntimeCleanupService {
    private AgentRuntimeCleanupService() {
    }

    public static void removeAgentsForLeader(int leaderCharId) {
        List<Character> agents = AgentRuntimeRegistry.entriesForLeader(leaderCharId).stream()
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(java.util.Objects::nonNull)
                .toList();
        agents.forEach(agent -> PartnerRecoveryService.getInstance().onAgentRuntimeRemoval(
                agent, "Agent leader runtime was removed"));
        AgentLifecycleService.removeLeaderEntries(
                AgentRuntimeRegistry.entriesByLeaderId(),
                AgentFormationService.formationsByLeaderId(),
                AgentLeaderSafetyService.townClusterAnchorsByLeaderId(),
                leaderCharId,
                AgentLifecycleService::cancelScheduledTickIfPresent);
        agents.stream().map(Character::getId)
                .forEach(AgentRuntimeCleanupService::clearAgentStateIfInactive);
        clearLeaderStateIfInactive(leaderCharId);
    }

    public static boolean removeAgentByCharacterId(int agentCharId) {
        List<AgentRuntimeEntry> matchingEntries = AgentRuntimeRegistry.entriesByLeaderId().values().stream()
                .flatMap(List::stream)
                .filter(runtime -> AgentRuntimeIdentityRuntime.botIs(runtime, agentCharId))
                .toList();
        List<Integer> leaderIds = AgentRuntimeRegistry.entriesByLeaderId().entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(runtime -> AgentRuntimeIdentityRuntime.botIs(runtime, agentCharId)))
                .map(java.util.Map.Entry::getKey)
                .toList();
        matchingEntries.stream().map(AgentRuntimeIdentityRuntime::bot)
                .filter(java.util.Objects::nonNull)
                .forEach(agent -> PartnerRecoveryService.getInstance().onAgentRuntimeRemoval(
                        agent, "Agent runtime was removed by character ID"));
        boolean removed = AgentLifecycleService.removeAgentByCharacterId(
                AgentRuntimeRegistry.entriesByLeaderId(),
                AgentFormationService.formationsByLeaderId(),
                AgentLeaderSafetyService.townClusterAnchorsByLeaderId(),
                agentCharId,
                AgentLifecycleService::cancelScheduledTickIfPresent);
        clearAgentStateIfInactive(agentCharId);
        leaderIds.forEach(AgentRuntimeCleanupService::clearLeaderStateIfInactive);
        return removed;
    }

    public static boolean removeAgent(AgentRuntimeEntry entry) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        Character leader = AgentRuntimeIdentityRuntime.owner(entry);
        PartnerRecoveryService.getInstance().onAgentRuntimeRemoval(
                agent, "Agent runtime was removed");
        boolean removed = AgentLifecycleService.removeAgentEntry(
                AgentRuntimeRegistry.entriesByLeaderId(),
                AgentFormationService.formationsByLeaderId(),
                AgentLeaderSafetyService.townClusterAnchorsByLeaderId(),
                entry,
                AgentLifecycleService::cancelScheduledTickIfPresent);
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
        for (List<AgentRuntimeEntry> entries : AgentRuntimeRegistry.entriesByLeaderId().values()) {
            for (AgentRuntimeEntry entry : entries) {
                if (AgentRuntimeIdentityRuntime.bot(entry) == agent) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static void clearAgentStateIfInactive(int agentId) {
        if (AgentRuntimeRegistry.hasActiveAgentCharacterId(agentId)) {
            return;
        }
        AgentAutoEquipThrottle.clearAgentRuntimeState(agentId);
        AgentTransferRuntime.clearAgentRuntimeState(agentId);
        AgentLlmReplyService.clearAgentRuntimeState(agentId);
        AgentMakerService.clearAgentRuntimeState(agentId);
        AgentManualTradeService.clearAgentRuntimeState(agentId);
    }

    private static void clearLeaderStateIfInactive(int leaderId) {
        if (!AgentRuntimeRegistry.entriesForLeader(leaderId).isEmpty()) {
            return;
        }
        AgentPotionService.clearLeaderRuntimeState(leaderId);
        AgentAmmoService.clearLeaderRuntimeState(leaderId);
        AgentNavigationWarmupService.clearLeaderRuntimeState(leaderId);
    }
}
