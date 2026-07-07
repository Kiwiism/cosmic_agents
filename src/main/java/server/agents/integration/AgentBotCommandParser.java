package server.agents.integration;

import java.util.List;
import server.agents.commands.AgentCommandParser;
import server.agents.commands.AgentNamedCommandTarget;
import server.agents.commands.AgentTargetedCommandMatch;
import server.agents.commands.AgentTransferCommand;
import server.agents.runtime.AgentRuntimeEntry;

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

    public static <E extends AgentRuntimeEntry> AgentTargetedCommandMatch<E> resolveTargetedBot(List<E> entries,
                                                                                                String message) {
        List<AgentNamedCommandTarget<E>> targets = entries == null
                ? null
                : entries.stream()
                .map(entry -> new AgentNamedCommandTarget<>(entry, AgentBotRuntimeIdentityRuntime.botName(entry)))
                .toList();
        AgentCommandParser.TargetedAgentMatch<AgentNamedCommandTarget<E>> match =
                AgentCommandParser.resolveTargetedAgent(targets, message);
        return new AgentTargetedCommandMatch<>(
                match.target() == null ? null : match.target().entry(),
                match.commandText(),
                match.feedbackMessage());
    }
}
