package server.agents.integration;

import server.agents.commands.AgentCommandTarget;
import server.bots.BotEntry;

public record AgentBotCommandTarget(BotEntry entry) implements AgentCommandTarget {
    @Override
    public String name() {
        return AgentBotRuntimeIdentityRuntime.botName(entry);
    }
}
