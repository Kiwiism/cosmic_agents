package server.agents.integration;

import java.util.List;
import server.agents.commands.AgentCommandParser;
import server.agents.commands.AgentTargetedCommandMatch;
import server.agents.commands.AgentTransferCommand;
import server.bots.BotEntry;

public final class AgentBotCommandParser {
    private AgentBotCommandParser() {
    }

    public static AgentTransferCommand matchBotTransferCommand(String message) {
        AgentCommandParser.AgentTransferCommand command = AgentCommandParser.matchTransferCommand(message);
        if (command == null) {
            return null;
        }

        return new AgentTransferCommand(command.agentName(), command.targetName());
    }

    public static AgentTargetedCommandMatch<BotEntry> resolveTargetedBot(List<BotEntry> entries, String message) {
        List<AgentBotCommandTarget> targets = entries == null
                ? null
                : entries.stream().map(AgentBotCommandTarget::new).toList();
        AgentCommandParser.TargetedAgentMatch<AgentBotCommandTarget> match =
                AgentCommandParser.resolveTargetedAgent(targets, message);
        return new AgentTargetedCommandMatch<>(
                match.target() == null ? null : match.target().entry(),
                match.commandText(),
                match.feedbackMessage());
    }
}
