package server.agents.integration;

import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentReplyChannelState;
import server.agents.runtime.AgentRuntimeEntry;

/**
 * Agent-owned adapter for AgentRuntimeEntry-backed reply-channel state.
 */
public final class AgentBotReplyChannelStateRuntime {
    private AgentBotReplyChannelStateRuntime() {
    }

    public static AgentReplyChannel replyChannel(AgentRuntimeEntry entry) {
        return state(entry).channel();
    }

    public static void setReplyChannel(AgentRuntimeEntry entry, AgentReplyChannel channel) {
        state(entry).setChannel(channel);
    }

    public static void setWhisper(AgentRuntimeEntry entry) {
        state(entry).setWhisper();
    }

    private static AgentReplyChannelState state(AgentRuntimeEntry entry) {
        return entry.replyChannelState();
    }
}
