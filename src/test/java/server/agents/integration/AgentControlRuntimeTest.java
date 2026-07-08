package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.integration.AgentControlRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AgentControlRuntimeTest {
    @Test
    void toggleCallbacksScheduleLegacyControlSideEffects() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatToggleFlow.ToggleCallbacks callbacks = AgentControlRuntime.toggleCallbacks(entry);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            callbacks.setSupport(false);
            callbacks.setHeals(false);
            callbacks.setBuffConsumables(true);
            callbacks.setBuffConsumablesCheapMode(false);
            callbacks.setProactiveOffers(false);

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(5));
        }
    }

    @Test
    void buffQueryCallbacksScheduleLegacyReportSideEffects() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatBuffQueryFlow.BuffQueryCallbacks callbacks = AgentControlRuntime.buffQueryCallbacks(entry);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            callbacks.reportBuffList();
            callbacks.reportBuffDebug();
            callbacks.reportSkillBuffDebug();

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(3));
        }
    }

    @Test
    void respecCallbacksScheduleLegacyBuildSideEffects() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatRespecFlow.RespecCallbacks callbacks = AgentControlRuntime.respecCallbacks(entry);

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class)) {
            callbacks.respecAp();
            callbacks.respecSp();

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(2));
        }
    }

    @Test
    void broadReplyRuntimeStillSupportsControlReplies() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentReplyRuntime.replyNow(entry, "ok");

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "ok"));
        }
    }

    @Test
    void broadSchedulerRuntimeStillSupportsControlDelays() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentSchedulerRuntime.afterRandomDelay(500, 700, action);

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(500, 700, action));
        }
    }
}
