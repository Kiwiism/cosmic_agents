package server.agents.integration;

import server.bots.BotEntry;
import server.bots.ReplyChannel;

/**
 * Agent-owned adapter for temporary BotEntry-backed reply-channel state.
 */
public final class AgentBotReplyChannelStateRuntime {
    private AgentBotReplyChannelStateRuntime() {
    }

    public static ReplyChannel replyChannel(BotEntry entry) {
        return entry.getReplyChannel();
    }

    public static void setReplyChannel(BotEntry entry, ReplyChannel channel) {
        entry.setReplyChannel(channel == null ? ReplyChannel.MAP : channel);
    }

    public static void setWhisper(BotEntry entry) {
        setReplyChannel(entry, ReplyChannel.WHISPER);
    }
}
