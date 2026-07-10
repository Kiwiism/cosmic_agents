package server.agents.capabilities.build;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

class AgentBuildRuntimeTest {
    @Test
    void spVariantCallbacksSetVariantAndAutoAssignSp() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentBuildService> buildManager = mockStatic(AgentBuildService.class)) {
            AgentBuildRuntime.spVariantCallbacks(entry).oneHanded();

            assertEquals(AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT, AgentBuildStateRuntime.spVariant(entry));
            replies.verify(() -> AgentReplyRuntime.replyNow(entry, AgentChatBuildFlow.oneHandedSpVariantReply()));
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
            AgentBuildRuntime.apBuildCallbacks(entry).selectBuild("dexless");

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

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class);
             MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            AgentBuildRuntime.jobAdvancementCallbacks(entry).advanceTo(Job.HUNTER);

            replies.verify(() -> AgentReplyRuntime.replyNow(eq(entry), argThat(message ->
                    message != null && message.contains("hunter"))));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(
                    org.mockito.ArgumentMatchers.eq(entry),
                    org.mockito.ArgumentMatchers.eq(900),
                    org.mockito.ArgumentMatchers.eq(1100),
                    org.mockito.ArgumentMatchers.any(Runnable.class)));
        }
    }

    @Test
    void confirmApBuildUsesAgentReplyRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentBuildRuntime.confirmApBuild(entry, "confirm");

            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "confirm"));
        }
    }
}
