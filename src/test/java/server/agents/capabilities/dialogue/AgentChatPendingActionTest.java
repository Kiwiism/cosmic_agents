package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatPendingActionTest {
    @Test
    void shouldExposeLegacyPendingActionValues() {
        assertEquals("skill_tree_choice", AgentChatPendingAction.SKILL_TREE_CHOICE);
        assertEquals("relog", AgentChatPendingAction.RELOG);
        assertEquals("logout", AgentChatPendingAction.LOGOUT);
        assertEquals("owner_away", AgentChatPendingAction.OWNER_AWAY);
        assertEquals("item_choice", AgentChatPendingAction.ITEM_CHOICE);
    }

    @Test
    void shouldClassifyPendingActions() {
        assertTrue(AgentChatPendingAction.isSkillTreeChoice("skill_tree_choice"));
        assertTrue(AgentChatPendingAction.isRelog("relog"));
        assertTrue(AgentChatPendingAction.isOwnerAway("owner_away"));
        assertTrue(AgentChatPendingAction.isItemChoice("item_choice"));
        assertFalse(AgentChatPendingAction.isRelog("logout"));
    }
}
