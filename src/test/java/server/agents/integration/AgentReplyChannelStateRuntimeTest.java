package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentReplyChannelStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentReplyChannelStateRuntimeTest {
    @Test
    void adaptsReplyChannelAndDefaultsNullToMap() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(AgentReplyChannel.MAP, AgentReplyChannelStateRuntime.replyChannel(entry));

        AgentReplyChannelStateRuntime.setReplyChannel(entry, AgentReplyChannel.PARTY);
        assertEquals(AgentReplyChannel.PARTY, AgentReplyChannelStateRuntime.replyChannel(entry));

        AgentReplyChannelStateRuntime.setWhisper(entry);
        assertEquals(AgentReplyChannel.WHISPER, AgentReplyChannelStateRuntime.replyChannel(entry));

        AgentReplyChannelStateRuntime.setReplyChannel(entry, null);
        assertEquals(AgentReplyChannel.MAP, AgentReplyChannelStateRuntime.replyChannel(entry));
    }
}
