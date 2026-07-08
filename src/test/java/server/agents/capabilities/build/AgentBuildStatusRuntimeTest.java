package server.agents.capabilities.build;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.trade.AgentOfferService;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.runtime.AgentActivityStateRuntime;
import server.agents.integration.AgentReplyRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBuildStatusRuntimeTest {
    @Test
    void checkBuildStatusRunsStatusRuntimeWithBuildActions() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);

        try (MockedStatic<AgentChatStatusRuntime> statusRuntime = mockStatic(AgentChatStatusRuntime.class)) {
            AgentBuildStatusRuntime.checkBuildStatus(entry, bot);

            statusRuntime.verify(() -> AgentChatStatusRuntime.checkStatus(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()));
        }
    }

    @Test
    void statusCheckActionsDelegateBuildPromptsAndAssignments() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);
        AgentChatStatusRuntime.StatusCheckActions actions =
                AgentBuildStatusRuntime.statusCheckActions(entry, bot);

        try (MockedStatic<AgentBuildService> buildManager = mockStatic(AgentBuildService.class)) {
            buildManager.when(() -> AgentBuildService.buildJobPrompt(entry, bot)).thenReturn("job?");
            buildManager.when(() -> AgentBuildService.buildSpVariantPrompt(entry, bot)).thenReturn("sp?");
            buildManager.when(() -> AgentBuildService.buildApPrompt(entry, bot)).thenReturn("ap?");

            assertEquals("job?", actions.buildJobPrompt());
            assertEquals("sp?", actions.buildSpVariantPrompt());
            assertEquals("ap?", actions.buildApPrompt());

            actions.autoAssignSp();
            actions.autoAssignAp();

            buildManager.verify(() -> AgentBuildService.autoAssignSp(entry, bot));
            buildManager.verify(() -> AgentBuildService.autoAssignAp(entry, bot));
        }
    }

    @Test
    void canOfferSpawnUpgradeRequiresOwnerAndIdleAndPendingState() {
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, owner, null);
        Character bot = mock(Character.class);
        AgentChatStatusRuntime.StatusCheckActions actions =
                AgentBuildStatusRuntime.statusCheckActions(entry, bot);

        try (MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class)) {
            offers.when(() -> AgentOfferService.hasPendingOffer(entry)).thenReturn(false);

            assertTrue(actions.canOfferSpawnUpgrade());

            AgentActivityStateRuntime.setOwnerWasAfk(entry, true);

            assertFalse(actions.canOfferSpawnUpgrade());
        }
    }

    @Test
    void statusCheckQueueReplyUsesAgentReplyRuntime() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Character bot = mock(Character.class);
        AgentChatStatusRuntime.StatusCheckActions actions =
                AgentBuildStatusRuntime.statusCheckActions(entry, bot);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            actions.queueReply("build?");

            replies.verify(() -> AgentReplyRuntime.queueReply(entry, "build?"));
        }
    }
}
