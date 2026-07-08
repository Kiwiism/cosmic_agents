package server.agents.runtime;

import client.Character;
import server.agents.commands.AgentTransferCommand;
import server.agents.integration.AgentCommandTargetResolver;

public final class AgentTransferCommandService {
    private AgentTransferCommandService() {
    }

    public record Hooks(TransferAction transferAction,
                        LeaderMessage leaderMessage) {
    }

    @FunctionalInterface
    public interface TransferAction {
        String transfer(int leaderCharId, Character leader, String agentName, String targetName);
    }

    @FunctionalInterface
    public interface LeaderMessage {
        void send(Character leader, String message);
    }

    public static boolean handleTransferCommand(Character leader, String message, Hooks hooks) {
        AgentTransferCommand command = AgentCommandTargetResolver.matchAgentTransferCommand(message);
        if (command == null) {
            return false;
        }

        String error = hooks.transferAction().transfer(
                leader.getId(),
                leader,
                command.botName(),
                command.targetName());
        if (error != null) {
            hooks.leaderMessage().send(leader, error);
        } else {
            hooks.leaderMessage().send(
                    leader,
                    "Bot '" + command.botName() + "' transferred to " + command.targetName() + ".");
        }
        return true;
    }
}
