package server.agents.integration;

import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;

/**
 * Agent-owned adapter for temporary BotEntry-backed reply-channel state.
 */
public final class AgentBotReplyChannelStateRuntime {
    private AgentBotReplyChannelStateRuntime() {
    }

    public static AgentReplyChannel replyChannel(BotEntry entry) {
        return entry.getReplyChannel();
    }

    public static void setReplyChannel(BotEntry entry, AgentReplyChannel channel) {
        entry.setReplyChannel(channel == null ? AgentReplyChannel.MAP : channel);
    }

    public static void setWhisper(BotEntry entry) {
        setReplyChannel(entry, AgentReplyChannel.WHISPER);
    }
}
