package server.bots;

import server.agents.commands.AgentReplyChannel;

public enum ReplyChannel {
    MAP,
    PARTY,
    WHISPER;

    public AgentReplyChannel toAgentReplyChannel() {
        return AgentReplyChannel.valueOf(name());
    }
}
