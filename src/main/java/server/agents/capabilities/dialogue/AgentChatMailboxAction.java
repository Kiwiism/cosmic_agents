package server.agents.capabilities.dialogue;

import server.agents.runtime.AgentChatOrchestratorContext;
import server.agents.runtime.AgentMailboxAction;
import server.agents.runtime.AgentRuntimeEntry;

public record AgentChatMailboxAction(String message) implements AgentMailboxAction<Boolean> {
    @Override
    public Boolean execute(AgentRuntimeEntry entry) {
        return AgentChatRuntime.handleChat(message, new AgentChatOrchestratorContext(entry));
    }
}
