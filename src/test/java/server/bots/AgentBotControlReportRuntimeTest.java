package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotChatReportRuntime;
import server.agents.integration.AgentBotControlReportRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentBotControlReportRuntimeTest {
    @Test
    void controlReportMethodsDelegateToChatReportRuntime() {
        Character bot = org.mockito.Mockito.mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotChatReportRuntime> reports = mockStatic(AgentBotChatReportRuntime.class)) {
            AgentBotControlReportRuntime.reportBuffDebug(entry);
            AgentBotControlReportRuntime.reportSkillBuffDebug(entry);

            reports.verify(() -> AgentBotChatReportRuntime.reportBuffDebug(entry, bot));
            reports.verify(() -> AgentBotChatReportRuntime.reportSkillBuffDebug(entry, bot));
        }
    }
}
