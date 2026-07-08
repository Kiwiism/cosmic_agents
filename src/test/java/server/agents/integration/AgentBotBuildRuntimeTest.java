package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.capabilities.build.AgentBuildService;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.integration.AgentBotBuildRuntime;
import server.agents.integration.AgentBotBuildStateRuntime;
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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class);
             MockedStatic<AgentBuildService> buildManager = mockStatic(AgentBuildService.class)) {
            AgentBotBuildRuntime.spVariantCallbacks(entry).oneHanded();

            assertEquals(AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT, AgentBotBuildStateRuntime.spVariant(entry));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, AgentChatBuildFlow.oneHandedSpVariantReply()));
            buildManager.verify(() -> AgentBuildService.autoAssignSp(entry, bot));
        }
    }

    @Test
    void apBuildCallbacksResolveAndApplySelectedBuild() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.THIEF);
        when(bot.getDex()).thenReturn(25);
        when(bot.getLuk()).thenReturn(40);
        when(bot.getStr()).thenReturn(4);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        ArgumentCaptor<AgentBuildService.ApBuild> buildCaptor = ArgumentCaptor.forClass(AgentBuildService.ApBuild.class);

        try (MockedStatic<AgentBuildService> buildManager = mockStatic(AgentBuildService.class)) {
            AgentBotBuildRuntime.apBuildCallbacks(entry).selectBuild("dexless");

            buildManager.verify(() -> AgentBuildService.setApBuild(
                    org.mockito.ArgumentMatchers.eq(entry),
                    buildCaptor.capture(),
                    org.mockito.ArgumentMatchers.anyString()));
            AgentBuildService.ApBuild build = buildCaptor.getValue();
            assertEquals(AgentBuildService.StatType.LUK, build.primaryStat());
            assertEquals(AgentBuildService.StatType.DEX, build.secondaryStat());
            assertEquals(4, build.secondaryTarget());
        }
    }

    @Test
    void jobAdvancementCallbackRepliesThenSchedulesAdvance() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotBuildRuntime.confirmApBuild(entry, "confirm");

            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "confirm"));
        }
    }
}
