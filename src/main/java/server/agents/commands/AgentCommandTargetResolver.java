package server.agents.commands;

import java.util.List;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;

public final class AgentCommandTargetResolver {
    private AgentCommandTargetResolver() {
    }

    public static AgentTransferCommand matchAgentTransferCommand(String message) {
        AgentCommandParser.AgentTransferCommand command = AgentCommandParser.matchTransferCommand(message);
        if (command == null) {
            return null;
        }

        return new AgentTransferCommand(command.agentName(), command.targetName());
    }

    public static <E extends AgentRuntimeEntry> AgentTargetedCommandMatch<E> resolveTargetedAgent(List<E> entries,
                                                                                                String message) {
        List<AgentNamedCommandTarget<E>> targets = entries == null
                ? null
                : entries.stream()
                .map(entry -> new AgentNamedCommandTarget<>(entry, AgentRuntimeIdentityRuntime.botName(entry)))
                .toList();
        AgentCommandParser.TargetedAgentMatch<AgentNamedCommandTarget<E>> match =
                AgentCommandParser.resolveTargetedAgent(targets, message);
        return new AgentTargetedCommandMatch<>(
                match.target() == null ? null : match.target().entry(),
                match.commandText(),
                match.feedbackMessage());
    }
}
