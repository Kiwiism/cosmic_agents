package server.agents.capabilities.dialogue.llm;

import server.agents.capabilities.dialogue.llm.AgentLlmConfig;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeHandle;
import server.bots.BotEntry;

import java.lang.reflect.Constructor;
import java.util.concurrent.ScheduledFuture;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLlmReplyServiceTest {
    @Test
    void deliverReplyPartsRoutesImmediateAndFollowUpsThroughAgentReplyRuntime() throws Exception {
        BotEntry entry = newBotEntry();
        int oldDelay = AgentLlmConfig.multiMessageDelayMs;
        AgentLlmConfig.multiMessageDelayMs = 250;
        List<Long> delays = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<String> replies = new ArrayList<>();
        TestHandle handle = new TestHandle();

        try {
            AgentLlmReplyService.deliverReplyParts(handle, List.of("one", "two", "three"), (replyHandle, message) -> {
                assertEquals(handle, replyHandle);
                replies.add(message);
            }, (action, delayMs) -> {
                delays.add(delayMs);
                actions.add(action);
            });

            assertEquals(List.of("one"), replies);
            assertEquals(List.of(250L, 500L), delays);

            actions.forEach(Runnable::run);
            assertEquals(List.of("one", "two", "three"), replies);
        } finally {
            AgentLlmConfig.multiMessageDelayMs = oldDelay;
        }
    }

    @Test
    void AgentPromptBuildersUseAgentIdentityBoundaryValues() throws Exception {
        Character bot = mock(Character.class);
        when(bot.getName()).thenReturn("agent123");
        when(bot.getJob()).thenReturn(Job.THIEF);
        when(bot.getLevel()).thenReturn(30);
        String system = AgentPromptBuilder.buildSystem(bot, AgentSenderRelation.STRANGER, "Alice");
        String prompt = AgentPromptBuilder.buildPrompt("agent123", "", "Alice", "hi", "", List.of());

        assertTrue(system.contains("Your IGN is agent123."));
        assertTrue(system.contains("level 30 thief"));
        assertTrue(prompt.endsWith("agent123:"));
    }

    @Test
    void AgentSenderRelationUsesAgentIdentityBoundaryValues() throws Exception {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        Character sender = mock(Character.class);
        when(owner.getId()).thenReturn(10);
        when(sender.getId()).thenReturn(10);

        assertEquals(AgentSenderRelation.OWNER, AgentSenderRelation.resolve(bot, owner, sender));
    }

    @Test
    void AgentSituationBuilderUsesResolvedSnapshotValues() {
        Character bot = mock(Character.class);
        when(bot.getLevel()).thenReturn(30);
        when(bot.getExp()).thenReturn(0);
        when(bot.getParty()).thenReturn(null);

        String situation = AgentSituationBuilder.build(
                bot,
                null,
                true,
                false,
                true,
                "pots",
                1_000L,
                13_000L);

        assertTrue(situation.contains("Status: grinding (camping this spot)"));
        assertTrue(situation.contains("Level 30"));
        assertTrue(situation.contains("Last command from owner: \"pots\" (12s ago)"));
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

    private static final class TestHandle implements AgentRuntimeHandle {
    }
}
