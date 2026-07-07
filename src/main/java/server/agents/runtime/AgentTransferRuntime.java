package server.agents.runtime;

import client.Character;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import java.util.List;

/**
 * Runtime hook bundle for transferring live Agents between leaders.
 */
public final class AgentTransferRuntime {
    private AgentTransferRuntime() {
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
                        AgentTransferRuntime::entriesByLeader,
                        AgentRuntimeRegistry::findByName,
                        (candidateLeader, target) -> candidateLeader.getMap().getCharacterByName(target),
                        (target, agent) -> AgentOwnershipService.getInstance().ensureCanControl(target, agent),
                        AgentScheduledTaskRuntime::cancelScheduledTask,
                        stopper,
                        registrar,
                        AgentBotSchedulerRuntime::afterDelay,
                        () -> AgentRandom.randMs(700, 900),
                        AgentBotReplyRuntime::sayMapNow,
                        () -> AgentDialogueSelector.randomReply(List.of(
                                "ok!",
                                "sure!",
                                "hey " + targetName + "!",
                                "hi " + targetName + "!"))));
    }

    @SuppressWarnings("unchecked")
    private static List<AgentRuntimeEntry> entriesByLeader(int leaderCharId) {
        return (List<AgentRuntimeEntry>) (List<?>) AgentRuntimeRegistry.entriesByLeaderId().get(leaderCharId);
    }
}
