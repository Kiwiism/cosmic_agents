package server.agents.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentReplyChannelStateTest {
    @Test
    void defaultsToMapAndNormalizesNull() {
        AgentReplyChannelState state = new AgentReplyChannelState();

        assertEquals(AgentReplyChannel.MAP, state.channel());

        state.setChannel(AgentReplyChannel.PARTY);
        assertEquals(AgentReplyChannel.PARTY, state.channel());

        state.setWhisper();
        assertEquals(AgentReplyChannel.WHISPER, state.channel());

        state.setChannel(null);
        assertEquals(AgentReplyChannel.MAP, state.channel());
    }
}
