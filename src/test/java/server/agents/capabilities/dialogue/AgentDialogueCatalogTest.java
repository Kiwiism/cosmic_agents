package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.bots.BotChatManager;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDialogueCatalogTest {
    @Test
    void botChatManagerUsesAgentDialogueCatalogPools() throws Exception {
        assertSame(AgentDialogueCatalog.followReplies(), botChatPool("FOLLOW_REPLIES"));
        assertSame(AgentDialogueCatalog.moveHereReplies(), botChatPool("MOVE_HERE_REPLIES"));
        assertSame(AgentDialogueCatalog.stopReplies(), botChatPool("STOP_REPLIES"));
        assertSame(AgentDialogueCatalog.greetingReplies(), botChatPool("GREETING_REPLIES"));
        assertSame(AgentDialogueCatalog.welcomeBackReplies(), botChatPool("WB_REPLIES"));
        assertSame(AgentDialogueCatalog.mesoReplies(), botChatPool("MESO_REPLIES"));
    }

    @Test
    void catalogPreservesExpectedLegacyLines() {
        assertTrue(AgentDialogueCatalog.followReplies().contains("w8 up"));
        assertTrue(AgentDialogueCatalog.ownerPotShortageReplies().contains("we're low on %s pots too, boss"));
        assertTrue(AgentDialogueCatalog.fameOkReplies().contains("famed %s"));
        assertTrue(AgentDialogueCatalog.welcomeBackOfflinePartyTemplates().contains("wb!! we're at %s"));
        assertTrue(AgentDialogueCatalog.relogConfirmPrompts().contains("save and relog? type yes"));
        assertTrue(AgentDialogueCatalog.logoutConfirmedReplies().contains("cya!!"));
        assertTrue(AgentDialogueCatalog.jobChangeReplyTemplates().contains("ok %s it is!"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> botChatPool(String fieldName) throws Exception {
        Field field = BotChatManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (List<String>) field.get(null);
    }
}
