package server.agents.commands;

import server.agents.capabilities.trade.AgentTransferService;

import client.Character;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentScheduledTaskRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import java.util.List;

/**
 * Runtime hook bundle for transferring live Agents between leaders.
 */
public final class AgentLeaderTransferCoordinator {
    private AgentLeaderTransferCoordinator() {
    }

    public static String transferAgent(int leaderCharId,
                                       Character leader,
                                       String agentName,
                                       String targetName,
                                       AgentTransferService.AgentStopper stopper,
                                       AgentTransferService.AgentRegistrar registrar) {
        return AgentTransferService.transferAgent(
                leaderCharId,
                leader,
                agentName,
                targetName,
                new AgentTransferService.Hooks(
                        AgentLeaderTransferCoordinator::entriesByLeader,
                        AgentRuntimeRegistry::findByName,
                        AgentRuntimeRegistry::unregisterEntry,
                        (candidateLeader, target) -> candidateLeader.getMap().getCharacterByName(target),
                        (target, agent) -> AgentOwnershipService.getInstance().ensureCanControl(target, agent),
                        AgentScheduledTaskRuntime::cancelScheduledTask,
                        stopper,
                        registrar,
                        AgentSchedulerRuntime::afterDelay,
                        () -> AgentRandom.randMs(700, 900),
                        AgentReplyRuntime::sayMapNow,
                        () -> AgentDialogueSelector.randomReply(List.of(
                                "ok!",
                                "sure!",
                                "hey " + targetName + "!",
                                "hi " + targetName + "!"))));
    }

    @SuppressWarnings("unchecked")
    private static List<AgentRuntimeEntry> entriesByLeader(int leaderCharId) {
        List<AgentRuntimeEntry> entries = AgentRuntimeRegistry.entriesForLeader(leaderCharId);
        return entries.isEmpty() ? null : entries;
    }
}
