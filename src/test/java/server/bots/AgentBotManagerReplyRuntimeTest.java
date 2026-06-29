package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.AgentBotManagerReplyRuntime;
import server.agents.integration.AgentBotReplyRuntime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotManagerReplyRuntimeTest {
    @Test
    void managerReplyMethodsDelegateToAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotManagerReplyRuntime.queueReply(entry, "queued");
            AgentBotManagerReplyRuntime.replyNow(entry, "reply");
            AgentBotManagerReplyRuntime.visibleSayNow(entry, "visible");
            AgentBotManagerReplyRuntime.sayMapNow(bot, "map");
            AgentBotManagerReplyRuntime.sayNow(bot, AgentReplyChannel.PARTY, "channel");
            AgentBotManagerReplyRuntime.sayPartyNow(bot, "party");

            replies.verify(() -> AgentBotReplyRuntime.queueReply(entry, "queued"));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "reply"));
            replies.verify(() -> AgentBotReplyRuntime.visibleSayNow(entry, "visible"));
            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(bot, "map"));
            replies.verify(() -> AgentBotReplyRuntime.sayNow(bot, AgentReplyChannel.PARTY, "channel"));
            replies.verify(() -> AgentBotReplyRuntime.sayPartyNow(bot, "party"));
        }
    }
}
