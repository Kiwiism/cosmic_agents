package server.agents.commands;

import server.agents.capabilities.trade.AgentTransferCommandService;
import server.agents.capabilities.trade.AgentTransferService;
import server.agents.runtime.AgentDismissRuntime;
import server.agents.runtime.AgentRecruitRuntime;
import server.agents.runtime.AgentRecruitService;
import server.agents.runtime.AgentRuntimeEntry;

import client.Character;

import java.util.function.Consumer;

/**
 * Agent-owned lifecycle chat command wiring for recruit, transfer, and dismiss.
 */
public final class AgentLifecycleCommandCoordinator {
    private AgentLifecycleCommandCoordinator() {
    }

    public static String recruitAgent(int leaderCharId,
                                      Character leader,
                                      String agentName,
                                      AgentRecruitService.AgentRegistrar registrar) {
        return AgentRecruitRuntime.recruitAgent(leaderCharId, leader, agentName, registrar);
    }

    public static String transferAgent(int leaderCharId,
                                       Character leader,
                                       String agentName,
                                       String targetName,
                                       AgentTransferService.AgentStopper stopper,
                                       AgentTransferService.AgentRegistrar registrar) {
        return AgentLeaderTransferCoordinator.transferAgent(
                leaderCharId, leader, agentName, targetName, stopper, registrar);
    }

    public static boolean dismissAgent(int leaderCharId,
                                       String agentName,
                                       Consumer<AgentRuntimeEntry> stopAgent) {
        return AgentDismissRuntime.dismissAgentByName(leaderCharId, agentName, stopAgent);
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
