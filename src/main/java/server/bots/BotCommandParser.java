package server.bots;

import java.util.List;
import server.agents.commands.AgentCommandParser;
import server.agents.commands.AgentCommandTarget;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;

final class BotCommandParser {
    private BotCommandParser() {
    }

    record BotTransferCommand(String botName, String targetName) {}

    record TargetedBotMatch(BotEntry entry, String commandText, String feedbackMessage) {}

    static BotTransferCommand matchBotTransferCommand(String message) {
        AgentCommandParser.AgentTransferCommand command = AgentCommandParser.matchTransferCommand(message);
        if (command == null) {
            return null;
        }

        return new BotTransferCommand(command.agentName(), command.targetName());
    }

    static TargetedBotMatch resolveTargetedBot(List<BotEntry> entries, String message) {
        List<BotCommandTarget> targets = entries == null
                ? null
                : entries.stream().map(BotCommandTarget::new).toList();
        AgentCommandParser.TargetedAgentMatch<BotCommandTarget> match =
                AgentCommandParser.resolveTargetedAgent(targets, message);
        return new TargetedBotMatch(
                match.target() == null ? null : match.target().entry(),
                match.commandText(),
                match.feedbackMessage());
    }

    private record BotCommandTarget(BotEntry entry) implements AgentCommandTarget {
        @Override
        public String name() {
            return AgentBotRuntimeIdentityRuntime.botName(entry);
        }
    }
}
