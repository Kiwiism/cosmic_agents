package server.agents.commands;

import server.agents.capabilities.trade.AgentTransferCommandService;
import server.agents.capabilities.trade.AgentTransferService;
import server.agents.auth.AgentOwnershipService;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRandom;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentScheduledTaskRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import client.Character;

import java.util.List;
import java.util.function.Consumer;

/**
 * Agent-owned lifecycle chat command wiring for recruit, transfer, and dismiss.
 */
public final class AgentLifecycleCommandCoordinator {
    private static final List<String> FAREWELL_MESSAGES = List.of(
            "ok", "sure", "alright", "gotcha",
            "later!", "see ya", "take care", "cya", "peace out");

    private AgentLifecycleCommandCoordinator() {
    }

    public static String recruitAgent(int leaderCharId,
                                      Character leader,
                                      String agentName,
                                      AgentRecruitService.AgentRegistrar registrar) {
        return AgentRecruitService.recruitAgent(
                leaderCharId,
                leader,
                agentName,
                new AgentRecruitService.Hooks(
                        AgentRuntimeRegistry::findUnclaimedOnlineAgentByName,
                        (candidateLeader, agent) -> AgentOwnershipService.getInstance()
                                .ensureCanControl(candidateLeader, agent),
                        registrar));
    }

    public static String transferAgent(int leaderCharId,
                                       Character leader,
                                       String agentName,
                                       String targetName,
                                       AgentTransferService.AgentStopper stopper,
                                       AgentTransferService.AgentRegistrar registrar) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(leaderCharId, agentName);
        if (entry != null && entry.isPartnerManaged()) {
            return "Agent E manages this Partner session. Release the Partner before transferring them.";
        }
        return AgentLeaderTransferCoordinator.transferAgent(
                leaderCharId, leader, agentName, targetName, stopper, registrar);
    }

    public static boolean dismissAgent(int leaderCharId,
                                       String agentName,
                                       Consumer<AgentRuntimeEntry> stopAgent) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByName(leaderCharId, agentName);
        if (entry != null && entry.isPartnerManaged()) {
            return false;
        }
        return AgentLifecycleService.dismissAgentByName(
                leaderCharId,
                agentName,
                new AgentLifecycleService.DismissHooks(
                        AgentScheduledTaskRuntime::cancelScheduledTask,
                        stopAgent,
                        AgentSchedulerRuntime::afterDelay,
                        () -> AgentRandom.randMs(400, 600),
                        AgentReplyRuntime::replyNow,
                        () -> AgentDialogueSelector.randomReply(FAREWELL_MESSAGES)));
    }

    public static boolean handleRecruitCommand(Character leader,
                                               String message,
                                               AgentRecruitCommandService.RecruitAction recruitAction) {
        return AgentRecruitCommandService.handleRecruitCommand(
                leader,
                message,
                new AgentRecruitCommandService.Hooks(
                        recruitAction,
                        Character::yellowMessage));
    }

    public static boolean handleTransferCommand(Character leader,
                                                String message,
                                                AgentTransferCommandService.TransferAction transferAction) {
        return AgentTransferCommandService.handleTransferCommand(
                leader,
                message,
                new AgentTransferCommandService.Hooks(
                        transferAction,
                        Character::yellowMessage));
    }

    public static boolean handleDismissCommand(Character leader,
                                               String message,
                                               AgentDismissCommandService.DismissAction dismissAction) {
        return AgentDismissCommandService.handleDismissCommand(
                leader,
                message,
                new AgentDismissCommandService.Hooks(
                        dismissAction,
                        Character::yellowMessage));
    }
}
