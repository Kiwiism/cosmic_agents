package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotReplyChannelStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentBotReplyChannelStateRuntimeTest {
    @Test
    void adaptsReplyChannelAndDefaultsNullToMap() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(ReplyChannel.MAP, AgentBotReplyChannelStateRuntime.replyChannel(entry));

        AgentBotReplyChannelStateRuntime.setReplyChannel(entry, ReplyChannel.PARTY);
        assertEquals(ReplyChannel.PARTY, AgentBotReplyChannelStateRuntime.replyChannel(entry));

        AgentBotReplyChannelStateRuntime.setWhisper(entry);
        assertEquals(ReplyChannel.WHISPER, AgentBotReplyChannelStateRuntime.replyChannel(entry));

        AgentBotReplyChannelStateRuntime.setReplyChannel(entry, null);
        assertEquals(ReplyChannel.MAP, AgentBotReplyChannelStateRuntime.replyChannel(entry));
    }
}
