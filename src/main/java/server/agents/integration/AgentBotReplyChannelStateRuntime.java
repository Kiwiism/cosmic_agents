package server.agents.integration;

import server.agents.commands.AgentReplyChannel;
import server.agents.commands.AgentReplyChannelState;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed reply-channel state.
 */
public final class AgentBotReplyChannelStateRuntime {
    private AgentBotReplyChannelStateRuntime() {
    }

    public static AgentReplyChannel replyChannel(BotEntry entry) {
        return state(entry).channel();
    }

    public static void setReplyChannel(BotEntry entry, AgentReplyChannel channel) {
        state(entry).setChannel(channel);
    }

    public static void setWhisper(BotEntry entry) {
        state(entry).setWhisper();
    }

    private static AgentReplyChannelState state(BotEntry entry) {
        return entry.replyChannelState();
    }
}
