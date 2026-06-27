package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.integration.AgentBotBuildStatusRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotBuildStatusRuntimeTest {
    @Test
    void checkBuildStatusRunsStatusRuntimeWithBuildActions() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);

        try (MockedStatic<AgentChatStatusRuntime> statusRuntime = mockStatic(AgentChatStatusRuntime.class)) {
            AgentBotBuildStatusRuntime.checkBuildStatus(entry, bot);

            statusRuntime.verify(() -> AgentChatStatusRuntime.checkStatus(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any()));
        }
    }

    @Test
    void statusCheckActionsDelegateBuildPromptsAndAssignments() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);
        AgentChatStatusRuntime.StatusCheckActions actions =
                AgentBotBuildStatusRuntime.statusCheckActions(entry, bot);

        try (MockedStatic<BotBuildManager> buildManager = mockStatic(BotBuildManager.class)) {
            buildManager.when(() -> BotBuildManager.buildJobPrompt(entry, bot)).thenReturn("job?");
            buildManager.when(() -> BotBuildManager.buildSpVariantPrompt(entry, bot)).thenReturn("sp?");
            buildManager.when(() -> BotBuildManager.buildApPrompt(entry, bot)).thenReturn("ap?");

            assertEquals("job?", actions.buildJobPrompt());
            assertEquals("sp?", actions.buildSpVariantPrompt());
            assertEquals("ap?", actions.buildApPrompt());

            actions.autoAssignSp();
            actions.autoAssignAp();

            buildManager.verify(() -> BotBuildManager.autoAssignSp(entry, bot));
            buildManager.verify(() -> BotBuildManager.autoAssignAp(entry, bot));
        }
    }

    @Test
    void canOfferSpawnUpgradeRequiresOwnerAndIdleAndPendingState() {
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(null, owner, null);
        Character bot = mock(Character.class);
        AgentChatStatusRuntime.StatusCheckActions actions =
                AgentBotBuildStatusRuntime.statusCheckActions(entry, bot);

        try (MockedStatic<BotOfferManager> offers = mockStatic(BotOfferManager.class)) {
            offers.when(() -> BotOfferManager.hasPendingOffer(entry)).thenReturn(false);

            assertTrue(actions.canOfferSpawnUpgrade());

            entry.setOwnerWasAfk(true);

            assertFalse(actions.canOfferSpawnUpgrade());
        }
    }
}
