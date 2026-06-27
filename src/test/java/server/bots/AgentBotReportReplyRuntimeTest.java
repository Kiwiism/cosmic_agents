package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotReportReplyRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentBotReportReplyRuntimeTest {
    @Test
    void queueReplyDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotReportReplyRuntime.queueReply(entry, "line");

            replies.verify(() -> AgentBotReplyRuntime.queueReply(entry, "line"));
        }
    }
}
