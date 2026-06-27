package server.bots;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.integration.AgentBotBuildRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

class AgentBotBuildRuntimeTest {
    @Test
    void spVariantCallbacksSetVariantAndAutoAssignSp() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<BotBuildManager> buildManager = mockStatic(BotBuildManager.class)) {
            AgentBotBuildRuntime.spVariantCallbacks(entry).oneHanded();

            assertEquals(AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT, entry.spVariant);
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, AgentChatBuildFlow.oneHandedSpVariantReply()));
            buildManager.verify(() -> BotBuildManager.autoAssignSp(entry, bot));
        }
    }

    @Test
    void apBuildCallbacksResolveAndApplySelectedBuild() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.THIEF);
        when(bot.getDex()).thenReturn(25);
        when(bot.getLuk()).thenReturn(40);
        when(bot.getStr()).thenReturn(4);
        BotEntry entry = new BotEntry(bot, null, null);
        ArgumentCaptor<BotBuildManager.ApBuild> buildCaptor = ArgumentCaptor.forClass(BotBuildManager.ApBuild.class);

        try (MockedStatic<BotBuildManager> buildManager = mockStatic(BotBuildManager.class)) {
            AgentBotBuildRuntime.apBuildCallbacks(entry).selectBuild("dexless");

            buildManager.verify(() -> BotBuildManager.setApBuild(
                    org.mockito.ArgumentMatchers.eq(entry),
                    buildCaptor.capture(),
                    org.mockito.ArgumentMatchers.anyString()));
            BotBuildManager.ApBuild build = buildCaptor.getValue();
            assertEquals(BotBuildManager.StatType.LUK, build.primaryStat());
            assertEquals(BotBuildManager.StatType.DEX, build.secondaryStat());
            assertEquals(4, build.secondaryTarget());
        }
    }

    @Test
    void jobAdvancementCallbackRepliesThenSchedulesAdvance() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotBuildRuntime.jobAdvancementCallbacks(entry).advanceTo(Job.HUNTER);

            replies.verify(() -> AgentBotReplyRuntime.replyNow(eq(entry), argThat(message ->
                    message != null && message.contains("hunter"))));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(
                    org.mockito.ArgumentMatchers.eq(900),
                    org.mockito.ArgumentMatchers.eq(1100),
                    org.mockito.ArgumentMatchers.any(Runnable.class)));
        }
    }

    @Test
    void confirmApBuildUsesAgentReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotBuildRuntime.confirmApBuild(entry, "confirm");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "confirm"));
        }
    }
}
