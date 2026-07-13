package server.agents.capabilities.dialogue;

import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentReplyChannelStateRuntime;
import server.agents.runtime.AgentChatOrchestratorContext;
import server.agents.runtime.AgentMailboxAction;
import server.agents.runtime.AgentRuntimeEntry;

public record AgentChatMailboxAction(String message,
                                     AgentReplyChannel replyChannel) implements AgentMailboxAction<Boolean> {
    public AgentChatMailboxAction(String message) {
        this(message, null);
    }

    @Override
    public Boolean execute(AgentRuntimeEntry entry) {
        if (replyChannel != null) {
            AgentReplyChannelStateRuntime.setReplyChannel(entry, replyChannel);
        }
        return AgentChatRuntime.handleChat(message, new AgentChatOrchestratorContext(entry));
    }
}
