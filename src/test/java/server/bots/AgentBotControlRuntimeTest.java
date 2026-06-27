package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.integration.AgentBotControlReplyRuntime;
import server.agents.integration.AgentBotControlRuntime;
import server.agents.integration.AgentBotControlSchedulerRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AgentBotControlRuntimeTest {
    @Test
    void toggleCallbacksScheduleLegacyControlSideEffects() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatToggleFlow.ToggleCallbacks callbacks = AgentBotControlRuntime.toggleCallbacks(entry);

        try (MockedStatic<AgentBotControlSchedulerRuntime> scheduler =
                     mockStatic(AgentBotControlSchedulerRuntime.class)) {
            callbacks.setSupport(false);
            callbacks.setHeals(false);
            callbacks.setBuffConsumables(true);
            callbacks.setBuffConsumablesCheapMode(false);
            callbacks.setProactiveOffers(false);

            scheduler.verify(() -> AgentBotControlSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(5));
        }
    }

    @Test
    void buffQueryCallbacksScheduleLegacyReportSideEffects() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatBuffQueryFlow.BuffQueryCallbacks callbacks = AgentBotControlRuntime.buffQueryCallbacks(entry);

        try (MockedStatic<AgentBotControlSchedulerRuntime> scheduler =
                     mockStatic(AgentBotControlSchedulerRuntime.class)) {
            callbacks.reportBuffList();
            callbacks.reportBuffDebug();
            callbacks.reportSkillBuffDebug();

            scheduler.verify(() -> AgentBotControlSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(3));
        }
    }

    @Test
    void respecCallbacksScheduleLegacyBuildSideEffects() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatRespecFlow.RespecCallbacks callbacks = AgentBotControlRuntime.respecCallbacks(entry);

        try (MockedStatic<AgentBotControlSchedulerRuntime> scheduler =
                     mockStatic(AgentBotControlSchedulerRuntime.class)) {
            callbacks.respecAp();
            callbacks.respecSp();

            scheduler.verify(() -> AgentBotControlSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(2));
        }
    }

    @Test
    void controlReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotControlReplyRuntime.replyNow(entry, "ok");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "ok"));
        }
    }

    @Test
    void controlSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotControlSchedulerRuntime.afterRandomDelay(500, 700, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(500, 700, action));
        }
    }
}
