package server.agents.commands;

/**
 * Mutable reply-channel routing state for one live Agent.
 */
public final class AgentReplyChannelState {
    private AgentReplyChannel channel = AgentReplyChannel.MAP;

    public AgentReplyChannel channel() {
        return channel;
    }

    public void setChannel(AgentReplyChannel channel) {
        this.channel = channel == null ? AgentReplyChannel.MAP : channel;
    }

    public void setWhisper() {
        channel = AgentReplyChannel.WHISPER;
    }
}
