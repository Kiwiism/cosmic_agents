package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotReplyChannelStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBotReplyChannelStateRuntimeTest {
    @Test
    void adaptsReplyChannelAndDefaultsNullToMap() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(AgentReplyChannel.MAP, AgentBotReplyChannelStateRuntime.replyChannel(entry));

        AgentBotReplyChannelStateRuntime.setReplyChannel(entry, AgentReplyChannel.PARTY);
        assertEquals(AgentReplyChannel.PARTY, AgentBotReplyChannelStateRuntime.replyChannel(entry));

        AgentBotReplyChannelStateRuntime.setWhisper(entry);
        assertEquals(AgentReplyChannel.WHISPER, AgentBotReplyChannelStateRuntime.replyChannel(entry));

        AgentBotReplyChannelStateRuntime.setReplyChannel(entry, null);
        assertEquals(AgentReplyChannel.MAP, AgentBotReplyChannelStateRuntime.replyChannel(entry));
    }
}
