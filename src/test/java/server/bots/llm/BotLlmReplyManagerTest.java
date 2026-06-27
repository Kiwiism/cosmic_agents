package server.bots.llm;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotReplyRuntime;
import server.bots.BotEntry;

import java.lang.reflect.Constructor;
import java.util.concurrent.ScheduledFuture;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class BotLlmReplyManagerTest {
    @Test
    void deliverReplyPartsRoutesImmediateAndFollowUpsThroughAgentReplyRuntime() throws Exception {
        BotEntry entry = newBotEntry();
        int oldDelay = BotLlmConfig.multiMessageDelayMs;
        BotLlmConfig.multiMessageDelayMs = 250;
        List<Long> delays = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            BotLlmReplyManager.deliverReplyParts(entry, List.of("one", "two", "three"), (action, delayMs) -> {
                delays.add(delayMs);
                actions.add(action);
            });

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "one"));
            org.junit.jupiter.api.Assertions.assertEquals(List.of(250L, 500L), delays);

            actions.forEach(Runnable::run);
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "two"));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "three"));
        } finally {
            BotLlmConfig.multiMessageDelayMs = oldDelay;
        }
    }

    private static BotEntry newBotEntry() throws Exception {
        Constructor<BotEntry> constructor = BotEntry.class.getDeclaredConstructor(
                Character.class, Character.class, ScheduledFuture.class);
        constructor.setAccessible(true);
        return constructor.newInstance(mock(Character.class), mock(Character.class), null);
    }
}
