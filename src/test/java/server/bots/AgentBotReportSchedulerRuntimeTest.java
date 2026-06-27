package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatReportRuntime;
import server.agents.integration.AgentBotReportSchedulerRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.Mockito.mockStatic;

class AgentBotReportSchedulerRuntimeTest {
    @Test
    void reportSchedulerDelegatesToBroadSchedulerRuntime() {
        Runnable action = () -> {
        };
        AgentChatReportRuntime.ReportScheduler scheduler = AgentBotReportSchedulerRuntime.reportScheduler();

        try (MockedStatic<AgentBotSchedulerRuntime> broadScheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            scheduler.afterRandomDelay(500, 700, action);

            broadScheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(500, 700, action));
        }
    }
}
