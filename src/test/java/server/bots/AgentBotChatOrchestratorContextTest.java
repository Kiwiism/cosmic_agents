package server.bots;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotChatOrchestratorContext;
import server.agents.integration.AgentBotPendingActionStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotChatOrchestratorContextTest {
    @Test
    void adaptsBotEntryStateToAgentChatContext() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.FIGHTER);
        when(bot.getLevel()).thenReturn(34);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentBotPendingActionStateRuntime.setPendingAction(entry, "item-choice");
        AgentBotPendingActionStateRuntime.setPendingDropCategory(entry, "scrolls");

        AgentBotChatOrchestratorContext context = new AgentBotChatOrchestratorContext(entry);

        assertTrue(context.hasPendingAction());
        assertEquals("item-choice", context.pendingActionState().pendingAction());
        assertEquals("scrolls", context.pendingActionState().pendingDropCategory());
        assertFalse(context.isWaitingForApBuild());
        assertFalse(context.isWaitingForSpVariant());
        assertEquals(Job.FIGHTER, context.currentJob());
        assertEquals(34, context.level());
        assertNotNull(context.sessionRequestCallbacks());
        assertNotNull(context.supplyRequestCallbacks());
        assertNotNull(context.socialCallbacks());
        assertNotNull(context.toggleCallbacks());
        assertNotNull(context.buffQueryCallbacks());
        assertNotNull(context.respecCallbacks());
        assertNotNull(context.equipmentCallbacks());
        assertNotNull(context.movementCallbacks());
        assertNotNull(context.spVariantCallbacks());
        assertNotNull(context.apBuildCallbacks());
        assertNotNull(context.utilityCallbacks());
        assertNotNull(context.itemQueryCallbacks());
        assertNotNull(context.reportCallbacks());
        assertNotNull(context.jobAdvancementCallbacks());
    }
}
