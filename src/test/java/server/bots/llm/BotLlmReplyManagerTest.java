package server.bots.llm;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotLlmRuntime;
import server.bots.BotEntry;

import java.lang.reflect.Constructor;
import java.util.concurrent.ScheduledFuture;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BotLlmReplyManagerTest {
    @Test
    void deliverReplyPartsRoutesImmediateAndFollowUpsThroughAgentReplyRuntime() throws Exception {
        BotEntry entry = newBotEntry();
        int oldDelay = BotLlmConfig.multiMessageDelayMs;
        BotLlmConfig.multiMessageDelayMs = 250;
        List<Long> delays = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        try (MockedStatic<AgentBotLlmRuntime> replies = mockStatic(AgentBotLlmRuntime.class)) {
            BotLlmReplyManager.deliverReplyParts(entry, List.of("one", "two", "three"), (action, delayMs) -> {
                delays.add(delayMs);
                actions.add(action);
            });

            replies.verify(() -> AgentBotLlmRuntime.replyNow(entry, "one"));
            org.junit.jupiter.api.Assertions.assertEquals(List.of(250L, 500L), delays);

            actions.forEach(Runnable::run);
            replies.verify(() -> AgentBotLlmRuntime.replyNow(entry, "two"));
            replies.verify(() -> AgentBotLlmRuntime.replyNow(entry, "three"));
        } finally {
            BotLlmConfig.multiMessageDelayMs = oldDelay;
        }
    }

    @Test
    void promptBuildersUseAgentIdentityBoundaryValues() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getName()).thenReturn("agent123");
        when(bot.getJob()).thenReturn(Job.THIEF);
        when(bot.getLevel()).thenReturn(30);
        BotEntry entry = newBotEntry(bot, null);

        String system = PromptBuilder.buildSystem(entry, SenderRelation.STRANGER, "Alice");
        String prompt = PromptBuilder.buildPrompt(entry, "Alice", "hi", "", List.of());

        assertTrue(system.contains("Your IGN is agent123."));
        assertTrue(system.contains("level 30 thief"));
        assertTrue(prompt.endsWith("agent123:"));
    }

    @Test
    void senderRelationUsesAgentIdentityBoundaryValues() throws Exception {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        Character sender = mock(Character.class);
        when(owner.getId()).thenReturn(10);
        when(sender.getId()).thenReturn(10);
        BotEntry entry = newBotEntry(bot, owner);

        assertEquals(SenderRelation.OWNER, SenderRelation.resolve(entry, sender));
    }

    private static BotEntry newBotEntry() throws Exception {
        return newBotEntry(mock(Character.class), mock(Character.class));
    }

    private static BotEntry newBotEntry(Character bot, Character owner) throws Exception {
        Constructor<BotEntry> constructor = BotEntry.class.getDeclaredConstructor(
                Character.class, Character.class, ScheduledFuture.class);
        constructor.setAccessible(true);
        return constructor.newInstance(bot, owner, null);
    }
}
